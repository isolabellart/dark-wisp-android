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
import kotlinx.coroutines.delay
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
import com.darkwisp.app.nostr.Filter
import com.darkwisp.app.nostr.ClientMessage
import com.darkwisp.app.nostr.NostrSigner
import com.darkwisp.app.nostr.NostrEvent
import com.darkwisp.app.ui.component.ZapDialog
import com.darkwisp.app.relay.RelayPool
import com.darkwisp.app.repo.EventRepository
import com.darkwisp.app.viewmodel.MarketplaceScope
import com.darkwisp.app.viewmodel.MarketplaceViewModel

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
private fun MarketplaceListingCard(event: NostrEvent, eventRepository: EventRepository, onClick: () -> Unit) {
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
    fetchPaymentTargets: (suspend (String) -> List<com.darkwisp.app.nostr.NipA3.PaymentTarget>)?
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
    val reactionVersion by eventRepository.reactionVersion.collectAsState(initial = 0)
    val reactionCount = remember(event.id, reactionVersion) { eventRepository.getReactionCount(event.id) }
    val zapVersion by eventRepository.zapVersion.collectAsState(initial = 0)
    val zapSats = remember(event.id, zapVersion) { eventRepository.getZapSats(event.id) }
    val suggestedMessage = stringResource(R.string.marketplace_message_intro, title)
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
        val subId = "marketplace-comments-${event.id.take(12)}"
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            relayPool.relayEvents.filter { it.subscriptionId == subId }.collect { frame ->
                val comment = frame.event
                if (Nip22.referencesAddressableRoot(comment, commentAddress) &&
                    eventRepository.isEventVisibleToUser(comment) && comments.none { it.id == comment.id }) {
                    comments.add(comment)
                    comments.sortBy { it.created_at }
                    eventRepository.addEvent(comment)
                    eventRepository.requestProfileIfMissing(comment.pubkey)
                }
            }
        }
        relayPool.sendToReadRelays(ClientMessage.req(
            subId, Filter(kinds = listOf(Nip22.KIND_COMMENT), bigATags = listOf(commentAddress), limit = 100)
        ))
        try { awaitCancellation() } finally {
            collector.cancel()
            relayPool.closeOnAllRelays(subId)
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Text(event.content, style = MaterialTheme.typography.bodyLarge)
                if (event.pubkey == userPubkey) {
                    Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.marketplace_edit)) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.marketplace_feedback_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onReact, enabled = signer != null) { Text(stringResource(R.string.marketplace_react_count, reactionCount)) }
                        OutlinedButton(onClick = { showZapDialog = true }, enabled = !sold) { Text(stringResource(R.string.marketplace_zap_count, zapSats)) }
                    }
                    Text(stringResource(R.string.marketplace_comment_count, comments.size), style = MaterialTheme.typography.titleMedium)
                    if (comments.isEmpty()) Text(stringResource(R.string.marketplace_comment_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else comments.sortedBy { it.created_at }.takeLast(40).forEach { comment ->
                        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(comment.pubkey.take(8) + "…" + comment.pubkey.takeLast(4), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(comment.content, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
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
                                relayPool.sendToWriteRelays(ClientMessage.event(comment))
                                eventRepository.addEvent(comment)
                                if (comments.none { it.id == comment.id }) { comments.add(comment); comments.sortBy { it.created_at } }
                                commentText = ""
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
