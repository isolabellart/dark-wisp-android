package com.darkwisp.app.nostr

/**
 * Public relay destinations used to discover and publish NIP-99 classified listings.
 *
 * Barattolo's relay is deliberately read-only here: its operator restricts writes to
 * approved accounts. Public marketplace relays are kept separate from each user's NIP-65
 * relay preferences, so read-only personal relays are never silently promoted to writers.
 */
object MarketplaceRelays {
    val READ = listOf(
        "wss://relay.dwadziesciaj21.pl",
        "wss://nostr.einundzwanzig.space",
        "wss://espelho.girino.org",
        "wss://adre.su",
        "wss://nostr.mom",
        "wss://purplepag.es",
        "wss://relay.barattolo.store",
        "wss://relay.nostr.band",
    )

    val WRITE = listOf(
        "wss://relay.dwadziesciaj21.pl",
        "wss://nostr.einundzwanzig.space",
        "wss://espelho.girino.org",
        "wss://adre.su",
        "wss://nostr.mom",
        "wss://purplepag.es",
        "wss://relay.nostr.band",
        "wss://relay.damus.io",
        "wss://relay.primal.net",
        "wss://nos.lol"
    )
}
