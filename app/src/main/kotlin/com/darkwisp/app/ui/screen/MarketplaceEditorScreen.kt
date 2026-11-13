package com.darkwisp.app.ui.screen

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.darkwisp.app.R
import com.darkwisp.app.nostr.ClientMessage
import com.darkwisp.app.nostr.Nip09
import com.darkwisp.app.nostr.Nip99
import com.darkwisp.app.nostr.Nip01EventOrder
import com.darkwisp.app.nostr.MarketplaceRelays
import com.darkwisp.app.nostr.MarketplaceCurrencies
import com.darkwisp.app.ui.screen.MarketplaceLightningEstimate
import com.darkwisp.app.ui.component.MarketplaceAvailabilityBadge

import com.darkwisp.app.nostr.NostrEvent
import com.darkwisp.app.nostr.NostrSigner
import com.darkwisp.app.relay.RelayPool
import com.darkwisp.app.repo.BlossomRepository
import com.darkwisp.app.repo.EventRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val MARKETPLACE_CURRENCY_CODES = MarketplaceCurrencies.ALL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceEditorScreen(
    existingEvent: NostrEvent?,
    userPubkey: String?,
    signer: NostrSigner?,
    relayPool: RelayPool,
    eventRepository: EventRepository,
    onBack: () -> Unit,
    onPublished: (NostrEvent) -> Unit,
    onWithdrawn: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val blossomRepository = remember(userPubkey) { BlossomRepository(context, userPubkey) }
    val isEditing = existingEvent != null

    var title by remember(existingEvent?.id) { mutableStateOf(existingEvent?.let { Nip99.title(it) } ?: "") }
    var summary by remember(existingEvent?.id) { mutableStateOf(existingEvent?.let { Nip99.summary(it) } ?: "") }
    var description by remember(existingEvent?.id) { mutableStateOf(existingEvent?.content ?: "") }
    var amount by remember(existingEvent?.id) { mutableStateOf(existingEvent?.let { Nip99.price(it)?.amount } ?: "") }
    var currency by remember(existingEvent?.id) { mutableStateOf(existingEvent?.let { Nip99.price(it)?.currency }?.uppercase() ?: "BTC") }
    var currencyMenuExpanded by remember(existingEvent?.id) { mutableStateOf(false) }
    var location by remember(existingEvent?.id) { mutableStateOf(existingEvent?.let { Nip99.location(it) } ?: "") }
    var category by remember(existingEvent?.id) { mutableStateOf(existingEvent?.let { Nip99.categories(it).joinToString(", ") } ?: "") }
    var images by remember(existingEvent?.id) { mutableStateOf(existingEvent?.let { Nip99.images(it) } ?: emptyList()) }
    var status by remember(existingEvent?.id) { mutableStateOf(existingEvent?.let { Nip99.status(it) } ?: "active") }
    var stock by remember(existingEvent?.id) { mutableStateOf(existingEvent?.let { Nip99.stock(it)?.toString() }.orEmpty()) }
    val dTag = remember(existingEvent?.id) {
        existingEvent?.let { Nip99.dTag(it) }?.takeIf { it.isNotBlank() }
            ?: ("listing-" + UUID.randomUUID().toString())
    }
    val publishedAt = remember(existingEvent?.id) {
        existingEvent?.let { Nip99.publishedAt(it) } ?: (System.currentTimeMillis() / 1000).toString()
    }

    var uploading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var withdrawing by remember { mutableStateOf(false) }
    var showWithdrawConfirmation by remember(existingEvent?.id) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                uploading = true
                error = null
                try {
                    for (uri in uris.take(8 - images.size)) {
                        val result = withContext(Dispatchers.IO) {
                            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                ?: throw IllegalStateException("Could not read selected image")
                            val ext = android.webkit.MimeTypeMap.getSingleton()
                                .getExtensionFromMimeType(mime) ?: "jpg"
                            blossomRepository.uploadMedia(bytes, mime, ext, signer)
                        }
                        images = (images + result).distinct()
                    }
                } catch (e: Exception) {
                    error = context.getString(R.string.marketplace_upload_failed, e.message ?: "")
                } finally {
                    uploading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (isEditing) R.string.marketplace_edit else R.string.marketplace_new
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.marketplace_title_field)) },
                            singleLine = true
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = summary,
                            onValueChange = { summary = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.marketplace_summary_field)) },
                            singleLine = true
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.marketplace_description_field)) },
                            minLines = 5
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = amount,
                                onValueChange = { amount = it },
                                modifier = Modifier.weight(1f),
                                label = { Text(stringResource(R.string.marketplace_price_field)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                            Box(modifier = Modifier.width(124.dp)) {
                                OutlinedTextField(
                                    value = currency,
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(stringResource(R.string.marketplace_currency_field)) },
                                    trailingIcon = { Text(if (currencyMenuExpanded) "▴" else "▾") },
                                    singleLine = true
                                )
                                // An overlay receives taps across the whole outlined field,
                                // including its centre, without depending on TextField internals.
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable { currencyMenuExpanded = true }
                                )
                                DropdownMenu(
                                    expanded = currencyMenuExpanded,
                                    onDismissRequest = { currencyMenuExpanded = false },
                                    modifier = Modifier.heightIn(max = 360.dp)
                                ) {
                                    MARKETPLACE_CURRENCY_CODES.forEach { code ->
                                        DropdownMenuItem(
                                            text = { Text(code) },
                                            onClick = {
                                                currency = code
                                                currencyMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        if (currency != "BTC" && currency != "SATS") {
                            MarketplaceLightningEstimate(amount = amount, currency = currency)
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.marketplace_location_field)) },
                            singleLine = true
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.marketplace_category_field)) },
                            singleLine = true,
                            supportingText = { Text(stringResource(R.string.marketplace_category_hint)) }
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = stock,
                            onValueChange = { value -> stock = value.filter { it.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.marketplace_stock_field)) },
                            supportingText = { Text(stringResource(R.string.marketplace_stock_hint)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.marketplace_status_label), style = MaterialTheme.typography.titleSmall)
                            FilterChip(
                                selected = status == "active",
                                onClick = { status = "active" },
                                label = { Text(stringResource(R.string.marketplace_active)) }
                            )
                            FilterChip(
                                selected = status == "sold",
                                onClick = { status = "sold" },
                                label = { Text(stringResource(R.string.marketplace_sold)) }
                            )
                        }
                    }
                    if (existingEvent != null && existingEvent.pubkey == userPubkey) {
                        item {
                            TextButton(
                                onClick = { showWithdrawConfirmation = true },
                                enabled = !saving && !uploading && !withdrawing &&
                                    signer?.pubkeyHex == existingEvent.pubkey,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    stringResource(R.string.marketplace_withdraw_listing),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.marketplace_photos), style = MaterialTheme.typography.titleSmall)
                            Button(
                                onClick = { imagePicker.launch("image/*") },
                                enabled = !uploading && images.size < 8
                            ) {
                                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.marketplace_add_photos))
                            }
                        }
                    }
                    if (images.isNotEmpty()) {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(images) { url ->
                                    Box(
                                        modifier = Modifier.size(112.dp).clip(RoundedCornerShape(12.dp))
                                    ) {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        IconButton(
                                            onClick = { images = images - url },
                                            modifier = Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(50))
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_remove_image))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (uploading) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(stringResource(R.string.marketplace_uploading), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item {
                        MarketplaceDraftPreview(
                            title = title, summary = summary, amount = amount, currency = currency,
                            location = location, images = images, status = status, stock = stock.toIntOrNull()
                        )
                    }
                    if (error != null) {
                        item {
                            Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (signer == null) {
                        item {
                            Text(stringResource(R.string.marketplace_login_required), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Button(
                    onClick = {
                        if (title.isBlank() || description.isBlank()) {
                            error = context.getString(R.string.marketplace_required_fields)
                            return@Button
                        }
                        if (signer == null) {
                            error = context.getString(R.string.marketplace_login_required)
                            return@Button
                        }
                        val declaredStock = stock.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
                        if (stock.isNotBlank() && (declaredStock == null || declaredStock < 0)) {
                            error = context.getString(R.string.marketplace_stock_invalid)
                            return@Button
                        }
                        scope.launch {
                            saving = true
                            error = null
                            try {
                                val tags = Nip99.buildTags(
                                    dTag = dTag,
                                    title = title,
                                    summary = summary,
                                    publishedAt = publishedAt,
                                    amount = amount,
                                    currency = currency,
                                    location = location,
                                    category = category,
                                    images = images,
                                    status = status,
                                    stock = declaredStock
                                )
                                val latestKnownCreatedAt = eventRepository.findAddressableEvent(
                                    kind = Nip99.KIND_LISTING,
                                    author = signer.pubkeyHex,
                                    dTag = dTag
                                )?.created_at
                                val previousCreatedAt = listOfNotNull(
                                    existingEvent?.takeIf { it.pubkey == signer.pubkeyHex }?.created_at,
                                    latestKnownCreatedAt
                                ).maxOrNull()
                                val createdAt = Nip01EventOrder.nextCreatedAt(
                                    now = System.currentTimeMillis() / 1000,
                                    previousCreatedAt = previousCreatedAt
                                )
                                val event = signer.signEvent(
                                    kind = Nip99.KIND_LISTING,
                                    content = description.trim(),
                                    tags = tags,
                                    createdAt = createdAt
                                )
                                publishMarketplaceEvent(relayPool, event, context)
                                eventRepository.addEvent(event)
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        if (isEditing) R.string.marketplace_update_confirmed
                                        else R.string.marketplace_publish_confirmed
                                    ),
                                    Toast.LENGTH_LONG
                                ).show()
                                onPublished(event)
                            } catch (e: Exception) {
                                error = context.getString(R.string.marketplace_publish_failed, e.message ?: "")
                            } finally {
                                saving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving && !uploading && !withdrawing && signer != null
                ) {
                    if (saving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(if (isEditing) R.string.marketplace_update else R.string.marketplace_publish))
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    if (showWithdrawConfirmation && existingEvent != null) {
        AlertDialog(
            onDismissRequest = {
                if (!withdrawing) showWithdrawConfirmation = false
            },
            title = { Text(stringResource(R.string.marketplace_withdraw_title)) },
            text = { Text(stringResource(R.string.marketplace_withdraw_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (withdrawing) return@TextButton
                        scope.launch {
                            withdrawing = true
                            error = null
                            try {
                                val currentSigner = signer
                                    ?: throw IllegalStateException(context.getString(R.string.marketplace_login_required))
                                if (currentSigner.pubkeyHex != existingEvent.pubkey) {
                                    throw IllegalStateException(context.getString(R.string.marketplace_withdraw_wrong_account))
                                }
                                val listingDTag = Nip99.dTag(existingEvent)
                                require(listingDTag.isNotBlank()) {
                                    context.getString(R.string.marketplace_withdraw_invalid_address)
                                }
                                val deletionEvent = currentSigner.signEvent(
                                    kind = Nip09.KIND_DELETION,
                                    content = context.getString(R.string.marketplace_withdrawal_reason),
                                    tags = Nip09.buildAddressableDeletionTags(
                                        kind = Nip99.KIND_LISTING,
                                        pubkey = existingEvent.pubkey,
                                        dTag = listingDTag
                                    )
                                )
                                publishMarketplaceEvent(relayPool, deletionEvent, context)
                                eventRepository.addEvent(deletionEvent)
                                showWithdrawConfirmation = false
                                onWithdrawn()
                            } catch (e: Exception) {
                                error = context.getString(
                                    R.string.marketplace_withdraw_failed,
                                    e.message ?: ""
                                )
                                showWithdrawConfirmation = false
                            } finally {
                                withdrawing = false
                            }
                        }
                    },
                    enabled = !withdrawing && !saving && !uploading
                ) {
                    if (withdrawing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        stringResource(R.string.marketplace_withdraw_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showWithdrawConfirmation = false },
                    enabled = !withdrawing
                ) {
                    Text(stringResource(R.string.marketplace_withdraw_cancel))
                }
            }
        )
    }
}
@Composable
private fun MarketplaceDraftPreview(title: String, summary: String, amount: String, currency: String, location: String, images: List<String>, status: String, stock: Int?) {
    val availability = Nip99.availability(status, stock)
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.marketplace_preview_title), style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text(stringResource(R.string.marketplace_preview_live), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(190.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            val firstImage = images.firstOrNull()
            if (firstImage != null) {
                AsyncImage(model = firstImage, contentDescription = title.ifBlank { null }, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                Text(stringResource(R.string.marketplace_preview_no_image), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title.ifBlank { stringResource(R.string.marketplace_preview_untitled) }, style = MaterialTheme.typography.titleLarge, maxLines = 2)
            if (summary.isNotBlank()) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
            if (amount.isNotBlank()) Text("$amount $currency", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            MarketplaceAvailabilityBadge(availability)
            if (location.isNotBlank()) Text(location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
