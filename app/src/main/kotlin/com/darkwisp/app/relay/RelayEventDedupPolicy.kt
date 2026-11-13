package com.darkwisp.app.relay

/**
 * Subscription IDs that must receive responses even when the same event ID was
 * already delivered through another subscription.
 *
 * Explicit NIP-19 address resolution and NIP-65 peer relay-list discovery need this:
 * the active request must be able to observe the relay response and update the resolver.
 */
internal object RelayEventDedupPolicy {
    val defaultBypassPrefixes = listOf(
        "thread-", "user", "quote-", "editprofile", "notif", "dms", "search-", "hashtag-",
        "addr-", "peer-relays-"
    )

    fun shouldBypass(
        subscriptionId: String,
        prefixes: Collection<String> = defaultBypassPrefixes
    ): Boolean = prefixes.any { prefix ->
        if (prefix.endsWith("-")) subscriptionId.startsWith(prefix)
        else subscriptionId == prefix || subscriptionId.startsWith(prefix)
    }
}
