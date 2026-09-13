package moe.antimony.hoshi.features.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * The reader owns the tap vs long-press decision with a single threshold. A tap opens lookup,
 * a deliberate long press owns native sentence/translation selection. Owning the decision (instead
 * of letting the platform long press fire first) is what keeps slow taps reported by sluggish
 * panels (E-ink readers) from being stolen by the system long press, which would otherwise open a
 * selection action mode and shadow the app's tap. The threshold is at least the system
 * touch-and-hold delay plus a floor long enough for the sluggish taps E-ink panels report.
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

internal const val ReaderTapDurationFloorMillis = 1500L

internal const val ReaderTapSlopFloorPx = 12f

abstract class SwipePageTouchListener : View.OnTouchListener {
    private val tracker = ReaderSwipeGestureTracker(
        minDistance = MIN_DISTANCE,
        tapDurationMillis = ::readerTapDurationMillis,
    )
    private val handler = Handler(Looper.getMainLooper())
    private var downX = 0f
    private var downY = 0f
    private var longPressFired = false
    private val longPressRunnable = Runnable {
        longPressFired = true
        onLongPress(downX, downY)
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (shouldIgnoreReaderGesture(event)) {
            Log.d("HoshiGesture", "Swipe IGNORED action=${event.actionMasked} x=${event.x} y=${event.y}")
            cancelLongPress()
            tracker.suppressCurrentGesture()
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                longPressFired = false
                tracker.onDown(event.x, event.y, event.eventTime)
                handler.postDelayed(longPressRunnable, readerTapDurationMillis())
                Log.d("HoshiGesture", "Swipe DOWN x=$downX y=$downY t=${event.eventTime} tapMs=${readerTapDurationMillis()}")
            }
            MotionEvent.ACTION_MOVE -> {
                if (exceedsLongPressSlop(view, event)) cancelLongPress()
                dispatch(tracker.onMove(event.x, event.y, event.eventTime))
            }
            MotionEvent.ACTION_UP -> {
                cancelLongPress()
                if (!longPressFired) {
                    val r = tracker.onUp(event.x, event.y, event.eventTime)
                    Log.d("HoshiGesture", "Swipe UP result=$r dx=${event.x - downX} dy=${event.y - downY} longPressFired=$longPressFired")
                    dispatch(r)
                } else {
                    Log.d("HoshiGesture", "Swipe UP after longPressFired")
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelLongPress()
                tracker.onCancel()
            }
        }
        return false
    }

    private fun exceedsLongPressSlop(view: View, event: MotionEvent): Boolean {
        val slop = readerTapSlopPx(view.context)
        return abs(event.x - downX) >= slop || abs(event.y - downY) >= slop
    }

    private fun cancelLongPress() {
        handler.removeCallbacks(longPressRunnable)
    }

    open fun onLeftSwipe() = Unit
    open fun onRightSwipe() = Unit
    open fun onTap(x: Float, y: Float) = Unit
    open fun onLongPress(x: Float, y: Float) = Unit
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
