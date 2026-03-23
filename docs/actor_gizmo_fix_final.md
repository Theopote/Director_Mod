# Actor Gizmo 问题修复 - 最终方案

## 已完成修复

### 修复 1：方块选区问题

✅ **修复位置**：
- `ActorSelectionHub.selectActor()` - 在选中 Actor 时清除方块选区
- `ActorGizmoController.setTargetActor()` - 在设置目标 Actor 时清除方块选区

✅ **修复代码**：
```java
// ActorSelectionHub.selectActor()
try {
    com.director.core.selection.SelectionManager blockSelectionManager = 
        com.director.core.selection.SelectionManager.get();
    if (blockSelectionManager != null) {
        blockSelectionManager.clearSelection();
    }
} catch (Exception e) {
    LOGGER.debug("[ActorSelectionHub] 清除方块选区失败: {}", e.getMessage());
}
```

### 修复 2：Gizmo 拖动不顺畅

✅ **修复位置**：
- `ActorGizmoController.updateGizmoPosition()` - 优化更新逻辑

✅ **修复内容**：
1. 拖动时暂停位置更新（`if (dragging) return;`）
2. 添加位置缓存（`lastGizmoBox`, `lastGizmoPivot`）
3. 只在 Box 或 Pivot 真正变化时更新

✅ **修复代码**：
```java
private void updateGizmoPosition(Entity entity) {
    // ⭐ 拖动时不要更新位置，避免干扰拖动操作
    if (dragging) {
        return;
    }
    
    // ⭐ 检查 Box 和 Pivot 是否真正变化（避免不必要的更新）
    boolean boxChanged = lastGizmoBox == null || !boxesEqual(box, lastGizmoBox);
    boolean pivotChanged = lastGizmoPivot == null || 
                          lastGizmoPivot.squaredDistanceTo(pivot) > BOX_CHANGE_THRESHOLD_SQ;
    
    if (!boxChanged && !pivotChanged && GizmoManager.get().isActive()) {
        return; // 没有变化，跳过更新
    }
    
    // ... 更新逻辑 ...
}
```

### 修复 3：Gizmo 从方块选区更新 Box

✅ **修复位置**：
- `GizmoInteractionController.applyTransform()` - 跳过 TRANSFORM Gizmo
- `GizmoInteractionController.endDrag()` - 跳过 TRANSFORM Gizmo

✅ **待修复**：
- `GizmoInteractionController.updateDrag()` - 第198-200行，还需要修复

**待修复代码**（第198-200行）：
```java
// ✅ BOUNDS Gizmo（移动包围盒）拖拽中不要用"当前选区 bounds"覆盖 targetBox，
// 否则会出现"Gizmo 能动但包围盒不动"的现象（因为选区此时还没重建）。
if (!(context != null && context.getType() == GizmoType.BOUNDS && isMoveHandle(activeHandle))) {
    updateBoxFromSelection();
}
```

**应该改为**：
```java
// ⭐ TRANSFORM Gizmo（Actor 编辑）不应该从方块选区更新 Box
// ✅ BOUNDS Gizmo（移动包围盒）拖拽中不要用"当前选区 bounds"覆盖 targetBox
if (context != null && context.getType() == GizmoType.TRANSFORM) {
    // TRANSFORM Gizmo：Box 由 ActorGizmoController.updateGizmoPosition() 管理
} else if (!(context != null && context.getType() == GizmoType.BOUNDS && isMoveHandle(activeHandle))) {
    updateBoxFromSelection();
}
```

## 待修复问题

### updateDrag() 中的 updateBoxFromSelection() 调用

**位置**：`GizmoInteractionController.updateDrag()` 第198-200行

**问题**：会从方块选区更新 TRANSFORM Gizmo 的 Box，导致 Gizmo 位置错误

**需要手动修复**：由于代码格式问题，需要手动修改
