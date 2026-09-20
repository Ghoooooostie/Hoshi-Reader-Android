# Changelog

All notable user-visible changes to Hoshi Reader Android are documented here.
The format follows a Keep a Changelog style, and release sections use Semantic Versioning.
Historical release notes before v1.3.0 live in [CHANGELOG_ARCHIVE.md](CHANGELOG_ARCHIVE.md).

## [Unreleased]

### Fixed

- Fix the in-chapter reading progress freezing in paginated mode: turning pages no
  longer updates the position indicator and the page-turn position is not saved.
  This also made AI full-page translation stop after the first page, since it is
  re-triggered by progress changes.
- Fix the reading position jumping a few sentences while persistent AI translation
  is on: inserting a translation used to reflow the page without restoring the
  reading anchor. Both paginated and continuous modes are affected.
- Fix Read Aloud skipping sentences when persistent AI translation is on, which
  also left those paragraphs permanently untranslated: both the speech queue and
  the translation queue now advance in document order instead of re-reading
  whatever happens to be on screen, so no paragraph is skipped when a translation
  reflows the page.

### Added

- Add Read Aloud in the reader menu. It opens a settings popup where you pick the
  engine (system text-to-speech or a downloaded local model), a specific system TTS
  voice and the speech rate, then start speaking the Japanese text of the visible
  page sentence by sentence. While reading, a floating play/pause button stays on
  screen; tapping pause reveals previous and next sentence buttons underneath it.
  Read Aloud runs as a foreground media service: it shows a lock-screen / notification
  media control, requests audio focus (and pauses for calls or when headphones are
  unplugged), and lets you pick a specific installed system TTS engine. The
  paragraph currently being spoken is highlighted in the reader and the page
  follows along, turning pages (with a brief pause per page in Read-by-page mode)
  until the chapter ends. New Read Aloud settings add: ignore audio focus, pause
  during phone calls, keep the service awake (wake lock), media-button previous/next
  mapped to paragraphs, read by page, and a shortcut to the system TTS settings.
- Add a Read Aloud option to start speaking from the sentence you trigger with the sentence
  gesture. When enabled in the Read Aloud settings, the sentence gesture both shows the
  translation and begins reading from that sentence, then continues through the chapter in order.
- Add configurable reader gestures in Settings > Behavior. The long-press action can be set to
  "Read & translate sentence" (the previous default) or "Slide to select words": the latter keeps
  the native text selection, so you can long-press a word and drag the selection handles to adjust
  it, then use the system menu to copy, annotate, or look it up. The double-tap action can be set
  to "Read & translate sentence" so you can trigger the sentence gesture with a double-tap instead
  of a long-press. Both default to off / long-press sentence so existing behavior is unchanged
  until configured.
- Add an Advanced AI settings page that can show AI word-in-sentence analysis
  above lookup popup dictionaries, show AI sentence Chinese analysis in Process
  Text lookup, and let Anki templates render `{sentence-cn}` and
  `{advanced-ai-word}` when configured.
- Add a Reader Translation (AI) panel with profile-scoped long-press mode,
  in-reader sentence translation and sentence analysis cards, and visible-page
  paragraph translation that fills in directly under the source text.
  Full-page translation adds a display mode: always visible keeps translations
  under each paragraph, while on long press keeps them hidden until the original
  paragraph is long-pressed. Translations are prepared in the background while
  full-page translation is on, so a long press reveals an existing translation
  instead of starting a request. Long-pressing a translation re-translates that
  paragraph.
- Add an optional “Hide thumbnails when collapsed” switch in Manage Shelves.
  It defaults off; when enabled, collapsed shelves show only their title row.

- Add global Theme settings in Settings and Reader. Follow system
  brightness with one palette selected in each light/dark group, or choose one
  of the same six options manually. Custom Light and Custom Dark retain separate
  reading colors and explicitly select light/dark interfaces and dictionary
  popups. Customize background, text and secondary text colors with previews.
  Choose system, preset or custom interface accents, and apply E-ink optimization
  across the app. v1.3.3 display settings migrate from the active profile; missing
  or unreadable profile settings fall back to legacy global preferences or defaults.
  E-ink optimization sits below automatic switching and hides palette/accent
  choices while enabled; follow system brightness or choose light/dark
  independently of saved colors.
  Color changes preserve the visible controls and scroll position while saving.
  The Reader Theme panel's title scrolls with its settings.
- Preserve deleted books' reading statistics in an archive, restore them on
  reimport, and edit daily records from book distribution rows. Single-day
  deletion is available inside the day editor; deleting all records requires
  confirmation. Daily records use compact grouped rows with dates and character
  counts on the left, durations and navigation arrows on the right. Archived
  books have a small trash marker in the book list. Local and archived book
  headers use the dashboard title style, avoiding a font-size jump while loading.
  E-ink mode outlines the daily-record group and its delete-all button.
- Add natural calendar periods and all-time Statistics overview, with compact
  period controls in the Reading Time card and the current week selected by default.
  Tap chart bars to inspect a day within a week/month or a month within a
  year/all-time period. Include elapsed-period averages and comparisons,
  plus historical goal streaks and best-day summaries.
- Support Anki tag handlebars such as `{document-title}` and `{expression}`,
  joining whitespace inside substituted values with underscores; new card
  formats default to the `hoshi` tag while saved tags stay unchanged.
- Long-press a lookup result's audio button to choose among named local and
  remote audio candidates; the selected recording is also used for Anki cards.
- Show source text above Dictionary and externally shared lookup results; tap a
  character to look up from that position while keeping the full sentence for
  Anki cards. Back/Forward restores the selected occurrence for mining, and
  source text size is adjustable from 12 to 48.
- Add Off, Dimmed, Toggle, and Hidden furigana modes in Reading Settings;
  Toggle reveals whitespace-adjacent ruby annotations together on the first tap.

- Add Show, Blur, and Hide privacy modes for bookshelf covers, plus deterministic
  title and author artwork when a book has no visible cover.
- Warn users before enabling AnkiConnect that most setups can create cards
  directly through AnkiDroid and that an incorrect AnkiConnect configuration
  prevents fetching decks and note types or creating cards.
- Add term dictionary categories with category-aware Anki definition mappings
  and iOS-aligned advanced category/fallback controls, Kanji dictionary
  import/management and popup lookup, a verified one-tap
  stroke-order font download for Kanji users, plus complete pitch data with H/L
  patterns and nasal/devoice markers.
- Add downloadable recommended Japanese font families to Reading Settings,
  including separate family and named variant selectors, real static and
  variable weight selection, verified app-private downloads, and family/variant
  grouping for imported TTF and OTF fonts, with compact one-level type grouping
  in the font menu.
- Add an optional Reader Behavior setting that uses the volume keys to jump
  between terms in the topmost lookup popup.
- Add `hoshi://search?text=...` deep links for opening lookup results in the
  existing popup overlay, with `mode=app` support for opening the Dictionary
  tab instead.

### Changed

- Apply the selected Recent/Title sort to Google Drive books. Recent uses the
  latest reading or audiobook progress time, falling back to bookdata last access.

- Separate global display settings from profile Reading Settings, which now show
  the edited profile name. Opening books or switching profiles keeps the same
  colors and E-ink setting.
- Unify page, grouped card, nested control and popup colors across tabs, settings
  and native Reader panels. Ordinary groups use tonal backgrounds, while E-ink
  uses visible outlines and selection shapes, including continuous lazy lists.
  Bottom and side navigation use a subtle theme tint, and Dictionary search
  shares the page background through the status bar for a continuous top area.
  Native backgrounds use a softer, more neutral tint and lighter separators,
  while buttons and active states retain the selected accent color.
  Theme uses the same inset row dividers as other settings pages.
  Reading Settings uses inset rounded selections inside continuous neutral
  segment tracks, keeping the current choice clear without vertical separators.
- Keep Statistics always available and move its settings to the Stats tab's
  upper-right corner. Statistics sync defaults on only when unset; saved daily goals,
  sync opt-outs and Reader display preferences are preserved. Book Open and
  Page Turn autostart remain independent and default off. Group settings into
  autostart, reset time, Sync and Archive sections, with explanatory footers
  and the archived-book count below the clear action.
- Simplify Statistics to daily goals and reading-time results. Include a
  display-only reading-intensity heatmap with fully visible, row-aligned weekday
  labels in the daily card and a compact
  goal popup with a scrollable value picker. Show reading-time
  bars with calendar-aligned dashed grid lines, summary rows labeled Characters
  Read and Reading Speed, and time-ranked books with per-book character counts
  and reading times together instead of three tabs,
  with consistent section headings, grouped cards and compact charts and rows.
  Identify daily averages in Week/Month and monthly averages in Year/All.
  Show complete period comparisons with larger text and direction arrows
  alongside rounded percentages; keep Show More left-aligned with
  a divider above it. In E-ink mode, chart bars are all filled until a single
  bar is selected; then only that bar stays filled and the others become hollow.
  Clearing selection restores all filled bars. Period and goal-type selectors use
  outlined tracks with filled, inverse-text selections, and the goal value wheel
  outlines its center selection. Remove the separate This Week
  card and weekly goal; weekly totals and trends remain available through the
  reading-time chart. Keep dashboard cards in memory while scrolling to avoid
  rebuilding the heatmap and charts during fast vertical swipes.
- Make Reader progress calculation during continuous scrolling much cheaper.
  It now binary-searches text nodes instead of measuring every node on each
  scroll, removing the repeated forced layout that caused stutter on long
  chapters. Page-turn and Sasayaki cue progress use the same faster path.

### Fixed

- Count Korean text consistently in Reader progress, book search, and new
  Sasayaki subtitle matches, and exclude ruby fallback text from native counts
  and search. Existing books refresh their cached counts when reopened.
- Ask for confirmation before resetting Dictionary custom CSS to prevent
  accidental clearing.
- Keep text and images reachable in every Reader mode when publisher CSS wraps
  paragraphs or empty layout struts in oversized inline blocks.
- Restore book files and covers from iOS Books backups when equivalent Unicode
  paths use different composed forms.
- Match Sasayaki subtitles immediately after selecting an SRT, including unique
  cues immediately before the stable starting sequence, combined-volume EPUBs,
  and large text gaps, without requiring Search Window tuning or a separate
  Match action.
- Import EPUB and TTU bookdata with multibyte titles that exceed Android's
  filename byte limit while preserving the complete visible title and cleaning
  temporary EPUB data after failed imports.
- Keep wide inline gaiji at the publisher's text-relative size, recognize any
  publisher class containing `gaiji`, and render gaiji plus transparent
  monochrome images embedded in text with the active Reader text color while
  blending away their image backgrounds in standard and custom themes and
  excluding gaiji from image navigation.
- Use EPUB fallback text for failed inline gaiji images, while retaining a
  broken-image marker and its inline space when no fallback text is available.
- Open EPUB pages that use paired XHTML viewport metadata instead of remaining
  on the Reader loading screen.
- Keep oversized lookup popup frames fully inside the visible screen so their
  bottom border and all scrollable content remain reachable.
- Keep the Dictionary type selector's `Frequency` label on one line on compact
  screens.
- Prefer Arial throughout lookup popups before Android's Japanese font fallback
  so pitch-accent markers stay aligned with their reading, and keep pitch
  dictionary labels intact when compact entries wrap.

## [v1.3.3] - 2026-08-13

### Added

- Automatically center the current chapter when opening the Reader Contents or
  Sasayaki chapter list.
- Add per-source enable controls for imported local audio databases while
  preserving each source's configured priority.
- Add Ogg Opus audiobook import with embedded title, artist, cover, and chapter
  metadata in Sasayaki, load its artist without the platform-reader delay, and
  show MP3, M4B, and Opus audiobook duration before playback starts.
- Add a Reader Appearance swipe-threshold control for paginated and VN modes;
  setting it to zero disables swipe page turns while preserving hardware page
  keys.
- Add up to three named Anki card formats with independent icons, decks, note
  types, field mappings, tags, and duplicate states.
- Add confirmation before deleting Anki formats and an edit-screen action that
  duplicates a format and returns to the format list.
- Add Anki duplicate-note search buttons that appear only for matching notes,
  plus grouped per-format mining and search actions placed before audio.
- Add precise cloze-part handlebars, numeric pitch accent graph handlebars, and
  advanced glossary mapping options for Anki cards.

### Changed

- Increase the default lookup popup size to 500 × 500 dp and allow its
  height to be adjusted up to 1000 dp.
- Align the built-in Lapis, Kiku, and Senren field presets with iOS by no longer
  setting sentence-card marker fields.

### Fixed

- Allow importing supported files whose display names contain `#` or `?`,
  including EPUB volume numbers such as `Book #01.epub`.
- Base reduced-motion popup scrolling on the portion of the popup that is
  actually visible when its configured height exceeds the screen.
- Show Reader Contents, fragment jumps, and chapter labels for EPUBs whose
  navigation document is stored in a subdirectory.
- Prevent staggered multi-touch taps in paginated and VN Reader modes from
  being mistaken for a page-turn swipe.
- Keep Google Drive sync and TTU backup restore compatible with progress files
  whose book data IDs exceed Android's 32-bit integer range.
- Keep dictionary lookup available when tapping Sasayaki-highlighted text in VN
  Reader mode.

## [v1.3.2] - 2026-08-04

### Added

- Add the option to create a new shelf while moving one or more selected books.
- Add a Reader image gallery, true table-of-contents chapter ranges, and optional
  current-chapter progress in Reader chrome and statistics.
- Add a configurable daily statistics reset time and pause reading statistics
  while Reader sheets or fullscreen images are open.
- Add optional current-book cover publishing for the Android lock screen and a
  fixed PNG file used by compatible E-ink sleep-screen tools, plus direct
  integration with iReader’s built-in Book Cover screen saver on compatible
  domestic and Musnap overseas firmware using standard PNG output, with Fit,
  Fill, and Stretch scaling modes.

### Changed

- Expand Sasayaki delay adjustment to -4...4 seconds and playback speed to
  0.5...3x.
- Rename the Reader Go to panel to Contents, order its tabs as Chapters,
  Highlights, Gallery, and Search, and remove overscroll deformation from
  scrolling surfaces throughout the app.

### Fixed

- Keep Reader progress, search, and Sasayaki character offsets stable around
  numeric HTML entities, and keep lookup sentence expansion and recursive
  expression-tag scanning within the selected text boundary.
- Keep manual bookshelf sync from rebuilding the entire shelf, while refreshing
  imported reading progress in place.
- Keep large bookshelves smooth during repeated scrolling by reusing
  size-appropriate persistent cover thumbnails instead of decoding original
  covers again after they leave memory, while recovering from transient
  generation failures or damaged thumbnail-cache entries without hiding valid
  covers.
- Remember the selected Contents and Sasayaki tabs for the current Reader
  session, and keep Sasayaki on the current tab after importing an audiobook.
- Keep VN lookups and mined Anki sentences complete when a word or sentence
  continues onto a later screen.
- Keep Anki audio, book covers, Sasayaki clips, and dictionary media from
  overwriting different exported media by using content-specific filenames.
- Keep dictionary definitions in the configured dictionary order when an
  inflected lookup merges multiple deinflection candidates.
- Keep Sasayaki jumps to cues in the previous chapter from counting the target
  chapter in the current reading session when image holding is enabled.

## [v1.3.1] - 2026-07-11

### Added

- Add a Reader Appearance setting for top safe area height.

### Changed

- Let Advanced AI prompt fields use the exact text saved in Settings, and refresh
  the default word-analysis prompt so `Word_Analyze` explains the selected
  word's meaning instead of forcing the old three-line template.
- Improve dictionary lookup and import behavior by honoring Yomitan term scores
  and normalizing Japanese iteration marks, full-width numbers, and emphatic
  sequences.
- Raise Statistics daily goal limits to 200,000 characters and 12 hours.

### Fixed

- Keep Process Text long-sentence analysis popups showing the Advanced AI card
  even when sentence lookup returns no dictionary entries, including taps near
  opening quote boundaries.
- Keep Reader Translation (AI) long-press popups from covering visible
  paragraph translations when there is room to move, and remove the extra
  tinted background from the in-popup AI card.
- Keep Reader full-page Translation (AI) from skipping sentences when a visible
  paragraph contains multiple source sentences.
- Keep the Statistics tab visible after enabling it and switching away from Settings.
- Refresh Statistics by-book covers when changing calendar ranges.
- Keep Reader lookup highlights from expanding to an entire ruby annotation when
  selecting a shorter word inside it.
- Keep VN vertical text from jumping to a new column immediately after a ruby
  annotation.
- Keep long-pressed Reader volume keys paging or seeking Sasayaki instead of
  falling back to system volume changes after the first press.

## [v1.3.0] - 2026-07-01

### Added

- Add a full-library Statistics tab with habit summaries, calendar range browsing, per-book distribution, daily and weekly goals, and an Advanced Statistics visibility switch.

### Changed

- Open the Reader Go to panel on Chapters by default, order its tabs as Chapters, Highlights, and Search, and focus the search field when Search is selected.

### Fixed

- Prefer exact expression-and-reading local audio matches before falling back to reading-only or expression-only entries.
- Read Sasayaki M4B title, author, and cover metadata from MP4 atoms when Android's platform metadata reader returns empty.
- Improve VN reader media screens, first-highlight display, vertical layout, punctuation wrapping, and lookup and Sasayaki highlight alignment.
- Keep VN and continuous vertical reader content aligned to the configured vertical padding instead of the bottom overlap area.
- Prevent reader lookups from crashing on words that begin with supplementary-plane kanji such as 𠮟.
- Keep Sasayaki image hold active while viewing fullscreen Reader images, and avoid repeated holds once the continuous Reader target image is already visible.
