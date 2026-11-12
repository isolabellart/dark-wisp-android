# Postilla — release signing and publishing

This document describes the permanent signing identity and the release process for the independent Postilla Android application.

## Current release identity

| Setting | Current value |
|---|---|
| Application ID | `com.isolabellart.darkwispenhanced` |
| Staging application ID | `com.isolabellart.darkwispenhanced.staging` |
| Current stable version | `1.0.10` |
| Current version code | `11` |
| Minimum Android version | Android 8.0 (API 26) |
| Packaged ABI | `arm64-v8a` |

The Enhanced application ID is intentionally separate from the upstream Dark Wisp package. It allows both applications to be installed side by side, with separate local application data.

## Permanent APK signing identity

Every public Enhanced APK update must be signed with the same private keystore. **Never generate a replacement key for an existing installation line, change the alias, or change the expected certificate fingerprint.** Android will reject an update signed with a different certificate.

- Keystore file: `dark-wisp-enhanced-release.jks` (kept outside the repository)
- Keystore type: JKS
- Key alias: `dark-wisp-enhanced-release`
- Key algorithm: RSA 4096 / SHA256withRSA
- Certificate SHA-256: `86:86:8A:36:59:72:9A:65:31:F3:E8:A3:1D:5D:CF:E0:44:05:E2:A8:68:66:F3:E0:EB:0A:BB:69:65:CD:B8:41`

Keep at least two secure backups of the keystore and store both passwords in a password manager. Never commit the JKS file, passwords, base64 keystore contents, or personal access tokens to Git.

## GitHub Actions secrets

Configure these as repository Actions secrets under **Settings → Secrets and variables → Actions**. Their `DARK_WISP_ENHANCED_` names are intentionally retained during the rebrand so existing CI signing configuration is not broken; do not rename or recreate them as part of this source-only transition:

1. `DARK_WISP_ENHANCED_KEYSTORE_BASE64` — base64-encoded contents of the JKS file.
2. `DARK_WISP_ENHANCED_STORE_PASSWORD` — keystore password.
3. `DARK_WISP_ENHANCED_KEY_ALIAS` — `dark-wisp-enhanced-release`.
4. `DARK_WISP_ENHANCED_KEY_PASSWORD` — private-key password.
5. `DARK_WISP_ENHANCED_RELEASE_TOKEN` — fine-grained personal access token used by the release workflow to publish the GitHub Release.

The release token must be limited to this repository and have **Contents: Read and write** and **Workflows: Read and write** permissions. The workflow checks for this secret before attempting to publish. Do not use the keystore password or any other signing secret as the release token.

On macOS, create a single-line base64 value and copy it to the clipboard with:

```bash
base64 -i dark-wisp-enhanced-release.jks | tr -d '\n' | pbcopy
```

Do not paste the keystore or any password/token into source files, issues, pull requests, logs, or chat messages.

On the Mac used to publish with `zsp`, keep the temporary `SIGN_WITH=bunker://…` value in a local `.env` file at the repository root. This file is ignored by Git. Restrict it to your user with `chmod 600 .env`, and remove the bunker connection from Amber when the publishing session is finished. Never put a bunker URL in `zapstore.yaml`, source code, or a committed file.

## Build and release workflow

The dedicated workflow is still stored at `.github/workflows/dark-wisp-enhanced-release.yml` on the existing `release/dark-wisp-enhanced` branch. Its visible Actions name is Postilla; the historical file path and branch name are retained for continuity during this preparation phase. It is **manual-only**: a push does not publish a release.

The workflow performs the following checks and actions:

1. Confirms all signing secrets and the release token are present.
2. Runs `testStagingUnitTest` on a manual dispatch.
3. Builds the minified release APK with `assembleRelease`.
4. Verifies the package ID, version name, version code, APK signature, and permanent certificate fingerprint.
5. Uploads the signed APK as a GitHub Actions artifact.
6. Creates a GitHub Release and attaches the signed APK. The default is a stable release; the `prerelease` input can explicitly request a pre-release.

The staging workflow is separate. It runs unit tests, builds the signed staging APK, checks its staging package ID and certificate, and uploads it as a workflow artifact. Install and test a staging build on a real device before publishing a new stable version.

To dispatch the release workflow with GitHub CLI, from a checkout authenticated to this repository:

```bash
gh workflow run dark-wisp-enhanced-release.yml \
  --ref release/dark-wisp-enhanced \
  -f release_tag=v1.0.11 \
  -f prerelease=false
```

Replace the example tag with the exact new version being released. The workflow file must be present on the selected ref. A tag must not already exist.

## Preparing a future version

For every new release:

1. Increase `versionCode` by at least one and update `versionName` in `app/build.gradle.kts`.
2. Update the version-specific artifact and release notes as appropriate. The Postilla release workflow reads the expected version name and code directly from Gradle.
3. Update the English and Italian release-note text embedded in the release workflow so it describes the actual version and features.
4. Commit the changes to `release/dark-wisp-enhanced`.
5. Run staging validation, install the APK on a device, and verify the features being released.
6. Dispatch the release workflow with a new tag. Confirm the resulting GitHub Release is stable (not marked as a pre-release) unless a pre-release was intentional.

Do not reuse a published version code or tag. Keep the application ID, keystore, alias, and certificate fingerprint unchanged across all updates in this release line.

## Wallet and user-data safety

Postilla is a separate application from upstream Dark Wisp. Installing it does not migrate or overwrite the original application's local data. Users must import or restore accounts and wallet access through the app's normal secure recovery flow.

Never ask users to share a wallet recovery phrase or private key. Keep recovery material offline and never include it in release assets, logs, or support messages.
