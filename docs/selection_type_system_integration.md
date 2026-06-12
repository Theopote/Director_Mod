# SelectionType 系统集成文档

## 一、为什么必须有 SelectionType？

Director 中已经隐含了 4 种 Selection 语义，但需要显式区分：

| SelectionType | 语义 | PropertiesPanel 显示 |
|--------------|------|---------------------|
| **PROJECT** | 项目级资产 | Project 面板（含 Actor 列表） |
| **ACTOR** | 演员（Cast） | Actor 面板（ActorProjectPropertyAdapter） |
| **TRACK** | 时间线轨道 | Track 面板（TimelineTrackPropertyAdapter） |
| **CLIP** | 表演片段 | Clip 面板（Entity*ClipPropertyAdapter） |

**如果不引入 SelectionType.ACTOR**：
- ❌ Actor 面板显示时机不可预测
- ❌ 世界点选演员时，PropertiesPanel 会"跳错面板"
- ❌ 后期加 AI / Actor 分组一定会炸

---

## 二、SelectionType 枚举定义

**位置**：`src/main/java/com/director/ui/selection/SelectionType.java`

```java
public enum SelectionType {
    /** 项目级选择（默认） */
    PROJECT,
    
    /** 演员选择 */
    ACTOR,
    
    /** 轨道选择 */
    TRACK,
    
    /** 片段选择 */
    CLIP
}
```

---

## 三、SelectionManager 的 API

### 3.1 核心 API

```java
// 设置选择（带类型）
void setSelection(SelectionType type, Object payload)

// 获取选择类型
SelectionType getSelectionType()

// 获取选择的有效载荷
Object getSelectionPayload()
```

### 3.2 Convenience Helpers

```java
// Actor 选择
void selectActor(UUID actorId)
UUID getSelectedActorId()

// Track 选择
void selectTrack(TimelineTrack track)
TimelineTrack getSelectedTrack()

// Clip 选择
void selectClip(EntityClip clip)
EntityClip getSelectedClip()
```

### 3.3 向后兼容 API

```java
// 自动推断 SelectionType
void setCurrentSelection(Object selection)
Object getCurrentSelection()

// 清除选择（回到 PROJECT）
void clearSelection()
```

---

## 四、PropertiesPanel 的精确切换逻辑

### 4.1 核心切换代码

**位置**：`src/main/java/com/director/ui/panels/PropertiesPanel.java`

```java
@Override
protected void doRender() {
    SelectionManager selectionManager = SelectionManager.get();
    SelectionType selectionType = selectionManager.getSelectionType();
    
    switch (selectionType) {
        case PROJECT -> {
            // Project 面板（含 Actor 列表）
            renderProjectPanel();
        }
        
        case ACTOR -> {
            // Actor 面板（ActorProjectPropertyAdapter）
            renderActorPanel(selectionManager);
        }
        
        case TRACK -> {
            // Track 面板（TimelineTrackPropertyAdapter）
            renderTrackPanel(selectionManager);
        }
        
        case CLIP -> {
            // Clip 面板（Entity*ClipPropertyAdapter）
            renderClipPanel(selectionManager);
        }
    }
}
```

### 4.2 各面板的渲染逻辑

#### renderProjectPanel()

```java
private void renderProjectPanel() {
    renderNoSelectionMessage();
    // renderNoSelectionMessage() 内部会显示 Actor 列表
}
```

**显示内容**：
- 未选择对象提示
- 🎭 Actors 列表（通过 ActorProjectPropertyAdapter）
- 支持的对象类型列表

---

#### renderActorPanel()

```java
private void renderActorPanel(SelectionManager selectionManager) {
    UUID actorId = selectionManager.getSelectedActorId();
    if (actorId == null) {
        renderProjectPanel();
        return;
    }
    
    // 获取 Actor 对象
    var actorRegistry = ActorRegistryManager.getInstance();
    var actorOpt = actorRegistry.getActor(actorId);
    if (actorOpt.isEmpty()) {
        selectionManager.clearSelection();
        renderProjectPanel();
        return;
    }
    
    Actor actor = actorOpt.get();
    
    // 使用 ActorProjectPropertyAdapter
    var adapter = PropertyAdapterRegistry.getAdapterFor(actor);
    if (adapter != null) {
        adapter.renderProperties(actor);
    }
}
```

**显示内容**：
- Actor 列表
- 选中 Actor 的属性编辑器（name, type）
- "Focus in World" 按钮
- "Delete Actor" 按钮

---

#### renderTrackPanel()

```java
private void renderTrackPanel(SelectionManager selectionManager) {
    var track = selectionManager.getSelectedTrack();
    if (track == null) {
        renderProjectPanel();
        return;
    }
    
    // 使用 TimelineTrackPropertyAdapter
    var adapter = PropertyAdapterRegistry.getAdapterFor(track);
    if (adapter != null) {
        adapter.renderProperties(track);
    }
}
```

**显示内容**：
- 轨道基础属性
- Actor 快捷操作（"Look At Camera"）

---

#### renderClipPanel()

```java
private void renderClipPanel(SelectionManager selectionManager) {
    var clip = selectionManager.getSelectedClip();
    if (clip == null) {
        renderProjectPanel();
        return;
    }
    
    // 使用对应的 Entity*ClipPropertyAdapter
    var adapter = PropertyAdapterRegistry.getAdapterFor(clip);
    if (adapter != null) {
        adapter.renderProperties(clip);
    }
}
```

**显示内容**：
- 根据 Clip 类型显示对应的属性编辑器
- EntityTransformClip → EntityTransformClipPropertyAdapter
- EntityLookAtClip → EntityLookAtClipPropertyAdapter
- EntityAnimationClip → EntityAnimationClipPropertyAdapter
- ...

---

## 五、ActorSelectionHub 的对接

**位置**：`src/main/java/com/director/ui/actor/selection/ActorSelectionHub.java`

```java
public static void selectActor(UUID actorId) {
    // ...
    
    // 3️⃣ SelectionManager：明确设置为 ACTOR 类型
    SelectionManager selectionManager = SelectionManager.get();
    // ⭐ 关键：使用 selectActor() 明确设置 SelectionType.ACTOR
    selectionManager.selectActor(actorId);
    
    // ...
}
```

**关键点**：
- ✅ 使用 `selectActor(actorId)` 而不是 `setCurrentSelection(track)`
- ✅ 明确设置 `SelectionType.ACTOR`
- ✅ PropertiesPanel 会自动切换到 Actor 面板

---

## 六、最终行为矩阵

### 6.1 世界中点演员

```
用户点击世界中的 PreviewActorEntity
    ↓
ActorPickController.update()
    ↓
ActorSelectionHub.selectActor(actorId)
    ├─ TimelineModel.selectItem(track)  // Timeline 选中 Track
    ├─ SelectionManager.selectActor(actorId)  // SelectionType = ACTOR
    ├─ ActorGizmoController.setTargetActor(actorId)  // Gizmo 激活
    └─ ActorLookAtGizmoController.setTargetActor(actorId)  // LookAt Gizmo 激活
    ↓
PropertiesPanel.renderActorPanel()
    └─ ActorProjectPropertyAdapter.renderProperties(actor)
```

**结果**：
- ✅ SelectionType = ACTOR
- ✅ PropertiesPanel = Actor 面板
- ✅ Timeline = 选中 Track
- ✅ Gizmo = 激活

---

### 6.2 Timeline 点 Clip

```
用户点击 Timeline 中的 Clip
    ↓
TimelineInteractionHandler.selectClip(clip)
    ↓
SelectionManager.selectClip(clip)  // SelectionType = CLIP
    ↓
PropertiesPanel.renderClipPanel()
    └─ Entity*ClipPropertyAdapter.renderProperties(clip)
```

**结果**：
- ✅ SelectionType = CLIP
- ✅ PropertiesPanel = Clip 面板
- ✅ Actor 面板完全消失

---

### 6.3 空白 / 清空

```
用户点击空白区域
    ↓
SelectionManager.clearSelection()  // SelectionType = PROJECT
    ↓
PropertiesPanel.renderProjectPanel()
    └─ renderNoSelectionMessage()
        └─ ActorProjectPropertyAdapter.renderProperties("ACTOR_PROJECT")
```

**结果**：
- ✅ SelectionType = PROJECT
- ✅ PropertiesPanel = Project + Actor 列表

---

### 6.4 点击 Actor 列表中的 Actor

```
用户点击 ActorProjectPropertyAdapter 中的 Actor 项
    ↓
ActorProjectPropertyAdapter.selectActor(actor)
    ↓
SelectionManager.selectActor(actor.getActorId())  // SelectionType = ACTOR
    ↓
PropertiesPanel.renderActorPanel()
    └─ ActorProjectPropertyAdapter.renderProperties(actor)
```

**结果**：
- ✅ SelectionType = ACTOR
- ✅ PropertiesPanel = Actor 面板
- ✅ Timeline 不会被强制切换（职责分离）

---

## 七、关键设计原则

### 7.1 SelectionManager 不 import Actor 类

**原因**：
- SelectionManager 只存 UUID（ActorId），不存 Actor 对象
- 符合 Actor ≠ Runtime Entity ≠ UI 的设计哲学
- 避免循环依赖

**实现**：
```java
// ✅ 正确：只存 UUID
public void selectActor(UUID actorId) {
    setSelection(SelectionType.ACTOR, actorId);
}

// ❌ 错误：不要存 Actor 对象
// public void selectActor(Actor actor) { ... }
```

---

### 7.2 PropertiesPanel 根据 SelectionType 精确切换

**原因**：
- 避免在一个 render() 里 if/else 所有面板
- 使用 SelectionType → Adapter 的映射
- 清晰、可维护、可扩展

**实现**：
```java
switch (selectionType) {
    case PROJECT -> renderProjectPanel();
    case ACTOR -> renderActorPanel(selectionManager);
    case TRACK -> renderTrackPanel(selectionManager);
    case CLIP -> renderClipPanel(selectionManager);
}
```

---

### 7.3 ActorProjectPropertyAdapter 支持两种输入

**原因**：
- 无选择时：显示 Actor 列表（通过 "ACTOR_PROJECT" 标记）
- 选中 Actor 时：显示 Actor 属性编辑器（通过 Actor 对象）

**实现**：
```java
@Override
public boolean supports(Object obj) {
    if (obj == null) return false;
    if ("ACTOR_PROJECT".equals(obj)) return true;
    return obj instanceof Actor;
}
```

---

## 八、为什么这一步"必须现在做"

因为你已经有了：
- ✅ Actor
- ✅ ActorTrack
- ✅ Gizmo
- ✅ LookAt / Marker / Strength

如果 SelectionType 不明确，后面会发生：
- ❌ Actor 面板和 Clip 面板同时出现
- ❌ 世界点选 → 面板错乱
- ❌ 后期 AI Actor 无法落位

**👉 你现在做这一步，是在防未来的架构债。**

---

## 九、总结

**SelectionType 系统的核心价值**：

1. **🎯 明确语义** - 4 种选择类型，职责清晰
2. **🔄 精确切换** - PropertiesPanel 根据 SelectionType 自动切换
3. **🧩 职责分离** - Actor 管理 ≠ Track 管理 ≠ Clip 管理
4. **🌍 世界反馈** - 世界点选 → 正确的面板显示
5. **🚀 未来扩展** - 为 AI / Actor 分组预留空间

**这就是 Director 的"SelectionType → PropertiesPanel → PropertyAdapter"完整体系！** 🎨

