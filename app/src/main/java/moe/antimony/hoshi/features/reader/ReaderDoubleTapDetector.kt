package moe.antimony.hoshi.features.reader

import kotlin.math.abs

/**
 * 纯逻辑的双击判定器，便于在不依赖 Android 框架的单元测试中验证。
 *
 * [timeoutMillis] 两次点击的最大间隔，[slopPx] 两次点击坐标允许的最大偏移。
 * 调用 [registerTap] 返回 `true` 表示本次点击构成一次双击。
 */
internal class ReaderDoubleTapDetector(
    private val timeoutMillis: Long = DEFAULT_DOUBLE_TAP_TIMEOUT_MS,
    private val slopPx: Float = DEFAULT_DOUBLE_TAP_SLOP_PX,
) {
    private var lastTapTime = Long.MIN_VALUE
    private var lastTapX = 0f
    private var lastTapY = 0f

    fun registerTap(x: Float, y: Float, nowMillis: Long): Boolean {
        val isDouble = nowMillis - lastTapTime <= timeoutMillis &&
            abs(x - lastTapX) <= slopPx &&
            abs(y - lastTapY) <= slopPx
        if (isDouble) {
            lastTapTime = Long.MIN_VALUE
        } else {
            lastTapTime = nowMillis
            lastTapX = x
            lastTapY = y
        }
        return isDouble
    }

    fun reset() {
        lastTapTime = Long.MIN_VALUE
    }
}

internal const val DEFAULT_DOUBLE_TAP_TIMEOUT_MS: Long = 300L
internal const val DEFAULT_DOUBLE_TAP_SLOP_PX: Float = 24f
