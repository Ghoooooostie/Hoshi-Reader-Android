package moe.antimony.hoshi.features.reader

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * A reader tap opens lookup while the platform long press owns native selection and the
 * sentence/translation actions, so the tap window has to end where the system touch-and-hold
 * delay begins. Slow and E-ink devices raise that delay and report longer presses, and a fixed
 * window drops those taps: they are too long for a tap and too short for the long press.
 */
internal fun readerTapDurationMillis(): Long =
    maxOf(ReaderTapDurationFloorMillis, ViewConfiguration.getLongPressTimeout().toLong())

/**
 * Movement tolerance of a reader tap, taken from the platform so that devices with a noisier
 * touch panel (E-ink readers) or a different density keep Android's own tap tolerance instead
 * of a hardcoded pixel value.
 */
internal fun readerTapSlopPx(context: Context): Float =
    maxOf(ReaderTapSlopFloorPx, ViewConfiguration.get(context).scaledTouchSlop.toFloat())

internal const val ReaderTapDurationFloorMillis = 500L

internal const val ReaderTapSlopFloorPx = 12f

abstract class SwipePageTouchListener : View.OnTouchListener {
    private val tracker = ReaderSwipeGestureTracker(
        minDistance = MIN_DISTANCE,
        tapDurationMillis = ::readerTapDurationMillis,
    )

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (shouldIgnoreReaderGesture(event)) {
            tracker.suppressCurrentGesture()
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> tracker.onDown(event.x, event.y, event.eventTime)
            MotionEvent.ACTION_MOVE -> dispatch(tracker.onMove(event.x, event.y, event.eventTime))
            MotionEvent.ACTION_UP -> dispatch(tracker.onUp(event.x, event.y, event.eventTime))
            MotionEvent.ACTION_CANCEL -> tracker.onCancel()
        }
        return false
    }

    open fun onLeftSwipe() = Unit
    open fun onRightSwipe() = Unit
    open fun onTap(x: Float, y: Float) = Unit
    open fun shouldIgnoreReaderGesture(event: MotionEvent): Boolean = false

    private fun dispatch(result: ReaderSwipeGestureTracker.Result) {
        when (result) {
            ReaderSwipeGestureTracker.Result.LeftSwipe -> onLeftSwipe()
            ReaderSwipeGestureTracker.Result.RightSwipe -> onRightSwipe()
            is ReaderSwipeGestureTracker.Result.Tap -> onTap(result.x, result.y)
            ReaderSwipeGestureTracker.Result.None -> Unit
        }
    }

    private companion object {
        const val MIN_DISTANCE = 72f
    }
}

internal class ReaderSwipeGestureTracker(
    private val minDistance: Float,
    private val tapDurationMillis: () -> Long = { ReaderTapDurationFloorMillis },
) {
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var downTapDurationMillis = ReaderTapDurationFloorMillis
    private var hasDown = false
    private var swipeDispatched = false

    fun onDown(x: Float, y: Float, eventTime: Long) {
        downX = x
        downY = y
        downTime = eventTime
        downTapDurationMillis = tapDurationMillis()
        hasDown = true
        swipeDispatched = false
    }

    fun onMove(x: Float, y: Float, eventTime: Long): Result {
        if (!hasDown || swipeDispatched) return Result.None
        val dx = x - downX
        val dy = y - downY
        if (abs(dx) <= abs(dy)) return Result.None
        val elapsedMs = (eventTime - downTime).coerceAtLeast(1L)
        val velocityX = abs(dx) * 1_000f / elapsedMs
        val hasPageDistance = abs(dx) >= minDistance
        val hasFastFlickDistance = abs(dx) >= MIN_FAST_FLICK_DISTANCE &&
            velocityX >= MIN_FAST_FLICK_VELOCITY_PX_PER_SECOND
        if (
            !hasPageDistance && !hasFastFlickDistance ||
            elapsedMs > MAX_EARLY_SWIPE_DURATION_MS ||
            velocityX < MIN_EARLY_SWIPE_VELOCITY_PX_PER_SECOND
        ) {
            return Result.None
        }
        swipeDispatched = true
        return if (dx < 0f) Result.LeftSwipe else Result.RightSwipe
    }

    fun onUp(x: Float, y: Float, eventTime: Long): Result {
        if (!hasDown) return Result.None
        val dx = x - downX
        val dy = y - downY
        val elapsedMs = eventTime - downTime
        val activeTapDurationMillis = downTapDurationMillis
        val wasSwipeDispatched = swipeDispatched
        onCancel()
        return if (
            !wasSwipeDispatched &&
            elapsedMs <= activeTapDurationMillis &&
            abs(dx) < minDistance &&
            abs(dy) < minDistance
        ) {
            Result.Tap(x, y)
        } else {
            Result.None
        }
    }

    fun onCancel() {
        hasDown = false
        swipeDispatched = false
    }

    fun suppressCurrentGesture() {
        onCancel()
    }

    sealed class Result {
        data object None : Result()
        data object LeftSwipe : Result()
        data object RightSwipe : Result()
        data class Tap(val x: Float, val y: Float) : Result()
    }

    private companion object {
        const val MIN_FAST_FLICK_DISTANCE = 36f
        const val MIN_FAST_FLICK_VELOCITY_PX_PER_SECOND = 900f
        const val MAX_EARLY_SWIPE_DURATION_MS = 300L
        const val MIN_EARLY_SWIPE_VELOCITY_PX_PER_SECOND = 360f
    }
}
