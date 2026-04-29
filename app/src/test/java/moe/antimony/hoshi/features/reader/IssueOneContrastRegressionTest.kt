package moe.antimony.hoshi.features.reader

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IssueOneContrastRegressionTest {
    @Test
    fun mainShellProvidesReadableContentColorOnAppBackground() {
        val source = File("src/main/java/moe/antimony/hoshi/features/bookshelf/BookshelfView.kt").readText()
        val shell = source.substringAfter("private fun HoshiMainShell(")
            .substringBefore("@Composable\nprivate fun BooksTab(")

        assertTrue(shell.contains("Surface("))
        assertTrue(shell.contains("color = MaterialTheme.colorScheme.background"))
        assertTrue(shell.contains("contentColor = MaterialTheme.colorScheme.onBackground"))
    }

    @Test
    fun chapterSheetProvidesReadableContentColorOnScrimmedSurface() {
        val source = File("src/main/java/moe/antimony/hoshi/features/reader/ReaderChapterSheet.kt").readText()
        val sheet = source.substringAfter("ModalBottomSheet(")
            .substringBefore(") {\n        LazyColumn")

        assertTrue(sheet.contains("containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)"))
        assertTrue(sheet.contains("contentColor = MaterialTheme.colorScheme.onSurface"))
    }
}
