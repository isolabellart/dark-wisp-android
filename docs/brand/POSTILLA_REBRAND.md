# Postilla — public rebrand preparation

## Scope

This branch prepares the public-facing transition from **Dark Wisp Enhanced** to **Postilla**. It does not publish a release, modify the official release branch, rename the GitHub repository, or publish a Zapstore/Nostr event.

The approved presentation board is `postilla-rebrand-board.webp`. The launcher icon, logo, wordmark and four feed glyphs approved in staging #155 are frozen and must not be altered as part of the rebrand.

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

The legacy Android namespace, package IDs, release branch and GitHub Actions secret identifiers are technical continuity points, not user-visible product branding. Renaming them is not required for users to see Postilla or receive future updates.

## Prepared assets and metadata

- `README.md` presents Postilla and embeds the supplied brand board.
- `zapstore.yaml` contains the future Postilla name, description and brand-board image. It keeps the existing repository URL and matches both the historical Dark Wisp Enhanced APK filename and future Postilla filenames.
- The Postilla staging workflow uses Postilla labels and artifact naming, and includes this branch.
- The release workflow is still manual-only and now refuses to publish from this preparation branch.
- `RELEASE_SIGNING.md` documents the unchanged application ID, certificate and legacy Actions secret names.

## Not done intentionally

- No `zsp check`, `zsp publish`, Nostr signing or Zapstore listing update.
- No GitHub repository rename or metadata settings change.
- No tag creation, GitHub Release, APK publication or version bump.
- No screenshot package or marketing page yet; screenshots will be produced later from a real, tested Postilla build.
- No change to launcher icons, approved glyphs, visual palette, Android application IDs or signing material.

## Safe activation sequence for a later, explicit release decision

1. Finish reviewing this branch and merge the source-only rebrand into the appropriate release line.
2. Confirm the final staging APK installs over the current Dark Wisp Enhanced installation and preserves its package, signing certificate and local data.
3. Rename the GitHub repository in a separately planned step, then update local Git remotes and any external canonical links. GitHub documents redirects for most repository URLs, but raw URLs and workflow-action references need special care.
4. Update the existing Zapstore listing using the same Android package identity; do not create a second Postilla listing.
5. Capture real screenshots from the approved build and add them to Zapstore metadata.
6. Publish a new version only after the user explicitly authorizes the release.
