package com.darkwisp.app.repo

/**
 * Deterministic relay plan for resolving one addressable Nostr coordinate.
 * Explicit NIP-19 hints and the author's NIP-65 outbox stay at the front of the
 * plan and are separately tracked as priority destinations for EOSE completion.
 */
internal data class AddressableRelayPlan(
    val targetUrls: List<String>,
    val priorityRelayUrls: List<String>
)

internal fun buildAddressableRelayPlan(
    priorityRelays: Collection<String>,
    topRelays: Collection<String>,
    accountReadRelays: Collection<String>,
    marketplaceRelays: Collection<String>
): AddressableRelayPlan {
    val priority = priorityRelays.filter(String::isNotBlank).distinct()
    val targets = (
        priority +
            topRelays.filter(String::isNotBlank) +
            accountReadRelays.filter(String::isNotBlank) +
            marketplaceRelays.filter(String::isNotBlank)
        ).distinct()
    return AddressableRelayPlan(
        targetUrls = targets,
        priorityRelayUrls = priority
    )
}
