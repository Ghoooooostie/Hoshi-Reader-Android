# MEMORY

## WebView JS 诊断（durable）
- Android WebView 里 Text 节点没有 `getBoundingClientRect()`（只有 Element/Range 有）。`createWalker()` 产出 Text 节点，取几何必须 `document.createRange(); range.selectNodeContents(node); range.getBoundingClientRect()/getClientRects()`。曾导致 calculateProgress() 抛 TypeError → evaluateJavascript 回调收到 null → 章内进度/书签/常驻翻译全停（e92a195 修复）。
- `evaluateJavascript` 返回 null：JS 抛异常→Java null；结果 NaN→JSON "null"。诊断要 try/catch + typeof/String(v) 包一层；判根因前把"回调触发/守卫通过/原始返回值"三层都打日志。
- 改 reader JS 后验证：先 `node app/src/test/js/reader-paginated.test.mjs`（注意测试 DOM shim 给 Text 也提供 getBoundingClientRect，可能掩盖此类回归）；再确认新实现进了 APK（开 APK zip grep 资产）；装机后必须 `force-stop` 冷启动。
- 真机自动化：`adb shell input swipe 900 1100 150 1100 180` 模拟翻页 + `adb logcat -d | grep HoshiBM`；`adb exec-out screencap -p` 截图确认当前界面。

## Windows 构建环境（durable）
- Android SDK 在 `D:\Program_Files\Android\Sdk`，`JAVA_HOME=D:\Program Files\Android\Android Studio\jbr`，cargo 在 `D:\AndroidCache\.cargo\bin\cargo.exe`。`app/build.gradle.kts:13` 用 `System.getenv("HOME")+"/.cargo/bin/cargo"` 找 cargo（非 CARGO_HOME）。新 PowerShell 会话常无 HOME，构建报 `null/.cargo/bin/cargo` 失败；先 `$env:HOME='D:\AndroidCache'` 再 gradle。命令：`Set-Location d:\My_Project\Hoshi-Reader-Android; .\gradlew.bat :app:assembleDebug --no-daemon`。勿给 gradlew 传 `-Dorg.gradle.java.home=`（PowerShell 下会被当成 task）。
- CMake 包不被 AGP SDK 自动下载安装；需手工下载 `cmake-<ver>-windows.zip` 解压到 `Sdk\cmake\<ver>\`。该 SDK 无 cmdline-tools/sdkmanager。
- sherpa-onnx 本地 AAR 不在 git：`app/libs/sherpa-onnx-1.13.8.aar` 需手工下载（GitHub Release k2-fsa/sherpa-onnx v1.13.8 ~50MB，直连超时走 Clash 代理 `curl -L -x http://127.0.0.1:7897`）。1.13.8 的 `OfflineTts` 构造是 `OfflineTts(AssetManager, OfflineTtsConfig)`。supertonic-ja 模型包内真实文件名（run-as 核对）：`duration_predictor.int8.onnx`、`text_encoder.int8.onnx`、`vector_estimator.int8.onnx`、`vocoder.int8.onnx`、`tts.json`、`unicode_indexer.bin`、`voice.bin`；`ReadAloudModels.kt` 的 `TtsModelConfig` 字段须一一对应，`isModelInstalled` 要求 `requiredModelFiles` 全存在。改文件名前务必 run-as 核对设备真实文件。
- `androidx.media:media` 1.7.0 在本机缓存不暴露 `androidx.media.session.MediaSessionCompat`（仅老命名空间）。Hoshi 朗读媒体会话用框架原生 `android.media.session.MediaSession` + `Notification.MediaStyle` + `AudioFocusRequest`（minSdk 26），不依赖 androidx.media。
- adb 偶尔枚举不到 USB 设备：用 `$env:ADB_TRACE='usb'; adb devices`（随后 `Remove-Item Env:ADB_TRACE`）通常重现；必要时 `adb kill-server` + `adb start-server`。设备：E-ink `S101022210032`、Meizu `381QYGEM226CZ`；debug 包名 `moe.antimony.hoshi.debug`。
- `C:\Users\Administrator\.gitconfig` 设 `http.proxy=http://127.0.0.1:7897`（Clash）。代理没开时 git 推拉失败；直连 GitHub 可用 `git -c http.proxy= -c https.proxy= push ...` 单条绕过，不改全局。
- 本仓库只配 origin（fork `Ghoooooostie/Hoshi-Reader-Android`），无 upstream remote。对齐上游须显式 fetch：`git -c http.proxy= fetch --no-tags https://github.com/HuangAntimony/Hoshi-Reader-Android.git main`。
- 工作区外临时构建：`git archive --format=zip -o <zip> <branch>` 解压到 `D:\AndroidCache\tmp\...` 并复制 `local.properties`；git archive 不带子模块，须 `robocopy <repo>\third_party\hoshidicts-kotlin-bridge <copy>\third_party\hoshidicts-kotlin-bridge /E /XD .git`，否则 `configureCMakeDebug` 缺 `app/src/main/cpp/hoshidicts` 失败（CMakeLists 由此路径 add_subdirectory）。

## WebView JS 兼容性（durable）
- E-ink 设备 WebView ≈ Chrome 80-84：`hoshi-web/**` 及 Kotlin 模板生成的 JS 不得使用 `??=`/`||=`/`&&=`、`replaceAll`、`Array.at`、`structuredClone` 等 Chrome>84 语法/API。
- 阅读器长按选词必须走 WebView 原生文字选择（`Hoshi-Reader-Android_demo` 是好用的参照）；不要自己实现 selectText+拖动扩展，会与 SwipePageTouchListener 冲突且原生手柄体验更好。长按=选词时 listener 返回 false 让原生选择接管；原生 ActionMode 激活时 `shouldIgnoreReaderGestureEvent` 已忽略翻页/单击手势。

## Android 界面异常诊断手法（durable）
- 白屏/黑屏 vs 有内容：`adb exec-out screencap -p > x.png` 看文件大小（纯白屏 ~31KB，正常书库页 300KB~1.7MB）。
- `adb shell dumpsys activity <pkg>` 含该包 Activity 的 View Hierarchy（Flyme 上 dumpsys activity top 不一定含目标）。
- `uiautomator dump` 只能证明逻辑界面存在，不能证明屏幕上看得见；界面正常但截图全白时优先怀疑残留覆盖 View（DecorView 直接子 View 里出现无 id 全屏 android.view.View）。
- `enableEdgeToEdge()` 会在 DecorView 上加 SystemBarStateMonitor$1 和 ProtectionLayout（4 条边衬保护 View），属正常。
- 前后台切换后立刻 dump 做差分：遮罩层若每次 onResume 都新增，数量会出现 1→2→1 规律。

## Compose / Material3 踩坑（durable）
- 当前 Material3 版本没有 `Surface(onClick=...)` 重载（编译器报 No parameter 'onClick'）。可点击卡片用 `Surface(...)`（非点击重载 + color/shape/tonalElevation/border）+ `Modifier.clickable`，或用 `IconButton`。
- 有声书（听书）= `sasayaki` 模块。播放按钮标准写法：`IconButton { Icon(Icons.Rounded.SkipPrevious/PlayArrow|Pause/SkipNext) }` 排成 `Row(Arrangement.spacedBy(2.dp))`。朗读面板 `ReadAloudFloatingControls` 与之对齐。

## LunaTranslator 功能改动（durable，跨项目）
- 未翻译兜底重翻（2026-09-21）：AI/在线翻译结果假名占比 ≥ 阈值时自动用备用翻译重翻。实现在 translator/basetranslator.py + LunaTranslator.py + gui/setting/translate.py。配置键：untranslated_refallback(bool)、untranslated_refallback_engine(str)、untranslated_refallback_threshold(float 默认 0.5)。前提：备用引擎须先 use=true 启用。

## Hoshi AI 翻译未翻译兜底重翻（2026-09-21, durable）
- 用户原话"参考 LunaTranslator 彩云"实际改的是 Hoshi Reader Android 的 AI 全文翻译：AI 偶尔把日文段落原样返回。开启开关后，整页翻译队列每段 AI 结果检测日文假名占比 ≥ 0.5 即视为未翻译，自动用强制简体中文提示词把"原文段落"重新请求重翻，覆盖缓存与显示。
- 改动：AdvancedAiClient.kt、ReaderWebView.kt（pumpReaderPageTranslationQueue 接入）、ReaderTranslationAiSheet.kt（开关 reader_translation_ai_fallback_enable）、ReaderSettings.kt（readerAiTranslationFallbackEnabled 字段，9 处）、res/values*/strings.xml。配置键 ReaderSettings.readerAiTranslationFallbackEnabled（默认 false），在 阅读器→翻译(AI)面板→全文翻译设置组 内。当前兜底=AI 自身二次强约束重翻（零配置即可用）；若接专业在线翻译需新增客户端+token UI。编译 `:app:compileDebugKotlin` BUILD SUCCESSFUL。长按句子翻译暂未加兜底，仅整页。

## 阅读器查词弹窗点击空白卡顿（durable）
- 朗读播放时点击空白关闭查词弹窗卡顿的根因：`ReaderChapterWebView.kt` 的 `onTap` 先跑 `selectAt()` 异步 JS 选词检测（`evaluateJavascript(ReaderSelectionCommand.SelectText)`），回调里 `selectedNothing` 才关闭弹窗。朗读时阅读 WebView 正忙于 `highlightReadAloudTarget(reveal=true)` 高亮+滚动当前句，该 JS 被排队延迟 → 弹窗迟滞、点击响应卡顿。
- 修复：弹窗已开（`currentReaderPopupFrames.value.isNotEmpty()`）时 `onTap` 直接 `onReaderTapOutside()` 关闭、跳过 selectAt；弹窗未开仍走原 selectAt。行为变化：弹窗打开时点击空白直接关闭，不再先尝试点击处查新词。改之前先确认此路径，勿一上来怀疑 TTS 线程。
- 顺带修过今天 WIP 引入的编译错误：`ReaderWebView.kt` 的 `resumeReadAloudAfterPageTranslationIfNeeded`（局部函数）被 `clearReaderPageTranslations` 在定义前前向调用，前移到调用点之前。
- 用户要的"AI 分析默认折叠"正确目标是**长按查词弹窗内的 AI 分析卡片**，不是设置页。`AdvancedAiSettingsView.kt`（设置页"高级 AI"）的 `CollapsibleSection` 折叠改动是误改、已回退。详见「长按弹出 AI 分析卡片」。

## 长按弹出 AI 分析卡片（durable）
- 长按查词弹出的 AI 分析卡片在 WebView iframe 内渲染：`app/src/main/assets/hoshi-web/popup/popup.js` 的 `createAdvancedAiCard`（样式 `popup.css`）。
- 默认折叠（`data-collapsed="true"`）：标题行 `advanced-ai-card-header`（含 `advanced-ai-card-toggle` 三角指示器）可点击切换；折叠时 `.advanced-ai-card-body` 与 `.advanced-ai-card-mode-switch` 由 CSS `display:none` 隐藏，展开时三角朝下。
- 该 popup 是固定 `readerSettings.popupHeight` 窗口、内容内部滚动，不靠 iframe 上报 `contentHeight` 自适应；折叠只隐藏内容、不改变窗口大小（JS 里无需 post contentHeight）。
- 对应 JS 单测在 `app/src/test/js/popup.test.mjs`（注意：该文件 `popupContext` 原有一处重复 `const entriesContainer` 语法错误导致整文件无法运行，已修掉重复声明）。

## 朗读语速不生效（durable）
- 朗读有两条引擎：System（`TextToSpeech`，默认）与 Local（sherpa-onnx Supertonic 离线模型，包名 `app/libs/sherpa-onnx-1.13.8.aar`）。语速设置走 `ReadAloudSettings.speechRate` → `ReadAloudController` 的 DataStore 收集器 → `engine.setSpeechRate()`，本地引擎在 `speak()` 生成时读 `speechRate` 字段。
- Supertonic（V2）的 `OfflineTtsSupertonicImpl::Generate` 取语速逻辑是 `config.GetExtraFloat("speed", config.speed > 0 ? config.speed : 1.05f)`：**优先读 `extra` map 里的 `"speed"` 字符串**，没有才回退到顶层 `speed` 字段。原生 `extra` 已被 JNI 转发（现有 `"lang"->"ja"` 生效即证明），但顶层 `speed` 字段是否被转发不可靠。
- 症状"调了语速没效果"的根因（本地引擎）：`LocalReadAloudEngine.kt` 的 `GenerationConfig` 只设了 `extra = mapOf("lang" to "ja")`，没把 `"speed"` 放进 `extra`，若 JNI 没转发顶层 `speed` 即静默用默认 1.0。修复：生成时 `extra = mapOf("lang" to "ja", "speed" to speechRate.toString())`，让 Supertonic 一定从 extra 读到语速。
- System 引擎只能靠 `TextToSpeech.setSpeechRate()`（对象级），标准 Android SDK 无 `KEY_PARAM_RATE` 这种公开按句语速常量（只有 `KEY_PARAM_STREAM/VOLUME/UTTERANCE_ID/SESSION_ID/PAN`），不要引用 `KEY_PARAM_RATE`，否则编译失败。部分设备日文语音忽略对象级 setSpeechRate 时应用层无标准解法。
- 验证：`app\src\main\java\moe\antimony\hoshi\features\readaloud\LocalReadAloudEngine.kt` 与 `SystemReadAloudEngine.kt`；编译 `:app:compileDebugKotlin` BUILD SUCCESSFUL。

## 朗读换引擎即时生效（durable）
- 朗读会话正在播放时切换引擎（`engineId` 系统/本地，或在系统引擎下切换具体语音 `selectedSystemEngineName`）原本不会即时生效：`playFrom` 只在 start/resume/skip 时 `selectEngine()`，正在跑的句子循环一直持有旧引擎引用，要等暂停+恢复或跳句才换。
- 修复在 `ReadAloudController.kt` 的 init 设置收集器里：记录 `prevEngineId`/`prevSelectedSystemEngineName`，当 `engineId` 变化（或系统引擎下 `selectedSystemEngineName` 变化）且 `isActive && isPlaying` 时，`playbackJob?.cancel()` + `activeEngine.stop()` + `playFrom(currentIndex)` 用新引擎从当前句重启。仅在真正影响当前引擎时才重启（本地引擎下改系统语音不重启）。语速变化不触发重启，靠每句 `setSpeechRate` 自然生效。
