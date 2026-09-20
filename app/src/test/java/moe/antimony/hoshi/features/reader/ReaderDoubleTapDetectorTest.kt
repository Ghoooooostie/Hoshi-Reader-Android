package moe.antimony.hoshi.features.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderDoubleTapDetectorTest {
    @Test
    fun `single tap is not a double tap`() {
        val detector = ReaderDoubleTapDetector()
        assertFalse(detector.registerTap(10f, 10f, 1000L))
    }

    @Test
    fun `two close taps within timeout form a double tap`() {
        val detector = ReaderDoubleTapDetector()
        assertFalse(detector.registerTap(10f, 10f, 1000L))
        assertTrue(detector.registerTap(10f, 10f, 1100L))
    }

    @Test
    fun `second double tap after reset is fresh`() {
        val detector = ReaderDoubleTapDetector()
        assertFalse(detector.registerTap(10f, 10f, 1000L))
        assertTrue(detector.registerTap(10f, 10f, 1100L))
        // After a double tap the detector resets, so the next pair is independent.
        assertFalse(detector.registerTap(10f, 10f, 2000L))
        assertTrue(detector.registerTap(10f, 10f, 2100L))
    }

    @Test
    fun `tap after timeout is not a double tap`() {
        val detector = ReaderDoubleTapDetector(timeoutMillis = 300L)
        assertFalse(detector.registerTap(10f, 10f, 1000L))
        assertFalse(detector.registerTap(10f, 10f, 1400L))
    }

    @Test
    fun `tap too far away is not a double tap`() {
        val detector = ReaderDoubleTapDetector(slopPx = 24f)
        assertFalse(detector.registerTap(10f, 10f, 1000L))
        assertFalse(detector.registerTap(400f, 400f, 1100L))
    }

    @Test
    fun `reset clears pending tap state`() {
        val detector = ReaderDoubleTapDetector()
        assertFalse(detector.registerTap(10f, 10f, 1000L))
        detector.reset()
        assertFalse(detector.registerTap(10f, 10f, 1100L))
    }

    @Test
    fun `finite slop boundaries are inclusive`() {
        val detector = ReaderDoubleTapDetector(slopPx = 24f, timeoutMillis = 300L)
        assertFalse(detector.registerTap(10f, 10f, 1000L))
        assertTrue(detector.registerTap(34f, 10f, 1100L))
    }
}
