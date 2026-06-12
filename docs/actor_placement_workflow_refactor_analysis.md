# Actor 放置工作流重构分析

## 一、建议内容总览

建议的核心思想：
- **Actor 放置 = 世界交互行为，不是时间线编辑行为**
- **职责分离**：ActorSelectorPanel（选演员） → ActorSelectionHub（状态中枢） → ActorPickController（放置工具） → TimelineModel（记录结果）

## 二、现有实现 vs 建议对比

### ✅ 已实现的部分

1. **ActorPlacementController** ✅
   - 位置：`com.director.ui.actor.placement.ActorPlacementController`
   - 功能：已有放置功能，但实现方式不同
   - 差异：
     - 建议：`beginPlace(Actor)` 
     - 现有：`startPlacement(String actorName, ActorType actorType)`
   - 已有功能：
     - ✅ 创建临时 Actor 和 PreviewEntity
     - ✅ 监听右键点击放置
     - ✅ 创建 ActorTrack 和 EntitySpawnClip
     - ✅ Ghost 预览（通过 PreviewEntity）

2. **ActorSelectionHub** ✅
   - 位置：`com.director.ui.actor.selection.ActorSelectionHub`
   - 功能：统一的选择入口
   - 缺失：
     - ❌ 缺少 `ActorSelectionMode.PLACE` 状态
     - ❌ 缺少 `beginPlace(Actor)` 方法
     - ❌ 缺少 `isPlacingActor()` 方法

3. **ActorProjectPropertyAdapter** ✅
   - 位置：`com.director.ui.properties.adapters.ActorProjectPropertyAdapter`
   - 功能：Actor 列表和属性编辑
   - 缺失：
     - ❌ 缺少"Place"按钮（只有"Place at Camera"）
     - ❌ 没有调用 `ActorSelectionHub.beginPlace()`

4. **ActorPickController** ✅
   - 位置：`com.director.ui.actor.pick.ActorPickController`
   - 功能：点击选择 Actor
   - 缺失：
     - ❌ 没有监听 PLACE 状态
     - ❌ 没有处理放置逻辑

### ❌ 未实现的部分

1. **ActorSelectionMode 枚举** ❌
   - 需要添加 `PLACE` 状态

2. **Actor Ghost 渲染优化** ⚠️
   - 现有：通过 `PreviewEntity` 实现
   - 建议：半透明、不播放动画、简单站姿
   - 状态：基本实现，可能需要优化

3. **一个 Actor 一个 Track 约束** ❌
   - 需要添加检查逻辑

## 三、实施计划

### 步骤 1：扩展 ActorSelectionHub（状态中枢）

**目标**：添加 PLACE 模式状态管理

**修改**：
- 添加 `ActorSelectionMode` 枚举（或使用布尔标志）
- 添加 `beginPlace(Actor)` 方法
- 添加 `cancelPlace()` 方法
- 添加 `isPlacingActor()` 方法
- 添加 `getPendingActor()` 方法

### 步骤 2：修改 ActorProjectPropertyAdapter（UI 层）

**目标**：添加"Place"按钮

**修改**：
- 在 Actor 列表中添加"Place"按钮
- 点击时调用 `ActorSelectionHub.beginPlace(actor)`

### 步骤 3：修改 ActorPickController（Viewport Tool）

**目标**：监听 PLACE 状态并处理放置

**修改**：
- 在 `update()` 中检查 `ActorSelectionHub.isPlacingActor()`
- 如果正在放置，显示 Ghost 并监听点击
- 点击时调用放置逻辑（可以复用 `ActorPlacementController` 的逻辑）

### 步骤 4：添加一个 Actor 一个 Track 约束

**目标**：确保一个 Actor 在一个 Timeline 中最多一个 Track

**修改**：
- 在创建 Track 前检查是否已存在
- 如果存在，可以选择：
  - 不允许再次放置（推荐）
  - 或自动定位到已有 Track

### 步骤 5：优化 Actor Ghost 渲染（可选）

**目标**：改进 Ghost 视觉效果

**修改**：
- 半透明渲染
- 禁用动画播放
- 使用简单站姿

## 四、设计原则对齐

### ✅ 已对齐的原则

1. **职责分离** ✅
   - ActorSelectorPanel：只选演员
   - ActorPlacementController：处理放置逻辑
   - TimelineModel：记录结果

2. **单向协作** ✅
   - 各组件不互相知道细节

### ⚠️ 需要改进的地方

1. **状态管理** ⚠️
   - 建议：通过 `ActorSelectionHub` 统一管理状态
   - 现有：`ActorPlacementController` 自己管理状态
   - 改进：将状态管理移到 `ActorSelectionHub`

2. **放置入口** ⚠️
   - 建议：从 UI 触发 `beginPlace()`
   - 现有：直接调用 `ActorPlacementController.startPlacement()`
   - 改进：统一通过 `ActorSelectionHub.beginPlace()`

## 五、实施优先级

1. **高优先级**（核心功能）：
   - ✅ 步骤 1：扩展 ActorSelectionHub
   - ✅ 步骤 2：修改 ActorProjectPropertyAdapter
   - ✅ 步骤 3：修改 ActorPickController

2. **中优先级**（体验优化）：
   - ⚠️ 步骤 4：添加一个 Actor 一个 Track 约束

3. **低优先级**（视觉优化）：
   - ⚠️ 步骤 5：优化 Actor Ghost 渲染

## 六、兼容性考虑

- 现有 `ActorPlacementController` 可以保留，但建议逐步迁移到新流程
- 新流程和旧流程可以并存，通过 `ActorSelectionHub` 统一入口
