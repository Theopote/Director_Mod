# Actor Gizmo 和选区问题系统分析

## 一、问题描述

1. **Gizmo拖动不顺畅**：拖动演员时响应延迟、卡顿
2. **Gizmo箭头和旋转框分离**：Gizmo 的轴箭头和旋转环位置不一致
3. **放置位置总有方块选区**：选中演员时总是出现一个方块大小的选区

## 二、系统架构分析

### 2.1 当前调用链

```
选中 Actor
  ↓
ActorSelectionHub.selectActor(actorId)
  ├─ SelectionManager.selectActor(actorId)  // ⚠️ 可能触发方块选区
  └─ ActorGizmoController.setTargetActor(actorId)
       └─ resolveTargetTrack()
           └─ (每帧) update()
               └─ updateGizmoPosition()
                   └─ GizmoManager.setTargetBox(box)  // ⚠️ 每帧调用
```

### 2.2 问题根源分析

#### 问题 1：Gizmo拖动不顺畅

**可能原因**：
1. `updateGizmoPosition()` 每帧调用 `setTargetBox()`，即使实体位置没变
2. `setTargetBox()` 可能触发 Gizmo 重建，导致拖动中断
3. `ActorTransformApplier.applyMove()` 中的增量计算可能有问题

**检查点**：
- `ActorGizmoController.update()` 每帧都调用 `updateGizmoPosition()`
- `updateGizmoPosition()` 即使 Gizmo 已激活也调用 `setTargetBox()`
- `setTargetBox()` 可能触发 handle 重建

#### 问题 2：Gizmo箭头和旋转框分离

**可能原因**：
1. Gizmo 激活后，`updateGizmoPosition()` 频繁调用 `setTargetBox()` 更新 Box
2. 但 handle 位置（箭头、旋转环）可能没有正确更新
3. `GizmoInteractionController` 的 handle 位置计算可能与 Box 不同步

**检查点**：
- `GizmoManager.setTargetBox()` 只更新 Box，不更新 handle 位置
- `GizmoInteractionController.updateHandlePositions()` 可能没有正确调用

#### 问题 3：放置位置总有方块选区

**可能原因**：
1. `SelectionManager.selectActor()` 可能触发了方块选区的创建
2. Gizmo 激活时，Box 被转换为方块选区（通过 `GizmoManager.apply()`）
3. 方块选区没有被正确清除

**检查点**：
- `SelectionManager.selectActor()` 是否创建了方块选区
- `ActorGizmoController.setTargetActor()` 中是否清除了选区
- `GizmoManager.activate()` 是否触发了 `apply()`

## 三、设计缺陷分析

### 3.1 Gizmo 位置更新策略

**当前设计**：
```java
// 每帧都调用
updateGizmoPosition() {
    if (!isActive()) {
        activate(...);
    } else {
        setTargetBox(box);  // ⚠️ 每帧调用
    }
}
```

**问题**：
- `setTargetBox()` 即使 Box 没变也会执行
- 频繁调用可能导致性能问题
- 可能干扰正在进行的拖动操作

**建议**：
- 只在 Box 真正变化时调用 `setTargetBox()`
- 比较当前 Box 和新 Box，只在差异超过阈值时更新

### 3.2 方块选区管理混乱

**当前设计**：
- `SelectionManager.selectActor()` - UI 选择系统
- `com.chronoblocks.core.selection.SelectionManager` - 方块选区系统
- 两者可能互相干扰

**问题**：
- Actor 选择不应该创建方块选区
- 方块选区应该只在框选工具激活时存在

**建议**：
- `SelectionManager.selectActor()` 不应该影响方块选区
- `ActorGizmoController` 激活/停用时明确清除方块选区

### 3.3 Gizmo Handle 位置同步

**当前设计**：
- `setTargetBox()` 只更新 `GizmoContext.targetBox`
- Handle 位置由 `GizmoInteractionController` 管理
- 可能存在不同步

**问题**：
- Box 更新后，handle 位置可能没有立即更新
- 旋转环的位置可能与 Box 中心不匹配

**建议**：
- `setTargetBox()` 后立即调用 `updateHandlePositions()`
- 或者确保 `GizmoInteractionController.tick()` 正确更新 handle 位置

## 四、修复方案

### 4.1 修复 Gizmo 拖动不顺畅

**方案 1：优化 `updateGizmoPosition()` 调用频率**

```java
private Box lastGizmoBox = null;
private Vec3d lastGizmoPivot = null;
private static final double BOX_CHANGE_THRESHOLD = 0.001;

private void updateGizmoPosition(Entity entity) {
    Box box = entity.getBoundingBox();
    if (box == null) {
        return;
    }

    Vec3d pivot = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
    
    // ⭐ 只在 Box 或 Pivot 真正变化时更新
    boolean boxChanged = lastGizmoBox == null || 
                         !box.equals(lastGizmoBox, BOX_CHANGE_THRESHOLD);
    boolean pivotChanged = lastGizmoPivot == null || 
                          lastGizmoPivot.squaredDistanceTo(pivot) > BOX_CHANGE_THRESHOLD * BOX_CHANGE_THRESHOLD;
    
    if (!boxChanged && !pivotChanged && GizmoManager.get().isActive()) {
        return; // 没有变化，跳过更新
    }

    if (!GizmoManager.get().isActive()) {
        GizmoManager.get().activate(GizmoType.TRANSFORM, box, pivot);
        // ... 设置 ActorTransformApplier ...
    } else {
        // ⭐ 只在真正变化时更新
        if (boxChanged) {
            GizmoManager.get().setTargetBox(box);
        }
        if (pivotChanged) {
            GizmoManager.get().setPivot(pivot);
        }
    }
    
    lastGizmoBox = box;
    lastGizmoPivot = pivot;
}
```

**方案 2：拖动时暂停位置更新**

```java
private void updateGizmoPosition(Entity entity) {
    // ⭐ 拖动时不要更新位置，避免干扰
    if (dragging) {
        return;
    }
    // ... 原有逻辑 ...
}
```

### 4.2 修复 Gizmo 箭头和旋转框分离

**方案：确保 handle 位置同步**

```java
// 在 GizmoManager.setTargetBox() 后
// 或者在 GizmoInteractionController.tick() 中
public void updateHandlePositions() {
    if (context == null || handles == null) {
        return;
    }
    
    Box box = context.getTargetBox();
    Vec3d pivot = context.getPivot();
    
    // 更新所有 handle 的位置
    for (GizmoHandle handle : handles) {
        // 根据 box 和 pivot 更新 handle 位置
        handle.updatePosition(box, pivot);
    }
    
    // 更新旋转环位置
    for (RotationHandle rotHandle : rotationHandles) {
        rotHandle.updatePosition(pivot);
    }
}
```

### 4.3 修复方块选区问题

**方案：明确清除方块选区**

```java
// 在 ActorSelectionHub.selectActor() 中
public static void selectActor(UUID actorId) {
    // ... 现有逻辑 ...
    
    // ⭐ 明确清除方块选区（Actor 选择不应该有方块选区）
    try {
        com.chronoblocks.core.selection.SelectionManager blockSelectionManager = 
            com.chronoblocks.core.selection.SelectionManager.get();
        if (blockSelectionManager != null) {
            blockSelectionManager.clearSelection();
        }
    } catch (Exception e) {
        LOGGER.debug("[ActorSelectionHub] 清除方块选区失败: {}", e.getMessage());
    }
    
    // ... 其他逻辑 ...
}

// 在 ActorGizmoController.setTargetActor() 中
public void setTargetActor(UUID actorId) {
    // ... 现有逻辑 ...
    
    // ⭐ 清除方块选区（Gizmo 激活前）
    try {
        com.chronoblocks.core.selection.SelectionManager blockSelectionManager = 
            com.chronoblocks.core.selection.SelectionManager.get();
        if (blockSelectionManager != null) {
            blockSelectionManager.clearSelection();
        }
    } catch (Exception e) {
        LOGGER.debug("[ActorGizmoController] 清除方块选区失败: {}", e.getMessage());
    }
    
    resolveTargetTrack();
}
```

## 五、实施优先级

1. **高优先级**：修复方块选区问题（影响用户体验）
2. **中优先级**：优化 Gizmo 位置更新（提升性能）
3. **中优先级**：修复 Gizmo handle 同步（修复视觉问题）

## 六、检查清单

- [ ] `SelectionManager.selectActor()` 是否创建了方块选区
- [ ] `ActorGizmoController.updateGizmoPosition()` 是否每帧都更新 Box
- [ ] `GizmoManager.setTargetBox()` 是否触发 handle 重建
- [ ] `GizmoInteractionController.updateHandlePositions()` 是否正确调用
- [ ] 拖动时是否还在更新 Gizmo 位置
