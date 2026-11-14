# Postilla — public rebrand preparation

## Scope

This branch prepares the public-facing transition from **Dark Wisp Enhanced** to **Postilla**.
It does not publish a release, modify the official release branch, rename the GitHub
repository, or publish a Zapstore/Nostr event.

The current face mark and extracted wordmark in the supplied Postilla presentation board
supersede the earlier paper-note artwork approved during staging #155. The launcher icon,
in-app mark, splash identity and wordmark must all use this current version.

## Identity continuity

| Layer | Prepared state |
|---|---|
| Public product name | Postilla |
| App label in Android | Postilla (already present in staging #155) |
| Android application ID | `com.isolabellart.darkwispenhanced` — intentionally unchanged |
| Staging application ID | `com.isolabellart.darkwispenhanced.staging` — unchanged |
| Signing certificate | Existing permanent certificate — unchanged |
| GitHub repository | `isolabellart/dark-wisp-android` — not renamed in this phase |
| Release branch | `release/dark-wisp-enhanced` — not renamed in this phase |
| Zapstore package identity | Existing application package — no new listing created |

The legacy Android namespace, package IDs, release branch and GitHub Actions secret
identifiers are technical continuity points, not user-visible product branding. Renaming
them is not required for users to see Postilla or receive future updates.

## Prepared assets and metadata

- `README.md` presents Postilla and embeds the official brand board.
- The Android app mark and adaptive launcher foreground use the new face symbol.
- The in-app wordmark uses vector outlines extracted from the supplied artwork, in
  light and dark variants; no system-font spacing is involved.
- `zapstore.yaml` contains the future Postilla name, description and brand-board image.
  It keeps the existing repository URL and matches both the historical Dark Wisp Enhanced
  APK filename and future Postilla filenames.
- The Postilla staging workflow uses Postilla labels and artifact naming, and includes
  this branch.
- The release workflow is still manual-only and refuses to publish from this preparation branch.
- `RELEASE_SIGNING.md` documents the unchanged application ID, certificate and legacy
  Actions secret names.

## Store screenshots

The current Postilla phone screenshots for the GitHub README and future store/Zapstore presentation are maintained in:

`fastlane/metadata/android/en-US/images/phoneScreenshots/`

This branch adds five updated PNG screenshots, named `01.png` through `05.png`.
The previous JPG screenshots have been removed from this branch. Their originals
remain recoverable from the local backup and Git history.

Use these five PNG files as the current screenshot set for the next store presentation.
No Zapstore listing has been published or updated by this documentation change.

## Not done intentionally

- No `zsp check`, `zsp publish`, Nostr signing or Zapstore listing update.
- No GitHub repository rename or metadata settings change.
- No tag creation, GitHub Release, APK publication or version bump.
- No screenshot package or marketing page publication.
- No change to application IDs, package identity or signing material.

## Safe activation sequence for a later, explicit release decision

1. Finish reviewing this branch and merge the source-only rebrand into the appropriate release line.
2. Confirm the final staging APK installs over the current Dark Wisp Enhanced installation
   and preserves its package, signing certificate and local data.
3. Rename the GitHub repository in a separately planned step, then update local Git remotes
   and any external canonical links. GitHub redirects most repository URLs, but raw URLs
   and workflow-action references need special care.
4. Update the existing Zapstore listing using the same Android package identity; do not
   create a second Postilla listing.
5. Use the refreshed English phone screenshots from the Fastlane directory and capture
   any additional real screenshots needed for the final listing.
6. Publish a new version only after the user explicitly authorizes the release.
