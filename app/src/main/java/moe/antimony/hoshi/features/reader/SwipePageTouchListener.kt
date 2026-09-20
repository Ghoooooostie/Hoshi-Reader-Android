package moe.antimony.hoshi.features.reader

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

abstract class SwipePageTouchListener(
    swipeDistance: Float = DEFAULT_SWIPE_DISTANCE,
    private val doubleTapEnabled: Boolean = false,
    private val consumeTrailingLongPressGesture: () -> Boolean = { false },
) : View.OnTouchListener {
    private val tracker = ReaderSwipeGestureTracker(minDistance = swipeDistance)
    private val doubleTapDetector = ReaderDoubleTapDetector()
    private var pendingTap: Runnable? = null
    private var hostView: View? = null

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        hostView = view
        if (shouldIgnoreReaderGesture(event)) {
            tracker.suppressCurrentGesture()
            cancelPendingTap()
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> tracker.onDown(event.x, event.y, event.eventTime)
            MotionEvent.ACTION_POINTER_DOWN -> tracker.onAdditionalPointerDown()
            MotionEvent.ACTION_MOVE -> dispatch(view, tracker.onMove(event.x, event.y, event.eventTime))
            MotionEvent.ACTION_UP -> {
                if (consumeTrailingLongPressGesture()) {
                    cancelPendingTap()
                    tracker.suppressCurrentGesture()
                    return false
                }
                dispatch(view, tracker.onUp(event.x, event.y, event.eventTime))
            }
            MotionEvent.ACTION_CANCEL -> {
                if (consumeTrailingLongPressGesture()) {
                    cancelPendingTap()
                }
                tracker.onCancel()
                cancelPendingTap()
            }
        }
        return false
    }

    open fun onLeftSwipe() = Unit
    open fun onRightSwipe() = Unit
    open fun onTap(x: Float, y: Float) = Unit
    open fun onDoubleTap(x: Float, y: Float) = Unit
    open fun shouldIgnoreReaderGesture(event: MotionEvent): Boolean = false

    private fun dispatch(view: View, result: ReaderSwipeGestureTracker.Result) {
        when (result) {
            ReaderSwipeGestureTracker.Result.LeftSwipe -> onLeftSwipe()
            ReaderSwipeGestureTracker.Result.RightSwipe -> onRightSwipe()
            is ReaderSwipeGestureTracker.Result.Tap -> onTapResult(view, result.x, result.y)
            ReaderSwipeGestureTracker.Result.None -> Unit
        }
    }

    private fun onTapResult(view: View, x: Float, y: Float) {
        if (!doubleTapEnabled) {
            onTap(x, y)
            return
        }
        if (doubleTapDetector.registerTap(x, y, SystemClock.uptimeMillis())) {
            cancelPendingTap()
            onDoubleTap(x, y)
            return
        }
        cancelPendingTap()
        val runnable = Runnable {
            pendingTap = null
            onTap(x, y)
        }
        pendingTap = runnable
        view.postDelayed(runnable, DEFAULT_DOUBLE_TAP_TIMEOUT_MS)
    }

    private fun cancelPendingTap() {
        pendingTap?.let { hostView?.removeCallbacks(it) }
        pendingTap = null
    }

    private companion object {
        const val DEFAULT_SWIPE_DISTANCE = 72f
    }
}

internal class ReaderSwipeGestureTracker(
    private val minDistance: Float,
) {
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var hasDown = false
    private var swipeDispatched = false

    fun onDown(x: Float, y: Float, eventTime: Long) {
        downX = x
        downY = y
        downTime = eventTime
        hasDown = true
        swipeDispatched = false
    }

    fun onMove(x: Float, y: Float, eventTime: Long): Result {
        if (!hasDown || swipeDispatched || minDistance <= 0f) return Result.None
        val dx = x - downX
        val dy = y - downY
        if (abs(dx) <= abs(dy)) return Result.None
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
        val wasSwipeDispatched = swipeDispatched
        onCancel()
        return if (
            !wasSwipeDispatched &&
            elapsedMs <= MAX_TAP_DURATION_MS &&
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
        const val MAX_TAP_DURATION_MS = 500L
        const val MIN_EARLY_SWIPE_VELOCITY_PX_PER_SECOND = 360f
    }
}
