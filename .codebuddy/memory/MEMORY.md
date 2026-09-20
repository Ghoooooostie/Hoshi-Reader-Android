# MEMORY

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
