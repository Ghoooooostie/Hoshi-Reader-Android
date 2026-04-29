package moe.antimony.hoshi.webview

import android.view.View
import android.webkit.WebView

fun WebView.disableNativeOverscrollStretch() {
    overScrollMode = View.OVER_SCROLL_NEVER
}
