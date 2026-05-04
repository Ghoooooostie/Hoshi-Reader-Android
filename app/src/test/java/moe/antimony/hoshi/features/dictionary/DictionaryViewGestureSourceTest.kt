package moe.antimony.hoshi.features.dictionary

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryViewGestureSourceTest {
    @Test
    fun reorderHandleDoesNotRequireLongPressBeforeDragging() {
        val source = File("src/main/java/moe/antimony/hoshi/features/dictionary/DictionaryView.kt").readText()

        assertTrue(source.contains("rememberDraggableState"))
        assertTrue(source.contains(".draggable("))
        assertTrue(source.contains("startDragImmediately = true"))
        assertFalse(source.contains("detectDragGesturesAfterLongPress("))
    }

    @Test
    fun revealedDeleteButtonUsesTrashIconOnly() {
        val source = File("src/main/java/moe/antimony/hoshi/features/dictionary/DictionaryView.kt").readText()

        assertTrue(source.contains("Icons.Rounded.Delete"))
        assertTrue(source.contains("contentDescription = \"Delete Dictionary\""))
        assertFalse(source.contains("text = \"Delete\""))
    }

    @Test
    fun dictionaryRowKeepsTitleAndRevisionSingleLine() {
        val source = File("src/main/java/moe/antimony/hoshi/features/dictionary/DictionaryView.kt").readText()

        val singleLineTextCount = Regex("maxLines = 1").findAll(source).count()
        assertTrue(singleLineTextCount >= 2)
    }

    @Test
    fun reorderUpdatesWorkingOrderBeforePersistingFinalMove() {
        val source = File("src/main/java/moe/antimony/hoshi/features/dictionary/DictionaryView.kt").readText()

        assertTrue(source.contains("dragWorkingDictionaries = DictionaryDragReorder.previewOrder"))
        assertTrue(source.indexOf("Animatable(releasedOffset).animateTo(0f") < source.indexOf("dictionaryViewModel.moveDictionary"))
        assertFalse(source.contains("settlingFileName"))
    }
}
