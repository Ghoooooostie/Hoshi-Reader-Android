package moe.antimony.hoshi.features.eink

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * 墨水屏整屏刷新（清残影/ghosting）。
 *
 * 目标设备（Rockchip EB1004P，Android 11）暴露了系统服务 `eink`
 *（`android.os.IEinkManager`），其 `setProperty("sys.eink.one_full_mode_timeline", ...)`
 * 可触发一次全屏帧刷新。但 `IEinkManager` 是隐藏 API，普通应用进程受 Android
 * 隐藏 API 限制无法反射调用 `asInterface`，因此这里优先尝试系统服务，失败则
 * 回退到**全屏翻转**方案：在 DecorView 上叠加一个覆盖全屏的纯色层并保持约一帧
 * 再移除，驱动检测到全屏尺寸的更新通常会触发 GC 全刷，从而清除残影。
 *
 * 两种路径都失败时静默降级，不影响其他行为。
 */
object EinkScreenRefresh {
    private const val TAG = "EinkScreenRefresh"
    private const val SERVICE_NAME = "eink"
    private const val STUB_CLASS = "android.os.IEinkManager\$Stub"
    private const val EINK_DESCRIPTOR = "android.os.IEinkManager"
    private const val KEY_ONE_FULL_FRAME = "sys.eink.one_full_mode_timeline"
    private const val KEY_MODE = "sys.eink.mode"
    private const val MODE_FULL_GC = "2"
    private const val OVERLAY_HOLD_MS = 250L

    fun refresh(context: Context) {
        val activity = context.findActivity() ?: run {
            Log.w(TAG, "no activity context available, skip eink refresh")
            return
        }
        if (!trySystemRefresh()) {
            overlayRefresh(activity)
        }
    }

    private fun trySystemRefresh(): Boolean {
        return try {
            val sm = Class.forName("android.os.ServiceManager")
            val binder = sm.getMethod("getService", String::class.java)
                .invoke(null, SERVICE_NAME) as? IBinder ?: return false
            val code = einkSetPropertyCode() ?: return false
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(EINK_DESCRIPTOR)
                data.writeString(KEY_ONE_FULL_FRAME)
                data.writeString(System.currentTimeMillis().toString())
                binder.transact(code, data, reply, 0)
                runCatching {
                    val d2 = Parcel.obtain()
                    val r2 = Parcel.obtain()
                    try {
                        d2.writeInterfaceToken(EINK_DESCRIPTOR)
                        d2.writeString(KEY_MODE)
                        d2.writeString(MODE_FULL_GC)
                        binder.transact(code, d2, r2, 0)
                    } finally {
                        d2.recycle()
                        r2.recycle()
                    }
                }
                Log.d(TAG, "eink system full refresh via transact (code=$code)")
                true
            } finally {
                data.recycle()
                reply.recycle()
            }
        } catch (t: Throwable) {
            Log.d(TAG, "eink transact refresh unavailable: ${t.message}")
            false
        }
    }

    private fun einkSetPropertyCode(): Int? = try {
        val stub = Class.forName(STUB_CLASS)
        stub.getField("TRANSACTION_setProperty").getInt(null)
    } catch (t: Throwable) {
        Log.d(TAG, "TRANSACTION_setProperty unavailable: ${t.message}")
        null
    }

    private fun overlayRefresh(activity: Activity) {
        try {
            val decor = (activity.window?.decorView as? ViewGroup) ?: return
            val overlay = View(activity).apply {
                setBackgroundColor(Color.WHITE)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            decor.addView(overlay)
            // 白 -> 黑 -> 移除：两次极端全屏翻转，驱动会对全屏尺寸更新做 GC 全刷以清除残影。
            overlay.postDelayed({
                overlay.setBackgroundColor(Color.BLACK)
                overlay.postDelayed({ decor.removeView(overlay) }, OVERLAY_HOLD_MS)
            }, OVERLAY_HOLD_MS)
            Log.d(TAG, "eink overlay refresh requested")
        } catch (t: Throwable) {
            Log.w(TAG, "eink overlay refresh failed", t)
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
