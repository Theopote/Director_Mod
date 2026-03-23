# Director Actor System · 完整架构文档

> **核心宪法**：实体轨道（EntityTrack）不是"控制 Minecraft 实体"，而是"导演一个虚拟演员（Actor）的表演"。

---

## 📐 一、宏观架构：三层分离

```
┌─────────────────────────────────────────────────────────┐
│  导演层（Director Layer）                                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │   Actor      │  │ EntityTrack  │  │ EntityClip  │ │
│  │  (演员概念)   │  │  (表演轨道)   │  │  (表演片段)  │ │
│  └──────────────┘  └──────────────┘  └──────────────┘ │
│         ↓                ↓                ↓            │
│  ┌──────────────────────────────────────────────────┐  │
│  │         TimelineModel (时间线模型)                │  │
│  │  - 只描述"意图"，不执行任何行为                    │  │
│  │  - 纯数据，可序列化、可导出                        │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│  运行时层（Runtime Layer）                                │
│  ┌──────────────────┐  ┌──────────────────┐          │
│  │ EntityClipSampler│  │ActorRuntimeController│        │
│  │  (采样器)        │  │  (运行时控制器)     │          │
│  │  time → Sample   │  │  Sample → Entity  │          │
│  └──────────────────┘  └──────────────────┘          │
│         ↓                        ↓                     │
│  ┌──────────────────────────────────────────────────┐  │
│  │         EntitySample (状态快照)                    │  │
│  │  - active, position, rotation, lookAt, state...  │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│  呈现层（Presentation Layer）                            │
│  ┌──────────────────┐  ┌──────────────────┐          │
│  │PreviewActorEntity│  │ActorLookAtVisualizer│        │
│  │  (客户端投影)     │  │  (可视化辅助)      │          │
│  │  - 只客户端存在   │  │  - 视线 + Marker  │          │
│  │  - 不写世界       │  │  - 编辑态专用     │          │
│  └──────────────────┘  └──────────────────┘          │
│         ↓                        ↓                     │
│  ┌──────────────────────────────────────────────────┐  │
│  │         Minecraft World (渲染层)                 │  │
│  │  - 世界只是"画布"，不存储任何表演数据               │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### 关键设计原则

1. **Actor ≠ Minecraft Entity**
   - Actor 是"导演概念"，存在于 Timeline/Project
   - Minecraft Entity 只是 Actor 的"表现形式"

2. **Timeline ≠ 世界状态**
   - Timeline 只描述"表演意图"
   - World 只负责"呈现效果"

3. **编辑态 = 播放态**
   - 同一套 Runtime 逻辑
   - 编辑时：手动改时间 → Runtime 采样 → 立即更新
   - 播放时：自动走时间 → Runtime 采样 → 连续更新

---

## 🎭 二、核心角色分工

### 1️⃣ Actor（演员）——"谁在表演"

**位置**：`com.director.actor.Actor`

```java
public final class Actor {
    UUID actorId;        // 唯一标识
    String name;        // 演员名称（如"商人A"、"守卫B"）
    ActorType type;     // 演员类型（VILLAGER / COW / PLAYER / CUSTOM）
}
```

**核心特征**：
- ✅ 纯数据对象，不包含世界信息
- ✅ 不包含位置、时间、Entity UUID
- ✅ 只描述"我是谁"
- ✅ 可被任意 Timeline / Runtime 使用
- ✅ 可复制、可克隆（UUID 可换）

**存储**：`ActorRegistry` → `actor_registry.json`

---

### 2️⃣ EntityTrack（实体轨道）——"这个演员演什么"

**位置**：`com.director.ui.timeline.model.entity.EntityTrack`

```java
public class EntityTrack {
    ActorRef actorRef;           // 指向 Actor（轻量引用）
    List<EntityClip> clips;      // 表演片段列表（按时间排序）
    EntityBinding binding;       // 绑定信息（SPAWN_NEW / BIND_EXISTING）
    boolean enabled;             // 轨道开关
    int colorRgba;              // 轨道颜色（UI 用）
}
```

**核心职责**：
- 把"Actor"和"时间线上的表演片段"关联起来
- 管理该演员的所有表演片段（Clips）

**存储**：`TimelineTrack.entityTrackPayload` → `timeline_model.json`

---

### 3️⃣ EntityClip（表演片段）——"在某一段时间做什么"

**位置**：`com.director.ui.timeline.model.entity.EntityClip`（抽象基类）

**7 种 Clip 类型**：

| Clip 类型 | 语义 | 关键字段 |
|----------|------|---------|
| `EntitySpawnClip` | 什么时候登场 | `position`, `yawDeg`, `pitchDeg`, `visibleOnSpawn` |
| `EntityTransformClip` | 如何移动/旋转/缩放 | `fromPos/toPos`, `fromRot/toRot`, `fromScale/toScale`, `interpolation` |
| `EntityLookAtClip` | 看向谁/看向哪里 | `mode` (WORLD_POINT/ENTITY_UUID/CAMERA), `worldPoint`, `strength` |
| `EntityAnimationClip` | 播放什么动画 | `animationId`, `source`, `loop`, `speed` |
| `EntityStateClip` | 持续状态 | `sneaking`, `sprinting`, `glowing`, `invulnerable`, `visibility` |
| `EntityActionClip` | 一次性事件 | `actionType`, `params`, `triggerOnce` |
| `EntityDespawnClip` | 什么时候离场 | `mode` (HIDE/REMOVE) |

**核心特征**：
- ✅ 所有 Clip 都实现 `TimelineSelectable`（可在时间线选择和编辑）
- ✅ 统一的 `startTime` / `endTime` / `muted` / `weight`
- ✅ 可比较排序（按时间）
- ✅ 支持时间偏移（`offsetTime`）

---

### 4️⃣ TimelineModel（时间线模型）——"全局导演台本"

**位置**：`com.director.ui.timeline.model.TimelineModel`

**核心职责**：
- 管理所有轨道（包括 EntityTrack）
- 维护当前时间（`currentTime`）
- 管理选择状态（`selectedItem`）
- 提供序列化/反序列化

**关键方法**：
- `findEntityTrackByActorId(UUID actorId)` - 根据 Actor ID 查找轨道
- `selectItem(TimelineSelectable, boolean)` - 统一的选择机制
- `markDirty()` - 标记需要保存

---

### 5️⃣ EntityClipSampler（采样器）——"导演助理"

**位置**：`com.director.ui.timeline.playback.entity.EntityClipSampler`

**核心职责**：
- 在时间 `t`，把一堆 Clips 变成一个"演员状态快照"（EntitySample）

**采样逻辑**：
```
EntityTrack + time t
    ↓
找到所有覆盖时间 t 的 Clips
    ↓
按优先级叠加（Spawn → Transform → Animation → State → LookAt → Despawn → Action）
    ↓
EntitySample（完整状态快照）
```

**优先级规则**（last-wins）：
1. **Spawn** - 决定是否 active
2. **Transform** - 位置/旋转/缩放（插值）
3. **Animation** - 动画播放
4. **State** - 持续状态（叠加）
5. **LookAt** - 看向目标（last-wins）
6. **Despawn** - 决定是否 active
7. **Action** - 一次性事件（edge trigger）

---

### 6️⃣ EntitySample（状态快照）——"这一帧演员应该处于什么状态"

**位置**：`com.director.ui.timeline.playback.entity.EntitySample`

```java
public class EntitySample {
    boolean active;                    // 是否存在
    
    // Transform
    Vector3f position;                 // 位置
    Vector3f rotation;                 // 旋转（yaw, pitch, roll）
    Vector3f scale;                    // 缩放
    
    // Animation
    String animationId;                // 动画 ID
    boolean animationLoop;            // 是否循环
    float animationSpeed;             // 播放速度
    
    // LookAt
    boolean lookAtEnabled;             // 是否启用 LookAt
    TargetMode lookAtMode;            // 目标模式
    Vector3f lookAtPoint;             // 目标点
    UUID lookAtEntityUuid;            // 目标实体 UUID
    float lookAtStrength;             // 强度（0-1）
    
    // State
    boolean sneaking;                  // 潜行
    boolean sprinting;                 // 冲刺
    boolean glowing;                  // 发光
    boolean invulnerable;             // 无敌
    float visibility;                 // 可见性（0-1）
    
    // Action
    List<EntityActionEvent> actionEvents;  // 一次性事件列表
}
```

**核心特征**：
- ✅ 纯数据，不包含任何 Minecraft 对象
- ✅ 可插值（`lerpFrom`）
- ✅ 可复制（`copyFrom`）

---

### 7️⃣ ActorRuntimeController（运行时控制器）——"导演现场执行官"

**位置**：`com.director.actor.runtime.ActorRuntimeController`

**核心职责**：
- 每一帧，对每条 EntityTrack：
  1. 解析 Actor（通过 ActorRef → ActorRegistry）
  2. 采样（`EntityClipSampler.sampleAt(time)`）
  3. 把 Sample 应用到演员的"舞台投影"（PreviewActorEntity）

**更新循环**：
```java
public void update() {
    float time = timelineModel.getCurrentTime();
    
    for (TrackRuntime rt : runtimes.values()) {
        rt.update(time);  // 对每条轨道：
                          // 1. resolve Actor
                          // 2. sampler.sampleAt(time)
                          // 3. applySample(entity, sample)
    }
}
```

**职责边界**：
- ✅ 遍历 Timeline 中的 EntityTrack
- ✅ 通过 ActorRef → Actor（通过 ActorRegistry）
- ✅ 调用 EntityClipSampler 得到 EntitySample
- ✅ 根据 Sample：Spawn / Despawn PreviewActorEntity
- ✅ Apply Transform / LookAt / State / Action
- ❌ 不关心世界实体
- ❌ 不生成真实 MC Entity
- ❌ 不 resolve 世界 Entity
- ❌ 不处理服务器同步
- ❌ 不写世界状态

---

### 8️⃣ PreviewActorEntity（预览实体）——"演员的舞台形象"

**位置**：`com.director.actor.preview.PreviewActorEntity`

**核心特征**：
- ✅ 继承 `Entity`（复用渲染 / hitbox / yaw / pitch）
- ✅ 只存在于客户端
- ✅ 不保存、不同步、不参与 AI
- ✅ 无碰撞（`noClip = true`）
- ✅ 可被选中、可高亮
- ✅ 100% 受 Timeline 驱动

**生命周期**：
- 创建：`ActorPreviewManager.ensurePreview(Actor)` → `EntityType.create()`
- 更新：`ActorRuntimeController.applySample()` → `refreshPositionAndAngles()`
- 销毁：`ActorPreviewManager.removePreview(UUID)`

---

### 9️⃣ ActorPreviewManager（预览管理器）——"演员到实体的映射"

**位置**：`com.director.actor.preview.ActorPreviewManager`

**核心职责**：
- 管理 `Actor` → `PreviewActorEntity` 的映射
- 提供反向查找：`entityId` → `actorId`（用于点击检测）
- 控制 PreviewEntity 的生命周期

**关键方法**：
- `ensurePreview(Actor)` - 确保某个 Actor 有 PreviewEntity
- `removePreview(UUID)` - 移除某个 Actor 的 PreviewEntity
- `getPreview(UUID)` - 获取 PreviewEntity
- `getActorId(PreviewActorEntity)` - 反向查找 Actor ID
- `clear()` - 清空所有预览实体

---

## 🔄 三、完整数据流

### 3.1 创建 Actor 流程

```
用户点击 "+ Add Actor"
    ↓
ActorSelectorPanel.createActorAndTrack()
    ↓
┌─────────────────────────────────────┐
│ 1. ActorRegistry.createActor()      │
│    → 创建 Actor (UUID, name, type)  │
│    → 保存到 actor_registry.json     │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│ 2. TimelineModel.addTrack()         │
│    → 创建 TimelineTrack (type=ENTITY)│
│    → 创建 EntityTrack (ActorRef)    │
│    → 保存到 timeline_model.json    │
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│ 3. EntityTrack.addDefaultSpawnClip()│
│    → 创建 EntitySpawnClip           │
│    → 设置初始位置（当前摄像机位置）  │
└─────────────────────────────────────┘
    ↓
ActorRuntimeController.rebuildRuntimes()
    ↓
TrackRuntime 创建（绑定 EntityTrack）
    ↓
下一帧 update() → sampler.sampleAt() → EntitySample.active = true
    ↓
ActorPreviewManager.ensurePreview(Actor)
    ↓
PreviewActorEntity 出现在世界中
```

---

### 3.2 运行时更新流程（每帧）

```
TimelinePanel.doRender()
    ↓
ActorRuntimeController.update()
    ↓
┌─────────────────────────────────────┐
│ 对每条 EntityTrack:                 │
│                                     │
│ 1. TrackRuntime.update(time)        │
│    ↓                                │
│ 2. resolve Actor                    │
│    ActorRef → ActorRegistry         │
│    → Actor                          │
│    ↓                                │
│ 3. sampler.sampleAt(time)           │
│    EntityTrack + time               │
│    → 找到所有覆盖的 Clips            │
│    → 按优先级叠加                    │
│    → EntitySample                   │
│    ↓                                │
│ 4. handleSpawnLifecycle(sample)     │
│    if (sample.active && !spawned)   │
│        ensurePreview(actor)         │
│    if (!sample.active && spawned)   │
│        removePreview(actorId)       │
│    ↓                                │
│ 5. applySample(entity, sample)      │
│    - refreshPositionAndAngles()      │
│    - applyLookAt()                  │
│    - applyState()                   │
│    - applyActionEvents()            │
└─────────────────────────────────────┘
    ↓
PreviewActorEntity 更新完成
```

---

### 3.3 编辑态交互流程

#### 3.3.1 世界 → Timeline 选择

```
用户点击世界中的 PreviewActorEntity
    ↓
ActorPickController.update()
    ↓
检测鼠标点击 + 射线检测
    ↓
找到 PreviewActorEntity
    ↓
ActorPreviewManager.getActorId(entity)
    ↓
ActorSelectionHub.selectActor(actorId)
    ↓
┌─────────────────────────────────────┐
│ 1. TimelineModel.findEntityTrackByActorId()│
│    → 找到对应的 EntityTrack         │
│    ↓                                │
│ 2. TimelineModel.selectItem(track)  │
│    → 时间线选中该轨道               │
│    ↓                                │
│ 3. SelectionManager.setCurrentSelection(track)│
│    → 属性面板显示轨道属性           │
│    ↓                                │
│ 4. ActorGizmoController.setTargetActor(actorId)│
│    → Gizmo 激活                     │
│    ↓                                │
│ 5. ActorLookAtGizmoController.setTargetActor(actorId)│
│    → LookAt Gizmo 激活              │
└─────────────────────────────────────┘
    ↓
UI 同步完成（时间线 + 属性面板 + Gizmo）
```

#### 3.3.2 Gizmo → Clip 修改

```
用户拖动 Gizmo（Translate/Rotate/Scale）
    ↓
GizmoInteractionController.startDrag()
    ↓
ActorGizmoController.handleGizmoDragStart()
    ↓
┌─────────────────────────────────────┐
│ 1. 找到或创建 EntityTransformClip   │
│    - 查找覆盖当前时间的 Clip        │
│    - 如果没有，创建新的 Clip        │
│    ↓                                │
│ 2. 初始化 from/to 值               │
│    - fromPos = 当前采样位置         │
│    - toPos = 当前采样位置           │
│    - fromRot/toRot, fromScale/toScale 同理│
│    ↓                                │
│ 3. 设置 dragBasePos/Rot/Scale      │
│    → 记录拖拽起始值                 │
└─────────────────────────────────────┘
    ↓
用户继续拖动
    ↓
GizmoInteractionController.updateDrag()
    ↓
ActorGizmoController.handleGizmoDragUpdate()
    ↓
┌─────────────────────────────────────┐
│ 根据 GizmoMode:                     │
│                                     │
│ TRANSLATE:                          │
│   editingClip.toPos = dragBasePos + delta│
│                                     │
│ ROTATE:                             │
│   editingClip.toRot = dragBaseRot + delta│
│                                     │
│ SCALE:                              │
│   editingClip.toScale = dragBaseScale * delta│
│                                     │
│ → TimelineModel.markDirty()         │
└─────────────────────────────────────┘
    ↓
用户释放鼠标
    ↓
GizmoInteractionController.endDrag()
    ↓
ActorGizmoController.handleGizmoDragEnd()
    ↓
清除 dragBase 值
    ↓
下一帧 update() → 立即看到效果
```

#### 3.3.3 LookAt 交互流程

```
用户切换到 LOOK_AT 模式
    ↓
GizmoManager.setMode(GizmoMode.LOOK_AT)
    ↓
用户点击世界中的点
    ↓
ActorLookAtGizmoController.update()
    ↓
检测点击 + 射线检测
    ↓
ActorLookAtGizmoController.createOrUpdateLookAtClip()
    ↓
┌─────────────────────────────────────┐
│ 1. 找到或创建 EntityLookAtClip      │
│    - 查找覆盖当前时间的 Clip        │
│    - 如果没有，创建新的 Clip        │
│    ↓                                │
│ 2. 设置 Clip 属性                   │
│    - mode = WORLD_POINT             │
│    - worldPoint = 点击位置           │
│    - strength = 1.0f                │
│    ↓                                │
│ 3. ActorLookAtSelectionContext.setActiveLookAtClip(clip)│
│    → 更新上下文（供 MarkerDragController 使用）│
│    ↓                                │
│ 4. TimelineModel.markDirty()       │
└─────────────────────────────────────┘
    ↓
ActorLookAtVisualizer.render()
    ↓
┌─────────────────────────────────────┐
│ 1. 找到活跃的 LookAtClip            │
│    ↓                                │
│ 2. 解析目标点                       │
│    - WORLD_POINT → worldPoint       │
│    - CAMERA → 摄像机位置            │
│    ↓                                │
│ 3. 渲染                             │
│    - renderLookAtLine() (视线)      │
│    - renderTargetMarker() (Marker)  │
│    - 保存 lastMarkerWorldPos        │
└─────────────────────────────────────┘
    ↓
用户拖动 Marker
    ↓
MarkerDragController.update()
    ↓
检测鼠标悬停 + 拖拽
    ↓
MarkerDragController.updateDrag()
    ↓
┌─────────────────────────────────────┐
│ 1. 从 ActorLookAtSelectionContext 获取 Clip│
│    ↓                                │
│ 2. 更新 Clip.worldPoint             │
│    - 根据射线检测结果更新            │
│    ↓                                │
│ 3. TimelineModel.markDirty()        │
└─────────────────────────────────────┘
    ↓
下一帧 update() → 立即看到效果
```

---

## 🎬 四、完整使用流程（从 0 到 1）

### 步骤 1：创建 Actor

1. 打开 Director UI（按 C 键）
2. 在属性面板中，找到 "🎭 Actors" 区域
3. 点击 "+ Add Actor" 按钮
4. 在弹出的对话框中：
   - 输入演员名称（如"商人A"）
   - 选择演员类型（VILLAGER / COW / PLAYER 等）
5. 点击"创建"

**系统自动完成**：
- ✅ 创建 `Actor` 对象（保存到 `actor_registry.json`）
- ✅ 创建 `EntityTrack`（保存到 `timeline_model.json`）
- ✅ 创建默认 `EntitySpawnClip`（位置 = 当前摄像机位置）
- ✅ `ActorRuntimeController` 自动绑定轨道
- ✅ 下一帧，`PreviewActorEntity` 出现在世界中

---

### 步骤 2：调整演员位置（Gizmo Translate）

1. 在世界中点击 `PreviewActorEntity`（或从时间线选择轨道）
2. Gizmo 自动激活（默认 Translate 模式）
3. 拖动 Gizmo 的箭头（X/Y/Z 轴）
4. 系统自动：
   - 找到或创建 `EntityTransformClip`（覆盖当前时间）
   - 设置 `fromPos` = 当前采样位置
   - 设置 `toPos` = 拖拽后的位置
   - 更新 `TimelineModel`（标记为 dirty）
5. 下一帧，演员立即移动到新位置

---

### 步骤 3：设置演员看向目标（LookAt）

1. 切换到 LookAt 模式（Gizmo Mode = LOOK_AT）
2. 在世界中点击目标点
3. 系统自动：
   - 创建 `EntityLookAtClip`（mode = WORLD_POINT）
   - 设置 `worldPoint` = 点击位置
   - 渲染视线和 Marker
4. 拖动 Marker 微调目标点
5. 系统实时更新 `EntityLookAtClip.worldPoint`
6. 下一帧，演员立即看向新目标

---

### 步骤 4：调整表演节奏（Clip 时间）

1. 在时间线中，找到 `EntityLookAtClip` 的片段
2. 拖动片段的开始/结束边界
3. 系统自动：
   - 更新 `EntityLookAtClip.startTime` / `endTime`
   - 更新 `TimelineModel`（标记为 dirty）
4. 调整时间线播放头位置
5. 系统自动：
   - `ActorRuntimeController.update()` 采样新时间
   - `PreviewActorEntity` 立即更新到新状态

---

### 步骤 5：播放完整表演

1. 点击时间线的"播放"按钮
2. 系统自动：
   - `TimelineModel.currentTime` 自动递增
   - 每帧调用 `ActorRuntimeController.update()`
   - 每帧采样新的 `EntitySample`
   - 每帧更新 `PreviewActorEntity`
3. 演员按照时间线上的 Clips 执行完整表演

---

## 🎯 五、设计优势

### 5.1 长期正确性

1. **Actor 是第一等公民**
   - Actor 不依赖世界，可跨世界使用
   - 可导出、可复制、可重放

2. **Timeline 纯粹描述意图**
   - Timeline 不执行任何行为
   - 可序列化、可版本控制、可协作编辑

3. **Runtime 是函数式的**
   - `time → EntitySample`（纯函数）
   - 无副作用，可预测、可测试

4. **编辑态 = 播放态**
   - 同一套 Runtime 逻辑
   - 编辑时立即看到效果
   - 播放时连续看到效果

5. **世界只是渲染层**
   - 世界不存储任何表演数据
   - 可切换世界、可导出动画、可重放

---

### 5.2 未来扩展性

基于当前架构，未来可以轻松扩展：

1. **导出动画**
   - Timeline → 动画文件（FBX / glTF）
   - 无需修改核心逻辑

2. **重放系统**
   - Timeline → 重放 Timeline
   - 无需修改核心逻辑

3. **切换世界**
   - Timeline 不依赖世界
   - 可在任意世界重放

4. **绑定真实玩家**
   - 扩展 `EntityBinding.BIND_EXISTING`
   - Runtime 应用到真实 Entity

5. **接入 AI 行为**
   - 扩展 `EntityActionClip`
   - 触发 AI 脚本

6. **多人协作**
   - Timeline 可序列化
   - 可版本控制、可合并

---

## 📊 六、模块依赖关系图

```
┌─────────────────────────────────────────────────────────┐
│                   数据层（Data Layer）                    │
│                                                          │
│  ActorRegistry ──────┐                                   │
│       │              │                                   │
│       │              │                                   │
│  Actor ──→ ActorRef ─┼──→ EntityTrack ──→ EntityClip   │
│                       │         │                        │
│                       │         └──→ TimelineTrack       │
│                       │              │                    │
│                       └──────────────┼──→ TimelineModel  │
│                                      │                    │
└──────────────────────────────────────┼──────────────────┘
                                       │
┌──────────────────────────────────────┼──────────────────┐
│                   运行时层（Runtime Layer）               │
│                                      │                    │
│  EntityClipSampler ──→ EntitySample  │                    │
│         ↑                            │                    │
│         │                            │                    │
│  EntityTrack ───────────────────────┘                    │
│         │                                                  │
│         └──→ ActorRuntimeController                       │
│                      │                                    │
│                      ↓                                    │
│              ActorPreviewManager                          │
│                      │                                    │
│                      ↓                                    │
│              PreviewActorEntity                           │
│                                                           │
└───────────────────────────────────────────────────────────┘
                                       │
┌──────────────────────────────────────┼──────────────────┐
│                   交互层（Interaction Layer）             │
│                                      │                    │
│  ActorPickController ──→ ActorSelectionHub               │
│         ↑                            │                    │
│         │                            │                    │
│  PreviewActorEntity ─────────────────┘                    │
│         │                                                  │
│         └──→ ActorGizmoController                        │
│                      │                                    │
│                      ↓                                    │
│              EntityTransformClip                          │
│                                                           │
│  ActorLookAtGizmoController ──→ EntityLookAtClip         │
│         ↑                            │                    │
│         │                            │                    │
│  MarkerDragController ──────────────┘                    │
│         │                                                  │
│         └──→ ActorLookAtVisualizer                        │
│                                                           │
└───────────────────────────────────────────────────────────┘
```

---

## 🔑 七、关键设计决策

### 7.1 为什么 Actor 和 EntityTrack 分离？

**原因**：
- Actor 是"角色概念"，可被多个 Timeline 复用
- EntityTrack 是"表演安排"，绑定到特定 Timeline
- 分离后，Actor 可跨项目使用，EntityTrack 可独立编辑

**类比**：
- Actor = 演员（人）
- EntityTrack = 剧本（表演安排）

---

### 7.2 为什么使用 PreviewActorEntity 而不是真实 Entity？

**原因**：
- 真实 Entity 会污染世界（保存、同步、AI）
- PreviewActorEntity 只存在于客户端，不写世界
- 编辑时，世界保持"干净"，可随时恢复

**优势**：
- 可撤销、可重做
- 可切换世界
- 可导出动画
- 不影响游戏逻辑

---

### 7.3 为什么 Gizmo 直接修改 Clip 而不是 Entity？

**原因**：
- Gizmo 的目标是"编辑 Timeline"，不是"移动 Entity"
- 直接修改 Clip，编辑和播放使用同一套逻辑
- 支持撤销、重做、时间偏移

**优势**：
- 编辑态 = 播放态
- 可时间偏移（整体移动 Clip）
- 可复制、可粘贴 Clip

---

### 7.4 为什么 LookAt 有独立的 Gizmo 和 Visualizer？

**原因**：
- LookAt 是"导演级"功能，需要可视化辅助
- 独立的 Gizmo 和 Visualizer，职责清晰
- Marker 拖拽和强度圆环，提供精确控制

**优势**：
- 职责分离（Gizmo = 编辑，Visualizer = 渲染）
- 可扩展（未来可添加更多可视化）
- 用户体验好（直观、精确）

---

## 📝 八、总结

Director 的 Actor System 是一个**完整的导演级角色表演系统**，具备以下特征：

1. **🎭 Actor 是第一等公民** - 不依赖世界，可跨项目使用
2. **🎬 Timeline 纯粹描述意图** - 可序列化、可导出、可重放
3. **🧠 Runtime 是函数式的** - `time → EntitySample`，无副作用
4. **🧩 编辑态 = 播放态** - 同一套逻辑，立即看到效果
5. **🌍 世界只是渲染层** - 不存储任何表演数据，可切换世界

**核心价值**：
- ✅ 世界从始至终没有被污染
- ✅ 编辑和播放使用同一套逻辑
- ✅ 可导出、可重放、可协作
- ✅ 未来可扩展（AI、真实玩家、多人协作）

**这就是 Director 的"导演级 Actor System"！** 🎬

---

## 🎨 九、Actor 面板在 Director 属性编辑体系中的工作机制

### 9.1 四层属性编辑体系

Director 已经形成了一个清晰的 **4 层属性编辑体系**：

```
┌─────────────────────────────────────────────────────────┐
│  第 1 层：Selection（选择层）                            │
│  SelectionManager.getCurrentSelection()                 │
│  - 管理当前选中的对象（Object）                          │
│  - 触发选择变化事件                                      │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│  第 2 层：PropertyAdapter（适配器层）                    │
│  PropertyAdapterRegistry.getAdapterFor(selection)       │
│  - 根据选择对象类型，自动匹配对应的 PropertyAdapter      │
│  - 支持 4 级查找：精确 → 继承 → supports → fallback     │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│  第 3 层：数据对象（Data Layer）                         │
│  Actor / EntityTrack / EntityClip / TimelineTrack       │
│  - 纯数据对象，可序列化                                  │
│  - 不包含 UI 逻辑                                        │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│  第 4 层：Runtime / Sampler（运行时层）                  │
│  ActorRuntimeController / EntityClipSampler             │
│  - 根据数据对象，生成运行时状态                          │
│  - 驱动 PreviewActorEntity 更新                         │
└─────────────────────────────────────────────────────────┘
```

---

### 9.2 Actor 面板的定位

**核心结论**：

```
Actor 面板 ≠ Clip 面板的升级版
Actor 面板 = Project 级 PropertyAdapter
```

**Actor 面板在整个体系中的位置**：

| 层级 | 选择类型 | PropertyAdapter | 编辑内容 | 不编辑内容 |
|------|---------|----------------|---------|-----------|
| **Project** | `Selection = null` | `ActorSelectorPanel` | Actor 列表、创建/删除 | Transform / LookAt / Animation |
| **Track** | `Selection = TimelineTrack` | `TimelineTrackPropertyAdapter` | Track 开关、混合、颜色 | Actor 基础信息 |
| **Clip** | `Selection = EntityClip` | `Entity*ClipPropertyAdapter` | 精确表演参数 | Actor / Track |

---

### 9.3 Selection 驱动的面板切换规则

**PropertiesPanel 的切换逻辑**：

```java
if (Selection == null) {
    // 无选择 → 显示 ActorSelectorPanel（项目级入口）
    renderNoSelectionMessage();
    actorSelectorPanel.render();  // 🎭 Actors 列表
}
else if (Selection instanceof TimelineTrack) {
    // 选中轨道 → TimelineTrackPropertyAdapter
    TimelineTrackPropertyAdapter.render(track);
    if (track.getType() == ENTITY) {
        // 额外显示 Actor 快捷操作（如 "Look At Camera"）
    }
}
else if (Selection instanceof EntityClip) {
    // 选中 Clip → 对应的 Entity*ClipPropertyAdapter
    EntityTransformClipPropertyAdapter.render(clip);
    // 或 EntityLookAtClipPropertyAdapter
    // 或 EntityAnimationClipPropertyAdapter
    // ...
}
```

---

### 9.4 Actor 面板的打开时机

#### 时机 1：默认打开（第一次进 Director）

```
用户打开 Director UI
    ↓
SelectionManager.getCurrentSelection() == null
    ↓
PropertiesPanel.renderNoSelectionMessage()
    ↓
显示 ActorSelectorPanel
    ├─ 🎭 Actors 列表
    ├─ "+ Add Actor" 按钮
    └─ 支持的对象类型列表
```

**这是引导演员创建的入口**。

---

#### 时机 2：点击 Actor 列表中的 Actor

```
用户点击 ActorSelectorPanel 中的 Actor 项
    ↓
（当前实现：TODO - 选择 Actor 后可以显示其详细信息）
    ↓
未来实现：
    SelectionManager.setCurrentSelection(actor)
    ↓
PropertiesPanel 切换到 ActorPropertyAdapter
    ├─ Actor 基础信息（name, type）
    ├─ 关联的 EntityTrack 列表
    └─ 删除 Actor 按钮
```

**⚠️ 注意**：
- 这一步不会选中 Track，也不会选中 Clip
- Actor 面板只显示 Actor 的基础信息
- 要编辑表演参数，需要选中对应的 Clip

---

#### 时机 3：在世界中点击演员（PreviewActorEntity）

**这是系统中最完整的一条链路**：

```
用户点击世界中的 PreviewActorEntity
    ↓
ActorPickController.update()
    ├─ 检测鼠标点击
    ├─ 射线检测
    └─ 找到 PreviewActorEntity
    ↓
ActorPreviewManager.getActorId(entity)
    ↓
ActorSelectionHub.selectActor(actorId)
    ↓
┌─────────────────────────────────────┐
│ 1. TimelineModel.findEntityTrackByActorId()│
│    → 找到对应的 EntityTrack         │
│    ↓                                │
│ 2. TimelineModel.selectItem(track)  │
│    → 时间线选中该轨道               │
│    ↓                                │
│ 3. SelectionManager.setCurrentSelection(track)│
│    → 属性面板显示轨道属性           │
│    ↓                                │
│ 4. ActorGizmoController.setTargetActor(actorId)│
│    → Gizmo 激活                     │
│    ↓                                │
│ 5. ActorLookAtGizmoController.setTargetActor(actorId)│
│    → LookAt Gizmo 激活              │
└─────────────────────────────────────┘
    ↓
PropertiesPanel 显示 TimelineTrackPropertyAdapter
    ├─ 轨道基础属性
    ├─ Actor 快捷操作（"Look At Camera"）
    └─ 如果选中 Clip，切换到 Clip PropertyAdapter
```

**👉 Actor 面板是"世界选中演员"的第一反馈**。

---

### 9.5 Actor 面板 vs Clip 面板的职责边界

**对照表**：

| 面板 | 编辑什么 | 不编辑什么 | 何时显示 |
|------|---------|-----------|---------|
| **🎭 Actor 面板** | Actor 基础信息（name, type）、创建/删除 Actor | Transform / LookAt / Animation | `Selection == null` 或 `Selection == Actor` |
| **Track 面板** | Track 开关、混合模式、轨道颜色 | Actor 基础信息、Clip 参数 | `Selection == TimelineTrack` |
| **Clip 面板** | 精确表演参数（position, rotation, lookAt, animation） | Actor / Track | `Selection == EntityClip` |

**为什么要这样拆？**

因为所有"表演参数"都已经放进 Clip 里了：
- `EntityTransformClip` → 位置/旋转/缩放
- `EntityLookAtClip` → 看向目标
- `EntityAnimationClip` → 动画播放
- `EntityStateClip` → 持续状态
- `EntityActionClip` → 一次性事件

所以 Actor 面板不应该重复这些能力，它只负责：
- **管理演员列表**（创建、删除、重命名）
- **显示 Actor 基础信息**（name, type）

---

### 9.6 完整协作流程示例

**🎬 导演真实操作流程（系统内部发生的事）**

#### Step 1：创建演员

```
用户点击 "+ Add Actor"
    ↓
ActorSelectorPanel.createActorAndTrack()
    ├─ ActorRegistry.createActor() → actor_registry.json
    ├─ TimelineModel.addTrack() → timeline_model.json
    └─ EntityTrack.addDefaultSpawnClip()
    ↓
Selection = null（保持）
    ↓
PropertiesPanel 继续显示 ActorSelectorPanel
    ├─ Actor 列表更新（新增一个 Actor）
    └─ Clip 面板还没出现
```

**此时状态**：
- ✅ Actor 已创建
- ✅ EntityTrack 已创建
- ✅ SpawnClip 已创建
- ✅ Gizmo 已经可用（因为 Track 已创建）
- ❌ Clip 面板还没出现（因为还没有选中 Clip）

---

#### Step 2：拖动演员（Translate Gizmo）

```
用户拖动 Gizmo（Translate 模式）
    ↓
ActorGizmoController.handleGizmoDragStart()
    ├─ 找到或创建 EntityTransformClip（覆盖当前时间）
    ├─ 设置 fromPos = 当前采样位置
    └─ 设置 toPos = 拖拽后的位置
    ↓
TimelineModel.markDirty()
    ↓
SelectionManager.setCurrentSelection(transformClip)
    ↓
PropertiesPanel 自动切换
    ├─ TimelineTrackPropertyAdapter 退场
    └─ EntityTransformClipPropertyAdapter 接管
        ├─ fromPos / toPos 编辑
        ├─ interpolation 选择
        └─ fromRot / toRot / fromScale / toScale
```

**👉 Actor 面板自动"退场"，Clip 面板接管**。

---

#### Step 3：切换到 LookAt，点击目标

```
用户切换到 LookAt 模式，点击世界中的点
    ↓
ActorLookAtGizmoController.createOrUpdateLookAtClip()
    ├─ 创建 EntityLookAtClip
    ├─ mode = WORLD_POINT
    └─ worldPoint = 点击位置
    ↓
SelectionManager.setCurrentSelection(lookAtClip)
    ↓
PropertiesPanel 自动切换
    ├─ EntityTransformClipPropertyAdapter 退场
    └─ EntityLookAtClipPropertyAdapter 接管
        ├─ mode 选择（WORLD_POINT / CAMERA / ENTITY_UUID）
        ├─ worldPoint 编辑
        └─ strength 滑块
```

---

#### Step 4：点击回演员

```
用户在世界中点击 PreviewActorEntity
    ↓
ActorSelectionHub.selectActor(actorId)
    ├─ TimelineModel.selectItem(track)
    └─ SelectionManager.setCurrentSelection(track)
    ↓
PropertiesPanel 自动切换
    ├─ EntityLookAtClipPropertyAdapter 退场
    └─ TimelineTrackPropertyAdapter 接管
        ├─ 轨道基础属性
        └─ Actor 快捷操作（"Look At Camera"）
```

**👉 这就是"流畅"的本质：Selection 驱动**。

---

### 9.7 为什么 Actor 面板必须基于 Project 级

**关键原因**：

```
Actor 的生命周期 > Timeline
```

**未来扩展场景**：

1. **一个 Actor 出现在多个 Timeline**
   ```
   Actor "商人A"
   ├─ Timeline "场景1" → EntityTrack1
   └─ Timeline "场景2" → EntityTrack2
   ```

2. **切换 Timeline 不丢演员**
   ```
   切换到 Timeline "场景2"
   → Actor "商人A" 仍然存在
   → 只是 EntityTrack2 被激活
   ```

3. **复制 Timeline 不复制 Actor**
   ```
   复制 Timeline "场景1"
   → 创建新的 Timeline "场景1_copy"
   → EntityTrack1_copy 引用同一个 Actor "商人A"
   → Actor 不会被复制
   ```

**如果 Actor 面板挂在 Track / Clip 下，这一切都会被破坏**。

---

### 9.8 系统级真相

**Director 的实体轨道系统不是**：
```
"选中实体 → 改参数"
```

**而是**：
```
"选中演员 → 安排表演 → 运行时投影"
```

**Actor 面板，正是这个系统的导演入口**。

---

### 9.9 完整的数据流图

```
┌─────────────────────────────────────────────────────────┐
│                   用户交互层                             │
│                                                          │
│  世界点击 / UI 点击 / 时间线点击                          │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│                   Selection 层                          │
│                                                          │
│  SelectionManager.getCurrentSelection()                  │
│  ├─ null → ActorSelectorPanel                          │
│  ├─ Actor → ActorPropertyAdapter（未来）                │
│  ├─ TimelineTrack → TimelineTrackPropertyAdapter        │
│  └─ EntityClip → Entity*ClipPropertyAdapter             │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│                 PropertyAdapter 层                       │
│                                                          │
│  PropertiesPanel.render()                                │
│  ├─ 根据 Selection 类型，自动匹配 Adapter               │
│  ├─ 渲染对应的属性编辑 UI                               │
│  └─ 属性修改 → 直接更新数据对象                          │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│                   数据对象层                             │
│                                                          │
│  Actor / EntityTrack / EntityClip                        │
│  ├─ 纯数据，可序列化                                    │
│  ├─ 不包含 UI 逻辑                                      │
│  └─ TimelineModel.markDirty()                           │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│                  Runtime / Sampler 层                    │
│                                                          │
│  ActorRuntimeController.update()                        │
│  ├─ EntityClipSampler.sampleAt(time)                    │
│  ├─ EntitySample                                        │
│  └─ PreviewActorEntity 更新                             │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│                   呈现层                                 │
│                                                          │
│  Minecraft World（渲染）                                 │
│  ├─ PreviewActorEntity 显示                             │
│  ├─ ActorLookAtVisualizer 显示                          │
│  └─ Gizmo 显示                                          │
└─────────────────────────────────────────────────────────┘
```

---

### 9.10 总结

**Actor 面板在 Director 属性编辑体系中的核心价值**：

1. **🎭 项目级入口** - 管理整个项目的演员列表
2. **🔄 Selection 驱动** - 与 Clip 面板无缝切换
3. **🎬 导演入口** - "选中演员 → 安排表演 → 运行时投影"
4. **🧩 职责清晰** - 只管理 Actor，不编辑表演参数
5. **🌍 世界反馈** - 世界选中演员的第一反馈

**这就是 Director 的"Selection → PropertyAdapter → 数据对象 → Runtime"完整体系！** 🎨

