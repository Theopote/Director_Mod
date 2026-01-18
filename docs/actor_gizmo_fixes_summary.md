# Actor Gizmo 修复总结

## 已完成的修复

### 1. 方块选区问题修复

**问题**：选中 Actor 时总是出现方块大小的选区

**修复**：
- ✅ 在 `ActorSelectionHub.selectActor()` 中明确清除方块选区
- ✅ 在 `ActorGizmoController.setTargetActor()` 中清除方块选区
- ✅ 在 `ActorGizmoController.clearTarget()` 中清除方块选区（已存在）

**原理**：
- Actor 选择不应该与方块选区有任何关联
- 方块选区只应该在框选工具（BoxSelectTool）激活时存在
- TRANSFORM Gizmo（Actor 编辑）不应该从方块选区更新 Box

### 2. Gizmo 拖动不顺畅修复

**问题**：拖动演员时响应延迟、卡顿

**修复**：
- ✅ 优化 `updateGizmoPosition()` 只在 Box 或 Pivot 真正变化时更新
- ✅ 拖动时暂停位置更新（`if (dragging) return;`）
- ✅ 添加位置缓存机制（`lastGizmoBox`, `lastGizmoPivot`）
- ✅ 添加 Box 相等性检查（`boxesEqual()`）

**原理**：
- 每帧都调用 `setTargetBox()` 会触发不必要的更新
- 拖动时更新位置会干扰拖动操作
- 位置缓存可以避免不必要的更新

### 3. Gizmo 箭头和旋转框分离修复

**问题**：Gizmo 的轴箭头和旋转环位置不一致

**修复**：
- ✅ 修复 `GizmoInteractionController.updateBoxFromSelection()` 不更新 TRANSFORM Gizmo
- ✅ 在 `applyTransform()` 中跳过 TRANSFORM Gizmo 的 Box 更新
- ✅ 在 `updateDrag()` 中跳过 TRANSFORM Gizmo 的 Box 更新
- ✅ 在 `endDrag()` 中跳过 TRANSFORM Gizmo 的 Box 更新

**原理**：
- TRANSFORM Gizmo 的 Box 应该由 `ActorGizmoController.updateGizmoPosition()` 管理
- 不应该从方块选区更新 TRANSFORM Gizmo 的 Box
- `updateBoxFromSelection()` 应该只用于 BOUNDS Gizmo

## 设计原则总结

### 1. Actor 选择与方块选区分离

```
Actor 选择 → SelectionManager.selectActor()
  └─ 不应该影响方块选区
  └─ 不应该创建方块选区
  └─ 选中 Actor 时明确清除方块选区
```

### 2. TRANSFORM Gizmo 与 BOUNDS Gizmo 分离

```
TRANSFORM Gizmo（Actor 编辑）
  └─ Box 由 ActorGizmoController.updateGizmoPosition() 管理
  └─ 不应该从方块选区更新 Box
  └─ 不应该影响方块选区

BOUNDS Gizmo（框选工具）
  └─ Box 从方块选区更新
  └─ 影响方块选区
```

### 3. Gizmo 位置更新优化

```
位置更新策略：
  1. 拖动时：暂停位置更新（避免干扰）
  2. 未拖动：只在真正变化时更新（性能优化）
  3. 位置缓存：比较 Box 和 Pivot，只在差异超过阈值时更新
```

## 检查清单

- [x] `ActorSelectionHub.selectActor()` 清除方块选区
- [x] `ActorGizmoController.setTargetActor()` 清除方块选区
- [x] `ActorGizmoController.updateGizmoPosition()` 拖动时暂停更新
- [x] `ActorGizmoController.updateGizmoPosition()` 添加位置缓存
- [x] `GizmoInteractionController.applyTransform()` 跳过 TRANSFORM Gizmo
- [x] `GizmoInteractionController.updateDrag()` 跳过 TRANSFORM Gizmo
- [x] `GizmoInteractionController.endDrag()` 跳过 TRANSFORM Gizmo

## 后续建议

1. **测试**：验证所有修复是否正常工作
2. **性能**：监控位置更新的性能表现
3. **扩展**：考虑其他 Gizmo 类型的优化
