# Changelog

All notable user-visible changes to Hoshi Reader Android are documented here.
The format follows a Keep a Changelog style, and release sections use Semantic Versioning.
Historical release notes before v1.3.0 live in [CHANGELOG_ARCHIVE.md](CHANGELOG_ARCHIVE.md).

## [Unreleased]

### Added

- Add selectable translation sources for Reader translation (full page,
  sentence gesture, and read-aloud follow translation). Besides the Advanced AI
  backend, key-free web sources are now available: Microsoft, Bing, Google,
  ModernMt, QQ TransSmart, QQ IMT, Alibaba, Youdao, Caiyun, Translate.com,
  Yandex, Volcengine, and Papago. The source can be switched from the reader
  translation sheet and needs no API key.
- Enable full-page Reader translation in Visual Novel mode. VN screens now show
  paragraph translations like paginated and continuous modes; translations are keyed
  by source structure (`vn-<raw>-<depth>-<tag>`) so they stay correct across screen
  changes instead of leaking between screens.
- Add a translation background opacity setting to the reader translation style
  section. The grey highlight behind revealed translations can now be made more
  transparent or hidden entirely (0%).
- Lower the translation opacity range to 0-100% (was 30-100%). 0% hides the
  translation text entirely.

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
- Fix a long stall when dismissing the lookup popup by tapping a blank area while
  Read Aloud is playing: the tap used to run a JS word-selection probe that was
  queued behind the reader WebView's highlight/scroll work, leaving the popup
  frozen for seconds. A blank-area tap now closes an already-open popup directly
  without the probe.
- Fix taps on blank-looking areas next to the lookup popup not closing it: when
  the popup was shifted to avoid covering AI translation text, or shrunk to its
  content height, the tap blocker still used the pre-layout popup rectangle, so
  taps in the mismatched band were swallowed. The popup now reports its on-screen
  frame back to the reader and taps outside the visible popup dismiss it again.
- Fix Anki mining being slow and sometimes silently doing nothing. Mining no longer
  waits for every configured audio source before adding a card: it takes the first
  audio any source offers, and the audio candidate menu now queries all sources at
  once, so an unreachable audio source no longer adds its timeout to every card.
  A mine that fails, or a duplicate check that returns no answer, also no longer
  leaves the mine button permanently disabled: it shows the error and becomes
  tappable again.
- Fix the lookup popup showing the conjugated form (被动, 使役, た形 …) in English:
  the Chinese labels for those deinflection tags were never wired into the popup,
  so Chinese users saw the raw engine names such as `passive` and `causative`.
- Fix accidental dictionary lookups when tapping translation text: taps on reader
  translations (including read-aloud follow translations) no longer trigger word
  selection, so the translation area is no longer clickable for lookups.

### Changed

- The long-press lookup popup's advanced AI analysis card now defaults to
  collapsed; tap its header to expand it. This keeps the popup focused on the
  dictionary result on first open.
- Read Aloud no longer offers the downloadable local model: it speaks through the
  system text-to-speech engine only, and the Read Aloud settings now go straight to
  the system TTS voice and speech rate.

## [v1.4.0] - 2026-09-24

### Added

- Transcribe Japanese audiobooks on-device in Sasayaki. Download the model only when
  needed; pause/resume, continue with the panel closed or after switching apps, and
  save progress when leaving Reader. Reuse or clear transcripts, open the matching
  transcription tab, and choose Lightweight, Balanced (default), or Fast. Match speech
  to book text while reading without interrupting playback, with better recovery of
  short replies and recognition errors. Support M4A, show match coverage, import
  multiline subtitles, export partial or complete matches as SRT, and show localized
  error messages.
- Set lookup frequency sorting per profile: Auto, Ascending, Descending, or Disabled.
  Explicit ordering requires an enabled frequency dictionary.
- Preserve furigana in Reader highlights and Contents. Select an exact highlighted range
  to change its color or remove it in paginated, continuous, and VN modes.
- Optionally hide thumbnails in collapsed shelves; this setting is off by default.
- Add global Theme settings with system or manual light/dark palettes, separate custom
  light and dark reading colors, interface accents, and app-wide E-ink optimization.
- Archive deleted-book reading statistics, restore them on reimport, and edit daily
  records. Deleting all records requires confirmation.
- Add calendar and all-time Statistics views with period comparisons, averages, goal
  history, and best-day summaries.
- Support Anki tag handlebars such as `{document-title}` and `{expression}`; whitespace
  in substituted values becomes underscores. New formats use the `hoshi` tag by default.
- Long-press a lookup result's audio button to choose among named local or remote
  recordings; use the selected recording for Anki cards too.
- Show source text above Dictionary and shared lookup results. Tap a character to look it
  up while retaining the full sentence for Anki. Back/Forward restores the selected
  occurrence, and source text size is adjustable from 12–48.
- Add Off, Dimmed, Toggle, and Hidden furigana modes. Toggle reveals adjacent ruby
  annotations together on the first tap.
- Add Show, Blur, and Hide bookshelf-cover privacy modes, with title and author artwork
  when a cover is hidden or unavailable.
- Warn that AnkiDroid can create cards directly in most setups and that a misconfigured
  AnkiConnect can prevent loading decks and note types or creating cards.
- Add term dictionary categories and category-aware Anki mappings, Kanji dictionary
  import and popup lookup, stroke-order font download, and complete pitch accents with
  H/L patterns and nasal/devoice markers.
- Add downloadable Japanese fonts with family, variant, and weight choices.
- Use volume keys to move between terms in the topmost lookup popup.
- Add `hoshi://search?text=...` links to open lookup results in a popup; use `mode=app`
  to open the Dictionary tab.
- Fix transcription setup for some playable M4B and Ogg Opus audiobooks.

### Changed

- Translate the Anki selected-glossary fallback label into Simplified Chinese.
- Use Sasayaki accent colors to highlight the current chapter in Reader Contents outside
  E-ink mode.
- Clearing Dictionary search by pull-down or clear button now focuses the field and
  opens the keyboard while preserving results and navigation history.
- Set JapanesePod101, LanguagePod101, and Jisho as the default word-audio sources.
  Sources can be enabled and reordered independently; custom sources remain unchanged.
  Play the first match immediately and show recordings as each source loads.
- Always export dictionary images to Anki as media files; remove the “Embed media” switch.
- Adjust Reader line spacing to match iOS more closely.
- Use low-memory imports for automatic dictionary updates; manual imports follow the
  Low Memory Usage Mode setting.
- Make book search treat spaces and punctuation literally, show full sentence context,
  and return up to 100 results. Mark a selected result in blue until page navigation.
- Include surrounding Japanese quotation marks and punctuation in Sasayaki highlights
  across all Reader modes.
- Apply Recent/Title sorting to Google Drive books. Recent uses reading or audiobook
  progress time, then bookdata last access.
- Keep display settings global across profiles and show the edited profile name in
  Appearance. Opening books or switching profiles no longer changes colors or E-ink mode.
- Use theme colors consistently across navigation, cards, settings, and Reader panels;
  make selections clearer in E-ink mode.
- Keep Statistics available and move its settings to the Stats tab. Preserve saved goals
  and sync preferences; keep Book Open and Page Turn autostart independent and off by
  default. Group settings into Autostart, Reset Time, Sync, and Archive.
- Simplify Statistics around daily goals and reading time, with a heatmap, period charts,
  comparisons, averages, and time-ranked books. Weekly totals and trends remain in the chart.

### Fixed

- Preserve Dictionary results and Back/Forward history when switching tabs, including
  after following a definition link and swiping back.
- Recognize existing AnkiDroid cards with equivalent Unicode spellings to prevent
  duplicate cards.
- Open matching AnkiDroid cards regardless of its previously selected deck, while
  honoring the duplicate-search setting.
- Report failed dictionary imports by filename and reason, then continue the batch.
- Show a localized Reader message and Close action for missing or unreadable books.
- Recover Reader after a crash while preserving reading position, highlights, and
  Sasayaki cues.
- Avoid transient errors during automatic Google Drive refresh and keep cached books
  visible. Manual refresh still reports failures.
- Count Korean consistently in reading progress, search, and Sasayaki matches; ignore
  furigana fallback text in native counts and search.
- Confirm before resetting Dictionary custom CSS.
- Keep text and images visible in books with unusual publisher formatting.
- Restore book files and covers from iOS backups despite filename variations.
- Match Sasayaki subtitles immediately after selecting an SRT, including around chapter
  boundaries and large text gaps, without search tuning or a separate action.
- Import EPUB and TTU books with long, non-Latin titles without truncation.
- Render inline Japanese character images at the right size and color, and exclude them
  from image navigation.
- Show EPUB fallback text when an inline character image fails to load.
- Open EPUBs that previously remained on the Reader loading screen.
- Keep large lookup popups on-screen so their borders and scrollable content remain
  reachable.
- Keep the Dictionary `Frequency` label on one line on compact screens.
- Keep pitch accents aligned and dictionary labels readable when lookup entries wrap.

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
