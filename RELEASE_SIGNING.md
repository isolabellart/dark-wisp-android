# Dark Wisp Enhanced release signing

The release application has its own Android application ID:

`com.isolabellart.darkwispenhanced`

This is intentionally different from Barry Deen's upstream package and from the staging/debug variants. It allows Dark Wisp Enhanced to be installed alongside them and gives this release line its own update identity.

## Signing key

The release APK must always be signed with the same private keystore. The keystore is generated separately and must never be committed to this repository.

- Keystore file: `dark-wisp-enhanced-release.jks`
- Key alias: `dark-wisp-enhanced-release`
- Store type: JKS
- Key algorithm: RSA 4096 / SHA256withRSA
- Certificate SHA-256: `86:86:8A:36:59:72:9A:65:31:F3:E8:A3:1D:5D:CF:E0:44:05:E2:A8:68:66:F3:E0:EB:0A:BB:69:65:CD:B8:41`

Store the keystore in at least two secure locations and keep both passwords in a password manager. Losing the keystore means future APKs cannot update installations signed with this identity.

## Configure GitHub Actions

In this repository, open **Settings → Secrets and variables → Actions** and create these repository secrets:

1. `DARK_WISP_ENHANCED_KEYSTORE_BASE64` — base64 encoding of the JKS file.
2. `DARK_WISP_ENHANCED_STORE_PASSWORD` — keystore password.
3. `DARK_WISP_ENHANCED_KEY_ALIAS` — `dark-wisp-enhanced-release`.
4. `DARK_WISP_ENHANCED_KEY_PASSWORD` — private-key password.

On macOS, copy the base64-encoded keystore to the clipboard with:

```bash
base64 -i dark-wisp-enhanced-release.jks | tr -d '\n' | pbcopy
```

Do not paste the keystore or either password into source files, issues, pull requests or chat messages.

After the secrets are configured, push a new commit to `release/dark-wisp-enhanced` to trigger the signed release workflow. The first run before secrets are configured skips safely without publishing an APK.

The workflow runs unit tests, builds `assembleRelease`, verifies the APK signature against the certificate fingerprint above, uploads the APK as a workflow artifact, and creates a GitHub pre-release with the APK attached. GitHub Release assets expose download counts; Actions artifacts do not.

## Versioning

The first published build had an invalid launcher foreground image and is superseded. The corrected release is versionCode `2`, versionName `1.0.1`. Increase versionCode for every subsequent Android update. Keep the application ID and signing key unchanged for all updates in this release line.
