# Reader Translation AI Design

**Date:** 2026-07-05

## Goal

在 Hoshi Reader Android 的阅读页新增一组 `翻译（AI）` 能力，同时保持当前阅读结构稳定：

- 右下角菜单新增 `翻译（AI）` 入口。
- 打开后使用和 `外观 / 统计 / 有声书` 一样的底部面板样式。
- 支持“全文翻译”：当前页原文下方按段落插入中文译文，翻到下一页后再开始翻译下一页。
- 支持“长按 AI 卡片”：长按后只显示 AI 内容，不再显示词典区，并可在 `整句翻译 / 长难句分析` 间切换。
- 复用现有 `高级 AI` 连接配置，不在阅读器面板里重复放 API 配置。

## Chosen Approach

采用“现有高级 AI 配置 + 阅读器内行为面板 + WebView 页内附加显示层”的方案：

1. `高级 AI` 页面继续负责 `Base URL / API Key / Model / Prompt` 配置与连通性测试。
2. 阅读器右下角菜单新增 `翻译（AI）`，打开一个和现有 Reader sheets 同风格的底部面板。
3. 全文翻译只在 `Paginated / Continuous` 两种模式下工作，通过 WebView 当前页 DOM 识别段落、顺序请求翻译、在原文下方插入译文块。
4. 长按入口不再走词典 popup，而是走一个新的 `AI 专用卡片` 路径；卡片顶部提供 `整句翻译 / 长难句分析` 切换。
5. `VN` 模式首版只支持长按 AI 卡片，不支持全文逐段翻译。

这样做的原因：

- 用户已经确认面板必须和 `外观 / 统计` 一样，继续复用 Reader sheet 结构最稳。
- `Paginated / Continuous` 的正文 DOM 比较直接，适合插入附加译文；`VN` 不是简单整页 DOM，硬接风险高。
- 长按 AI 卡片与全文翻译是两类不同路径：一个是整页显示层，一个是单句 AI 结果卡，分开建模更稳。
- 继续保留 `高级 AI` 配置入口，避免把连接配置、阅读行为、临时结果混在同一个面板里。

## Alternatives Considered

### 方案 A：阅读器内独立行为面板 + 页内附加译文层（采用）

- 优点：入口清楚，和用户确认的交互最一致，现有设置和阅读行为职责清楚。
- 缺点：需要同时改 Reader 菜单、sheet、WebView JS、长按路径和测试。

### 方案 B：只复用现有高级 AI 设置页，不新增阅读器面板

- 优点：开发面更小。
- 缺点：阅读时要跳出当前上下文改设置，和本次确认的体验不一致。

### 方案 C：把全文翻译做成单独阅读模式

- 优点：从结构上最独立。
- 缺点：会改变当前阅读路径，也会把普通阅读和翻译阅读拆成两套体验，超出本次范围。

## User Experience

### 1. 阅读器菜单与底部面板

- 右下角菜单新增 `翻译（AI）` 项。
- 点开后使用统一 Reader bottom sheet 样式：
  - 顶部拖拽条。
  - 与现有 sheet 一致的容器、圆角和分组卡片。
  - 不使用右侧浮层或独立弹窗。

面板仅包含三组：

- `全文翻译设置组`
- `长按配置`
- `样式组`

### 2. 全文翻译设置组

显示内容：

- `全文翻译` 开关。
- 说明文案：`当前页原文下方按段落显示译文`。
- 说明文案：`翻到下一页后，再开始翻译下一页`。

行为约束：

- 开启后，仅翻译当前显示页。
- 翻页或跳到下一页后，旧页请求立即失效，新页重新开始。
- 不做“停留几分钟后预翻下一页”。
- 关闭开关后，当前页译文立即从显示层移除。

### 3. 样式组

首版只做自动样式，不暴露手填颜色配置：

- 译文颜色默认比正文更浅一点。
- 明暗主题都跟随当前 Reader 文字色自动推导。
- 不新增“自定义译文颜色”编辑器。

### 4. 长按配置

持久化设置只保存一个默认模式：

- `整句翻译`
- `长难句分析`

行为：

- 默认模式决定用户长按后第一次看到的 AI 卡片内容。
- 这个默认值保存在 Reader settings 里，而不是高级 AI 配置里。

### 5. 长按 AI 卡片

长按后不再显示词典 popup，而是显示一个 `AI 专用卡片`：

- 顶部 segmented toggle：
  - `整句翻译`
  - `长难句分析`
- 主体只显示对应 AI 结果。
- 不显示词典、词频、发音、Anki、词典递归跳转等现有 popup 内容。

切换规则：

- 默认落在全局设置的长按默认模式。
- 用户在卡片顶部切换时，只刷新当前卡片内容。
- 这个切换不回写全局设置。

失败规则：

- 任一模式失败时，只显示明确失败状态。
- 不做兜底假内容。
- 不退回词典内容。

## Scope Boundary

### 首版支持

- `Paginated` 全文逐段翻译
- `Continuous` 全文逐段翻译
- 所有模式下的长按 `整句翻译 / 长难句分析`
- Reader menu 新入口与同款底部面板

### 首版不支持

- `VisualNovel` 全文逐段翻译
- 预翻下一页
- 流式输出
- 译文结果落盘或同步
- 词典与 AI 结果混排的混合卡片

### 为什么 `VN` 不支持全文逐段翻译

`VN` 模式不是直接把当前页正文原样显示出来，而是自己控制：

- 分屏
- 逐句 reveal
- 点击推进
- source-to-screen range map
- 高亮与定位映射

如果把“原文下方插译文”直接插进 `VN` 当前结构，最容易破坏：

- reveal 节奏
- 当前屏布局
- 点击推进边界
- 高亮和定位映射
- 恢复位置与阅读进度

所以首版把边界收在 `VN 不接全文逐段翻译，只保留长按 AI 卡片`。

## Architecture

### Shared AI Configuration Layer

继续复用 `features/advancedai`：

- `AdvancedAiSettingsRepository`
- `AdvancedAiSettings`
- `AdvancedAiAvailability`
- `AdvancedAiClient`

现有能力保留：

- `translateSentence(...)`
- `analyzeSentence(...)`

新增约束：

- Reader 全文翻译只调用句子翻译 prompt，不复用长难句分析 prompt。
- 阅读器行为设置不放进 `AdvancedAiSettings`，避免把 provider 配置和阅读行为耦合。

### Reader Settings Layer

在 Reader settings 里新增持久化字段，负责阅读行为：

- `readerAiFullPageTranslationEnabled: Boolean`
- `readerAiLongPressMode: ReaderAiLongPressMode`

新增枚举：

- `ReaderAiLongPressMode.Translation`
- `ReaderAiLongPressMode.Analysis`

这些值和现有 Reader sheet 设置一样，走 Reader settings repository 持久化。

### Reader Runtime State

在 Reader 会话态里新增临时状态：

- `showTranslationAiSheet`
- 当前页译文结果集合
- 当前页译文加载中 / 失败状态
- 当前 AI 卡片模式与对应请求版本号

这些状态只存在当前 Reader 会话，不写入书籍 sidecar。

### WebView Translation Overlay Layer

全文翻译不修改原始 EPUB 内容，也不改进度算法。实现方式是：

1. WebView 读取当前页可见段落。
2. Kotlin 顺序请求翻译。
3. WebView 在原文段落下方插入附加译文块。
4. 翻页、切章、关闭开关时清空这一层。

关键要求：

- 译文块必须是附加显示层，不参与现有原文字符计数。
- 译文块不能污染现有点击选句、分页进度、跳转位置算法。

## Data Flow

### 全文翻译

1. 用户在 Reader 里打开 `翻译（AI）` 面板并开启全文翻译。
2. Kotlin 先检查 `Advanced AI` 配置是否可用。
3. 当前模式若是 `Paginated / Continuous`，WebView 返回当前页可翻译段落列表。
4. Kotlin 依段落顺序逐个调用 `translateSentence(...)`。
5. 每段结果回写到对应段落的附加译文块。
6. 若用户翻页、切章、关闭开关，当前页请求版本立即失效，旧结果不能再回填。

### 长按 AI 卡片

1. 用户长按句子。
2. Reader 继续复用现有句子选择链路拿到 `ReaderSelectionData`。
3. 根据全局默认模式，打开 `AI 专用卡片`。
4. 若默认模式是 `整句翻译`，调用 `translateSentence(sentence)`。
5. 若默认模式是 `长难句分析`，调用 `analyzeSentence(sentence)`。
6. 用户点击卡片顶部切换按钮时，仅刷新当前卡片内容。

## Error Handling

- 高级 AI 未启用：不发请求。
- 高级 AI 配置缺失：Reader 面板显示简短不可用提示。
- `VN` 模式下打开全文翻译组：显示“当前模式暂不支持全文翻译”。
- 某一段翻译失败：只在该段显示失败状态，不影响其他段。
- 整张长按卡片失败：显示失败状态，不回退到词典。
- 关闭卡片、关闭全文翻译、翻页、切章后，旧请求结果不得串到新目标。

## UI Components

新增或修改的组件职责：

- Reader menu item：新增 `翻译（AI）`
- Reader bottom sheet：新增 `TranslationAiSheet`
- Reader AI card：新增 `ReaderAiResultCard`
- Reader translation overlay renderer：负责把当前页段落与译文状态同步到 WebView

不复用当前词典 popup 的原因：

- 当前 popup 绑定的是词典结果、音频、Anki、递归 lookup。
- 本次长按确认的是纯 AI 内容卡，不需要这些结构。

## Testing

必须覆盖三类自动化测试：

### 1. Reader settings 持久化

- 全文翻译开关保存与恢复
- 长按默认模式保存与恢复

### 2. AI 卡片状态切换

- 长按默认翻译时，先请求翻译
- 长按默认分析时，先请求分析
- 顶部切换模式时，只刷新当前卡片
- 关闭卡片后旧结果不会回填

### 3. 全文翻译页内逻辑

- `Paginated` 当前页段落顺序插入译文
- `Continuous` 当前页段落顺序插入译文
- 翻页后才开始翻译新页
- 关闭开关后译文层清空
- `VN` 模式显示不支持提示，不发全文翻译请求

手工验证至少覆盖：

- 菜单打开 `翻译（AI）` 面板
- 面板样式与 `外观 / 统计` 一致
- 全文翻译开启后当前页逐段显示译文
- 翻页后才开始翻译下一页
- 长按 AI 卡片顶部切换 `整句翻译 / 长难句分析`
- 两种模式都不显示词典区
- 深色模式下译文颜色仍比正文更浅

完成前命令：

- `./gradlew test`
- `./gradlew assembleDebug`
- 有可用设备时优先 `./gradlew.bat :app:installDebug`

## Out Of Scope

- `VN` 全文逐段翻译
- 预翻下一页
- 流式翻译结果
- 译文缓存落盘
- 译文同步到备份 / 云端
- 长按 AI 卡片里的词典回退入口

## Risks

- 页内附加译文层如果处理不好，会影响现有分页和点击命中；必须把它当作附加显示层，而不是修改正文结构。
- `Paginated` 与 `Continuous` 的可见段落识别逻辑不完全相同，JS helper 需要按模式验证。
- 长按入口从“词典 popup”分出“纯 AI 卡片”后，要避免破坏现有 highlight 和 selection 清理流程。
