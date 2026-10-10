# process-details 长行滚动显示方案

- **文档定位**：本文是 Minecraft 1.21.11 Fabric 客户端 mod "Process Details" 的 UI 方案，供仓库维护者与后续实施者使用，回答「超长详情行改为横向滚动显示」是否可行、怎么做、怎么验收。
- **适用范围与前提**：仅覆盖 mod 内三个加载画面共用的 `TextLayout` 文本布局。技术依据已对 Minecraft 1.21.11 源码（Mojang mappings）核实；仓库内代码描述基于当前 `src/` 树实际读到的内容。本文档只做方案设计，不含代码改动。
- **版本**：v1.0（2026-10-10）：初稿。

## 目录

1. 背景与现状
2. 可行性结论
3. 技术依据
4. 交互与视觉设计
5. 实施步骤
6. 风险与验证
7. 验收标准

## 1. 背景与现状

本节回答：现状是什么、问题出在哪。

### 1.1 涉及画面与代码

mod 在三个画面上绘制详情文本，全部经 `src/main/java/dev/processdetails/TextLayout.java` 排版与绘制：

| 画面 | 渲染入口 | 注入方式 | 排版调用 |
|---|---|---|---|
| 原版资源重载（Mojang 加载屏） | `reload/ReloadDetailsRenderer` | `LoadingOverlayMixin` 注入 `drawProgressBar` TAIL | `drawCenteredBlock` |
| 保存世界（"Saving world"） | `worldsave/WorldSaveDetailsRenderer` | `GenericMessageScreenMixin` 注入 `renderBackground` TAIL | `drawCenteredBlock` |
| Xaero's World Map 地图准备 | `xaero/LoadingScreenRenderer` | 替换 Xaero 原加载画面 | `drawLeftBlock` |

受影响的内容行：重载画面的阶段/百分比/耗时行、任务计数行（含等待名单 +N，`ReloadDetailsRenderer.buildPendingLine`）、GPU 详情（`GpuWarnlistStatus`）、colormap 链（`ColormapStatus`）；保存世界画面的主行与详情行；Xaero 画面的 phase 栈、键值对与调试旗标行。

### 1.2 现有折行与上移逻辑

`TextLayout.drawBlock` 的现行行为（`src/main/java/dev/processdetails/TextLayout.java`）：

1. 每行用 `font.split(整行, maxWidth)` 折成若干视觉行，块高度 = 各行折行数之和 × `font.lineHeight`；
2. 若块底部越出屏幕下缘（保留 `EDGE_MARGIN = 4` px），整块向上平移；仍放不下则钳到屏幕顶。

### 1.3 问题

中文等 CJK 文本没有空格断点、字形等宽，超宽的中文行会被 `font.split` 折成很多视觉行：块被撑高，触发整块上移，详情可能顶到屏幕上缘；一个语义单元被硬切到两行，扫读成本高。等待名单这类逗号分隔的长行同理。

## 2. 可行性结论

本节给出结论与关键依据，细节见第 3 节。

**结论：可行，建议实施。** 对超宽行改为「裁剪 + 横向滚动」的单行显示：

- 技术路径全部落在 `TextLayout.java` 内部，改动为百行级别，无需新增 mixin，不触碰现有注入点；
- 滚动偏移按 `Util.getMillis()` 每帧重算，`LoadingOverlayMixin` 已注入每帧必经的 `drawProgressBar` TAIL，动画自然驱动，无动画状态残留；
- 未打标记的行走原有 `font.split` 路径，现有行为零变化；
- 不确定点只有两个——scissor 在 `LoadingOverlay` 路径的实际表现、中文测宽精度——都可以用 `./gradlew runClient` 实测覆盖（见第 6 节）。

## 3. 技术依据

本节列出方案所依赖的已核实事实。以下四条均已对 Minecraft 1.21.11 源码（Mojang mappings）核实；仓库内行为已对照当前 `src/` 树核实。

1. **每帧重绘有保证**。`LoadingOverlay.render()` 每帧执行，非淡出阶段每帧调用 `drawProgressBar`（1.21.11 `LoadingOverlay.java` 121 行）；本 mod 的 `LoadingOverlayMixin` 注入其 TAIL（`src/main/java/dev/processdetails/mixin/LoadingOverlayMixin.java`）。因此按 `Util.getMillis()` 计算的滚动偏移每帧自然更新。淡出阶段 `drawProgressBar` 不再被调用，详情随进度条一起消失，不需要额外的淡出与清理逻辑。
2. **裁剪有现成设施与先例**。1.21.11 `GuiGraphics` 提供 `enableScissor(x1, y1, x2, y2)` / `disableScissor()`（`GuiGraphics.java` 167–176 行），内部走 scissor 栈并按当前 pose 自动换算；原版 `AbstractSelectionList.enableScissor`（`AbstractSelectionList.java` 244–245 行）正是用它裁剪列表内容。
3. **整行测宽可行**。`Font.width(FormattedCharSequence)`（`Font.java` 172 行）可精确测整行宽；`Component.getVisualOrderText()` 拿到带格式的视觉顺序序列；`TextLayout.drawBlock` 的左对齐分支已在用 `GuiGraphics.drawString(Font, FormattedCharSequence, …)` 重载，滚动行可直接复用该绘制路径。
4. **水平渐隐暂不做**。1.21.11 `fillGradient` 仅支持垂直渐变；滚动行左右边缘的渐隐需要自定义渲染管线（自绘几何与 shader），成本高，列为可选项，本期不做（见 4.4 节）。

仓库内佐证：`ReloadDetailsRenderer` 已在用 `Util.getMillis()` 做 elapsed 计时（`src/main/java/dev/processdetails/reload/ReloadDetailsRenderer.java`），说明该时间源在这条渲染路径上可直接使用。

## 4. 交互与视觉设计

本节定义新行为的完整规则：哪些行滚动、怎么量宽、怎么裁剪、怎么滚。

### 4.1 行标记：允许横向滚动

`TextLayout.Line`（record，组件为 `text` / `color` / `gapBefore`）增加一个可选标记「允许横向滚动」。示意如下，命名以实施为准：

```java
// 示意，非最终代码；另为 String 入参配一对便捷重载
public record Line(Component text, int color, int gapBefore, boolean scrollHorizontally) {
    public Line(Component text, int color) { this(text, color, 0, false); }
    public Line(Component text, int color, int gapBefore) { this(text, color, gapBefore, false); }
    public Line(String text, int color) { this(Component.literal(text), color, 0, false); }
    public Line gapBefore(int gap) { return new Line(text, color, gap, scrollHorizontally); }
    public static Line scrolling(Component text, int color) { return new Line(text, color, 0, true); }
}
```

- 不打标记的行保持现有 `font.split` 换行行为，一行不改；
- 建议首批打标对象（当前已知容易超宽的行，实施时可调）：重载画面等待名单行（`ReloadDetailsRenderer.buildPendingLine` 的 names + " +N"）、Xaero 画面带 `[detail]` 的 phase 行与 "region in view" 行（`LoadingScreenRenderer`）。

### 4.2 布局阶段

对标记行：

1. 用 `line.text().getVisualOrderText()` 取视觉序列，`font.width(序列)` 量整行宽；
2. 行宽 ≤ `maxWidth`：按普通行处理（不滚动，走现有路径）；
3. 行宽 > `maxWidth`：**不 split，高度按 1 行计**，记录溢出量 `overflow = 行宽 − maxWidth`。

比现行折行省下「折行数 − 1」行竖向空间；块整体变矮后，「整块上移」触发更少、幅度更小，与现有上移逻辑天然兼容。

### 4.3 绘制阶段与裁剪带

对实际超宽的标记行（下称滚动行）：

1. `enableScissor(带左, lineY, 带右, lineY + font.lineHeight)`——水平范围即该行的可用宽：居中块为 `[EDGE_MARGIN, guiWidth − EDGE_MARGIN]`，左对齐块为 `[x, guiWidth − EDGE_MARGIN]`；垂直范围夹住本行行框；
2. `drawString(font, 序列, baseX − offset, lineY, color)`——滚动行统一从裁剪带左缘起画，`baseX` 即裁剪带左界；
3. `disableScissor()`——与第 1 步严格成对。

设计决定：**滚动行表现为左对齐**。居中画一条超宽行会两侧同时溢出，滚动轨迹与静止帧位置都不可预期；按左对齐画，「停在行首」「停在行尾」才有明确含义。

### 4.4 滚动轨迹：三角波

偏移是当帧时间的纯函数，`TextLayout` 保持无状态：

- 周期 = 行首停顿（约 1.2 s）→ 向左滚到行尾可见（露出尾端 + 一格边距）→ 行尾停顿（约 1.2 s）→ 滚回行首，循环；
- 速度按溢出像素比例调整：建议 `滚动时长 = clamp(overflow / 基础速度, 下限, 上限)`，基础速度建议初值 24 px/s、上限 60 px/s；参数属可调项，实施时按观感定；
- 多条滚动行按行索引错开相位（建议每行错开约 0.4 s），避免齐步走。

时间源为 `Util.getMillis()`。画面结束（如重载淡出）时渲染器不再被调用，偏移自然停更，无需清理。

可选项：滚动行左右边缘的水平渐隐，用于替代硬边裁剪。因需要自定义渲染管线（见第 3 节第 4 条），成本高，本期不做。

### 4.5 三类行的行为对比

| 行类型 | 布局 | 绘制 |
|---|---|---|
| 普通行（未标记） | `font.split` 折行，高度 = 折行数 | 现有路径：居中 `drawCenteredString`，左对齐 `drawString` |
| 标记行，未超宽 | 与普通行相同 | 与普通行相同 |
| 标记行，超宽（滚动行） | 不折行，高度 1 行 | 裁剪带内按三角波偏移 `drawString`，左对齐 |

## 5. 实施步骤

本节给出动手顺序，每步附预期结果。工作量估计：核心改动集中在 `src/main/java/dev/processdetails/TextLayout.java`，百行级别（含标记组件、布局/绘制分支与轨迹函数），无需新增 mixin，无新依赖；调用点只在需要滚动行的位置把 `Line` 构造换成带标记的工厂。仓库无自动化测试，验证命令以 `AGENTS.md`（"Build / test commands"）为准：`./gradlew build` 与 `./gradlew runClient`。

1. `TextLayout.Line` 增加滚动标记组件与便捷构造器，默认 `false`。
   预期：`./gradlew build` 通过；三个画面行为与改动前完全一致。
2. `drawBlock` 布局阶段加分支：标记行测宽，超宽按 1 行计并记录溢出量。
   预期：临时给一条已知长行打标后，该行只占 1 行高，不再折行。
3. 绘制阶段加分支：`enableScissor` → `drawString(偏移)` → `disableScissor`。
   预期：滚动行被限制在裁剪带内，无越界字形；相邻行不受影响。
4. 实现三角波轨迹函数（停顿、速度参数、行索引相位错开）。
   预期：行首/行尾各有约 1.2 s 静止可读；多条滚动行不同步。
5. 按第 6.2 节清单在三个画面实测，再按第 7 节逐条验收。

## 6. 风险与验证

本节回答：哪里可能出问题、怎么测。

### 6.1 风险清单

| 风险 | 说明 | 缓解与验证 |
|---|---|---|
| scissor 在 `LoadingOverlay` 路径的表现未实测 | `enableScissor` 依赖 pose 换算，加载屏期间的 pose 栈状态与普通 Screen 不同 | `runClient` 用 F3+T 实测：裁剪矩形正确、无残留（必须实测项） |
| 中文测宽精度 | `font.width` 对 CJK 字符串需确认与绘制一致、无尾端截断 | `runClient` 用中文字符串实测（必须实测项） |
| scissor 泄漏 | `enableScissor` / `disableScissor` 不成对会影响后续绘制 | 代码严格成对调用；验收含「无泄漏到其他 UI」条目 |
| 兼容性 | 本方案不改 mixin 注入点，与 wrap `LoadingOverlay` 内部调用的 mod（如 RRLS）理论无冲突 | 装 RRLS 实测一次资源重载 |
| 极端窗口尺寸 | guiWidth 极小或溢出巨大 | 速度上限避免滚速过快；验收含极小窗口检查 |

### 6.2 验证清单（`runClient` 手测）

1. 资源重载：F3+T 触发，观察等待名单行、GPU 详情、colormap 链在打标后的滚动表现；淡出时详情随进度条消失。
2. 保存世界：进入单机世界后退出，观察主行与详情行；未打标行应保持折行不变。
3. Xaero 地图准备：打开世界地图，观察 phase 行与 "region in view" 行。
4. 中文字符串：在上述任一画面临时替换为中文长行实测测宽与滚动。
5. 其他 UI 无异常：滚动行出现的同时检查 HUD、聊天栏、原版进度条的字形完整性。

## 7. 验收标准

本节条目全部可检验，逐条通过即验收完成。

1. 超宽标记行不换行：该行在块内高度恰为 1 × `font.lineHeight`。
2. 可读出首尾：一个滚动周期内，行首与行尾均完整出现于裁剪带内，且各有约 1.2 s 静止可读。
3. 普通行行为不变：未标记行的折行位置、居中/左对齐与整块上移，和改动前一致。
4. 无 scissor 泄漏到其他 UI：滚动行出现前后，HUD、聊天栏、原版进度条、Xaero 界面渲染正常。
5. 三个画面回归通过（第 6.2 节清单第 1–3 条）。
6. `./gradlew build` 通过。
