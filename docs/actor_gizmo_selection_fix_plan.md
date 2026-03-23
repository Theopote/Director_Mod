# Actor Gizmo 和选区问题修复方案

## 一、问题根源分析

### 问题 1：方块选区总是出现

**根源**：
- `ActorGizmoController.updateGizmoPosition()` 激活 Gizmo 时，可能触发了方块选区的创建
- `GizmoInteractionController.updateBoxFromSelection()` 会从方块选区更新 Box
- 如果方块选区存在，Actor 选中时会错误地更新 Gizmo Box

**修复**：
- 在 `ActorSelectionHub.selectActor()` 中明确清除方块选区
- 在 `ActorGizmoController.setTargetActor()` 中清除方块选区
- 确保 `GizmoInteractionController` 不会在 TRANSFORM Gizmo 时从方块选区更新 Box

### 问题 2：Gizmo 拖动不顺畅

**根源**：
- `updateGizmoPosition()` 每帧调用 `setTargetBox()`，即使实体位置没变
- `setTargetBox()` 可能触发不必要的更新
- 拖动时不应该更新 Gizmo 位置

**修复**：
- 拖动时暂停位置更新
- 只在 Box 真正变化时更新

### 问题 3：Gizmo 箭头和旋转框分离

**根源**：
- `updateHandlePositions()` 只更新 CenterHandle
- AxisHandle 和 PlaneHandle 的位置需要根据 Box 和轴方向计算
- 旋转环的位置需要以 pivot 为中心

**修复**：
- 改进 `updateHandlePositions()` 正确更新所有 handle 位置
- 确保旋转环位置正确

## 二、修复实施

### 修复 1：清除方块选区

```java
// ActorSelectionHub.selectActor()
public static void selectActor(UUID actorId) {
    // ... 现有逻辑 ...
    
    // ⭐ 明确清除方块选区（Actor 选择不应该有方块选区）
    try {
        com.director.core.selection.SelectionManager blockSelectionManager = 
            com.director.core.selection.SelectionManager.get();
        if (blockSelectionManager != null) {
            blockSelectionManager.clearSelection();
        }
    } catch (Exception e) {
        LOGGER.debug("[ActorSelectionHub] 清除方块选区失败: {}", e.getMessage());
    }
    
    // ... 其他逻辑 ...
}

// ActorGizmoController.setTargetActor()
public void setTargetActor(UUID actorId) {
    // ⭐ 清除方块选区（Gizmo 激活前）
    try {
        com.director.core.selection.SelectionManager blockSelectionManager = 
            com.director.core.selection.SelectionManager.get();
        if (blockSelectionManager != null) {
            blockSelectionManager.clearSelection();
        }
    } catch (Exception e) {
        LOGGER.debug("[ActorGizmoController] 清除方块选区失败: {}", e.getMessage());
    }
    
    this.targetActorId = actorId;
    // ... 其他逻辑 ...
}
```

### 修复 2：优化 Gizmo 位置更新

```java
// ActorGizmoController.updateGizmoPosition()
private Box lastGizmoBox = null;
private Vec3d lastGizmoPivot = null;
private static final double BOX_CHANGE_THRESHOLD = 0.001;

private void updateGizmoPosition(Entity entity) {
    // ⭐ 拖动时不要更新位置，避免干扰
    if (dragging) {
        return;
    }
    
    Box box = entity.getBoundingBox();
    if (box == null) {
        return;
    }

    Vec3d pivot = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
    
    // ⭐ 只在 Box 或 Pivot 真正变化时更新
    boolean boxChanged = lastGizmoBox == null || 
                         !boxesEqual(box, lastGizmoBox);
    boolean pivotChanged = lastGizmoPivot == null || 
                          lastGizmoPivot.squaredDistanceTo(pivot) > BOX_CHANGE_THRESHOLD * BOX_CHANGE_THRESHOLD;
    
    if (!boxChanged && !pivotChanged && GizmoManager.get().isActive()) {
        return; // 没有变化，跳过更新
    }

    if (!GizmoManager.get().isActive()) {
        GizmoManager.get().activate(GizmoType.TRANSFORM, box, pivot);
        // ... 设置 ActorTransformApplier ...
    } else {
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

### 修复 3：防止 GizmoInteractionController 从方块选区更新 Box

```java
// GizmoInteractionController.applyTransform()
private void applyTransform(Vec3d delta) {
    // ... 现有逻辑 ...
    
    // ⭐ TRANSFORM Gizmo（Actor 编辑）不应该从方块选区更新 Box
    if (context != null && context.getType() == GizmoType.TRANSFORM) {
        // 跳过 updateBoxFromSelection()
        return;
    }
    
    // ... 其他逻辑 ...
}
```

## 三、优先级

1. **立即修复**：清除方块选区（问题 3）
2. **立即修复**：优化 Gizmo 位置更新（问题 1）
3. **后续修复**：改进 handle 位置更新（问题 2）
