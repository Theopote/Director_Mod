# ChronoBlocks Actor System · 完整架构文档

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

**位置**：`com.chronoblocks.actor.Actor`

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

**位置**：`com.chronoblocks.ui.timeline.model.entity.EntityTrack`

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

**位置**：`com.chronoblocks.ui.timeline.model.entity.EntityClip`（抽象基类）

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

**位置**：`com.chronoblocks.ui.timeline.model.TimelineModel`

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

**位置**：`com.chronoblocks.ui.timeline.playback.entity.EntityClipSampler`

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

**位置**：`com.chronoblocks.ui.timeline.playback.entity.EntitySample`

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

**位置**：`com.chronoblocks.actor.runtime.ActorRuntimeController`

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

**位置**：`com.chronoblocks.actor.preview.PreviewActorEntity`

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

**位置**：`com.chronoblocks.actor.preview.ActorPreviewManager`

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

1. 打开 ChronoBlocks UI（按 C 键）
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

ChronoBlocks 的 Actor System 是一个**完整的导演级角色表演系统**，具备以下特征：

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

**这就是 ChronoBlocks 的"导演级 Actor System"！** 🎬

