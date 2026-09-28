# Rust migration + consolidation plan — Grove Launcher 0.1.25

## The one rule

Rust can only take **pure logic**: no Views, Intents, ContentResolver, Bitmap/Canvas,
permissions, or any Android framework API. And every JNI crossing costs — so a move is
only worth it when the work is **batched** (one call per query/scan, like the native
calls already do) or too hot/tricky to keep in two languages. "Can move" is not "should
move": `PinnedApps.moveTo` is pure, but a JNI call for a 3-line list op is pure loss.

## Status: implemented in 0.1.25

All four items below are implemented, with Kotlin fallbacks preserved for when the
native library is absent (and unit-tested on the JVM).

### 1. Top-K search → single `searchNative` call
`CoreBridge.searchNative(labels, query, limit): IntArray` scores every label and
returns the winning **indices** in final order. One JNI call per keystroke — the old
path allocated a full score array and did the PriorityQueue selection in Kotlin.
The tie-break rule (score desc, index asc, earliest wins) is proven by the
`top_k_matches_kotlin_priority_queue_selection` and `top_k_large_scale_keeps_earliest_equal_scores`
Rust tests. `SearchResults.matching` keeps its signature; tests are unaffected.

### 2. UTF-16 edit distance — the parity hack is gone
The old `CoreBridge.rank` fell back to Kotlin for any non-ASCII query because Rust
counted Unicode scalars and Java counts UTF-16 code units. The Rust edit distance
now runs over UTF-16 code units (`str::encode_utf16`), and the length gates
(`term.length >= 3`) use UTF-16 counts too — so native and Kotlin agree
**bit-for-bit** on every input. The non-ASCII fallback branch is deleted.
`Search.normalize`/`prepare` deliberately stay in Kotlin: they are 3 correct lines,
already needed for the fallback, and moving them buys nothing measurable.
Proven by `utf16_edit_distance_matches_kotlin_for_non_ascii` (é vs e, emoji
surrogate pairs).

### 3. Extension → (mime, category) table → Rust
`CoreBridge.classifyNative(extensions): Array<String>` returns `"mime|category"`
per extension, or `""` when unknown. `FileIndex` keeps Android's `MimeTypeMap`
first and the octet-stream default last, so behavior is unchanged; the old
`extraMimeTypes` map and the separate `categoriesNative` call are gone.

### 4. Procedural wallpaper renderer → Rust
`CoreBridge.renderWallpaperNative(style, width, height): IntArray` runs a small
software rasterizer (diagonal 3-stop gradient, accent circle, translucent ellipses
or mountain polygons with SRC_OVER blending) and returns ARGB pixels, which
`WallpaperArt.create` wraps in a Bitmap. The Canvas painter stays as
`createCanvas` for the no-native fallback. Composition and colors match the old
art; edges are crisper (no anti-aliasing) — generative art, no pixel-parity
requirement.

## Do NOT move

- `ContactIndex.normalizeNumber` / `collapseChannels` / `whatsAppTargets` — one-line
  or tiny-list logic on the menu-open path; JNI overhead dominates. Stays Kotlin.
- `PinnedApps`, `Gestures` — pure but trivial; nothing to gain.
- `Config` JSON serialization — Kotlin data classes are the UI's language; validation
  (the part that matters) is already in Rust.
- `ThemeColors.wallpaperButtonColors` — 3 lines over Android's `WallpaperColors`.
- Anything touching the framework: `FileIndex.scan` traversal, `ContactIndex.load`
  / `details` (ContentResolver), all of `ui/UiKit`, widgets, intents.

## Kotlin consolidation (no Rust involved)

- `MainActivity` (~1590 lines) is the last big file: drawer tiles
  (`createTile`/`bindTile`), folder management, and the widget lifecycle would each
  make a clean screen/manager object like `SearchScreen` / `WidgetScreen`. Not urgent,
  but it's where the next "merge and consolidate" pass should go.
