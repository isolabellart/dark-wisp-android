package com.darkwisp.app.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ExperimentalFoundationApi
import com.darkwisp.app.ui.component.ProfilePicture
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.darkwisp.app.R
import com.darkwisp.app.nostr.MarketplaceRelays
import com.darkwisp.app.nostr.Nip19
import com.darkwisp.app.nostr.Nip99
import com.darkwisp.app.nostr.Nip22
import com.darkwisp.app.nostr.Nip51
import com.darkwisp.app.nostr.Filter
import com.darkwisp.app.nostr.ClientMessage
import com.darkwisp.app.nostr.NostrSigner
import com.darkwisp.app.nostr.NostrEvent
import com.darkwisp.app.ui.component.ZapDialog
import com.darkwisp.app.relay.RelayPool
import com.darkwisp.app.relay.RelayConfig
import com.darkwisp.app.repo.EventRepository
import com.darkwisp.app.repo.BookmarkSetRepository
import com.darkwisp.app.viewmodel.MarketplaceScope
import com.darkwisp.app.viewmodel.MarketplaceViewModel

private const val MARKETPLACE_WATCHLIST_DTAG = "dark-wisp-marketplace-watchlist"

private data class MarketplaceWatchlistSnapshot(
    val createdAt: Long,
    val isInterested: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(
    viewModel: MarketplaceViewModel,
    relayPool: RelayPool,
    eventRepository: EventRepository,
    followedPubkeys: List<String>,
    userPubkey: String?,
    onBack: () -> Unit,
    onOpenListing: (NostrEvent) -> Unit,
    onCreateListing: () -> Unit,
    sellerPubkey: String? = null,
    sellerName: String? = null,
    canCreateListing: Boolean = true
) {
    val listings by viewModel.listings.collectAsState()
    val selectedScope by viewModel.scope.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val categories by viewModel.availableCategories.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(selectedScope, followedPubkeys, userPubkey, sellerPubkey) {
        viewModel.load(selectedScope, relayPool, eventRepository, followedPubkeys, userPubkey, sellerPubkey)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Storefront, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (sellerPubkey != null) sellerName?.takeIf { it.isNotBlank() }?.let {
                                stringResource(R.string.marketplace_storefront_title, it)
                            } ?: stringResource(R.string.marketplace_storefront)
                            else stringResource(R.string.marketplace_title)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.load(selectedScope, relayPool, eventRepository, followedPubkeys, userPubkey, sellerPubkey)
                        },
                        enabled = !loading
                    ) {
                        if (loading) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.marketplace_refresh))
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (canCreateListing) FloatingActionButton(onClick = onCreateListing) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.marketplace_new))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (loading && listings.isNotEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (sellerPubkey == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = selectedScope == MarketplaceScope.ALL, onClick = { viewModel.setScope(MarketplaceScope.ALL) }, label = { Text(stringResource(R.string.marketplace_all)) })
                    FilterChip(selected = selectedScope == MarketplaceScope.FOLLOWING, onClick = { viewModel.setScope(MarketplaceScope.FOLLOWING) }, label = { Text(stringResource(R.string.marketplace_following)) })
                    FilterChip(selected = selectedScope == MarketplaceScope.MINE, onClick = { viewModel.setScope(MarketplaceScope.MINE) }, label = { Text(stringResource(R.string.marketplace_mine)) })
                }
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                },
                placeholder = { Text(stringResource(R.string.marketplace_search_hint)) },
                shape = RoundedCornerShape(18.dp)
            )
            if (categories.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
                    Text(stringResource(R.string.marketplace_categories), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = selectedCategory == null, onClick = { viewModel.setSelectedCategory(null) },
                            label = { Text(stringResource(R.string.marketplace_all)) })
                        categories.forEach { category ->
                            FilterChip(selected = selectedCategory.equals(category, true),
                                onClick = { viewModel.setSelectedCategory(if (selectedCategory.equals(category, true)) null else category) },
                                label = { Text(category.replaceFirstChar { it.uppercase() }) })
                        }
                    }
                }
            }

            when {
                loading && listings.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
                error != null && listings.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.marketplace_load_error, error ?: ""))
                }
                listings.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Storefront, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (searchQuery.isNotBlank() || selectedCategory != null) stringResource(R.string.marketplace_no_results)
                            else stringResource(R.string.marketplace_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (sellerPubkey != null && canCreateListing && searchQuery.isBlank() && selectedCategory == null) {
                            OutlinedButton(onClick = onCreateListing) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.marketplace_add_listing))
                            }
                        }
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(listings, key = { it.pubkey + ":" + Nip99.dTag(it) }) { event ->
                        MarketplaceListingCard(event = event, eventRepository = eventRepository, onClick = { onOpenListing(event) })
                    }
                }
            }
        }
    }
}

@Composable
internal fun MarketplaceListingCard(event: NostrEvent, eventRepository: EventRepository, onClick: () -> Unit) {
    val image = Nip99.images(event).firstOrNull()
    val sold = Nip99.isSold(event)
    val profileVersion by eventRepository.profileVersionFor(event.pubkey).collectAsState(initial = 0)
    val sellerProfile = remember(eventRepository, event.pubkey, profileVersion) {
        eventRepository.getProfileData(event.pubkey)
    }
    LaunchedEffect(event.pubkey) { eventRepository.requestProfileIfMissing(event.pubkey) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (image != null) {
                ZoomableMarketplaceImage(
                    imageUrl = image,
                    contentDescription = Nip99.title(event),
                    modifier = Modifier.size(112.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.size(112.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Storefront, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(Nip99.title(event), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Nip99.price(event)?.let { price ->
                    Text(
                        Nip99.priceLabel(event).orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    MarketplaceLightningEstimate(amount = price.amount, currency = price.currency)
                }
                val location = Nip99.location(event)
                if (location.isNotBlank()) Text(location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ProfilePicture(url = sellerProfile?.picture, size = 24)
                    Text(
                        sellerProfile?.displayString ?: event.pubkey.take(8) + "…" + event.pubkey.takeLast(4),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                if (sold) {
                    Badge(containerColor = MaterialTheme.colorScheme.errorContainer) {
                        Text(stringResource(R.string.marketplace_sold), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MarketplaceDetailScreen(
    event: NostrEvent,
    userPubkey: String?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onContactSeller: (String) -> Unit,
    eventRepository: EventRepository,
    relayPool: RelayPool,
    signer: NostrSigner?,
    onReact: () -> Unit,
    onZap: (Long, String, Boolean, Boolean) -> Unit,
    isWalletConnected: Boolean,
    zapPrefsRepo: com.darkwisp.app.repo.ZapPreferences,
    onGoToWallet: () -> Unit,
    recipientHasLud16: Boolean,
    profileLookup: (String) -> com.darkwisp.app.nostr.ProfileData?,
    fetchPaymentTargets: (suspend (String) -> List<com.darkwisp.app.nostr.NipA3.PaymentTarget>)?,
    onOpenProfile: (String) -> Unit = {},
    onBookmarkSetChanged: (NostrEvent) -> Unit = {},
    bookmarkSetRepository: BookmarkSetRepository? = null
) {
    val profileVersion by eventRepository.profileVersionFor(event.pubkey).collectAsState(initial = 0)
    val sellerProfile = remember(eventRepository, event.pubkey, profileVersion) {
        eventRepository.getProfileData(event.pubkey)
    }
    LaunchedEffect(event.pubkey) { eventRepository.requestProfileIfMissing(event.pubkey) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val detailScope = rememberCoroutineScope()
    val title = Nip99.title(event)
    val images = Nip99.images(event)
    val summary = Nip99.summary(event)
    val sold = Nip99.isSold(event)
    var message by remember(event.id) {
        mutableStateOf("")
    }
    var contactExpanded by remember(event.id) { mutableStateOf(false) }
    var showZapDialog by remember(event.id) { mutableStateOf(false) }
    val comments = remember(event.id) { mutableStateListOf<NostrEvent>() }
    var commentText by remember(event.id) { mutableStateOf("") }
    var commentSending by remember(event.id) { mutableStateOf(false) }
    var commentError by remember(event.id) { mutableStateOf<String?>(null) }
    val commentAddress = remember(event.pubkey, event.id) { Nip22.addressableRootTag(event) }
    val listingAddress = remember(event.pubkey, Nip99.dTag(event)) { Nip99.addressTag(event) }
    val reactionVersion by eventRepository.reactionVersion.collectAsState(initial = 0)
    val reactionCount = remember(listingAddress, reactionVersion) { eventRepository.getReactionCount(listingAddress) }
    val zapVersion by eventRepository.zapVersion.collectAsState(initial = 0)
    val zapSats = remember(event.id, zapVersion) { eventRepository.getZapSats(event.id) }
    val userHasReacted = userPubkey?.let { eventRepository.hasUserReacted(listingAddress, it) } ?: false
    val isOwnListing = event.pubkey == userPubkey
    var watchlistEvent by remember(userPubkey) { mutableStateOf<NostrEvent?>(null) }
    var bookmarkReady by remember(userPubkey, event.id) { mutableStateOf(userPubkey == null || isOwnListing) }
    var isBookmarked by remember(userPubkey, event.id) { mutableStateOf(false) }
    var bookmarkBusy by remember(userPubkey, event.id) { mutableStateOf(false) }
    var bookmarkError by remember(userPubkey, event.id) { mutableStateOf<String?>(null) }
    var publicInterestByPubkey by remember(event.id) {
        mutableStateOf<Map<String, MarketplaceWatchlistSnapshot>>(emptyMap())
    }
    val publicInterestCount = publicInterestByPubkey.count { (pubkey, entry) ->
        pubkey != event.pubkey && entry.isInterested
    }
    val interestedAuthors = remember(publicInterestByPubkey, event.pubkey) {
        publicInterestByPubkey.filter { (pubkey, entry) -> pubkey != event.pubkey && entry.isInterested }
            .keys.sorted()
    }
    val suggestedMessage = stringResource(R.string.marketplace_message_intro, title)
    val watchlistTitle = stringResource(R.string.marketplace_watchlist_title)
    val context = LocalContext.current
    val listingNaddr = remember(event.pubkey, event.id) {
        Nip19.naddrEncode(
            dTag = Nip99.dTag(event),
            authorHex = event.pubkey,
            kind = Nip99.KIND_LISTING,
            relays = MarketplaceRelays.READ.take(3)
        )
    }

    LaunchedEffect(event.id, commentAddress) {
        // Hydrate the discussion from ObjectBox first; relay replies then merge into
        // the same list instead of making locally persisted comments disappear.
        val cachedComments = withContext(Dispatchers.IO) {
            eventRepository.getRecentCommentsForAddressableRoot(commentAddress, limit = 100)
        }
        cachedComments.forEach { comment ->
            if (eventRepository.isEventVisibleToUser(comment) && comments.none { it.id == comment.id }) {
                comments.add(comment)
                eventRepository.requestProfileIfMissing(comment.pubkey)
            }
        }
        comments.sortBy { it.created_at }

        val subId = "marketplace-comments-${event.id.take(12)}"
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            relayPool.relayEvents.filter { it.subscriptionId == subId }.collect { frame ->
                val comment = frame.event
                if (Nip22.referencesAddressableRoot(comment, commentAddress) &&
                    eventRepository.isEventVisibleToUser(comment) && comments.none { it.id == comment.id }) {
                    eventRepository.addEvent(comment)
                    comments.add(comment)
                    comments.sortBy { it.created_at }
                    eventRepository.requestProfileIfMissing(comment.pubkey)
                }
            }
        }
        val commentRequest = ClientMessage.req(
            subId,
            Filter(kinds = listOf(Nip22.KIND_COMMENT), bigATags = listOf(commentAddress), limit = 100)
        )
        val marketplaceReadRelays = (MarketplaceRelays.READ + MarketplaceRelays.WRITE).distinct()
        (
            relayPool.getReadRelayUrls() +
                marketplaceReadRelays +
                RelayConfig.DEFAULT_INDEXER_RELAYS
            ).distinct().forEach { relay ->
                relayPool.sendToRelayOrEphemeral(
                    relay,
                    commentRequest,
                    skipBadCheck = true,
                    autoReconnect = relay in marketplaceReadRelays
                )
            }
        try { awaitCancellation() } finally {
            collector.cancel()
            relayPool.closeOnAllRelays(subId)
        }
    }

    LaunchedEffect(event.id) {
        val subId = "marketplace-reactions-${event.id.take(12)}"
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            relayPool.relayEvents
                .filter { it.subscriptionId == subId }
                .collect { frame ->
                    val reaction = frame.event
                    val targetsRevision = reaction.tags.any {
                        it.size >= 2 && it[0] == "e" && it[1] == event.id
                    }
                    val targetsListing = reaction.tags.any {
                        it.size >= 2 && it[0] == "a" && it[1] == listingAddress
                    }
                    if (reaction.kind == 7 && (targetsRevision || targetsListing)) {
                        eventRepository.addEvent(reaction)
                        eventRepository.requestProfileIfMissing(reaction.pubkey)
                    }
                }
        }
        val request = ClientMessage.req(
            subId,
            listOf(
                Filter(kinds = listOf(7), eTags = listOf(event.id), limit = 500),
                Filter(kinds = listOf(7), aTags = listOf(listingAddress), limit = 1000)
            )
        )
        val marketplaceReadRelays = (MarketplaceRelays.READ + MarketplaceRelays.WRITE).distinct()
        (relayPool.getReadRelayUrls() + marketplaceReadRelays + RelayConfig.DEFAULT_INDEXER_RELAYS)
            .distinct().forEach { relay ->
                relayPool.sendToRelayOrEphemeral(
                    relay,
                    request,
                    skipBadCheck = true,
                    autoReconnect = relay in marketplaceReadRelays
                )
            }
        try { awaitCancellation() } finally {
            collector.cancel()
            relayPool.closeOnAllRelays(subId)
        }
    }

    LaunchedEffect(event.id, listingAddress, userPubkey) {
        // Watchlists are written to both the curated Marketplace destinations and
        // the user's own relays. Read all public Marketplace write destinations too,
        // otherwise sellers miss interested buyers whose relays are not in READ.
        val targets = (
            relayPool.getReadRelayUrls() +
                RelayConfig.DEFAULT_INDEXER_RELAYS +
                MarketplaceRelays.READ +
                MarketplaceRelays.WRITE
            ).distinct()
        val savedWatchlist = userPubkey?.let { bookmarkSetRepository?.getSet(it, MARKETPLACE_WATCHLIST_DTAG) }
        if (savedWatchlist != null && !isOwnListing) {
            isBookmarked = listingAddress in savedWatchlist.coordinates
            bookmarkReady = true
            bookmarkError = null
        }
        val subscriptions = mutableListOf<String>()
        val collectors = mutableListOf<kotlinx.coroutines.Job>()

        try {
            if (userPubkey != null && !isOwnListing) {
                val watchlistSubId = "marketplace-watchlist-" + event.id.take(10) + "-" + userPubkey.take(8)
                subscriptions.add(watchlistSubId)
                var eoseCount = 0

                collectors.add(launch(start = CoroutineStart.UNDISPATCHED) {
                    relayPool.relayEvents
                        .filter { it.subscriptionId == watchlistSubId }
                        .collect { frame ->
                            val candidate = frame.event
                            val dTag = candidate.tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.get(1)
                            if (candidate.kind != Nip51.KIND_BOOKMARK_SET ||
                                candidate.pubkey != userPubkey ||
                                dTag != MARKETPLACE_WATCHLIST_DTAG) return@collect

                            val set = Nip51.parseBookmarkSet(candidate) ?: return@collect
                            val localTimestamp = bookmarkSetRepository?.getSet(userPubkey, MARKETPLACE_WATCHLIST_DTAG)?.createdAt ?: 0L
                            val latestTimestamp = maxOf(localTimestamp, watchlistEvent?.created_at ?: 0L)
                            if (candidate.created_at > latestTimestamp) {
                                watchlistEvent = candidate
                                isBookmarked = listingAddress in set.coordinates
                                bookmarkReady = true
                                bookmarkError = null
                                eventRepository.addEvent(candidate)
                                bookmarkSetRepository?.setOwner(userPubkey)
                                bookmarkSetRepository?.updateFromEvent(candidate)
                                onBookmarkSetChanged(candidate)
                            }
                        }
                })

                val eoseWaiter = launch(start = CoroutineStart.UNDISPATCHED) {
                    withTimeoutOrNull(5_000L) {
                        relayPool.eoseSignals
                            .filter { it == watchlistSubId }
                            .take(3)
                            .collect { eoseCount++ }
                    }
                }

                val request = ClientMessage.req(
                    watchlistSubId,
                    Filter(
                        kinds = listOf(Nip51.KIND_BOOKMARK_SET),
                        authors = listOf(userPubkey),
                        dTags = listOf(MARKETPLACE_WATCHLIST_DTAG),
                        limit = 10
                    )
                )
                targets.forEach { relay ->
                    relayPool.sendToRelayOrEphemeral(
                        relay,
                        request,
                        skipBadCheck = true,
                        autoReconnect = relay in marketplaceRelayUrls
                    )
                }
                eoseWaiter.join()
                val localSetAfterFetch = bookmarkSetRepository?.getSet(userPubkey, MARKETPLACE_WATCHLIST_DTAG)
                bookmarkReady = eoseCount > 0 || localSetAfterFetch != null
                if (localSetAfterFetch != null) {
                    isBookmarked = listingAddress in localSetAfterFetch.coordinates
                }
                if (!bookmarkReady) {
                    bookmarkError = context.getString(R.string.marketplace_watchlist_load_failed)
                }
            }

            val interestSubId = "marketplace-interests-" + event.id.take(12)
            subscriptions.add(interestSubId)
            collectors.add(launch(start = CoroutineStart.UNDISPATCHED) {
                relayPool.relayEvents
                    .filter { it.subscriptionId == interestSubId }
                    .collect { frame ->
                        val candidate = frame.event
                        val dTag = candidate.tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.get(1)
                        if (candidate.kind != Nip51.KIND_BOOKMARK_SET || dTag != MARKETPLACE_WATCHLIST_DTAG) {
                            return@collect
                        }

                        val set = Nip51.parseBookmarkSet(candidate) ?: return@collect
                        val previous = publicInterestByPubkey[candidate.pubkey]?.createdAt ?: 0L
                        if (candidate.created_at > previous) {
                            val interested = candidate.pubkey != event.pubkey && listingAddress in set.coordinates
                            val updated = publicInterestByPubkey.toMutableMap()
                            updated[candidate.pubkey] = MarketplaceWatchlistSnapshot(candidate.created_at, interested)
                            if (interested) eventRepository.requestProfileIfMissing(candidate.pubkey)
                            publicInterestByPubkey = updated
                            if (candidate.pubkey == userPubkey && !isOwnListing) {
                                val localTimestamp = bookmarkSetRepository?.getSet(userPubkey, MARKETPLACE_WATCHLIST_DTAG)?.createdAt ?: 0L
                                if (candidate.created_at > maxOf(localTimestamp, watchlistEvent?.created_at ?: 0L)) {
                                    watchlistEvent = candidate
                                    isBookmarked = listingAddress in set.coordinates
                                    bookmarkReady = true
                                    bookmarkSetRepository?.setOwner(userPubkey)
                                    bookmarkSetRepository?.updateFromEvent(candidate)
                                    onBookmarkSetChanged(candidate)
                                }
                            }
                            eventRepository.addEvent(candidate)
                        }
                    }
            })

            val interestRequest = ClientMessage.req(
                interestSubId,
                Filter(
                    kinds = listOf(Nip51.KIND_BOOKMARK_SET),
                    dTags = listOf(MARKETPLACE_WATCHLIST_DTAG),
                    limit = 1000
                )
            )
            targets.forEach { relay ->
                relayPool.sendToRelayOrEphemeral(
                    relay,
                    interestRequest,
                    skipBadCheck = true,
                    autoReconnect = relay in marketplaceRelayUrls
                )
            }

            awaitCancellation()
        } finally {
            collectors.forEach { it.cancel() }
            subscriptions.forEach { relayPool.closeOnAllRelays(it) }
        }
    }

    LaunchedEffect(event.id, suggestedMessage) {
        if (message.isBlank()) message = suggestedMessage
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.marketplace_listing_details)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (event.pubkey == userPubkey) {
                        Text(
                            text = stringResource(R.string.marketplace_edit),
                            modifier = Modifier.clickable(onClick = onEdit).padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).clip(RoundedCornerShape(0.dp))
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).imePadding().verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (event.pubkey != userPubkey) {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.marketplace_contact_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(R.string.marketplace_contact_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { contactExpanded = !contactExpanded }) {
                                    Icon(if (contactExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = stringResource(R.string.marketplace_contact_expand))
                                }
                            }
                            if (contactExpanded) {
                                OutlinedTextField(value = message, onValueChange = { message = it },
                                    modifier = Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoViewRequester).onFocusChanged { state ->
                                        if (state.isFocused) detailScope.launch { delay(250); bringIntoViewRequester.bringIntoView() }
                                    }, minLines = 3, label = { Text(stringResource(R.string.marketplace_message_label)) })
                                Button(onClick = { onContactSeller(message.trim()) }, enabled = !sold && message.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.marketplace_contact_button)) }
                                Text(stringResource(R.string.marketplace_dm_privacy), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenProfile(event.pubkey) }
                        .padding(vertical = 4.dp)
                ) {
                    ProfilePicture(url = sellerProfile?.picture, size = 38)
                    Column {
                        Text(
                            sellerProfile?.displayString ?: event.pubkey.take(8) + "…" + event.pubkey.takeLast(4),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(stringResource(R.string.marketplace_seller_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (images.isNotEmpty()) {
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(images) { image ->
                            ZoomableMarketplaceImage(
                                imageUrl = image,
                                contentDescription = title,
                                modifier = Modifier.width(280.dp).height(280.dp).clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Nip99.price(event)?.let { price ->
                    Text(
                        Nip99.priceLabel(event).orEmpty(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    MarketplaceLightningEstimate(amount = price.amount, currency = price.currency)
                }
                if (sold) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp)) {
                        Text(stringResource(R.string.marketplace_sold), modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                } else {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
                        Text(stringResource(R.string.marketplace_active), modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                if (summary.isNotBlank()) Text(summary, style = MaterialTheme.typography.titleMedium)
                val location = Nip99.location(event)
                if (location.isNotBlank()) {
                    Text(stringResource(R.string.marketplace_location_value, location), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(event.content, style = MaterialTheme.typography.bodyLarge)
                if (event.pubkey == userPubkey) {
                    Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.marketplace_edit)) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.marketplace_feedback_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onReact, enabled = signer != null) {
                                Icon(
                                    imageVector = if (userHasReacted) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = stringResource(if (userHasReacted) R.string.marketplace_unlike else R.string.marketplace_like),
                                    tint = Color(0xFFE53935)
                                )
                            }
                            Text(reactionCount.toString(), style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(onClick = { showZapDialog = true }, enabled = !sold) {
                            Text(stringResource(R.string.marketplace_zap_count, zapSats))
                        }
                        if (!isOwnListing) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val activeSigner = signer ?: return@IconButton
                                    if (!bookmarkReady || bookmarkBusy || isOwnListing) return@IconButton
                                    detailScope.launch {
                                        bookmarkBusy = true
                                        bookmarkError = null
                                        try {
                                            // Prefer the locally persisted snapshot over any relay copy that
                                            // may still be one revision behind the previous bookmark action.
                                            val currentSet = bookmarkSetRepository?.getSet(
                                                activeSigner.pubkeyHex,
                                                MARKETPLACE_WATCHLIST_DTAG
                                            ) ?: watchlistEvent?.let { Nip51.parseBookmarkSet(it) }
                                            val coordinates = currentSet?.coordinates?.toMutableSet() ?: mutableSetOf()
                                            if (listingAddress in coordinates) coordinates.remove(listingAddress)
                                            else coordinates.add(listingAddress)

                                            val tags = Nip51.buildBookmarkSetTags(
                                                dTag = MARKETPLACE_WATCHLIST_DTAG,
                                                eventIds = currentSet?.eventIds ?: emptySet(),
                                                coordinates = coordinates,
                                                hashtags = currentSet?.hashtags ?: emptySet(),
                                                title = currentSet?.name ?: watchlistTitle
                                            )
                                            val updatedEvent = activeSigner.signEvent(
                                                kind = Nip51.KIND_BOOKMARK_SET,
                                                content = "",
                                                tags = tags,
                                                createdAt = maxOf(
                                                    System.currentTimeMillis() / 1000,
                                                    (currentSet?.createdAt ?: 0L) + 1L,
                                                    (watchlistEvent?.created_at ?: 0L) + 1L
                                                )
                                            )
                                            // Persist and update the visible state optimistically. A slow or
                                            // unavailable relay must not make the selected item vanish.
                                            eventRepository.addEvent(updatedEvent)
                                            bookmarkSetRepository?.setOwner(activeSigner.pubkeyHex)
                                            bookmarkSetRepository?.updateFromEvent(updatedEvent)
                                            onBookmarkSetChanged(updatedEvent)
                                            watchlistEvent = updatedEvent
                                            isBookmarked = listingAddress in coordinates
                                            bookmarkReady = true

                                            val updatedInterests = publicInterestByPubkey.toMutableMap()
                                            val activePubkey = userPubkey
                                            if (activePubkey != null && activePubkey != event.pubkey) {
                                                updatedInterests[activePubkey] = MarketplaceWatchlistSnapshot(
                                                    updatedEvent.created_at,
                                                    isBookmarked
                                                )
                                            }
                                            publicInterestByPubkey = updatedInterests
                                            publishMarketplaceEvent(relayPool, updatedEvent, context)
                                        } catch (error: Exception) {
                                            bookmarkError = error.localizedMessage
                                                ?: context.getString(R.string.marketplace_watchlist_update_failed)
                                        } finally {
                                            bookmarkBusy = false
                                        }
                                    }
                                },
                                enabled = signer != null && bookmarkReady && !bookmarkBusy && !isOwnListing
                            ) {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    contentDescription = stringResource(if (isBookmarked) R.string.marketplace_unbookmark else R.string.marketplace_bookmark),
                                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(publicInterestCount.toString(), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    if (!isOwnListing && userPubkey != null) {
                        Text(
                            stringResource(R.string.marketplace_watchlist_public_notice),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (bookmarkError != null) {
                        Text(bookmarkError.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    if (isOwnListing) {
                        Text(
                            stringResource(R.string.marketplace_interested_buyers, publicInterestCount),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (interestedAuthors.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                interestedAuthors.forEach { pubkey ->
                                    MarketplaceInterestChip(
                                        pubkey = pubkey,
                                        eventRepository = eventRepository,
                                        onClick = { onOpenProfile(pubkey) }
                                    )
                                }
                            }
                        }
                    }
                    Text(stringResource(R.string.marketplace_comment_count, comments.size), style = MaterialTheme.typography.titleMedium)
                    if (comments.isEmpty()) Text(stringResource(R.string.marketplace_comment_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else comments.sortedBy { it.created_at }.takeLast(40).forEach { comment ->
                        MarketplaceCommentRow(
                            comment = comment,
                            eventRepository = eventRepository,
                            onProfileClick = onOpenProfile
                        )
                    }
                    OutlinedTextField(value = commentText, onValueChange = { commentText = it }, modifier = Modifier.fillMaxWidth(),
                        minLines = 2, label = { Text(stringResource(R.string.marketplace_comment_hint)) }, enabled = signer != null && !commentSending)
                    if (commentError != null) Text(commentError.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {
                        val activeSigner = signer ?: return@Button
                        val body = commentText.trim()
                        if (body.isBlank() || commentSending) return@Button
                        detailScope.launch {
                            commentSending = true
                            commentError = null
                            try {
                                val comment = activeSigner.signEvent(Nip22.KIND_COMMENT, body, Nip22.buildAddressableRootCommentTags(event))
                                // Add the signed comment to the local persistent repository before
                                // waiting for a relay ACK, so the discussion never flickers away.
                                eventRepository.addEvent(comment)
                                if (comments.none { it.id == comment.id }) { comments.add(comment); comments.sortBy { it.created_at } }
                                commentText = ""
                                publishMarketplaceEvent(relayPool, comment, context)
                            } catch (e: Exception) {
                                commentError = e.localizedMessage ?: e.message ?: "Unable to publish comment"
                            } finally { commentSending = false }
                        }
                    }, enabled = signer != null && commentText.isNotBlank() && !commentSending, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.marketplace_comment_send))
                    }
                    if (signer == null) Text(stringResource(R.string.marketplace_comment_signin), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("naddr", listingNaddr))
                        Toast.makeText(context, R.string.marketplace_naddr_copied, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.marketplace_copy_naddr))
                }
            }
        }
    }
    if (showZapDialog) {
        ZapDialog(
            isWalletConnected = isWalletConnected,
            onDismiss = { showZapDialog = false },
            onZap = { amount, zapMessage, anonymous, isPrivate ->
                onZap(amount, zapMessage, anonymous, isPrivate)
                showZapDialog = false
            },
            onGoToWallet = onGoToWallet,
            zapPrefsRepo = zapPrefsRepo,
            canPrivateZap = false,
            recipientPubkey = event.pubkey,
            profileLookup = profileLookup,
            recipientHasLud16 = recipientHasLud16,
            fetchPaymentTargets = fetchPaymentTargets
        )
    }

}


@Composable
private fun MarketplaceCommentRow(
    comment: NostrEvent,
    eventRepository: EventRepository,
    onProfileClick: (String) -> Unit
) {
    val profileVersion by eventRepository.profileVersionFor(comment.pubkey).collectAsState(initial = 0)
    val profile = remember(eventRepository, comment.pubkey, profileVersion) {
        eventRepository.getProfileData(comment.pubkey)
    }
    LaunchedEffect(comment.pubkey) { eventRepository.requestProfileIfMissing(comment.pubkey) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onProfileClick(comment.pubkey) }
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ProfilePicture(url = profile?.picture, size = 34)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(
                profile?.displayString ?: comment.pubkey.take(8) + "…" + comment.pubkey.takeLast(4),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(comment.content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MarketplaceInterestChip(
    pubkey: String,
    eventRepository: EventRepository,
    onClick: () -> Unit
) {
    val profileVersion by eventRepository.profileVersionFor(pubkey).collectAsState(initial = 0)
    val profile = remember(eventRepository, pubkey, profileVersion) {
        eventRepository.getProfileData(pubkey)
    }
    LaunchedEffect(pubkey) { eventRepository.requestProfileIfMissing(pubkey) }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ProfilePicture(url = profile?.picture, size = 30)
        Text(
            profile?.displayString ?: pubkey.take(8) + "…" + pubkey.takeLast(4),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
    }
}

@Composable
private fun ZoomableMarketplaceImage(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    var showZoom by remember(imageUrl) { mutableStateOf(false) }

    AsyncImage(
        model = imageUrl,
        contentDescription = contentDescription,
        modifier = modifier.clickable { showZoom = true },
        contentScale = contentScale
    )

    if (showZoom) {
        Dialog(
            onDismissRequest = { showZoom = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var zoom by remember(imageUrl) { mutableStateOf(1f) }
            var pan by remember(imageUrl) { mutableStateOf(Offset.Zero) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(imageUrl) {
                            detectTransformGestures { _, panChange, zoomChange, _ ->
                                val nextZoom = (zoom * zoomChange).coerceIn(1f, 6f)
                                zoom = nextZoom
                                pan = if (nextZoom <= 1.001f) Offset.Zero else pan + panChange
                            }
                        }
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            val maxX = (size.width * (zoom - 1f) / 2f).coerceAtLeast(0f)
                            val maxY = (size.height * (zoom - 1f) / 2f).coerceAtLeast(0f)
                            translationX = pan.x.coerceIn(-maxX, maxX)
                            translationY = pan.y.coerceIn(-maxY, maxY)
                        },
                    contentScale = ContentScale.Fit
                )

                IconButton(
                    onClick = { showZoom = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close image",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
