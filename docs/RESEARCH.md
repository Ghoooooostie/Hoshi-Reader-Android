# 外部参考结论

- 2026-09-15：AnyWebView 官方说明需要 Xposed/LSPosed 才能把非白名单 WebView 加入 Android 选择器；当前连接的 S11A（Android 11）只有厂商 `com.android.webview` 被系统服务列为有效 provider，无法仅靠 ADB 切换到 Google WebView。
