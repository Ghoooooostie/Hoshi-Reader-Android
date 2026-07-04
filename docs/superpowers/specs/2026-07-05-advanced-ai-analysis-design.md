# Advanced AI Analysis Design

**Date:** 2026-07-05

## Goal

在 Hoshi Reader Android 里新增一组受“高级 AI”开关控制的阅读辅助能力：

- 点词查词弹层自动请求 `AI 词语分析`，并显示在普通词典结果上方。
- Android 长选入口默认进入 `长难句分析` 视图，分析内容可滚动，底部操作条固定；现有查词内容继续保留在分析内容下方。
- 关闭“高级 AI”后，应用保持现在行为，不做额外请求，也不显示新增 AI 区块。

## Chosen Approach

采用一个共享的 `Advanced AI` 配置与请求层，同时接到两个现有入口里：

1. 设置侧复用现有 `AdvancedSettingsView`，新增一个 `Advanced AI` 子页面，不再额外开新的顶层设置入口。
2. 查词侧复用现有 `LookupPopupHtml` / iframe 弹层体系，在 root popup 渲染顶部 AI 卡片。
3. 长选侧复用现有 `ProcessTextLookupActivity` 和其 iframe host，不改底部动作条结构，只在内容区顶部插入长难句分析卡。

这样做的原因：

- 改动面最小，能直接挂在现在已经稳定的弹层和 overlay 基础设施上。
- 两种 AI 能力共用同一套 API 配置、请求超时、错误态和开关逻辑，后面维护成本更低。
- 不需要重做现有词典结果和长选底部工具条，只是在正确位置增加 AI 内容。

## Alternatives Considered

### 方案 A：自动请求并直接插入结果（采用）

- 优点：最符合本次确认的交互，用户点词后立刻看到“这个词在句子里的作用”。
- 缺点：会比纯查词多一次网络请求，需要做好加载和失败态。

### 方案 B：只放一个“AI 分析”按钮，点击后再请求

- 优点：更省请求次数。
- 缺点：多一步操作，不符合本次已确认的目标。

### 方案 C：把 AI 结果做成独立页面，不嵌入现有弹层

- 优点：技术上更独立。
- 缺点：割裂查词流程，也会破坏现在用户已经熟悉的弹层使用方式。

## User Experience

## 1. 高级 AI 设置

在现有 `高级` 页里新增一个 `Advanced AI` 行，进入后展示：

- 总开关：启用高级 AI。
- API Base URL
- API Key
- Model
- 词语分析 Prompt
- 长难句分析 Prompt
- 测试 API 按钮

行为约束：

- 总开关关闭时，不显示新的 AI 分析结果，也不触发 AI 请求。
- 总开关打开但配置不完整时，不触发请求；界面显示简短缺省提示，不做兜底猜测。
- Prompt 默认值提供两套：
  - 词语分析：要求解释该词在当前句子里的词性、语法作用、语气或搭配作用。
  - 长难句分析：要求拆句子结构、解释重点成分，并给出自然中文理解。

## 2. 查词弹层中的 AI 词语分析

触发条件：

- 仅在“高级 AI”总开关开启且配置完整时生效。
- 用户在 Reader 里点词，或在查词页的 popup 里递归点词时生效。

显示顺序：

1. 顶部词头与现有音频 / Anki 操作区不变。
2. 紧接着显示一张 `AI 词语分析` 卡片。
3. 下面继续显示现有普通词典结果。

卡片内容：

- 标题：`AI 词语分析`
- 正文：解释该词在当前句子中的作用，而不是只给字典释义。
- 加载中：显示简短 loading 文案，占位高度尽量稳定，避免弹层跳动太大。
- 失败时：显示一行简短失败提示，词典结果照常显示。

请求内容必须至少包含：

- 当前选中的词
- 当前所在句子
- 词在句子中的偏移或上下文信息

这样模型才能回答“在这句话里起什么作用”，而不是泛泛解释词义。

## 3. 长选后的长难句分析

触发条件：

- 仅在“高级 AI”总开关开启时，Android 长选入口默认先显示长难句分析。
- 关闭时保持现在的 `ProcessTextLookupActivity` 行为。

布局规则：

- 顶部主内容区显示 `长难句分析` 结果。
- 该内容区独立可滚动。
- 底部现有操作条保持固定，不随正文滚动。
- 现有查词内容保留在长难句分析内容下方，作为同一滚动区里的后续内容，而不是被删除。

展示顺序：

1. 标题 `长难句分析`
2. AI 分析正文
3. 现有词典 / 查词内容
4. 底部固定操作条

失败处理：

- AI 失败时，不关闭页面。
- 直接显示查词内容，并在上方保留一条简短失败提示。

## Shared AI Layer

新增一个共享的高级 AI 模块，职责如下：

- 持久化配置
- 统一判断“是否启用且可请求”
- 构造兼容 OpenAI chat completions 的请求
- 提供两个明确入口：
  - `analyzeWordInSentence(...)`
  - `analyzeSentence(...)`

数据模型至少需要区分：

- `AdvancedAiSettings`
- `AdvancedAiAvailability`
- `AdvancedAiWordAnalysisResult`
- `AdvancedAiSentenceAnalysisResult`

约束：

- 不把词语分析和长难句分析混成一个 prompt 字段。
- 不对失败结果做模糊后处理或假结果填充。
- 所有用户可见文案都走 strings 资源。

## Integration Points

## 设置页

- `AdvancedSettingsView`
- 新的 AI 详情页 composable
- Hilt / repository 注入入口

## 查词页与 Reader popup

- `DictionarySearchView`
- `ReaderWebView`
- `LookupPopupHtml`
- popup iframe / JS 渲染层

需要在 popup payload 里补充 AI 分析状态，让 root popup 和 child popup 都能各自拿到自己的 AI 结果。

## 长选页

- `ProcessTextLookupActivity`
- 对应 popup state / iframe payload

长选页需要增加一个“AI 分析块 + 词典内容”的组合渲染能力，而不是只渲染词典结果。

## Data Flow

### 点词

1. 用户点词，现有 lookup 先产生 popup。
2. popup 立即先显示现有词典框架与 loading 占位。
3. Kotlin 侧根据 `selection.text + selection.sentence` 发起 `analyzeWordInSentence`.
4. 结果回填到对应 popup 的 state。
5. iframe 重新同步，顶部 AI 卡片从 loading 切成正文或失败提示。

### 长选

1. Android PROCESS_TEXT 入口收到整句文本。
2. 若高级 AI 可用，则先发起 `analyzeSentence`.
3. 页面先展示可滚动分析区框架与底部固定按钮。
4. AI 返回后更新分析区正文；词典内容仍正常加载并排在后面。
5. 若 AI 失败，则分析区展示错误提示，词典内容继续可用。

## Error Handling

- 配置缺失：不发请求，显示配置未完成提示。
- HTTP / 解析失败：只展示简短失败文案，不影响原有词典结果和页面可操作性。
- 超时：按失败处理，不做重试风暴。
- popup 关闭或切换到别的词后，旧请求结果不得串到新 popup 上。
- 长选页面关闭后，晚到的结果不得再尝试更新已销毁页面。

## Testing

必须覆盖下面这些行为：

- 高级 AI 开关关闭时：
  - 点词不触发 AI 请求
  - 长选不切到 AI 分析模式
- 高级 AI 开关打开且配置完整时：
  - 点词会自动请求并在词典上方展示结果
  - 递归子 popup 也能各自展示对应 AI 分析
  - 长选先展示长难句分析，再展示原有查词内容
- 失败态：
  - popup AI 失败不影响词典结果
  - 长选 AI 失败不影响底部固定条和后续查词内容
- 文案资源：
  - 新增字符串通过本地化测试
- 请求构造：
  - Base URL、model、prompt 替换、正文解析都要有单测

建议验证层级：

- JVM 单元测试：settings、request builder、view model state、popup state 合成逻辑。
- JS 测试：popup HTML / JS 新增 AI 卡片渲染与状态切换。
- 构建验证：`./gradlew test`、`./gradlew assembleDebug`，如改了资源再跑 `./gradlew lint`。

## Out Of Scope

- 不做流式输出。
- 不做多 provider 适配，先只支持 OpenAI 兼容 chat completions。
- 不改现有 Anki 流程。
- 不改现有底部操作条按钮语义。

## Open Risks

- `reference/Hoshi-Reader-iOS` 当前本地子模块不可直接读取，AI 交互这次只能以 Android 现有结构和本次确认的行为为准。
- popup 是 iframe + WebView 体系，状态同步必须防止旧请求回填到错误 popup。
