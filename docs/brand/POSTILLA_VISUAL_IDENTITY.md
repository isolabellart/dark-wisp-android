# Postilla — visual identity

## Brand idea
Postilla is an editorial, human social space. A post is a small note left for others;
a marketplace listing is another thing worth sharing. The interface should feel like a
modern independent publication, not a technical Nostr dashboard.

## Official presentation board

![Postilla official rebrand board](postilla-rebrand-board.webp)

The board above is the approved public presentation of the mark and wordmark. Keep it as the visual reference; do not redraw or reinterpret the approved icon.

## Mark
The master mark is an ivory sheet with three dark lines and an orange folded corner,
set on a flat ebony rounded square. The lowercase wordmark uses a restrained sans-serif;
orange is a small punctuation mark, not a decorative fill.

## Palette
Only two appearances are supported.

| Role | Light | Dark |
|---|---|---|
| Background | Ivory #F4EBDD | Ebony #1B1916 |
| Raised surface | #FFFAF1 | #24211E |
| Inset surface | #EAE1D3 | #302C27 |
| Primary text | Ebony #1B1916 | Ivory #F4EBDD |
| Secondary text | #6C645A | #B8AEA1 |
| Accent | #A65318 | #F08A32 |

No purple, alternate colour themes, gradients, glows or ornamental shadows.
Orange is reserved for selected navigation, focus, small state cues and important actions.

## Typography
Use the native sans-serif system family as a fast, platform-safe Helvetica-like stack.
Keep a clear scale, generous line height and restrained weights. Do not bundle a web font
or block first paint to load typography.

## Layout and interaction
- Use editorial hierarchy, quiet dividers and whitespace instead of nested cards.
- Keep primary touch targets at least 48 dp and give icons breathing room.
- Prefer labels where they improve navigation; do not cram controls into the top bar.
- Drawer and popup motion should be short and calm, with flat ebony/ivory surfaces and
  a restrained orange active state.
- Images and event content must load independently of ornamental UI work.

## Implementation rule
This branch is a visual redesign. Preserve Nostr event semantics, signing, relay behaviour,
persistence and Marketplace data paths while the presentation changes.


## Frozen assets

The launcher icon, paper-note mark, wordmark and four feed-navigation glyphs approved in staging #155 are frozen. Any later visual work must build around them, not alter their geometry, proportions or character.
