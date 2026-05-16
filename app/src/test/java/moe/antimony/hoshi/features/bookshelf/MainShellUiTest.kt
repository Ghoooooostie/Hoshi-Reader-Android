package moe.antimony.hoshi.features.bookshelf

import kotlinx.coroutines.runBlocking
import moe.antimony.hoshi.epub.BookMetadata
import moe.antimony.hoshi.epub.BookEntry
import moe.antimony.hoshi.epub.BookInfo
import moe.antimony.hoshi.epub.BookRepository
import moe.antimony.hoshi.epub.Bookmark
import moe.antimony.hoshi.epub.BookShelf
import moe.antimony.hoshi.epub.BookSortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MainShellUiTest {
    @Test
    fun mainTabsMatchIosOrder() {
        assertEquals(listOf("Books", "Dictionary", "Settings"), MainTab.entries.map { it.label })
    }

    @Test
    fun settingsGroupsIncludeAndroidReaderBehaviorEntry() {
        val groups = settingsGroups()

        assertEquals(
            listOf("Dictionaries", "Anki", "Appearance", "Behavior", "Advanced"),
            groups.first().map { it.label },
        )
        assertEquals(listOf("Report an Issue", "Diagnostics", "About"), groups.last().map { it.label })
    }

    @Test
    fun importedBooksAreDisplayedInUnshelvedSection() {
        val entries = listOf(
            BookEntry(
                root = File("book-a"),
                metadata = BookMetadata(
                    id = "a",
                    title = "屍人荘の殺人",
                    cover = null,
                    folder = "book-a",
                    lastAccess = 1.0,
                ),
            ),
        )

        val sections = bookshelfSections(entries)

        assertEquals(1, sections.size)
        assertEquals("Unshelved", sections.single().title)
        assertEquals(entries, sections.single().books)
    }

    @Test
    fun bookshelfSectionsMatchIosShelvesReadingAndUnshelvedOrder() {
        val unread = bookEntry(id = "unread", title = "Unread", lastAccess = 3.0)
        val reading = bookEntry(id = "reading", title = "Reading", lastAccess = 2.0)
        val shelved = bookEntry(id = "shelved", title = "Shelved", lastAccess = 1.0)
        val entries = listOf(unread, reading, shelved)

        val sections = bookshelfSections(
            entries = entries,
            shelves = listOf(
                BookShelf(name = "Manga", bookIds = listOf("shelved", "missing")),
            ),
            progressById = mapOf(
                "unread" to 0.0,
                "reading" to 0.5,
                "shelved" to 1.0,
            ),
            showReading = true,
            sortOption = BookSortOption.Recent,
        )

        assertEquals(listOf("Reading", "Manga", "Unshelved"), sections.map { it.title })
        assertEquals(true, sections[0].isReading)
        assertTrue(sections[0].isCollapsible)
        assertEquals(listOf("reading"), sections[0].books.map { it.metadata.id })
        assertEquals(listOf("shelved"), sections[1].books.map { it.metadata.id })
        assertEquals(listOf("unread", "reading"), sections[2].books.map { it.metadata.id })
    }

    @Test
    fun bookshelfSectionsSortEachSectionByTitleWhenRequested() {
        val z = bookEntry(id = "z", title = "Zeta", lastAccess = 2.0)
        val a = bookEntry(id = "a", title = "Alpha", lastAccess = 1.0)

        val sections = bookshelfSections(
            entries = listOf(z, a),
            shelves = emptyList(),
            progressById = emptyMap(),
            showReading = false,
            sortOption = BookSortOption.Title,
        )

        assertEquals(listOf("a", "z"), sections.single().books.map { it.metadata.id })
    }

    @Test
    fun bookshelfSectionsSortEachSectionByRenamedDisplayTitleWhenRequested() {
        val originalA = bookEntry(id = "original-a", title = "Alpha", lastAccess = 1.0)
        val renamedToZ = bookEntry(id = "renamed", title = "Beta", lastAccess = 2.0, renamedTitle = "Zeta")

        val sections = bookshelfSections(
            entries = listOf(renamedToZ, originalA),
            shelves = emptyList(),
            progressById = emptyMap(),
            showReading = false,
            sortOption = BookSortOption.Title,
        )

        assertEquals(listOf("original-a", "renamed"), sections.single().books.map { it.metadata.id })
    }

    @Test
    fun compactWindowsUseBottomNavigationAndTwoBookColumns() {
        val spec = MainShellLayoutSpec.forWidthDp(360)

        assertEquals(MainShellNavigationLayout.BottomBar, spec.navigationLayout)
        assertEquals(64, spec.compactNavigationHeightDp)
        assertEquals(16, spec.pageHorizontalPaddingDp)
        assertEquals(2, spec.bookGridColumns(contentWidthDp = 360))
        assertEquals(4, spec.collapsedShelfPreviewColumns(contentWidthDp = 360))
        assertTrue(spec.collapsedShelfPreviewCoverWidthDp(contentWidthDp = 360) > 64)
        assertTrue(spec.collapsedShelfPreviewColumns(contentWidthDp = 360) > spec.bookGridColumns(contentWidthDp = 360))
    }

    @Test
    fun landscapeWindowsShowMoreCollapsedShelfPreviews() {
        val spec = MainShellLayoutSpec.forWidthDp(800)
        val contentWidth = spec.constrainedContentWidthDp(800)

        assertEquals(MainShellNavigationLayout.NavigationRail, spec.navigationLayout)
        assertTrue(spec.collapsedShelfPreviewColumns(contentWidth) > 4)
        assertTrue(spec.collapsedShelfPreviewCoverWidthDp(contentWidth) >= CollapsedShelfCoverTargetWidthDp)
    }

    @Test
    fun bookshelfHeaderUsesCompactMaterialTypography() {
        val spec = MainShellLayoutSpec.forWidthDp(360)

        assertEquals(MainShellTextStyle.TitleLarge, spec.shelfTitleTextStyle)
        assertEquals(MainShellFontWeight.SemiBold, spec.shelfTitleFontWeight)
        assertEquals(MainShellTextStyle.TitleMedium, spec.shelfCountTextStyle)
        assertEquals(0, spec.shelfHeaderVerticalPaddingDp)
        assertEquals(0, spec.bookGridTopPaddingDp)
        assertEquals(12, spec.bookGridVerticalSpacingDp)
        assertEquals(0, spec.bookGridBottomPaddingDp)
        assertEquals(MainShellTextStyle.BodyLarge, spec.bookTitleTextStyle)
        assertEquals(MainShellFontWeight.Normal, spec.bookTitleFontWeight)
    }

    @Test
    fun mediumWindowsUseNavigationRailAndConstrainedBookGrid() {
        val spec = MainShellLayoutSpec.forWidthDp(700)

        assertEquals(MainShellNavigationLayout.NavigationRail, spec.navigationLayout)
        assertEquals(640, spec.contentMaxWidthDp)
        assertEquals(3, spec.bookGridColumns(contentWidthDp = 640))
    }

    @Test
    fun expandedWindowsUseNavigationRailAndCapBookGridColumns() {
        val spec = MainShellLayoutSpec.forWidthDp(1200)

        assertEquals(MainShellNavigationLayout.NavigationRail, spec.navigationLayout)
        assertEquals(1040, spec.contentMaxWidthDp)
        assertEquals(5, spec.bookGridColumns(contentWidthDp = 1040))
    }

    @Test
    fun coverDecodeSampleSizeKeepsCoversNearTargetSize() {
        assertEquals(1, coverDecodeSampleSize(width = 600, height = 800, maxDimensionPx = 900))
        assertEquals(2, coverDecodeSampleSize(width = 1200, height = 1800, maxDimensionPx = 900))
        assertEquals(4, coverDecodeSampleSize(width = 2400, height = 3600, maxDimensionPx = 900))
    }

    @Test
    fun bookProgressIsLoadedOnceForShelfEntries() = runBlocking {
        val filesDir = Files.createTempDirectory("hoshi-bookshelf-progress").toFile()
        try {
            val repository = BookRepository(filesDir)
            val root = File(filesDir, "Books/book-a").also { it.mkdirs() }
            repository.saveBookInfo(root, BookInfo(characterCount = 200, chapterInfo = emptyMap()))
            repository.saveBookmark(
                root,
                Bookmark(
                    chapterIndex = 0,
                    progress = 0.0,
                    characterCount = 50,
                ),
            )
            val entry = BookEntry(
                root = root,
                metadata = BookMetadata(
                    id = "a",
                    title = "Book A",
                    cover = null,
                    folder = "book-a",
                    lastAccess = 1.0,
                ),
            )

            assertEquals(0.25, loadBookProgressById(listOf(entry), repository).getValue("a"), 0.0001)
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun bookshelfProgressShowsCompletedBooksAsRead() {
        assertFalse(isBookCompleted(progress = 0.998))
        assertTrue(isBookCompleted(progress = 0.999))
        assertTrue(isBookCompleted(progress = 1.0))
        assertEquals("99.8%", bookshelfProgressText(progress = 0.998))
        assertEquals("100.0%", bookshelfProgressText(progress = 0.999))
        assertEquals("100.0%", bookshelfProgressText(progress = 1.0))
    }

    private fun bookEntry(
        id: String,
        title: String,
        lastAccess: Double,
        renamedTitle: String? = null,
    ): BookEntry =
        BookEntry(
            root = File(id),
            metadata = BookMetadata(
                id = id,
                title = title,
                renamedTitle = renamedTitle,
                cover = null,
                folder = id,
                lastAccess = lastAccess,
            ),
        )
}
