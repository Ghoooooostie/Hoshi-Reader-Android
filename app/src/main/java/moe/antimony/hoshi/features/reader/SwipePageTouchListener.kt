package moe.antimony.hoshi.features.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

// 统一使用系统长按阈值，并为墨水屏上报的慢点击保留足够时间。
internal fun readerTapDurationMillis(): Long =
    maxOf(ReaderTapDurationFloorMillis, ViewConfiguration.getLongPressTimeout().toLong())

// 使用系统触摸容差，避免墨水屏触控抖动把按压误判为拖动。
internal fun readerTapSlopPx(context: Context): Float =
    maxOf(ReaderTapSlopFloorPx, ViewConfiguration.get(context).scaledTouchSlop.toFloat())

internal const val ReaderTapDurationFloorMillis = 1500L
internal const val ReaderTapSlopFloorPx = 12f

abstract class SwipePageTouchListener(
    swipeDistance: Float = DEFAULT_SWIPE_DISTANCE,
) : View.OnTouchListener {
    private val tracker = ReaderSwipeGestureTracker(
        minDistance = swipeDistance,
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
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                cancelLongPress()
                tracker.onAdditionalPointerDown()
            }
            MotionEvent.ACTION_MOVE -> {
                if (exceedsLongPressSlop(view, event)) cancelLongPress()
                dispatch(tracker.onMove(event.x, event.y, event.eventTime))
            }
            MotionEvent.ACTION_UP -> {
                cancelLongPress()
                if (!longPressFired) {
                    dispatch(tracker.onUp(event.x, event.y, event.eventTime))
                } else {
                    tracker.onCancel()
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
        const val DEFAULT_SWIPE_DISTANCE = 72f
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
        if (!hasDown || swipeDispatched || minDistance <= 0f) return Result.None
        val dx = x - downX
        val dy = y - downY
        val elapsedMs = (eventTime - downTime).coerceAtLeast(1L)
        val velocityX = abs(dx) * 1_000f / elapsedMs
        val hasPageDistance = abs(dx) >= minDistance
        val hasFastFlickDistance = abs(dx) >= minDistance / 2f &&
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
            abs(dx) < TAP_SLOP &&
            abs(dy) < TAP_SLOP
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

    fun onAdditionalPointerDown() {
        suppressCurrentGesture()
    }

    sealed class Result {
        data object None : Result()
        data object LeftSwipe : Result()
        data object RightSwipe : Result()
        data class Tap(val x: Float, val y: Float) : Result()
    }

    private companion object {
        const val TAP_SLOP = 72f
        const val MIN_FAST_FLICK_VELOCITY_PX_PER_SECOND = 900f
        const val MAX_EARLY_SWIPE_DURATION_MS = 300L
        const val MIN_EARLY_SWIPE_VELOCITY_PX_PER_SECOND = 360f
    }
}
