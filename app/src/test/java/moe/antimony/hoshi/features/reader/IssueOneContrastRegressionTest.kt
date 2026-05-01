package moe.antimony.hoshi.features.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IssueOneContrastRegressionTest {
    @Test
    fun mainShellProvidesReadableContentColorOnAppBackground() {
        val source = File("src/main/java/moe/antimony/hoshi/features/bookshelf/BookshelfView.kt").readText()
        val shell = source.substringAfter("internal fun HoshiMainShell(")
            .substringBefore("private val NavigationRailInset")

        assertTrue(shell.contains("NavigationSuiteScaffold("))
        assertTrue(shell.contains("containerColor = MaterialTheme.colorScheme.background"))
        assertTrue(shell.contains("contentColor = MaterialTheme.colorScheme.onBackground"))
    }

    @Test
    fun chapterSheetUsesOpaqueSurfaceSoReaderDoesNotShowThrough() {
        val source = File("src/main/java/moe/antimony/hoshi/features/reader/ReaderChapterSheet.kt").readText()
        val sheet = source.substringAfter("ModalBottomSheet(")
            .substringBefore(") {\n        LazyColumn")

        assertTrue(sheet.contains("containerColor = MaterialTheme.colorScheme.surface"))
        assertTrue(sheet.contains("contentColor = MaterialTheme.colorScheme.onSurface"))
        assertFalse(sheet.contains(".copy(alpha ="))
    }

    @Test
    fun readerHalfSheetsDoNotDimPureReaderBackgrounds() {
        val chapterSource = File("src/main/java/moe/antimony/hoshi/features/reader/ReaderChapterSheet.kt").readText()
        val appearanceSource = File("src/main/java/moe/antimony/hoshi/features/reader/ReaderAppearanceView.kt").readText()

        listOf(chapterSource, appearanceSource).forEach { source ->
            val sheet = source.substringAfter("ModalBottomSheet(")
                .substringBefore(") {")
            assertTrue(sheet.contains("scrimColor = Color.Transparent"))
        }
    }

    @Test
    fun readerHalfSheetsDrawTopOutlineBoundary() {
        val chapterSource = File("src/main/java/moe/antimony/hoshi/features/reader/ReaderChapterSheet.kt").readText()
        val appearanceSource = File("src/main/java/moe/antimony/hoshi/features/reader/ReaderAppearanceView.kt").readText()

        assertTrue(chapterSource.contains("ReaderSheetTopOutline("))
        assertTrue(appearanceSource.contains("ReaderSheetTopOutline("))
    }

    @Test
    fun appearanceSettingsScreenHandlesSystemBackLikeToolbarBack() {
        val source = File("src/main/java/moe/antimony/hoshi/features/reader/ReaderAppearanceView.kt").readText()
        val screen = source.substringAfter("internal fun ReaderAppearanceScreen(")
            .substringBefore("@OptIn(ExperimentalMaterial3Api::class)\n@Composable\ninternal fun ReaderAppearanceSheet(")

        assertTrue(screen.contains("BackHandler(onBack = onClose)"))
    }

    @Test
    fun appearanceSegmentedButtonsKeepMaterialSelectedIndicatorAndContrast() {
        val source = File("src/main/java/moe/antimony/hoshi/features/reader/ReaderAppearanceView.kt").readText()
        val segmentedRow = source.substringAfter("private fun SegmentedRow(")
            .substringBefore("internal fun segmentedControlWidthDp(")

        assertFalse(segmentedRow.contains("icon = {}"))
        assertFalse(segmentedRow.contains("colors ="))
        assertFalse(source.contains("private fun segmentedButtonColors("))
    }
}
