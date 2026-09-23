# MEMORY

## 构建 / 工具链
- SDK `D:\Program_Files\Android\Sdk`；`JAVA_HOME=D:\Program Files\Android\Android Studio\jbr`；cargo `D:\AndroidCache\.cargo\bin\cargo.exe`（`app/build.gradle.kts:13` 读 `$HOME` → 新 shell 先 `$env:HOME='D:\AndroidCache'`）。构建 `.\gradlew.bat :app:assembleDebug --no-daemon`；CMake 需手解压到 `Sdk\cmake\<ver>\`。
- `app/libs/sherpa-onnx-1.13.8.aar` 不在 git（k2-fsa v1.13.8，代理 `127.0.0.1:7897`），文件名须与 `ReadAloudModels.kt` 的 `TtsModelConfig` 对应。
- `.\scripts\install.ps1`（`-Variant/-Serial/-SkipBuild/-NoLaunch/-AllowDowngrade`）须带 BOM 的 UTF-8。
- git 仅 origin（fork `Ghoooooostie/Hoshi-Reader-Android`）；对齐上游 `git -c http.proxy= fetch --no-tags https://github.com/HuangAntimony/Hoshi-Reader-Android.git main`。工作区外临时构建：`git archive` 到 `D:\AndroidCache\tmp\...` + `local.properties` + robocopy `third_party/hoshidicts-kotlin-bridge`。
- 编译验证：`:app:compileDebugKotlin` 增量可能漏报主源码错 → 再跑 `:app:compileDebugUnitTestKotlin`。PS ParserError 多为 `"$var:"`（应 `${var}`）或脚本编码。

## 设备 / adb
- E-ink `S101022210032`；Meizu 20 `381QYGEM226CZ`（Android 16/API 36，Flyme 国行）；debug 包名 `moe.antimony.hoshi.debug`。adb 枚举不到 → `$env:ADB_TRACE='usb'`。
- 系统语言：该 ROM 只有 8 种语言；`framework-res`/`Settings`/`SystemUI`/`FlymeLauncher` 的 `aapt2 dump configurations` 全为 **NO ja** ⇒ **系统界面无法日语**；Android 也没有可下载的系统语言包。能日语化的只有自带 ja 资源的 App（per-app locale）。
- 切系统 locale：① **免重启**（推荐）装 `com.sightidea.setlocale`（"区域 & 语言+" 5.1.0；`adb install --bypass-low-target-sdk-block` 绕 targetSdk 17 限制 + `pm grant com.sightidea.setlocale android.permission.CHANGE_CONFIGURATION`）→ 点 日本語(日本)，`mGlobalConfig` 立刻变 `[ja_JP]` 且 App 热重配；② `settings put system system_locales ja-JP` + **重启**（不重启无效）。回滚：同一 App 选回中文，或写 `zh-CN` + 重启。
- 由 ① 引起的日文字符串（如设置页「ユーザー715397672」）是 Meizu 账号昵称等**运行时数据**，不是界面日语化的证据（系统 APK 内不存在日文字符串）。
- `persist.sys.locale` shell 写不进去（SELinux）；`cmd locale` 只有 `set/get-app-locales`、`set/get-app-localeconfig`（应用级）。`dumpsys texttospeech` 在本机返回空，查 TTS 用 TTS 设置页 + uiautomator dump。
- `Log.d` 在 Flyme 被过滤 → 用 `Log.w`/`Log.e`；`console.log` 不进 logcat。
- 自动化：翻页 `input swipe 900 1100 150 1100 180`；截图 `adb shell screencap -p` + `adb pull`（勿用 PS `>` 重定向二进制）；VN 双击 = 两次 `input tap`；CSS 坐标 = 设备 px / 2.8125。
- Meizu 上**别用 `input swipe` 上滑翻列表**（会触发 Flyme 上滑手势/全局搜索，可能把短信等隐私内容 dump 到屏幕）→ 列表翻页用 `input keyevent 93`(PAGE_DOWN)，返回 `keyevent 4`。`uiautomator dump` 的 XML 是单行，本地须先 `[xml]` 解析再取 `//node`.text；`scrollable="true"` 为 0 表示列表已完整。

## WebView / JS
- E-ink WebView ≈ Chrome 80-84：`hoshi-web/**` 与 Kotlin 模板生成的 JS 不得用 `??=`/`||=`/`&&=`、`replaceAll`、`Array.at`、`structuredClone`。
- Text 节点没有 `getBoundingClientRect()` → `document.createRange()` + `selectNodeContents`；缺失曾致 `calculateProgress()` TypeError（进度/书签/常驻翻译全停，e92a195）。
- `evaluateJavascript` 返回 null = JS 抛异常或结果 NaN；诊断用 try/catch + `typeof`/`String(v)`。
- 改 reader JS 后：`node app/src/test/js/reader-*.test.mjs` → 确认资产进 APK → force-stop 冷启动。`:app:testDebugUnitTest` 本工作区跑不起来（`HoshiDictsCompatibilityTest` 与本地词典 AAR API 不匹配，预存问题）。
- `popup.js` 的 `hoshiPopupGeometry` 是 CSS `zoom` 下 popup 坐标换算唯一入口；不得混用 `offsetTop` 与 `scrollTop`/`getBoundingClientRect()`，不得另写缩放补偿。

## Android UI 诊断
- `dumpsys activity <pkg>` 含 View Hierarchy；界面正常但截图全白优先怀疑残留全屏覆盖 View。`enableEdgeToEdge()` 在 DecorView 加 `SystemBarStateMonitor$1` + `ProtectionLayout`（4 边）属正常。
- Material3 当前版本无 `Surface(onClick=...)` → 非点击 `Surface` + `Modifier.clickable`。`androidx.media:media` 1.7.0 无 `MediaSessionCompat` → 框架原生 `MediaSession` + `Notification.MediaStyle` + `AudioFocusRequest`。

## 阅读器手势 / 交互
- 句子手势（长按/双击 `ReaderGestureAction.SentenceAction`，UI 名「朗读」）只朗读：`ReaderChapterWebView.runSentenceAction()` → `onReadAloudStartFromPoint(x,y)`，不再弹句子翻译/AI 卡。整页翻译开启时仍保留译文块手势（长按译文块重翻、长按原文段展开译文）。
- 已删：默认长按模式、从句子手势开始朗读开关、弹窗整句 AI 翻译卡（`readerAiPopupModes`/`requestReaderPopupSentenceAi`）；弹窗 payload 统一 `LookupPopupAdvancedAiState.toPayload`，保留 `AdvancedAiCardKind.Word`（选词弹窗）与 `Sentence`（ProcessText 查词）。
- 长按选词走 WebView 原生文字选择（不自实现 selectText+拖动，与 `SwipePageTouchListener` 冲突）；ActionMode 激活时 `shouldIgnoreReaderGestureEvent` 忽略翻页/单击。朗读中点击关弹窗卡顿：`onTap` 先 `selectAt()` → `currentReaderPopupFrames` 非空则直接 `onReaderTapOutside()`。
- AI 卡片由 `popup.js` 的 `createAdvancedAiCard` 渲染（默认 `data-collapsed="true"`）；popup 固定 `readerSettings.popupHeight` 窗口内部滚动，不靠 iframe 上报高度；单测 `popup.test.mjs`。
- VN 外观分层：页面背景色/背景图在 `html, body`；`.hoshi-vn-stage`/`.hoshi-vn-screen` 只做布局必须透明；文字底板才用 `.hoshi-vn-content`。
- VN 跨屏 key 必须来自源文档结构：`reader-visual-novel.js` 的 `translationTargetKeyForElement`（章内 raw offset + 结构层级 + tag → `vn-<raw>-<depth>-<tag>`），`reader-translation.js` 的 `stableTargetId` 优先用它；译文缓存键 `"$bookId:${loadPosition.index}"` + id 同章跨屏复用 → 屏内序号会撞号（回归测试「translation target ids stay unique across screens」）。durable：VN 任何跨屏复用 key（译文缓存、`lastReadAloudParagraphId`、书签）都不能用屏内序号 id。

## 朗读 ReadAloud
- 引擎 System（`TextToSpeech`，默认）/ Local（sherpa-onnx Supertonic，`extra = mapOf("lang" to "ja", "speed" to speechRate)`）。第三方 TTS（MultiTTS）不吃 `setSpeechRate()` 也不吃 per-utterance `"rate"`，语速只能在该引擎 App 里设。
- 换引擎即时生效：init 收集器记 prev，`isActive && isPlaying` 且引擎相关变化 → cancel + `stop()` + `playFrom(currentIndex)`；语速变化不重启。
- 队列：`collectVisibleTargets()` → `ReadAloudSentences.split` 切句；`extractTargetText` 必须保留标点（否则无法切句），正文判定用 `hasMatchableText()`；三种模式模板都含 `__HOSHI_READER_TRANSLATION_SCRIPT__` 占位符。
- VN：chapter 源文档被 `detachChapterSource()` 移出，body 只有当前 screen clone → 可见段落 = 当前一屏。
- VN reveal 与高亮互斥：`prepareTextNodeForReveal` 用 `[data-hoshi-visual-novel-unrevealed]` 保存未显示文本并重写 `textContent`；高亮会切开文本节点致重复字符 → 包裹高亮前先 `completeCurrentReveal()`，遍历跳过 unrevealed，清理高亮必须 unwrap span。
- `ReadAloudSettings.highlightWhilePlaying`（默认 true）：JS `highlightReadAloudTarget/Sentence` 末参 `highlightVisible=false` 时仍 reveal + 滚动但不加 class；UI 在 `ReadAloudSettingsSheet`。

## 翻译
- 译文样式：`ReaderSettings.translationColor`（`0x00000000` = 跟随正文）+ `translationOpacity`（0.3–1.0，默认 0.72）→ CSS 变量 `--hoshi-translation-color/opacity`（`ReaderContentStyles` 填占位符、`ReaderChapterWebView` 的 appearanceUpdateKey 运行时更新）。新增设置必须同步三处持久化（SharedPreferences / DataStore / `ProfileReaderAppearanceSettings`）+ `ReaderSettings` 字段。
- 跟读翻译（`ReadAloudSettings.translateCurrentSentence`）：块 `hoshi-reader-translation hoshi-read-aloud-translation`（`data-hoshi-read-aloud-translation-for`），同时只留一块；缓存键 `章::段落id::句文本`，AI 返回后用 `readAloudViewModel.state.value` 复核仍是当前句（effect 捕获的 `readAloudState` 是启动快照）。同段只留一份译文：见已有 `data-hoshi-translation-for` 块就复用（隐藏则 reveal），`applyTranslation` 先移除同 target 跟读块。
- 未翻译兜底重翻：整页翻译按假名占比 ≥ 0.5 判为未翻译 → 强制简中提示词重翻，覆盖缓存与显示；开关 `readerAiTranslationFallbackEnabled`（默认 false），仅整页。

## LunaTranslator（跨项目）
- 未翻译兜底重翻：`translator/basetranslator.py` + `LunaTranslator.py` + `gui/setting/translate.py`；键 `untranslated_refallback`(bool)、`untranslated_refallback_engine`(str)、`untranslated_refallback_threshold`(0.5)，备用引擎须 `use=true`。
- Steam Deck 闪退：`gui/translatorUI.py` 的 `mousetransparent_check` 被 `@threader` 放子线程却调 Qt GUI API → Xwayland 崩溃；Linux 原生直接 return。
- Linux 截图 HiDPI：`spectacle -b -o` 输出物理像素、OCR 区域是 Qt 逻辑坐标 → `NativeUtils._grab_with_qscreen` 乘 DPR 裁剪，`gui/rangeselect.py` 转回逻辑坐标。

## Anki（跨项目）
- launcher 版判别：安装目录含 `uv.exe`/`pyproject.toml`/`.python-version`，且无 `uv.lock`/`.venv`。
- launcher 版（25.07–25.09.4）无法内置升级到 26.05+ → 必报 `anki-release==26.9.2` 无解（该包从不上传 26.09 系列到 PyPI，最新只到 26.5）。升级只能：卸载 → 清残留安装目录 → 官网/GitHub 重装（`anki-26.09.2-win-x64.msi`），`%APPDATA%\Anki2` 保留但仍建议备份。
- 本机 `UV_CACHE_DIR=D:\Program_Files\packages\uv_cache`、`PIP_CACHE_DIR=...\pip_cache`。已知坑：26.09.2 启动崩溃（`charset_normalizer.md`、`rsbridge and anki build hashes do not match` = 新旧 launcher 残留混用）。
