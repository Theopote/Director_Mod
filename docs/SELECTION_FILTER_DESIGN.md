# 选择过滤器设计（Selection Filter）

## 一、定位

- **Selection Filter** = 在 selection pipeline 中，动态裁剪「哪些对象可以被选中」的**规则集合**。
- **SelectionFilterPanel** = 用来开关/配置这些规则的 UI；**不参与执行选择行为**。

## 二、三条红线（必须遵守）

### 红线 1：Panel 不直接碰 selection / pipeline

- **SelectionFilterPanel 不允许直接调用**：`SelectionManager.get()`、`getPipeline()`、以及任何 `selectionSet` / 选择提交 行为。
- **只允许**：`commands.selectionFilter().xxx()`（即 `SelectionFilterCommands` 接口上的方法）。

### 红线 2：SelectionUtil / Handler 中禁止 3 层以上 if 嵌套

- 一旦过滤逻辑出现 3 层 if 嵌套，视为失控，必须抽成「规则对象」或集中到 `BlockFilterPipeline` / `getEffectiveBlockFilter`。

### 红线 3：所有过滤条件集中在一处

- 方块选择：`SelectionManager.getEffectiveBlockFilter()` 为**唯一**用于解析「用哪个 BlockFilter」的集中点；工具只调用它，不自行实现「空管道 → nonAir」等分支。
- 管道内容由 `BlockFilterPipeline` 持有；管道变更**仅**经 `SelectionFilterCommands` 完成。

## 三、管道模型（方块选择）

```
User Click / Drag
       ↓
SelectionCandidate (BlockPos + BlockState)
       ↓
BlockFilterPipeline.test(world, pos, state)  ← 或 getEffectiveBlockFilter() 封装
       ↓
SelectionManager.commit / 工具写入 selectionSet
```

- **BlockFilter**：规则对象，`boolean test(World, BlockPos, BlockState)`。
- **BlockFilterPipeline**：有序规则列表；顺序即应用顺序，遇 false 短路。
- **getEffectiveBlockFilter()**：管道非空用管道，空则 `BlockFilters.nonAir()`，避免逻辑散落到各工具。

## 四、UI 与逻辑的边界

- UI：勾选 / 下拉 / 按钮 → 调用 `SelectionFilterCommands`（`applyQuickFilter`、`appendFilter`、`loadPreset`、`savePreset` 等）。
- **不**：`if (ImGui.checkbox("忽略锁定", ...)) { selectionManager.setIgnoreLocked(...) }` 这类由 UI 直接改「行为分支」的写法。
- 勾选 = 启用/禁用或替换某条**规则**（如替换管道为某预设），而不是改一个分散的 if 条件。

## 五、与现有实现的对应

| 概念         | 实现                                      |
|--------------|-------------------------------------------|
| 规则对象     | `BlockFilter`                             |
| 规则列表     | `BlockFilterPipeline`                     |
| 管道配置入口 | `SelectionFilterCommands`（Facade 内实现）|
| 有效过滤器   | `SelectionManager.getEffectiveBlockFilter()` |
| 魔棒 / 刷选需管线 | `SelectionManager.getEffectiveFilterPipeline()`：空管道时返回 `nonAir` 管线 |
| 配置 UI           | `SelectionFilterPanel`，仅调 `selectionFilter().*` |
| 刷选              | 已移除本地 `pipeline`，统一用全局；`getPipeline()` 只读透出全局供 ToolPropertiesPanel |
| 面选              | `FaceSelectTask`、`FaceSelectHoverTask` 接收 `BlockFilter`，在扩散/悬停时用 `getEffectiveBlockFilter()` 校验 |
| 套索              | `LassoSelectTask.fillPolygon` 用 `getEffectiveBlockFilter()` 过滤多边形内方块（与框选、魔棒、刷选、面选一致） |

## 六、扩展时注意

- 新增「过滤维度」：优先新增 `BlockFilter` 实现并注册到 `BlockFilterRegistry`，或新增预设；**不要**在工具 / 面板里加 `if (filter.allowX && ...)`。
- 若未来做「容器 / 时间线片段 / Actor」等非方块选择过滤：应抽象出 `SelectionRule` / `SelectionFilterEvaluator` 等，并同样遵守：**规则对象 + 集中解析、UI 只配置规则**。
