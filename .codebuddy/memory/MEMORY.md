# MEMORY

## 构建 / 工具链（durable）
- 环境：SDK `D:\Program_Files\Android\Sdk`；`JAVA_HOME=D:\Program Files\Android\Android Studio\jbr`；cargo `D:\AndroidCache\.cargo\bin\cargo.exe`。`app/build.gradle.kts:13` 用 `System.getenv("HOME")+"/.cargo/bin/cargo"` → 新 shell 先 `$env:HOME='D:\AndroidCache'`。构建：`cd d:\My_Project\Hoshi-Reader-Android; .\gradlew.bat :app:assembleDebug --no-daemon`。勿给 gradlew 传 `-Dorg.gradle.java.home=`（PS 当 task）。
- CMake 不被 AGP 自动下载：手工解压 `cmake-<ver>-windows.zip` 到 `Sdk\cmake\<ver>\`。
- `app/libs/sherpa-onnx-1.13.8.aar` 不在 git，需手工下载（k2-fsa/sherpa-onnx v1.13.8，直连超时走 `curl -L -x http://127.0.0.1:7897`）。文件名须与 `ReadAloudModels.kt` 的 `TtsModelConfig` 一一对应。
- 设备：E-ink `S101022210032`、Meizu `381QYGEM226CZ`；debug 包名 `moe.antimony.hoshi.debug`。adb 枚举不到 → `$env:ADB_TRACE='usb'; adb devices`。
- 一键构建+安装：`.\scripts\install.ps1`（`-Variant Release`/`-Serial`/`-SkipBuild`/`-NoLaunch`/`-AllowDowngrade`）。脚本必须带 BOM 的 UTF-8（否则 PS5.1 报 ParserError）。
- git 仅 origin（fork `Ghoooooostie/Hoshi-Reader-Android`）；对齐上游 `git -c http.proxy= fetch --no-tags https://github.com/HuangAntimony/Hoshi-Reader-Android.git main`；全局代理 `127.0.0.1:7897`。
- 工作区外临时构建：`git archive` 到 `D:\AndroidCache\tmp\...` + 复制 `local.properties` + robocopy `third_party/hoshidicts-kotlin-bridge`（否则 `configureCMakeDebug` 失败）。
- **编译验证陷阱**：`:app:compileDebugKotlin` 增量编译可能漏报主源码错误 → 改完再跑 `:app:compileDebugUnitTestKotlin` 交叉验证。
- `androidx.media:media` 1.7.0 无 `MediaSessionCompat`；用框架原生 `MediaSession` + `Notification.MediaStyle` + `AudioFocusRequest`。

## WebView JS 兼容与诊断（durable）
- E-ink WebView ≈ Chrome 80-84：`hoshi-web/**` 与 Kotlin 模板生成的 JS 不得用 `??=`/`||=`/`&&=`、`replaceAll`、`Array.at`、`structuredClone`。
- Android WebView 的 Text 节点没有 `getBoundingClientRect()`（只有 Element/Range）→ `document.createRange(); range.selectNodeContents(node)`。缺失曾致 calculateProgress() TypeError → 进度/书签/常驻翻译全停（e92a195）。
- `evaluateJavascript` 返回 null = JS 抛异常或结果 NaN；诊断要 try/catch + typeof/String(v)，记录「回调触发/守卫通过/原始返回值」三层。
- `Log.d` 在 Meizu/Flyme 被过滤 → 用 `Log.w`/`Log.e`；`console.log` 不进 logcat，诊断只能把信息塞进 evaluateJavascript 返回值。
- 改 reader JS 后验证：`node app/src/test/js/reader-*.test.mjs` → 确认资产进 APK → 装机 force-stop 冷启动。
- `:app:testDebugUnitTest` 本工作区跑不起来（`HoshiDictsCompatibilityTest.kt` 与本地词典 AAR API 不匹配，预存问题）；JS 测试不受影响。
- 自动化：翻页 `adb shell input swipe 900 1100 150 1100 180`；截图 `adb exec-out screencap -p`（纯白 ~31KB）；VN 双击 = 两次 `input tap`；截图 486x1080 ↔ 设备 1080x2400；CSS 坐标 = 设备 px / 2.8125。

## Android UI 诊断（durable）
- `dumpsys activity <pkg>` 含 View Hierarchy；界面正常但截图全白优先怀疑残留覆盖 View（DecorView 直接子 View 出现无 id 全屏 `android.view.View`）。
- `enableEdgeToEdge()` 在 DecorView 加 `SystemBarStateMonitor$1` + `ProtectionLayout`（4 条边），正常。
- Material3 当前版本无 `Surface(onClick=...)`：可点击卡片用非点击 `Surface` + `Modifier.clickable`。有声书（听书）= `sasayaki`。

## 阅读器手势（durable）
- 句子手势（长按/双击 `ReaderGestureAction.SentenceAction`，UI 名「朗读」）**只朗读**：`ReaderChapterWebView.runSentenceAction()` 直接 `onReadAloudStartFromPoint(x, y)`，不再弹句子翻译/AI 卡（该链路 `startSentenceSelection`/`onSentenceLongPressed`/`handleSentenceLongPressed` 已删）。整页翻译开启时仍保留译文块手势：长按译文块重翻、长按原文段展开译文（否则「长按显示」模式失效）。
- 朗读设置里不再有「从句子手势开始朗读」开关（手势本身即朗读）。
- 阅读器里不再有「默认长按模式」设置与弹窗整句 AI 翻译/分析卡（长按模式枚举、popup 模式切换、`requestReaderPopupSentenceAi`、`readerAiPopupModes` 均已删）；弹窗 payload 统一走 `LookupPopupAdvancedAiState.toPayload`。保留：`AdvancedAiCardKind.Word`（选词弹窗词语分析）与 `AdvancedAiCardKind.Sentence`（ProcessText 分享查词）。

## 阅读器交互（durable）
- 长按选词走 WebView 原生文字选择，不自实现 selectText+拖动（与 SwipePageTouchListener 冲突）；原生 ActionMode 激活时 `shouldIgnoreReaderGestureEvent` 忽略翻页/单击。
- 朗读播放时点空白关闭查词弹窗卡顿：根因 `ReaderChapterWebView.kt` 的 `onTap` 先跑 `selectAt()`；修复：弹窗已开（`currentReaderPopupFrames` 非空）时直接 `onReaderTapOutside()`。
- 查词弹窗 AI 分析卡片由 `hoshi-web/popup/popup.js` 的 `createAdvancedAiCard` 渲染（默认折叠，`data-collapsed="true"`，header `advanced-ai-card-header` + toggle `advanced-ai-card-toggle`）；popup 固定 `readerSettings.popupHeight` 窗口、内部滚动，不靠 iframe 上报 contentHeight；单测 `popup.test.mjs`。
- `popup.js` 的 `hoshiPopupGeometry` 是 CSS `zoom` 下 popup 坐标换算唯一入口；不得混用 `offsetTop` 与 `scrollTop`/`getBoundingClientRect()`，不得另写缩放补偿。
- VN 外观分层：页面背景色 + 背景图都在 `html, body`；`.hoshi-vn-stage`/`.hoshi-vn-screen` 必须透明只做布局；文字底板才用 `.hoshi-vn-content`。设置项「VN 页面背景色」vs「VN 文字底板 / 颜色」，仅 `viewMode == VisualNovel` 显示。
- **VN 跨屏 key 必须来自源文档结构位置**：`reader-visual-novel.js` 的 `translationTargetKeyForElement`（首个正文文本节点章内 raw offset + 结构层级 + tagName → `vn-<raw>-<depth>-<tag>`），`reader-translation.js` 的 `stableTargetId` 优先用它。译文缓存键是 `"$bookId:${loadPosition.index}"` + id，同章跨屏复用 → 屏内序号会撞号。回归测试 `reader-visual-novel.test.mjs`「translation target ids stay unique across screens」。

## 朗读 ReadAloud（durable）
- 引擎 System（`TextToSpeech`，默认）/ Local（sherpa-onnx Supertonic）；本地引擎生成传 `extra = mapOf("lang" to "ja", "speed" to speechRate)`。
- 第三方系统 TTS（MultiTTS）不吃 `setSpeechRate()` 也不吃 per-utterance `"rate"`：无应用层解法，语速去该引擎 App 设，不要反复尝试。
- 换引擎即时生效：init 收集器记录 prev，`isActive && isPlaying` 且引擎相关变化时 cancel + `activeEngine.stop()` + `playFrom(currentIndex)`；语速变化不重启。
- 队列：`collectVisibleTargets()` → `ReadAloudSentences.split` 切句；`__HOSHI_READER_TRANSLATION_SCRIPT__` 占位符必须在三种模式模板齐全。`extractTargetText` 必须保留标点（否则无法切句），"是否正文"用 `hasMatchableText()`。
- VN：chapter 源文档被 `detachChapterSource()` 移出，body 只有当前 screen clone → 可见段落 = 当前一屏。
- 媒体会话：框架原生 `MediaSession` + `Notification.MediaStyle`；`mediaButtonPerNext` 映射媒体键到段落。
- **VN reveal 与朗读高亮互斥**：`prepareTextNodeForReveal` 用 `[data-hoshi-visual-novel-unrevealed]` 保存未显示文本并重写 `segment.visible.textContent`；高亮切开文本节点会导致重复字符。约定：包裹高亮前先 `completeCurrentReveal()`，文本遍历跳过 unrevealed，清理高亮必须 unwrap span。
- 播放高亮开关 `ReadAloudSettings.highlightWhilePlaying`（默认 true）：JS `highlightReadAloudTarget/Sentence` 末尾参数 `highlightVisible` 为 false 时仍 reveal + 滚动但不加 class；UI 在 `ReadAloudSettingsSheet`。

## 翻译（durable）
- 译文样式：`ReaderSettings.translationColor`（`0x00000000`=跟随正文，沿用"alpha 0 表示未设置"约定）+ `translationOpacity`（0.3–1.0，默认 0.72）；经 CSS 变量 `--hoshi-translation-color/opacity` 生效，`ReaderContentStyles` 填占位符、`ReaderChapterWebView` 的 appearanceUpdateKey 运行时更新。新增设置必须同时改三处持久化（SharedPreferences / DataStore / `ProfileReaderAppearanceSettings`）+ `ReaderSettings` 字段。UI 在 `ReaderTranslationAiSheet` 样式组。
- 跟读翻译（`ReadAloudSettings.translateCurrentSentence`）：朗读到哪句翻译哪句，译文块 `hoshi-reader-translation hoshi-read-aloud-translation`（属性 `data-hoshi-read-aloud-translation-for`），同一时刻只留一块；Kotlin 侧缓存键 `章::段落id::句文本`，AI 返回后要用 `readAloudViewModel.state.value` 复核仍是当前句（effect 里捕获的 `readAloudState` 是启动快照）。**同段只留一份译文**：`showReadAloudTranslation` 见已有 `data-hoshi-translation-for` 块就复用（隐藏则 reveal），`applyTranslation` 先移除同 target 跟读块，否则手势译文 + 跟读块会叠两份。
- 未翻译兜底重翻（2026-09-21）：整页翻译队列每段按假名占比 ≥ 0.5 判为未翻译 → 用强制简中提示词重翻，覆盖缓存与显示。开关 `readerAiTranslationFallbackEnabled`（默认 false）。仅整页，长按句子翻译未加。

## LunaTranslator（durable，跨项目）
- 未翻译兜底重翻：`translator/basetranslator.py` + `LunaTranslator.py` + `gui/setting/translate.py`；配置键 `untranslated_refallback`(bool)、`untranslated_refallback_engine`(str)、`untranslated_refallback_threshold`(0.5)；备用引擎须 `use=true`。
- Steam Deck 闪退：`gui/translatorUI.py` 的 `mousetransparent_check` 被 `@threader` 放子线程却调 Qt GUI API → Xwayland 崩溃；Linux 原生直接 return（`windows.MouseTrans` 本就是空实现）。
- Linux 截图 HiDPI：`spectacle -b -o` 输出物理像素，OCR 区域是 Qt 逻辑坐标 → `NativeUtils._grab_with_qscreen` 乘 DPR 裁剪，`gui/rangeselect.py` 转回逻辑坐标。

## Anki（durable，跨项目）
- 判别 launcher 版：安装目录（`D:\Program_Files\Anki`）含 `uv.exe` / `pyproject.toml` / `.python-version` / `versions.py`，且无 `uv.lock`、`.venv`（每次启动现解析）。
- **launcher 版（25.07–25.09.4）的内置升级不能升到 26.05+**（官方公告：打包方式变更），必然报 `uv sync --upgrade --no-config --managed-python --python 3.13.5` → `No solution found ... there is no version of anki-release==26.9.2`。不是本机/代理/缓存问题：直连 PyPI 查 `https://pypi.org/pypi/anki-release/json`，`anki-release` 版本序列最新只到 26.5（26.09 系列从不上传 PyPI）。
- 正确升级路径：Windows 设置卸载 Anki（或 `uninstall.exe`）→ 手动删残留安装目录 → 从 https://apps.ankiweb.net/ 或 GitHub release（`anki-26.09.2-win-x64.msi`）重新安装。用户数据 `%APPDATA%\Anki2`（`账户 1` / `addons21` / `prefs21.db`）不受影响，但仍建议先备份。
- 本机相关环境变量：`UV_CACHE_DIR=D:\Program_Files\packages\uv_cache`、`PIP_CACHE_DIR=D:\Program_Files\packages\pip_cache`。
- 已知坑：26.09.2 有启动崩溃反馈（`AttributeError: module 'charset_normalizer.md'`、`rsbridge and anki build hashes do not match`，后者是新旧 launcher 残留混用）。
