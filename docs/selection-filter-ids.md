# Director Selection Filter ID 规范表（官方）

> 本文档是 **filterId 命名、语义、UI 文案、适用层级** 的唯一权威来源。  
> 代码中的 `FilterIds`、`BlockFilterRegistry`、`SelectionFilterPanel`、Preset 内置模式均须与此表一致。

---

## 命名总原则（非常重要）

| 原则 | 说明 |
|------|------|
| **filterId** | 机器友好、稳定、**不可随意改名** |
| **UI 文案** | 人话，可本地化，可改 |
| **一个 filterId** | 一个清晰、**原子**语义 |
| **禁止** | 把「编辑器概念」塞进来（容器 / 锁定 / 面板） |

---

## 一、基础内容过滤（Layer 1 · 普通用户）

**语义：** “我想选哪些类型的方块”  
**UI：** 默认展示，checkbox 形式

| filterId | 中文 UI 文案 | 英文语义 | 说明 | 默认 |
|----------|--------------|----------|------|:----:|
| `exclude_air` | 忽略空气 | Exclude Air | 不允许选中空气方块；**默认显式存在于管道**（SelectionManager 初值为 [exclude_air]），不再用「空管道=忽略空气」隐式规则 | ✅ |
| `exclude_water` | 忽略水 | Exclude Water | 排除所有水体方块 | ✅ |
| `exclude_lava` | 忽略岩浆 | Exclude Lava | 排除所有岩浆方块 | ✅ |
| `solid_only` | 仅选择固体方块 | Solid Only | 只允许实体固体 | ⬜ |
| `plant_only` | 仅选择植物 | Plant Only | 草、花、树叶等 | ⬜ |
| `replaceable_only` | 仅选择可替换方块 | Replaceable Only | 雪、草、花 | ⬜ |
| `transparent_only` | 仅选择透明方块 | Transparent Only | 玻璃等 | ⬜ |

**规范说明：**

- 使用 `exclude_` / `_only` 前缀，语义一眼可分
- **不允许**使用 `non_air` 这类「反向否定命名」（对用户和维护都不友好）

---

## 二、选择行为过滤（Layer 2 · 进阶）

**语义：** “选择时遵循什么规则”  
**UI：** 折叠展示，和工具强相关，但不属于工具

| filterId | 中文 UI 文案 | 英文语义 | 说明 |
|----------|--------------|----------|------|
| `same_block_type` | 仅选择同类方块 | Same Block Type | 魔棒常用 |
| `surface_only` | 仅选择表面方块 | Surface Only | 排除内部 |
| `visible_only` | 仅选择可见方块 | Visible Only | 排除遮挡 |
| `connected_only` | 仅选择连通区域 | Connected Only | Flood fill |
| `max_depth_limit` | 限制选择深度 | Depth Limited | 参数化 |

**规范说明：**

- 这些 filter 可能带参数
- 命名必须体现「**行为**」，而不是「类型」

---

## 三、空间 / 范围过滤（Layer 3 · 专业）

**语义：** “在什么空间范围内允许选择”  
**UI：** 默认隐藏，主要给高级用户 / 系统用

| filterId | 中文 UI 文案 | 说明 |
|----------|--------------|------|
| `within_bbox` | 限制在框选范围内 | Box / Lasso |
| `within_chunk` | 限制在区块内 | 性能用 |
| `within_radius` | 限制在半径内 | 参数化 |
| `max_block_count` | 最大选择数量 | 防爆 |

**规范说明：**

- 多数为 **参数化 filter**
- UI 需要专门 editor，不直接 checkbox

---

## 四、调试 / 系统过滤（隐藏，不给普通用户）

**语义：** 系统保护 / 调试  
**永远不出现在普通 UI**

| filterId | 用途 |
|----------|------|
| `debug_log_reject` | 打印被拒绝原因 |
| `profiling_only` | 性能统计 |
| `dry_run` | 不真正选中 |

**规范说明：**

- 这些 filter 只用于 Debug / 内部
- **不允许**出现在 Preset / Mode

---

## 五、明确禁止的 filterId（红线）

以下 **永远不允许** 出现在 Selection Filter 系统中：

- ❌ `locked_only` / `exclude_locked`
- ❌ `current_container_only`
- ❌ `current_layer_only`
- ❌ `editor_visible_only`

**原因：** 这些是 **编辑器对象 / UI 状态 / 容器语义**，不属于「世界方块选择」。

---

## 六、Preset / Mode 命名规范（基于 filterId 组合）

Preset 不是新能力，只是 **filterId 的组合**。

### 官方推荐内置模式

| 模式名 (presetId) | 内部组合 | UI 显示 |
|-------------------|----------|---------|
| `default` | `exclude_air` + `exclude_water` + `exclude_lava` | 通用编辑 |
| `terrain_edit` | `solid_only` + `exclude_air` | 地形编辑 |
| `plant_edit` | `plant_only` | 植物编辑 |
| `fluid_edit` | `exclude_air`（不排除水/岩浆） | 流体编辑 |
| `replaceable_edit` | `replaceable_only` | 可替换方块 |

---

## 七、BlockFilterRegistry 的强制约束建议

1. **禁止 magic string**：所有 filterId 来自 `FilterIds` 常量。
2. **禁止运行时随意注册未知 ID**：新增 ID 须先入表、入常量、再注册。
3. **注册即校验合法性**：id 非空、符合前缀/语义规范。

### 向后兼容（Legacy 别名）

为兼容旧配置与旧代码，`BlockFilterRegistry.getFilter` / `resolveFilterId` 及 Commands 内部对以下旧 id 做映射后再查找，**新代码不得再使用**：

| 旧 id | 规范 id |
|-------|---------|
| `non_air` | `exclude_air` |
| `not_water` | `exclude_water` |
| `not_lava` | `exclude_lava` |
| `solid` | `solid_only` |
| `plant` | `plant_only` |
| `replaceable` | `replaceable_only` |
| `transparent` | `transparent_only` |

### createFilter 与参数化规则

- 无参数规则：`getFilter(id)` 返回共享实例即可。
- 带参数规则（`max_depth_limit`、`within_radius`、`within_bbox`、`max_block_count`）：必须通过 `BlockFilterRegistry.createFilter(id)` 取得**新实例**，再填入参数；禁止用 `getFilter` 取共享实例，否则管道间参数会互相污染。

---

## 八、为什么这一步非常值（总结）

定好这份表，你就获得了：

- 🧠 **稳定的用户心智模型**
- 🧱 **稳定的代码边界**
- 🔁 **UI / Preset / Tool / Shortcut 的共同语言**
- 🚫 **避免未来「filterId 大爆炸」**
