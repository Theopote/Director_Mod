# ActorProjectPropertyAdapter 集成文档

## 一、职责边界

`ActorProjectPropertyAdapter` 是 Project 级的属性适配器，**只负责 Actor 管理**：

### ✅ 它能做的

- 显示 Actor 列表
- 新建 Actor
- 删除 Actor
- 编辑 Actor 的基础属性（name / type）
- 将 Actor 设为 Selection（但不直接操作 Gizmo / Clip）

### ❌ 它不能做的

- ❌ 不编辑 Transform / LookAt / Animation（这些在 Clip 面板）
- ❌ 不操作 Timeline 播放
- ❌ 不直接创建 Clip（SpawnClip 已在创建 Actor 时由上层处理）

---

## 二、文件位置

```
src/main/java/com/director/ui/properties/adapters/ActorProjectPropertyAdapter.java
```

---

## 三、如何被 PropertiesPanel 调用

### 3.1 注册到 PropertyAdapterBootstrap

已在 `PropertyAdapterBootstrap.java` 中注册：

```java
// ✅ Actor 项目适配器（Project-level Actor 管理）
registerAdapter(new ActorProjectPropertyAdapter(), "ActorProject");
registeredCount++;
```

### 3.2 PropertiesPanel 的调用逻辑

**无选择时**（`renderNoSelectionMessage()`）：

```java
// ========== 🎭 Actors 区块（使用 ActorProjectPropertyAdapter）==========
Object actorProjectMarker = "ACTOR_PROJECT";
var actorAdapter = PropertyAdapterRegistry.getAdapterFor(actorProjectMarker);
if (actorAdapter != null && actorAdapter.supports(actorProjectMarker)) {
    UITheme.spacing();
    try {
        actorAdapter.renderProperties(actorProjectMarker);
    } catch (Exception e) {
        LOGGER.error("[PropertiesPanel] 渲染 Actor 面板时出错", e);
    }
    UITheme.separator();
}
```

**选中 Actor 时**：

```java
// SelectionManager.setCurrentSelection(actor)
// → PropertiesPanel.updateSelection(actor)
// → PropertyAdapterRegistry.getAdapterFor(actor)
// → ActorProjectPropertyAdapter.supports(actor) == true
// → ActorProjectPropertyAdapter.renderProperties(actor)
```

---

## 四、真实工作流中的行为

### 4.1 初始状态

```
打开 Director
    ↓
SelectionManager.getCurrentSelection() == null
    ↓
PropertiesPanel.renderNoSelectionMessage()
    ↓
显示 ActorProjectPropertyAdapter
    ├─ 🎭 Actors 标题
    ├─ Actor 列表（空）
    ├─ "+ Add Actor" 按钮
    └─ 提示 "No actors in project."
```

---

### 4.2 点击 "Add Actor"

```
用户点击 "+ Add Actor" 按钮
    ↓
ActorProjectPropertyAdapter.openCreateActorPopup()
    ↓
显示创建弹窗
    ├─ Name 输入框
    ├─ Type 下拉框
    ├─ "Create" 按钮
    └─ "Cancel" 按钮
    ↓
用户输入名称，选择类型，点击 "Create"
    ↓
ActorProjectPropertyAdapter.renderCreateActorPopup()
    ├─ actorRegistry.createActor(name, type)
    ├─ selectActor(actor) → SelectionManager.setCurrentSelection(actor)
    ├─ TimelineModel.markDirty()
    └─ 关闭弹窗
    ↓
Actor 自动被选中
    ↓
PropertiesPanel 显示 Actor 属性编辑器
    ├─ Name 编辑框
    ├─ Type 下拉框
    ├─ "Focus in World" 按钮
    └─ "Delete Actor" 按钮
```

**⚠️ 注意**：
- 创建 Actor 后，**上层逻辑**（不是本 Adapter）会创建 EntityTrack + SpawnClip
- 本 Adapter 只负责 Actor 的创建，不涉及 Timeline 操作

---

### 4.3 点击 Actor 列表中的 Actor

```
用户点击 Actor 列表中的 Actor 项
    ↓
ActorProjectPropertyAdapter.renderActorList()
    ├─ ImGui.isItemClicked() == true
    └─ selectActor(actor)
        ├─ selectedActorId = actor.getActorId()
        └─ SelectionManager.setCurrentSelection(actor)
    ↓
PropertiesPanel 自动切换
    ├─ 继续显示 ActorProjectPropertyAdapter
    └─ renderSelectedActorEditor() 显示选中 Actor 的属性
        ├─ Name 编辑框
        ├─ Type 下拉框
        ├─ "Focus in World" 按钮
        └─ "Delete Actor" 按钮
```

**⚠️ 注意**：
- Timeline 不会被强制切换（职责分离）
- 只显示 Actor 的基础属性
- 要编辑表演参数，需要选中对应的 Clip

---

### 4.4 在世界中点击演员（PreviewActorEntity）

```
用户点击世界中的 PreviewActorEntity
    ↓
ActorPickController.update()
    ↓
ActorSelectionHub.selectActor(actorId)
    ├─ TimelineModel.findEntityTrackByActorId()
    ├─ TimelineModel.selectItem(track)
    └─ SelectionManager.setCurrentSelection(track)  // ⚠️ 注意：这里设置的是 track，不是 actor
    ↓
PropertiesPanel 显示 TimelineTrackPropertyAdapter
    ├─ 轨道基础属性
    └─ Actor 快捷操作（"Look At Camera"）
```

**👉 此时 ActorProjectPropertyAdapter 不会显示**，因为 Selection 是 `TimelineTrack`，不是 `Actor`。

**如果要显示 Actor 面板**，需要：
1. 从 TimelineTrack 获取 EntityTrack
2. 从 EntityTrack 获取 ActorRef
3. 从 ActorRegistry 获取 Actor
4. 设置 `SelectionManager.setCurrentSelection(actor)`

---

### 4.5 点击 "Focus in World"

```
用户点击 "Focus in World" 按钮
    ↓
ActorProjectPropertyAdapter.renderSelectedActorEditor()
    ├─ ActorSelectionHub.selectActor(actor.getActorId())
    └─ 触发完整的 Actor 选择流程
        ├─ TimelineModel.selectItem(track)
        ├─ SelectionManager.setCurrentSelection(track)
        ├─ ActorGizmoController.setTargetActor(actorId)
        └─ ActorLookAtGizmoController.setTargetActor(actorId)
    ↓
PropertiesPanel 自动切换
    ├─ ActorProjectPropertyAdapter 退场
    └─ TimelineTrackPropertyAdapter 接管
```

---

### 4.6 删除 Actor

```
用户点击 "Delete Actor" 按钮
    ↓
ActorProjectPropertyAdapter.openDeleteConfirm(actorId)
    ↓
显示删除确认弹窗
    ├─ "Are you sure you want to delete this actor?"
    ├─ Actor 信息显示
    ├─ "Delete" 按钮
    └─ "Cancel" 按钮
    ↓
用户点击 "Delete"
    ↓
ActorProjectPropertyAdapter.deleteActor()
    ├─ actorRegistry.removeActor(actorId)
    ├─ selectedActorId = null
    ├─ SelectionManager.clearSelection()
    └─ TimelineModel.markDirty()
    ↓
Actor 列表更新（删除的 Actor 不再显示）
```

---

## 五、与现有系统的协作

### 5.1 与 ActorSelectorPanel 的关系

**当前状态**：
- `ActorSelectorPanel` 仍然存在于 `PropertiesPanel.renderNoSelectionMessage()` 中作为**回退方案**
- `ActorProjectPropertyAdapter` 是**主要方案**，通过 PropertyAdapter 系统集成

**未来计划**：
- 可以完全移除 `ActorSelectorPanel`，只使用 `ActorProjectPropertyAdapter`
- 或者保留 `ActorSelectorPanel` 作为轻量级入口，`ActorProjectPropertyAdapter` 作为完整编辑器

---

### 5.2 与 Clip PropertyAdapter 的关系

**完全独立**：
- `ActorProjectPropertyAdapter` 不编辑 Clip
- `Entity*ClipPropertyAdapter` 不编辑 Actor
- 两者通过 Selection 系统自动切换

**切换流程**：
```
选中 Actor
    ↓
ActorProjectPropertyAdapter 显示
    ↓
拖动 Gizmo → 创建 EntityTransformClip
    ↓
SelectionManager.setCurrentSelection(transformClip)
    ↓
EntityTransformClipPropertyAdapter 显示
    ↓
ActorProjectPropertyAdapter 自动退场
```

---

### 5.3 与 TimelineTrackPropertyAdapter 的关系

**职责分离**：
- `ActorProjectPropertyAdapter`：管理 Actor（Project 级）
- `TimelineTrackPropertyAdapter`：管理 Track（Timeline 级）

**协作方式**：
- 选中 Actor → `ActorProjectPropertyAdapter`
- 选中 Track → `TimelineTrackPropertyAdapter`
- 两者可以互相切换（通过 "Focus in World" 按钮）

---

## 六、关键设计决策

### 6.1 为什么使用 `supports()` 方法？

**原因**：
- `ActorProjectPropertyAdapter` 需要支持两种输入：
  1. 字符串标记 `"ACTOR_PROJECT"`（无选择时）
  2. `Actor` 对象（选中 Actor 时）
- 使用 `supports()` 方法可以灵活判断是否支持某种输入

---

### 6.2 为什么不在创建 Actor 时自动创建 Track？

**原因**：
- **职责分离**：Actor 管理 ≠ Timeline 管理
- **灵活性**：Actor 可以存在但不被 Timeline 使用
- **未来扩展**：一个 Actor 可以出现在多个 Timeline

**当前实现**：
- 创建 Actor 后，**上层逻辑**（`ActorSelectorPanel.createActorAndTrack()`）会创建 Track
- `ActorProjectPropertyAdapter` 只负责 Actor 的创建

---

### 6.3 为什么删除 Actor 时不清除 Track？

**原因**：
- **数据完整性**：Track 可能包含重要的表演数据
- **用户控制**：让用户决定是否删除 Track
- **未来扩展**：可能支持"恢复已删除的 Actor"

**当前实现**：
- 删除 Actor 后，Track 仍然存在（但 ActorRef 指向的 Actor 不存在）
- Runtime 会检测到缺失的 Actor，并安全处理（移除 PreviewEntity）

---

## 七、总结

`ActorProjectPropertyAdapter` 是 Director 属性编辑体系中的**Project 级入口**：

1. **职责清晰**：只管理 Actor，不编辑 Clip/Track
2. **无缝集成**：通过 PropertyAdapter 系统自动切换
3. **用户友好**：提供完整的 Actor 管理功能（创建、编辑、删除）
4. **架构正确**：Actor 是 Project 级资产，生命周期 > Timeline

**这就是 Director 的"Selection → PropertyAdapter → 数据对象 → Runtime"完整体系！** 🎨

