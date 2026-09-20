# MEMORY

## WebView JS 诊断（durable）
- **Android WebView 里 Text 节点没有 `getBoundingClientRect()`**（只有 Element/Range 有）。`createWalker()` 产出的是 Text 节点，取几何必须 `document.createRange(); range.selectNodeContents(node); range.getBoundingClientRect()/getClientRects()`。曾导致 `calculateProgress()` 抛 TypeError → `evaluateJavascript` 回调收到 null → 章内进度/书签/常驻翻译全停（e92a195 修复）。
- **`evaluateJavascript` 返回 null 的含义**：JS 抛异常 → Java null；结果 `NaN` → JSON 序列化也是 "null"。诊断要 try/catch + `typeof`/`String(v)` 包一层，不能凭 null 断言"回调没执行"。判根因前把"回调触发 / 守卫通过 / 原始返回值"三层都打日志。
- **改 reader JS 后的验证**：先 `node app/src/test/js/reader-paginated.test.mjs`（无需编译，但注意测试 DOM shim 给 Text 也提供了 getBoundingClientRect，可能掩盖此类回归）；再确认新实现真进了 APK（PowerShell 开 APK zip grep 资产内容）；装机后必须 `force-stop` 冷启动。
- 真机自动化验证：`adb shell input swipe 900 1100 150 1100 180` 模拟翻页 + `adb logcat -d | grep HoshiBM`；`adb exec-out screencap -p` 截图确认当前界面。

## Windows 构建环境（durable）
- Android SDK 在 `D:\Program_Files\Android\Sdk`，`JAVA_HOME=D:\Program Files\Android\Android Studio\jbr`，
  cargo 装在 `D:\AndroidCache\.cargo\bin\cargo.exe`。
  **`app/build.gradle.kts:13` 用 `System.getenv("HOME") + "/.cargo/bin/cargo"` 找 cargo**（不是 `CARGO_HOME`）。
  新开的 PowerShell 会话通常没有 `HOME`，构建会报 `null/.cargo/bin/cargo` 然后失败；先设
  `$env:HOME='D:\AndroidCache'` 再跑 gradle。构建命令：
  `Set-Location d:\My_Project\Hoshi-Reader-Android; .\gradlew.bat :app:assembleDebug --no-daemon`。
  不要给 gradlew 传 `-Dorg.gradle.java.home=...`（PowerShell 下会被当成 task）。
- **CMake 包不会被 Gradle/AGP 的 SDK 自动下载开关安装**。需要哪个版本就直接手工装：
  下载 `https://dl.google.com/android/repository/cmake-<version>-windows.zip`，解压到
  `Sdk\cmake\<version>\`（zip 根目录即 `bin/ doc/ share/ source.properties`，符合 SDK 布局）。
  该 SDK 未安装 `cmdline-tools`，没有 sdkmanager 可用。
- **sherpa-onnx 本地 AAR 不在 git 里**：`app/libs/sherpa-onnx-1.13.8.aar` 需手工下载
  （GitHub Release `k2-fsa/sherpa-onnx` v1.13.8，约 50MB）。直连 GitHub 超时，走 Clash 代理：
  `curl -L -x http://127.0.0.1:7897 -o app/libs/sherpa-onnx-1.13.8.aar https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar`。
  1.13.8 的 `OfflineTts` 构造函数是 `OfflineTts(AssetManager, OfflineTtsConfig)`（旧的单参 `OfflineTts(config)` 已移除）。
  - **supertonic-ja 模型（`sherpa-onnx-supertonic-3-tts-int8-2026-05-11`）包内真实文件名**（`run-as` 在设备上确认）：
    `duration_predictor.int8.onnx`、`text_encoder.int8.onnx`、`vector_estimator.int8.onnx`、
    `vocoder.int8.onnx`、`tts.json`、`unicode_indexer.bin`、`voice.bin`。
    `ReadAloudModels.kt` 的 `TtsModelConfig` 字段必须一一对应这些名字；`isModelInstalled` 要求
    `requiredModelFiles` 全部存在才判为已装。**改这些文件名前务必先 `run-as` 核对设备上的真实文件**，
    否则写错一个（如误写成 `unicoder_indexer.txt`）会导致 `isModelInstalled` 永远 false → 一直提示"重新下载"。
- **`androidx.media:media` 1.7.0 在本机缓存里不暴露 `androidx.media.session.MediaSessionCompat`**（仅含
  `android/support/v4/media/session` 老命名空间）。因此 Hoshi 朗读的媒体会话用框架原生
  `android.media.session.MediaSession` + `Notification.MediaStyle` + `AudioFocusRequest`（minSdk 26）实现，
  不依赖 `androidx.media`。
- adb 偶尔枚举不到已插的 USB 设备；用 `$env:ADB_TRACE='usb'; adb devices`（随后可
  `Remove-Item Env:ADB_TRACE`）通常能让它重新出现；必要时 `adb kill-server` + `adb start-server`。
  设备：E-ink `S101022210032`、Meizu `381QYGEM226CZ`；debug 包名 `moe.antimony.hoshi.debug`。
- `C:\Users\Administrator\.gitconfig` 设了 `http.proxy=http://127.0.0.1:7897`（Clash）。代理没开时 git 推拉直接
  失败（`Failed to connect to 127.0.0.1 port 7897`），但直连 GitHub 可用：用
  `git -c http.proxy= -c https.proxy= push ...` 单条命令绕过，不要改全局配置。
- 本仓库只配了 `origin`（fork `Ghoooooostie/Hoshi-Reader-Android`），**没有 upstream remote**。要对齐上游必须显式
  fetch URL：`git -c http.proxy= fetch --no-tags https://github.com/HuangAntimony/Hoshi-Reader-Android.git main`
  （落到 `FETCH_HEAD`）。fork 的 `main` 长期落后上游（2026-07 起就没同步），所以 GitHub 上
  "ahead/behind HuangAntimony:main" 的数字多半是 fork `main` 的，不是当前功能分支的。

## WebView JS 兼容性（durable）
- E-ink 设备 WebView ≈ Chrome 80-84：`app/src/main/assets/hoshi-web/**` 及 Kotlin 模板生成的 JS 不得使用
  `??=` / `||=` / `&&=`、`replaceAll`、`Array.at`、`structuredClone` 等 Chrome>84 语法/API。
- **Reader 长按选词必须走 WebView 原生文字选择**（`D:\My_Project\Hoshi-Reader-Android_demo` 是好用的参照）：
  不要用 `setOnLongClickListener` 拦截后自己实现"selectText + 拖动扩展"（曾尝试 `extendSelectionTo` 自定义拖动，
  与 SwipePageTouchListener 冲突：长按手势的 ACTION_UP 会被判成 tap 再次 selectText 覆盖选区，且原生手柄体验远好于自绘）。
  正确做法：长按=选词时 listener 返回 false 让原生选择接管；原生选择 ActionMode 激活时
  `shouldIgnoreReaderGestureEvent` 已经会忽略翻页/单击手势；"标注"菜单由 `ReaderHighlightActionModeCallback` 注入。

## Android 界面异常诊断手法（durable）
- 判断"白屏/黑屏 vs 有内容"：`adb exec-out screencap -p > x.png` 后看文件大小（纯白屏 ~31KB，正常书库页 300KB~1.7MB）。
- 拿 app 的 View 树：`adb shell dumpsys activity <pkg>`（其中含该包 Activity 的 `View Hierarchy`）。
  这台 Flyme 上 `dumpsys activity top` 不一定包含目标 Activity，别依赖它。
- `uiautomator dump` 只能证明"逻辑界面存在"，不能证明"屏幕上看得见"；界面正常但截图全白时，
  优先怀疑有残留的覆盖 View（`DecorView` 直接子 View 里出现无 id 的全屏 `android.view.View`）。
- `enableEdgeToEdge()` 会在 DecorView 上加 `SystemBarStateMonitor$1` 和 `ProtectionLayout`（含 4 条边衬保护 View），
  这些属正常，不要误判为遮罩层。
- 用"前后台切换后立刻 dump"做差分：遮罩层若每次 `onResume` 都新增，数量会出现 1→2→1 的规律。

## 工作区外临时构建（durable）
- 需要在不动当前工作区/分支的前提下构建时：`git archive --format=zip -o <zip> <branch>` 解压到
  `D:\AndroidCache\tmp\...`，再复制 `local.properties`。
- **git archive 不带子模块内容**，必须 `robocopy <repo>\third_party\hoshidicts-kotlin-bridge <copy>\third_party\hoshidicts-kotlin-bridge /E /XD .git`，
  否则 `configureCMakeDebug` 会因缺 `app/src/main/cpp/hoshidicts` 失败（CMakeLists 里由此路径 add_subdirectory）。

## Compose / Material3 踩坑（durable）
- 本项目当前 Material3 版本**没有 `Surface(onClick=...)` 这个重载**（编译器报
  `No parameter with name 'onClick'` / `No value passed for parameter 'checked'`）。需要可点击卡片时用
  `Surface(...)`（非点击重载，带 `color/shape/tonalElevation/border`）+ `Modifier.clickable`，或干脆用 `IconButton`。
- **有声书（听书）= `sasayaki` 模块**（`features/sasayaki/SasayakiSheet.kt`）。它的播放按钮标准写法：
  `IconButton { Icon(Icons.Rounded.SkipPrevious / PlayArrow|Pause / SkipNext, ...) }` 排成
  `Row(Arrangement.spacedBy(2.dp))`。朗读面板 `ReadAloudFloatingControls` 要与有声书一致就照抄这套，别自创
  `Surface(onClick=...)`。朗读交互已接好：底部菜单"朗读"→ `ReadAloudSettingsSheet` 弹窗（带开始按钮）→ 播放中
  `ReadAloudFloatingControls` 浮在底部中间，暂停时显示"上一句/下一句"两个 `IconButton`。

## LunaTranslator 功能改动（durable）
- **未翻译兜底重翻（2026-09-21）**：AI/在线翻译结果疑似仍是日文（假名占比 ≥ 阈值）时，自动改用备用翻译重翻一次。
  实现位置：`translator/basetranslator.py`（`_kana_ratio`/`_result_untranslated`/`_try_refallback` + `translate_and_collect`
  集成，`maybezhconvwrapper` 透传 `replace` 标记）；`LunaTranslator.py` 的 `GetTranslationCallback` 增 `replace` 形参 +
  `_set_trans_segment`（用 `_trans_segments` dict 分段聚合合并译文，避免兜底替换时重复累加 `currenttranslate`）；
  `gui/setting/translate.py` 的 `renameapi` 右键菜单加"未翻译时改用其它翻译重翻"开关 + "设置重翻引擎…"对话框。
  配置键（存 `globalconfig["fanyi"][engine]`）：`untranslated_refallback`(bool)、`untranslated_refallback_engine`(str)、
  `untranslated_refallback_threshold`(float, 默认 0.5)。
  **前提**：备用引擎（如 `caiyun`）必须先启用（use=true），否则 `gobject.base.translators` 里没有其实例，兜底不生效。
  判定仅对"目标语言非日语"生效；日文源靠假名占比，不误伤中文→英文等场景。

## Hoshi AI 翻译未翻译兜底重翻（2026-09-21, durable）
- **需求纠正**：用户最初说"参考 LunaTranslator 的彩云"，实际要改的是 **Hoshi Reader（Android）** 的 AI 全文翻译——AI 偶尔把日文段落原样返回（未翻译）。
- **实现**：开启开关后，整页翻译队列里每段 AI 结果检测"日文假名占比"（平假名 3040-309F / 片假名 30A0-30FF）≥ 0.5 即视为未翻译，自动用**强制简体中文提示词**把"原文段落"重新请求一次 AI 重翻，覆盖缓存与显示。
- 改动文件：`features/advancedai/AdvancedAiClient.kt`（接口默认方法 `retranslateParagraphToChinese` + 检测 `isMostlyJapanese`/`japaneseKanaRatio`/阈值 0.5；默认降级为普通翻译，真实现 `OpenAiCompatibleAdvancedAiClient` 覆盖）、`features/reader/ReaderWebView.kt`（`pumpReaderPageTranslationQueue` 接入兜底）、`features/reader/ReaderTranslationAiSheet.kt`（整页 section 加开关 `reader_translation_ai_fallback_enable`）、`features/reader/ReaderSettings.kt`（`readerAiTranslationFallbackEnabled` 字段，主 data class + legacy SP + DataStore + `ProfileReaderAppearanceSettings` 聚合共 9 处）、`res/values*/strings.xml`（2 字符串）。
- 配置键：`ReaderSettings.readerAiTranslationFallbackEnabled`（默认 false）。开关在**阅读器 → 翻译(AI) 面板 → 全文翻译设置组**内（依赖"当前页翻译"已开启）。
- **当前兜底引擎 = AI 自身二次强约束重翻（零配置即可用）**，并非真正接彩云/在线翻译 API（Hoshi 此前无任何在线翻译集成，彩云需 token）。若用户要接专业在线翻译（彩云/百度等）作兜底，需新增在线翻译客户端 + token 配置 UI。
- 编译：`:app:compileDebugKotlin` BUILD SUCCESSFUL。长按句子翻译（Translation 模式）暂未加兜底，仅整页。
