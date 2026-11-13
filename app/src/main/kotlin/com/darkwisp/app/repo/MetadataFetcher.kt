package com.darkwisp.app.repo

import com.darkwisp.app.nostr.ClientMessage
import com.darkwisp.app.nostr.Filter
import com.darkwisp.app.nostr.Nip19
import com.darkwisp.app.nostr.MarketplaceRelays
import com.darkwisp.app.nostr.Nip22
import com.darkwisp.app.nostr.Nip99
import com.darkwisp.app.relay.RelayConfig
import com.darkwisp.app.nostr.NostrUriData
import com.darkwisp.app.relay.OutboxRouter
import com.darkwisp.app.relay.RelayPool
import com.darkwisp.app.relay.SubscriptionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext
import java.util.concurrent.atomic.AtomicLong

/**
 * Handles batched fetching of profiles, reply counts, zap counts, and quoted events.
 * Extracted from FeedViewModel to reduce its size.
 */
class MetadataFetcher(
    private val relayPool: RelayPool,
    private val outboxRouter: OutboxRouter,
    private val subManager: SubscriptionManager,
    private val profileRepo: ProfileRepository,
    private val eventRepo: EventRepository,
    private val relayListRepo: RelayListRepository,
    private val scope: CoroutineScope,
    private val processingContext: CoroutineContext
) {
    // Batched profile fetching
    private val pendingProfilePubkeys = mutableSetOf<String>()
    private val pendingProfileRelayHints = mutableMapOf<String, List<String>>()
    private val inFlightProfiles = mutableSetOf<String>() // currently being fetched, prevents re-queueing
    private var profileBatchJob: Job? = null
    private var metaBatchCounter = 0
    private val profileAttempts = mutableMapOf<String, Int>()

    // Batched reply count fetching
    private val pendingReplyCountIds = mutableSetOf<String>()
    private var replyCountBatchJob: Job? = null
    private var replyCountBatchCounter = 0

    // Batched zap count fetching
    private val pendingZapCountIds = mutableSetOf<String>()
    private var zapCountBatchJob: Job? = null
    private var zapCountBatchCounter = 0

    // Quoted event fetching
    private val scannedQuoteEvents = mutableSetOf<String>()
    private val failedQuoteFetches = mutableMapOf<String, Long>() // eventId -> timestamp of failure
    private val nostrNoteUriRegex = Regex("""nostr:(note1|nevent1|naddr1)[a-z0-9]+""")
    private val validHexId = Regex("""^[0-9a-f]{64}$""")

    // Pubkeys that have been attempted MAX times and never found — stop fetching from feeds.
    // Only cleared on account switch. Profile page bypasses this via forceProfileFetch().
    private val exhaustedProfiles = mutableSetOf<String>()

    companion object {
        private const val MAX_PROFILE_ATTEMPTS = 2
        private const val QUOTE_RETRY_MS = 30_000L // retry failed quotes after 30s
        private const val ADDRESSABLE_EOSE_TIMEOUT_MS = 15_000L
        private const val ADDRESSABLE_EOSE_DRAIN_GRACE_MS = 3_000L
    }

    // Batched quote fetching (unified queue for inline + on-demand)
    private val pendingOnDemandQuotes = mutableSetOf<String>()
    private val inFlightQuotes = mutableSetOf<String>() // currently being fetched, prevents re-queueing
    private val pendingRelayHints = mutableMapOf<String, List<String>>()
    private var onDemandQuoteBatchJob: Job? = null
    private var onDemandQuoteBatchCounter = 0

    // Addressable event fetching
    private val inFlightAddressables = mutableSetOf<String>()
    private val addressableBatchCounter = AtomicLong()

    // Poll vote fetching for quoted polls
    private val fetchedPollVotes = mutableSetOf<String>()
    private var pollVoteBatchCounter = 0

    // Zap poll vote fetching for quoted zap polls
    private val fetchedZapPollVotes = mutableSetOf<String>()
    private var zapPollVoteBatchCounter = 0

    /** Returns URLs of the top relays by author coverage for quote lookups. Set by FeedViewModel. */
    var quoteRelayProvider: (() -> List<String>)? = null

    fun clear() {
        profileBatchJob?.cancel()
        replyCountBatchJob?.cancel()
        zapCountBatchJob?.cancel()
        onDemandQuoteBatchJob?.cancel()
        synchronized(pendingProfilePubkeys) {
            pendingProfilePubkeys.clear()
            pendingProfileRelayHints.clear()
            inFlightProfiles.clear()
        }
        synchronized(pendingReplyCountIds) { pendingReplyCountIds.clear() }
        synchronized(pendingZapCountIds) { pendingZapCountIds.clear() }
        synchronized(pendingOnDemandQuotes) {
            pendingOnDemandQuotes.clear()
            inFlightQuotes.clear()
            pendingRelayHints.clear()
            inFlightAddressables.clear()
        }
        profileAttempts.clear()
        exhaustedProfiles.clear()
        scannedQuoteEvents.clear()
        failedQuoteFetches.clear()
        synchronized(fetchedPollVotes) { fetchedPollVotes.clear() }
        synchronized(fetchedZapPollVotes) { fetchedZapPollVotes.clear() }
        metaBatchCounter = 0
        replyCountBatchCounter = 0
        zapCountBatchCounter = 0
        onDemandQuoteBatchCounter = 0
    }

    fun queueProfileFetch(pubkey: String) = addToPendingProfiles(pubkey)

    /**
     * Force-fetch a profile even if it was previously exhausted. Used by profile pages
     * where the user explicitly navigated to a profile and expects to see data.
     */
    fun forceProfileFetch(pubkey: String) {
        synchronized(pendingProfilePubkeys) {
            if (profileRepo.has(pubkey)) return
            exhaustedProfiles.remove(pubkey)
            profileAttempts.remove(pubkey)
        }
        addToPendingProfiles(pubkey)
    }

    fun addToPendingProfiles(pubkey: String, relayHints: List<String> = emptyList()) {
        synchronized(pendingProfilePubkeys) {
            if (profileRepo.has(pubkey)) return
            if (pubkey in exhaustedProfiles) return
            if (pubkey in inFlightProfiles) return
            val attempts = profileAttempts[pubkey] ?: 0
            if (attempts >= MAX_PROFILE_ATTEMPTS) {
                exhaustedProfiles.add(pubkey)
                return
            }
            if (pubkey in pendingProfilePubkeys) return
            pendingProfilePubkeys.add(pubkey)
            if (relayHints.isNotEmpty()) pendingProfileRelayHints[pubkey] = relayHints
            val shouldFlushNow = pendingProfilePubkeys.size >= 200
            if (shouldFlushNow) {
                profileBatchJob?.cancel()
                flushProfileBatch()
            } else if (profileBatchJob == null || profileBatchJob?.isActive != true) {
                profileBatchJob = scope.launch(processingContext) {
                    delay(100)
                    synchronized(pendingProfilePubkeys) { flushProfileBatch() }
                }
            }
        }
    }

    fun addToPendingReplyCounts(eventId: String) {
        synchronized(pendingReplyCountIds) {
            pendingReplyCountIds.add(eventId)
            val shouldFlushNow = pendingReplyCountIds.size >= 150
            if (shouldFlushNow) {
                replyCountBatchJob?.cancel()
                flushReplyCountBatch()
            } else if (replyCountBatchJob == null || replyCountBatchJob?.isActive != true) {
                replyCountBatchJob = scope.launch(processingContext) {
                    delay(500)
                    synchronized(pendingReplyCountIds) { flushReplyCountBatch() }
                }
            }
        }
    }

    fun addToPendingZapCounts(eventId: String) {
        synchronized(pendingZapCountIds) {
            pendingZapCountIds.add(eventId)
            val shouldFlushNow = pendingZapCountIds.size >= 150
            if (shouldFlushNow) {
                zapCountBatchJob?.cancel()
                flushZapCountBatch()
            } else if (zapCountBatchJob == null || zapCountBatchJob?.isActive != true) {
                zapCountBatchJob = scope.launch(processingContext) {
                    delay(500)
                    synchronized(pendingZapCountIds) { flushZapCountBatch() }
                }
            }
        }
    }

    fun requestQuotedEvent(eventId: String, relayHints: List<String> = emptyList()) {
        synchronized(pendingOnDemandQuotes) {
            if (eventRepo.getEvent(eventId) != null) return
            if (eventId in inFlightQuotes) return
            val failedAt = failedQuoteFetches[eventId]
            if (failedAt != null && System.currentTimeMillis() - failedAt < QUOTE_RETRY_MS) return
            if (eventId in pendingOnDemandQuotes) return
            // Clear old failure so this attempt is fresh
            if (failedAt != null) failedQuoteFetches.remove(eventId)
            pendingOnDemandQuotes.add(eventId)
            if (relayHints.isNotEmpty()) {
                pendingRelayHints[eventId] = relayHints
            }
            val shouldFlushNow = pendingOnDemandQuotes.size >= 200
            if (shouldFlushNow) {
                onDemandQuoteBatchJob?.cancel()
                flushOnDemandQuoteBatch()
            } else if (onDemandQuoteBatchJob == null || onDemandQuoteBatchJob?.isActive != true) {
                onDemandQuoteBatchJob = scope.launch(processingContext) {
                    delay(300)
                    synchronized(pendingOnDemandQuotes) { flushOnDemandQuoteBatch() }
                }
            }
        }
    }

    /**
     * Fetch an event expected to live on our own write/read relays.
     * Sends a single-event REQ directly — no batching or ephemeral connections needed.
     */
    fun requestOwnEvent(eventId: String) {
        if (eventRepo.getEvent(eventId) != null) return
        val subId = "own-${onDemandQuoteBatchCounter++}"
        val msg = ClientMessage.req(subId, Filter(ids = listOf(eventId)))
        relayPool.sendToWriteRelays(msg)
        relayPool.sendToReadRelays(msg)
        scope.launch {
            subManager.awaitEoseWithTimeout(subId, timeoutMs = 5_000)
            subManager.closeSubscription(subId)
        }
    }

    /**
     * Fetch kind 1018 poll responses for a quoted poll event.
     * Sends a REQ to top relays so the PollSection can render results.
     */
    fun requestPollVotes(pollEventId: String) {
        synchronized(fetchedPollVotes) {
            if (!fetchedPollVotes.add(pollEventId)) return
        }
        val subId = "qpoll-${pollVoteBatchCounter++}"
        val filter = Filter(kinds = listOf(com.darkwisp.app.nostr.Nip88.KIND_POLL_RESPONSE), eTags = listOf(pollEventId))
        val msg = ClientMessage.req(subId, filter)
        relayPool.sendToAllRelays(msg)
        // Also try poll-specified relays
        val pollEvent = eventRepo.getEvent(pollEventId)
        if (pollEvent != null) {
            val sentUrls = relayPool.getReadRelayUrls().toSet() + relayPool.getWriteRelayUrls().toSet()
            for (url in com.darkwisp.app.nostr.Nip88.parsePollRelays(pollEvent)) {
                if (url !in sentUrls) relayPool.sendToRelayOrEphemeral(url, msg)
            }
        }
        scope.launch {
            subManager.awaitEoseWithTimeout(subId, timeoutMs = 8_000)
            subManager.closeSubscription(subId)
        }
    }

    /**
     * Fetch kind 9735 zap receipts for a quoted zap poll event.
     * Sends a REQ to top relays so the ZapPollSection can render results.
     */
    fun requestZapPollVotes(pollEventId: String) {
        synchronized(fetchedZapPollVotes) {
            if (!fetchedZapPollVotes.add(pollEventId)) return
        }
        val subId = "qzpoll-${zapPollVoteBatchCounter++}"
        val filter = Filter(kinds = listOf(9735), eTags = listOf(pollEventId))
        val msg = ClientMessage.req(subId, filter)
        relayPool.sendToAllRelays(msg)
        // Also try poll-specified relays
        val pollEvent = eventRepo.getEvent(pollEventId)
        if (pollEvent != null) {
            val sentUrls = relayPool.getReadRelayUrls().toSet() + relayPool.getWriteRelayUrls().toSet()
            for (url in com.darkwisp.app.nostr.Nip69.parseZapPollRelays(pollEvent)) {
                if (url !in sentUrls) relayPool.sendToRelayOrEphemeral(url, msg)
            }
        }
        scope.launch {
            subManager.awaitEoseWithTimeout(subId, timeoutMs = 8_000)
            subManager.closeSubscription(subId)
        }
    }

    fun requestAddressableEvent(kind: Int, author: String, dTag: String, relayHints: List<String> = emptyList()) {
        val coordKey = "$kind:$author:$dTag"
        synchronized(pendingOnDemandQuotes) {
            if (coordKey in inFlightAddressables) return
            inFlightAddressables.add(coordKey)
        }

        val subId = "addr-${addressableBatchCounter.incrementAndGet()}"
        val filter = Filter(kinds = listOf(kind), authors = listOf(author), dTags = listOf(dTag), limit = 1)
        val msg = ClientMessage.req(subId, filter)

        // Run relay discovery and the actual lookup in one cancellable job. For NIP-99
        // addresses, relay hints alone are not enough: the author's NIP-65 outbox may
        // point to the relay that actually stores the listing.
        scope.launch(processingContext) {
            var eoseCollector: Job? = null
            try {
                val isMarketplaceListing = kind == Nip99.KIND_LISTING
                val safeHints = relayHints
                    .map { it.trim() }
                    .filter { RelayConfig.isValidUrl(it) }
                    .distinct()

                var sellerWriteRelays = emptyList<String>()
                if (isMarketplaceListing) {
                    sellerWriteRelays = relayListRepo.getWriteRelays(author).orEmpty()
                    if (sellerWriteRelays.isEmpty()) {
                        try {
                            PeerRelayListLookup.fetch(author, relayPool, relayListRepo, safeHints)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            android.util.Log.w(
                                "MetadataFetcher",
                                "NIP-65 lookup failed for NIP-99 author ${author.take(12)}",
                                error
                            )
                        }
                        sellerWriteRelays = relayListRepo.getWriteRelays(author).orEmpty()
                    }
                }
                sellerWriteRelays = sellerWriteRelays.filter { RelayConfig.isValidUrl(it) }.distinct()

                val topRelays = quoteRelayProvider?.invoke().orEmpty()
                val marketplaceRelays = if (isMarketplaceListing) {
                    (MarketplaceRelays.READ + MarketplaceRelays.WRITE).distinct()
                } else {
                    emptyList()
                }
                val accountReadRelays = if (isMarketplaceListing) {
                    (relayPool.getReadRelayUrls() + RelayConfig.DEFAULT_INDEXER_RELAYS).distinct()
                } else {
                    emptyList()
                }

                // Hints and the seller's advertised outbox are priority destinations.
                // The query still fans out to account, indexer and marketplace relays,
                // but we do not close it until every explicit NIP-19 / NIP-65 target
                // has answered EOSE (or the overall timeout expires).
                val relayPlan = buildAddressableRelayPlan(
                    priorityRelays = safeHints + sellerWriteRelays,
                    topRelays = topRelays,
                    accountReadRelays = accountReadRelays,
                    marketplaceRelays = marketplaceRelays
                )
                val targetUrls = relayPlan.targetUrls.filter { RelayConfig.isValidUrl(it) }
                val priorityRelays = relayPlan.priorityRelayUrls.filter { it in targetUrls }
                val eoseTracker = AddressableRelayEoseTracker(targetUrls)

                eoseCollector = if (targetUrls.isNotEmpty()) {
                    scope.launch(processingContext, start = CoroutineStart.UNDISPATCHED) {
                        relayPool.relayEoseSignals.collect { signal ->
                            if (signal.subscriptionId == subId) {
                                eoseTracker.markCompleted(signal.relayUrl)
                            }
                        }
                    }
                } else {
                    null
                }

                if (targetUrls.isNotEmpty()) {
                    for (url in targetUrls) {
                        val isPriority = url in priorityRelays
                        if (isPriority) relayPool.clearCooldown(url)
                        relayPool.sendToRelayOrEphemeral(
                            url,
                            msg,
                            skipBadCheck = isPriority,
                            autoReconnect = true
                        )
                    }
                } else {
                    relayPool.sendToTopRelays(msg, maxRelays = 10)
                }

                if (targetUrls.isEmpty()) {
                    subManager.awaitEoseWithTimeout(subId)
                } else {
                    var drainStartedAt = 0L
                    val completedWithinWindow = withTimeoutOrNull(ADDRESSABLE_EOSE_TIMEOUT_MS) {
                        while (true) {
                            val completedCount = eoseTracker.completedCount()
                            if (eoseTracker.hasSatisfiedRequiredResponses(priorityRelays)) {
                                if (drainStartedAt == 0L) {
                                    drainStartedAt = System.currentTimeMillis()
                                }
                                val graceElapsed =
                                    System.currentTimeMillis() - drainStartedAt >= ADDRESSABLE_EOSE_DRAIN_GRACE_MS
                                if (eoseTracker.allTargetsCompleted() || graceElapsed) break
                            }
                            delay(100)
                        }
                        true
                    } == true

                    android.util.Log.i(
                        "MetadataFetcher",
                        "Addressable fetch finished sub=$subId kind=$kind " +
                            "relays=${eoseTracker.completedCount()}/${eoseTracker.targetCount()} " +
                            "priority=${priorityRelays.size} timeout=${!completedWithinWindow}"
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w(
                    "MetadataFetcher",
                    "Addressable fetch failed kind=$kind author=${author.take(12)} dTag=$dTag",
                    error
                )
            } finally {
                eoseCollector?.cancelAndJoin()
                subManager.closeSubscription(subId)
                synchronized(pendingOnDemandQuotes) {
                    inFlightAddressables.remove(coordKey)
                }
            }
        }
    }

    fun fetchQuotedEvents(event: com.darkwisp.app.nostr.NostrEvent) {
        event.tags.filter { it.size >= 2 && it[0] == "q" }
            .forEach { tag ->
                val id = tag[1].lowercase()
                if (validHexId.matches(id)) {
                    val hints = mutableListOf<String>()
                    if (tag.size >= 3 && tag[2].startsWith("wss://")) hints.add(tag[2])
                    requestQuotedEvent(id, hints)
                }
            }
        for (match in nostrNoteUriRegex.findAll(event.content)) {
            val decoded = Nip19.decodeNostrUri(match.value)
            when (decoded) {
                is NostrUriData.NoteRef -> requestQuotedEvent(decoded.eventId, decoded.relays)
                is NostrUriData.AddressRef -> {
                    val kind = decoded.kind ?: continue
                    val author = decoded.author ?: continue
                    requestAddressableEvent(kind, author, decoded.dTag, decoded.relays)
                }
                else -> {}
            }
        }
    }

    fun sweepMissingProfiles() {
        val currentFeed = eventRepo.feed.value
        for (event in currentFeed) {
            if (eventRepo.getProfileData(event.pubkey) == null) {
                addToPendingProfiles(event.pubkey)
            }
            for (reposter in eventRepo.getReposterPubkeys(event.id).take(5)) {
                if (eventRepo.getProfileData(reposter) == null) {
                    addToPendingProfiles(reposter)
                }
            }
            if ((event.kind == 1 || event.kind == 30023) && event.id !in scannedQuoteEvents) {
                scannedQuoteEvents.add(event.id)
                fetchQuotedEvents(event)
            }
        }
        // Prevent unbounded growth
        if (scannedQuoteEvents.size > 5000) scannedQuoteEvents.clear()
    }

    /** Must be called while holding pendingProfilePubkeys lock */
    private fun flushProfileBatch() {
        if (pendingProfilePubkeys.isEmpty()) return
        val subId = "meta-batch-${metaBatchCounter++}"
        val pubkeys = pendingProfilePubkeys.toList()
        val relayHints = pendingProfileRelayHints.toMap()
        pendingProfilePubkeys.clear()
        pendingProfileRelayHints.clear()
        inFlightProfiles.addAll(pubkeys)

        pubkeys.forEach { profileAttempts[it] = (profileAttempts[it] ?: 0) + 1 }

        val maxAttempt = pubkeys.maxOf { profileAttempts[it] ?: 1 }
        if (maxAttempt <= 1) {
            outboxRouter.requestProfiles(subId, pubkeys)
        } else {
            // Retry: send to top 10 relays instead of all — most profiles are on major relays
            val filter = Filter(kinds = listOf(0, 30315), authors = pubkeys, limit = pubkeys.size * 2)
            relayPool.sendToTopRelays(ClientMessage.req(subId, filter), maxRelays = 10)
        }

        // Also send to any relay hints from nprofile references
        val hintUrls = relayHints.values.flatten().distinct()
        if (hintUrls.isNotEmpty()) {
            val hintPubkeys = relayHints.keys.toList()
            val filter = Filter(kinds = listOf(0, 30315), authors = hintPubkeys, limit = hintPubkeys.size * 2)
            val msg = ClientMessage.req(subId, filter)
            for (url in hintUrls) {
                relayPool.sendToRelayOrEphemeral(url, msg)
            }
        }

        scope.launch(processingContext) {
            subManager.awaitEoseWithTimeout(subId)
            subManager.closeSubscription(subId)
            synchronized(pendingProfilePubkeys) {
                inFlightProfiles.removeAll(pubkeys.toSet())
            }
        }
    }

    private fun flushReplyCountBatch() {
        if (pendingReplyCountIds.isEmpty()) return
        val subId = "reply-count-${replyCountBatchCounter++}"
        val eventIds = pendingReplyCountIds.toList()
        pendingReplyCountIds.clear()
        val filter = Filter(kinds = listOf(1, Nip22.KIND_COMMENT), eTags = eventIds)
        relayPool.sendToReadRelays(ClientMessage.req(subId, filter))
        scope.launch {
            subManager.awaitEoseWithTimeout(subId)
            subManager.closeSubscription(subId)
        }
    }

    /** Must be called while holding pendingOnDemandQuotes lock */
    private fun flushOnDemandQuoteBatch() {
        if (pendingOnDemandQuotes.isEmpty()) return
        val batch = pendingOnDemandQuotes.toList()
        val hints = pendingRelayHints.toMap()
        pendingOnDemandQuotes.clear()
        pendingRelayHints.clear()
        inFlightQuotes.addAll(batch)

        val subId = "quote-${onDemandQuoteBatchCounter++}"
        val msg = ClientMessage.req(subId, Filter(ids = batch))

        // Send to top relays by coverage (most likely to have the quoted events)
        // plus any relay hints. Falls back to sendToAll if no scored relays available.
        val topRelays = quoteRelayProvider?.invoke() ?: emptyList()
        val hintUrls = hints.values.flatten().toSet()
        val targetUrls = (topRelays + hintUrls).distinct()

        if (targetUrls.isNotEmpty()) {
            for (url in targetUrls) {
                relayPool.sendToRelayOrEphemeral(url, msg)
            }
        } else {
            relayPool.sendToTopRelays(msg, maxRelays = 10)
        }

        scope.launch {
            subManager.awaitEoseWithTimeout(subId)
            subManager.closeSubscription(subId)
            synchronized(pendingOnDemandQuotes) {
                inFlightQuotes.removeAll(batch.toSet())
            }
            // Mark unfound events as temporarily failed (will retry after QUOTE_RETRY_MS)
            val now = System.currentTimeMillis()
            for (id in batch) {
                if (eventRepo.getEvent(id) == null) {
                    failedQuoteFetches[id] = now
                }
            }
            // Prevent unbounded growth — evict oldest entries
            if (failedQuoteFetches.size > 2000) {
                val cutoff = now - QUOTE_RETRY_MS * 2
                failedQuoteFetches.entries.removeAll { it.value < cutoff }
            }
        }
    }

    private fun flushZapCountBatch() {
        if (pendingZapCountIds.isEmpty()) return
        val subId = "zap-count-${zapCountBatchCounter++}"
        val eventIds = pendingZapCountIds.toList()
        pendingZapCountIds.clear()
        val filter = Filter(kinds = listOf(9735), eTags = eventIds)
        relayPool.sendToReadRelays(ClientMessage.req(subId, filter))
        scope.launch {
            subManager.awaitEoseWithTimeout(subId)
            subManager.closeSubscription(subId)
        }
    }
}
