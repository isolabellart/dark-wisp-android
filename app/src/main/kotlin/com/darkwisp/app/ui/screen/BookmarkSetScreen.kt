package com.darkwisp.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import com.darkwisp.app.R
import com.darkwisp.app.nostr.BookmarkSet
import com.darkwisp.app.nostr.Nip99
import com.darkwisp.app.nostr.NostrEvent
import com.darkwisp.app.repo.EventRepository
import com.darkwisp.app.repo.TranslationRepository
import com.darkwisp.app.ui.component.PostCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkSetScreen(
    bookmarkSet: BookmarkSet?,
    eventRepo: EventRepository,
    userPubkey: String?,
    isOwnList: Boolean,
    onBack: () -> Unit,
    onDeleteList: (() -> Unit)? = null,
    onNoteClick: (NostrEvent) -> Unit = {},
    onQuotedNoteClick: ((String) -> Unit)? = null,
    onReply: (NostrEvent) -> Unit = {},
    onReact: (NostrEvent, String) -> Unit = { _, _ -> },
    onProfileClick: (String) -> Unit = {},
    onRemoveFromSet: ((String) -> Unit)? = null,
    onToggleFollow: (String) -> Unit = {},
    onBlockUser: (String) -> Unit = {},
    translationRepo: TranslationRepository? = null,
    onMarketplaceListingClick: ((NostrEvent) -> Unit)? = null,
    priceTracker: com.darkwisp.app.repo.MarketplaceWatchlistPriceTracker? = null
) {
    val profileVersion by eventRepo.profileVersion.collectAsState()
    val reactionVersion by eventRepo.reactionVersion.collectAsState()
    val eventCacheVersion by eventRepo.eventCacheVersion.collectAsState()
    val translationVersion by translationRepo?.version?.collectAsState() ?: remember { mutableStateOf(0) }
    val emptyPriceChanges = remember {
        mutableStateOf<Map<String, com.darkwisp.app.repo.MarketplaceWatchlistPriceTracker.PriceChange>>(emptyMap())
    }
    val priceChanges by (priceTracker?.priceChanges?.collectAsState() ?: emptyPriceChanges)

    val bookmarkCoordinates = bookmarkSet?.coordinates.orEmpty()

    LaunchedEffect(bookmarkCoordinates) {
        bookmarkCoordinates.forEach { coordinate ->
            val parts = coordinate.split(":", limit = 3)
            if (parts.size == 3) {
                val kind = parts[0].toIntOrNull() ?: return@forEach
                eventRepo.requestAddressableEvent(kind, parts[1], parts[2])
            }
        }
    }

    val events = remember(bookmarkSet?.eventIds, bookmarkCoordinates, profileVersion, eventCacheVersion) {
        val resolved = linkedMapOf<String, NostrEvent>()
        bookmarkSet?.eventIds?.forEach { id ->
            val event = eventRepo.getEvent(id) ?: return@forEach
            val key = if (event.kind == Nip99.KIND_LISTING) {
                "a:" + Nip99.addressTag(event)
            } else {
                "e:" + event.id
            }
            resolved[key] = event
        }
        bookmarkCoordinates.forEach { coordinate ->
            val parts = coordinate.split(":", limit = 3)
            if (parts.size == 3) {
                val kind = parts[0].toIntOrNull()
                if (kind != null) {
                    eventRepo.findAddressableEvent(kind, parts[1], parts[2])?.let { event ->
                        resolved["a:" + coordinate] = event
                    }
                }
            }
        }
        resolved.values.sortedByDescending { it.created_at }
    }

    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(bookmarkSet?.name ?: "List") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (isOwnList && onDeleteList != null) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, "More options")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Delete List") },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteList()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (bookmarkSet == null || events.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (bookmarkSet == null) "List not found"
                    else if (bookmarkSet.eventIds.isEmpty() && bookmarkSet.coordinates.isEmpty()) "No items in this list"
                    else "Items not loaded yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                items(items = events, key = { it.id }) { event ->
                    val profile = eventRepo.getProfileData(event.pubkey)
                    val likeCount = reactionVersion.let { eventRepo.getReactionCount(event.id) }
                    val userEmojis = reactionVersion.let {
                        userPubkey?.let { eventRepo.getUserReactionEmojis(event.id, it) } ?: emptySet()
                    }
                    val translationState = remember(translationVersion, event.id) {
                        translationRepo?.getState(event.id) ?: com.darkwisp.app.repo.TranslationState()
                    }
                    if (event.kind == Nip99.KIND_LISTING) {
                        val address = Nip99.addressTag(event)
                        val priceChange = priceChanges[address]
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (priceChange != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text(
                                            stringResource(R.string.marketplace_watchlist_price_changed),
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Text(
                                            "${priceChange.previousPrice} → ${priceChange.currentPrice}",
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        TextButton(
                                            onClick = {
                                                priceTracker?.acknowledge(address)
                                                onMarketplaceListingClick?.invoke(event) ?: onNoteClick(event)
                                            }
                                        ) {
                                            Text(stringResource(R.string.marketplace_watchlist_open_listing))
                                        }
                                    }
                                }
                            }
                            MarketplaceListingCard(
                                event = event,
                                eventRepository = eventRepo,
                                onClick = {
                                    priceTracker?.acknowledge(address)
                                    onMarketplaceListingClick?.invoke(event) ?: onNoteClick(event)
                                }
                            )
                        }
                    } else {
                        PostCard(
                            event = event,
                            profile = profile,
                            onReply = { onReply(event) },
                            onProfileClick = { onProfileClick(event.pubkey) },
                            onNavigateToProfile = onProfileClick,
                            onNoteClick = { onNoteClick(event) },
                            onQuotedNoteClick = onQuotedNoteClick,
                            onReact = { emoji -> onReact(event, emoji) },
                            userReactionEmojis = userEmojis,
                            likeCount = likeCount,
                            eventRepo = eventRepo,
                            onFollowAuthor = { onToggleFollow(event.pubkey) },
                            onBlockAuthor = { onBlockUser(event.pubkey) },
                            isOwnEvent = event.pubkey == userPubkey,
                            translationState = translationState,
                            onTranslate = { translationRepo?.translate(event.id, event.content) }
                        )
                    }
                }
            }
        }
    }
}
