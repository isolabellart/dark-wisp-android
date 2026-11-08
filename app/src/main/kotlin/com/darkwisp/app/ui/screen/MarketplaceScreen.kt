package com.darkwisp.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import com.darkwisp.app.ui.component.ProfilePicture
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.darkwisp.app.R
import com.darkwisp.app.nostr.Nip99
import com.darkwisp.app.nostr.NostrEvent
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
    onCreateListing: () -> Unit
) {
    val listings by viewModel.listings.collectAsState()
    val selectedScope by viewModel.scope.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(selectedScope, followedPubkeys, userPubkey) {
        viewModel.load(selectedScope, relayPool, eventRepository, followedPubkeys, userPubkey)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Storefront, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.marketplace_title))
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
                            viewModel.load(selectedScope, relayPool, eventRepository, followedPubkeys, userPubkey)
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
            FloatingActionButton(onClick = onCreateListing) {
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedScope == MarketplaceScope.ALL,
                    onClick = { viewModel.setScope(MarketplaceScope.ALL) },
                    label = { Text(stringResource(R.string.marketplace_all)) }
                )
                FilterChip(
                    selected = selectedScope == MarketplaceScope.FOLLOWING,
                    onClick = { viewModel.setScope(MarketplaceScope.FOLLOWING) },
                    label = { Text(stringResource(R.string.marketplace_following)) }
                )
                FilterChip(
                    selected = selectedScope == MarketplaceScope.MINE,
                    onClick = { viewModel.setScope(MarketplaceScope.MINE) },
                    label = { Text(stringResource(R.string.marketplace_mine)) }
                )
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
                        Text(stringResource(R.string.marketplace_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    eventRepository: EventRepository
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
    val suggestedMessage = stringResource(R.string.marketplace_message_intro, title)

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
                    Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.marketplace_edit))
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.marketplace_contact_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.marketplace_contact_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = message,
                                onValueChange = { message = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(bringIntoViewRequester)
                                    .onFocusChanged { state ->
                                        if (state.isFocused) {
                                            detailScope.launch {
                                                delay(250)
                                                bringIntoViewRequester.bringIntoView()
                                            }
                                        }
                                    },
                                minLines = 3,
                                label = { Text(stringResource(R.string.marketplace_message_label)) }
                            )
                            Button(
                                onClick = { onContactSeller(message.trim()) },
                                enabled = !sold && message.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.marketplace_contact_button))
                            }
                            Text(
                                stringResource(R.string.marketplace_dm_privacy),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
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
