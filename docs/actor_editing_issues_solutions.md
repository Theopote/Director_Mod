# Actor 编辑系统问题解决方案

## 一、问题 1：Gizmo 拖动问题

### 问题描述

使用选择演员工具选中演员时，Gizmo 不能被拖动。

### 根本原因

**`ActorGizmoController.updateGizmoPosition()`** 激活 Gizmo 时，没有设置 `ActorTransformApplier`。

**当前代码**（229-235行）：
```java
if (!GizmoManager.get().isActive()) {
    GizmoManager.get().activate(GizmoType.TRANSFORM, box, pivot);
    // ⚠️ 问题：没有设置 TransformApplier
    if (GizmoManager.get().getContext() != null) {
        GizmoManager.get().getContext().setMode(currentMode);
    }
}
```

**`GizmoInteractionController`** 默认创建的是普通的 `TransformApplier`：
```java
public GizmoInteractionController(GizmoContext context) {
    this.context = context;
    // ⚠️ 问题：使用默认的 TransformApplier，不是 ActorTransformApplier
    this.transformApplier = new TransformApplier(
        com.chronoblocks.core.selection.SelectionManager.get()
    );
}
```

### 解决方案

**方案 A：在 `updateGizmoPosition()` 中设置 `ActorTransformApplier`**（推荐）

```java
private void updateGizmoPosition(Entity entity) {
    // ... 现有代码 ...
    
    if (!GizmoManager.get().isActive()) {
        GizmoManager.get().activate(GizmoType.TRANSFORM, box, pivot);
        
        // ⭐ 设置 ActorTransformApplier
        GizmoInteractionController controller = GizmoManager.get().getController();
        if (controller != null) {
            com.chronoblocks.core.selection.SelectionManager selectionManager =
                com.chronoblocks.core.selection.SelectionManager.get();
            ActorTransformApplier actorApplier = new ActorTransformApplier(selectionManager, this);
            controller.setTransformApplier(actorApplier);
        }
        
        if (GizmoManager.get().getContext() != null) {
            GizmoManager.get().getContext().setMode(currentMode);
        }
    } else {
        // 已激活时也需要检查 TransformApplier 是否正确设置
        GizmoInteractionController controller = GizmoManager.get().getController();
        if (controller != null && !(controller.getTransformApplier() instanceof ActorTransformApplier)) {
            com.chronoblocks.core.selection.SelectionManager selectionManager =
                com.chronoblocks.core.selection.SelectionManager.get();
            ActorTransformApplier actorApplier = new ActorTransformApplier(selectionManager, this);
            controller.setTransformApplier(actorApplier);
        }
        
        // ... 现有代码 ...
    }
}
```

**需要的修改**：
1. `GizmoManager` 需要提供 `getController()` 方法（或通过其他方式访问）
2. `GizmoInteractionController` 需要提供 `getTransformApplier()` 方法

## 二、问题 2：路径工具深化

### 问题描述

演员路径工具需要深化，可以左键在 Minecraft 中通过点击绘制路径曲线，类似摄像机轨迹。

### 现状分析

**现有实现**：
- `ActorPathController` 已支持左键点击添加路径点（✅ 已完成）
- 但路径是**直线段**（每两个点之间线性插值）
- 最终转换为多个 `TransformClip`（每个段一个 Clip）

**用户需求**：
- **曲线插值**（Bezier / Catmull-Rom），而不是直线段
- **可视化预览**路径曲线（类似 `CameraPathDebugRenderer`）
- **路径对象**（可复用，类似 CameraPath）

### 解决方案

**方案：参考 `TimelineCameraPathManager` 的实现**

#### 1. 创建 `ActorPath` 数据结构

```java
public class ActorPath {
    UUID id;
    String name;
    List<Vec3d> controlPoints;
    PathInterpolation interpolation;  // LINEAR, CATMULL_ROM, BEZIER
    
    Vec3d sampleAt(float t);  // t: [0, 1]
    float getLength();
}
```

#### 2. 修改 `EntityTransformClip` 支持路径绑定

```java
public class EntityTransformClip extends EntityClip {
    // 原有字段
    private Vector3f fromPos;
    private Vector3f toPos;
    
    // ⭐ 新增：路径支持
    private String pathId = null;        // 绑定的路径ID
    private boolean followPath = false;  // 是否沿着路径移动
    private float pathStartT = 0f;       // 路径起始参数 [0, 1]
    private float pathEndT = 1f;         // 路径结束参数 [0, 1]
}
```

#### 3. 修改 `ActorPathController`：创建路径对象而不是多个 Clip

**当前逻辑**（191-216行）：
```java
for (int i = 0; i < pathPoints.size() - 1; i++) {
    // 为每两个点创建一个 TransformClip（直线段）
    EntityTransformClip clip = new EntityTransformClip(...);
    clip.setFromPos(...);
    clip.setToPos(...);
    entityTrack.addClip(clip);
}
```

**改为**：
```java
// 创建路径对象
ActorPath path = new ActorPath(pathPoints, PathInterpolation.CATMULL_ROM);
PathRegistry.register(path);

// 创建一个 TransformClip，绑定到路径
EntityTransformClip clip = new EntityTransformClip("Path", startTime, endTime);
clip.setPathId(path.getId());
clip.setFollowPath(true);
clip.setPathStartT(0f);
clip.setPathEndT(1f);
entityTrack.addClip(clip);
```

#### 4. 修改 `EntityClipSampler` 支持路径采样

```java
// 在 sampleTransform() 中
if (clip.followPath() && clip.getPathId() != null) {
    ActorPath path = PathRegistry.get(clip.getPathId());
    // 将 Clip 时间映射到路径参数 [pathStartT, pathEndT]
    float t = mapTimeToPathParam(time, clip.getPathStartT(), clip.getPathEndT());
    Vec3d pos = path.sampleAt(t);
    sample.position = new Vec3f((float) pos.x, (float) pos.y, (float) pos.z);
} else {
    // 使用原有的 fromPos/toPos 线性插值
    sample.position = lerp(clip.getFromPos(), clip.getToPos(), time);
}
```

#### 5. 添加路径可视化渲染

创建 `ActorPathDebugRenderer`（参考 `CameraPathDebugRenderer`），在绘制路径曲线。

## 三、问题 3：片段关键帧

### 问题描述

片段在轨道上的时候，可以给片段设置关键帧。

### 现状分析

**现有实现**：
- `EntityClipKeyframe` 已存在（存储位置、旋转、缩放、行为）
- `TimelineClip` 有关键帧管理方法（`addKeyframe()`, `removeKeyframe()`）
- `EntityTimelineClip` 是桥接，但关键帧同步机制不明确

**问题**：
- `EntityClip` 没有关键帧管理方法
- 如何为 `EntityClip` 添加关键帧？
- 关键帧如何与 `EntityTimelineClip` 同步？

### 解决方案

#### 1. 在 `EntityClip` 中添加关键帧管理

```java
public abstract class EntityClip {
    // ⭐ 新增：关键帧列表
    private final List<EntityClipKeyframe> keyframes = new ArrayList<>();
    
    /**
     * 添加关键帧
     */
    public void addKeyframe(EntityClipKeyframe keyframe) {
        if (keyframe == null) {
            return;
        }
        // 确保关键帧在 Clip 时间范围内
        float clipTime = keyframe.getTime() - this.startTime;
        if (clipTime < 0 || clipTime > getLength()) {
            return;
        }
        // 调整 keyframe.time 为相对时间（相对于 Clip 开始时间）
        keyframe.setTime(clipTime);
        keyframes.add(keyframe);
        keyframes.sort(Comparator.comparing(EntityClipKeyframe::getTime));
        syncTimelineClip();
    }
    
    /**
     * 移除关键帧
     */
    public boolean removeKeyframe(UUID keyframeId) {
        boolean removed = keyframes.removeIf(kf -> kf.getId().equals(keyframeId));
        if (removed) {
            syncTimelineClip();
        }
        return removed;
    }
    
    /**
     * 获取所有关键帧
     */
    public List<EntityClipKeyframe> getKeyframes() {
        return Collections.unmodifiableList(keyframes);
    }
    
    /**
     * 查找指定时间的关键帧
     */
    public EntityClipKeyframe findKeyframeAt(float time, float epsilon) {
        // time 是全局时间，需要转换为相对时间
        float relativeTime = time - this.startTime;
        for (EntityClipKeyframe kf : keyframes) {
            if (Math.abs(kf.getTime() - relativeTime) <= epsilon) {
                return kf;
            }
        }
        return null;
    }
}
```

#### 2. 在 `EntityTimelineClip` 中实现关键帧同步

```java
public class EntityTimelineClip extends TimelineClip {
    // ⭐ 同步 EntityClip 关键帧到 TimelineClip
    public void syncKeyframesFromEntityClip() {
        List<EntityClipKeyframe> entityKeyframes = entityClip.getKeyframes();
        if (entityKeyframes == null || entityKeyframes.isEmpty()) {
            return;
        }
        
        // 清空现有关键帧
        for (ClipKeyframe kf : new ArrayList<>(this.getClipKeyframes())) {
            this.removeKeyframe(kf.getId());
        }
        
        // 添加 EntityClip 的关键帧（时间需要转换为全局时间）
        for (EntityClipKeyframe ekf : entityKeyframes) {
            float globalTime = entityClip.getStartTime() + ekf.getTime();
            ClipKeyframe kf = new ClipKeyframe(globalTime);
            kf.setInterpolation(ekf.getInterpolation());
            this.addKeyframe(kf);
        }
    }
}
```

#### 3. Timeline 交互：双击添加关键帧

**当前实现**（`TimelinePanel.handleKeyframeDoubleClick`）：
- 已支持双击添加关键帧
- 但只适用于普通 `TimelineClip`

**需要扩展**：
- 如果选中的是 `EntityTimelineClip`，创建 `EntityClipKeyframe` 而不是普通 `ClipKeyframe`
- 时间需要转换为相对时间（相对于 Clip 开始时间）

```java
if (clip instanceof EntityTimelineClip entityTimelineClip) {
    EntityClip entityClip = entityTimelineClip.getEntityClip();
    
    // 创建 EntityClipKeyframe
    float relativeTime = time - entityClip.getStartTime();
    EntityClipKeyframe keyframe = new EntityClipKeyframe(relativeTime);
    
    // 采样当前状态作为初始值
    EntityClipSampler sampler = new EntityClipSampler(
        timelineTrack.getEntityTrackPayload()
    );
    EntitySample sample = sampler.sampleAt(time);
    if (sample != null) {
        keyframe.setPosition(sample.position.x, sample.position.y, sample.position.z);
        keyframe.setRotation(sample.rotation.x, sample.rotation.y, sample.rotation.z);
        // ...
    }
    
    entityClip.addKeyframe(keyframe);
}
```

#### 4. 修改 `EntityClipSampler` 支持关键帧插值

```java
// 在 sampleTransform() 中，优先使用关键帧
List<EntityClipKeyframe> keyframes = clip.getKeyframes();
if (!keyframes.isEmpty()) {
    // 找到时间前后两个关键帧
    EntityClipKeyframe prev = findPrevKeyframe(time, keyframes);
    EntityClipKeyframe next = findNextKeyframe(time, keyframes);
    
    if (prev != null && next != null) {
        // 插值位置、旋转、缩放
        float t = (time - prev.getTime()) / (next.getTime() - prev.getTime());
        sample.position = lerp(prev.getPosition(), next.getPosition(), t);
        sample.rotation = slerp(prev.getRotation(), next.getRotation(), t);
        sample.scale = lerp(prev.getScale(), next.getScale(), t);
        return; // 使用关键帧，不再使用 TransformClip 的 fromPos/toPos
    }
}

// 如果没有关键帧，使用 TransformClip 的 fromPos/toPos 插值（原有逻辑）
```

## 四、实施优先级

### 优先级 1：Gizmo 拖动问题
- **影响**：直接影响用户体验
- **工作量**：小（只需修改 `updateGizmoPosition()`）
- **风险**：低

### 优先级 2：片段关键帧
- **影响**：核心编辑功能
- **工作量**：中（需要修改多个类）
- **风险**：中（需要确保同步机制正确）

### 优先级 3：路径工具深化
- **影响**：增强功能
- **工作量**：大（需要创建路径系统）
- **风险**：中（可以渐进实现）

## 五、技术细节

### Gizmo 拖动检查清单
- [ ] `GizmoManager` 是否提供 `getController()` 方法
- [ ] `GizmoInteractionController` 是否提供 `getTransformApplier()` 方法
- [ ] `updateGizmoPosition()` 中是否设置了 `ActorTransformApplier`
- [ ] `ActorTransformApplier.applyMove()` 是否被正确调用

### 关键帧检查清单
- [ ] `EntityClip` 是否有关键帧管理方法
- [ ] `EntityTimelineClip` 是否实现了关键帧同步
- [ ] `EntityClipSampler` 是否支持关键帧插值
- [ ] Timeline 交互是否支持"双击添加 EntityClipKeyframe"
- [ ] 是否有关键帧属性编辑器（`EntityClipKeyframePropertyAdapter`）

### 路径工具检查清单
- [ ] `ActorPath` 数据结构是否创建
- [ ] `PathRegistry` 是否实现（路径管理）
- [ ] `EntityTransformClip` 是否支持路径绑定
- [ ] `EntityClipSampler` 是否支持路径采样
- [ ] 路径可视化渲染是否实现
