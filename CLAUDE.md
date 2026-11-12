# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assembleDebug       # Build debug APK
./gradlew assembleRelease     # Build release APK (minified with R8)
./gradlew installDebug        # Build and install on connected device/emulator
./gradlew clean               # Clean build artifacts
```

Staging unit tests run with `./gradlew testStagingUnitTest`. JDK 17 and Android SDK 35 are required.

## Architecture

Postilla is an independent Android Nostr client using Kotlin + Jetpack Compose (Material 3), developed from the original Dark Wisp Android project. MVVM with five layers:

**UI** (`ui/screen/`, `ui/component/`) → **ViewModel** (`viewmodel/`) → **Repository** (`repo/`) → **Protocol** (`nostr/`) → **Relay** (`relay/`)

Application source lives under `app/src/main/kotlin/com/darkwisp/app/`. The historical namespace and Android application ID are intentionally retained for update continuity.

### Key Design Decisions

- **No database** — all state in-memory (LRU caches) or SharedPreferences/EncryptedSharedPreferences. Events re-fetched from relays each session.
- **Flow-based reactivity** — SharedFlow for relay events, StateFlow for UI state. No RxJava or LiveData.
- **NIP objects** — each NIP implemented as a Kotlin `object` with static helpers (e.g., `Nip17.createGiftWrap()`). New NIPs go in `NipXX.kt`.
- **Outbox/inbox relay model** — `OutboxRouter` routes queries to author write relays and delivers to recipient read relays based on NIP-65.
- **Relay pool** — `RelayPool` manages persistent connections (max 30) and ephemeral connections (max 50) with automatic cleanup and cooldowns.
- **Encrypted key storage** — private keys in EncryptedSharedPreferences (AES256-GCM), never plain SharedPreferences.

### Protocol Layer (`nostr/`)

Each NIP is a standalone Kotlin `object`:
- Events created via `NostrEvent.create(privkey, pubkey, kind, content, tags)`
- Hex encoding: `ByteArray.toHex()` / `String.hexToByteArray()` (extensions in Event.kt)
- Condensed NIP reference docs at `.claude/nips/*.md` with index at `.claude/nips/README.md`

### Relay Layer (`relay/`)

- `Relay` — single WebSocket connection via OkHttp
- `RelayPool` — connection pooling with persistent/ephemeral split
- `OutboxRouter` — outbox/inbox routing per NIP-65
- `RelayScoreBoard` — tracks relay reliability and author coverage
- `SubscriptionManager` — REQ subscription lifecycle

### Repository Layer (`repo/`)

- `EventRepository` — LRU cache (5,000 events), profile parsing, reaction/repost/zap tracking
- `ContactRepository` — follow list with SharedPreferences persistence
- `KeyRepository` — EncryptedSharedPreferences for private keys
- `DmRepository` — conversation caching with ECDH key cache

## Code Conventions

- Kotlin with Jetpack Compose — no XML layouts
- `Dispatchers.Default` for CPU-bound work, `Dispatchers.IO` for network
- `StateFlow` for UI state, `SharedFlow` for relay events
- Default relays: `wss://relay.damus.io`, `wss://relay.primal.net`
- Navigation routes defined in `Navigation.kt`

## Crypto Stack

- **Signing**: secp256k1-kmp (Schnorr) with JNI Android bindings
- **NIP-44 encryption**: ECDH + HKDF + XChaCha20 + HMAC-SHA256 (Bouncy Castle)
- **Key storage**: Android Security Crypto (AES256-GCM)

## ProGuard / R8

Release builds use R8 minification. Keep rules in `app/proguard-rules.pro` cover kotlinx.serialization, secp256k1 JNI, Bouncy Castle, OkHttp, Coil, Security Crypto, Media3, and ZXing.


## Postilla brand and release continuity

- Public product name: **Postilla**.
- The approved Postilla icon, launcher mark and four feed navigation glyphs from staging #155 are frozen. Do not redesign or replace them unless the user explicitly requests it.
- Visual identity: ebony, ivory and small orange accents only; no purple or gradients.
- Keep `com.darkwisp.app`, `com.isolabellart.darkwispenhanced` and `com.isolabellart.darkwispenhanced.staging` unchanged during the rebrand. Do not change the signing certificate.
- GitHub Actions secret identifiers beginning `DARK_WISP_ENHANCED_` are intentionally retained until a separately approved secrets migration.
- Do not rename the GitHub repository, modify the official release branch, publish GitHub Releases or publish Zapstore/Nostr events as part of source-only rebranding work.
