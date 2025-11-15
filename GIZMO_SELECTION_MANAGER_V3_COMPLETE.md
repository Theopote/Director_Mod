# 🧭 SelectionManager v3.0 - Gizmo 集成完成

## ✅ 已完成的工作

### 1. SelectionManager v3.0 扩展

**文件**: `src/main/java/com/chronoblocks/core/selection/SelectionManager.java`

**新增方法**:

#### `expandSelection(Axis axis, int delta)` - 选区缩放核心逻辑

```java
public void expandSelection(Axis axis, int delta)
```

**功能**:
- ✅ 沿指定轴扩展或收缩当前选区
- ✅ 正/负方向扩展选区（delta > 0 扩展，delta < 0 收缩）
- ✅ 自动重新计算 Bounding Box
- ✅ 兼容 BoxSelectTool_v3 与 GizmoRenderer
- ✅ 实时预览刷新（通过 `notifySelectionChanged()`）

**参数**:
- `axis`: 方向轴（X/Y/Z）
- `delta`: 增量（>0 扩展，<0 收缩）

**实现细节**:
- **正向扩展**：在最大边界外侧新增层（maxX/maxY/maxZ + delta）
- **负向收缩**：移除对应方向的外层方块（从 max 向 min 方向移除）
- **自动边界检测**：每次操作都重新计算当前选区的 min/max

#### `offsetSelection(BlockPos offset)` - 平移选区

```java
public void offsetSelection(BlockPos offset)
```

**功能**:
- ✅ 平移选区到新位置
- ✅ 自动通知选择变化（触发渲染更新）

**参数**:
- `offset`: 偏移向量

### 2. TransformApplier 优化

**文件**: `src/main/java/com/chronoblocks/client/gizmo/logic/TransformApplier.java`

**更新**:
- ✅ `applyMove()` 使用 `SelectionManager.offsetSelection()`
- ✅ `applyResize(Axis, double)` 使用 `SelectionManager.expandSelection()`
- ✅ 统一选区操作逻辑，减少代码重复

**改进**:
- 代码更简洁：TransformApplier 不再重复实现选区操作逻辑
- 易于维护：所有选区操作集中在 SelectionManager
- 自动通知：通过 `notifySelectionChanged()` 自动触发渲染更新

## 🔄 完整工作流程

### ResizeHandle 缩放操作流程

```
用户拖拽 ResizeHandle
    ↓
ResizeHandle.onDragUpdate()
    ↓
计算轴增量
    ↓
TransformApplier.applyResize(axis, delta)
    ↓
SelectionManager.expandSelection(axis, deltaInt)
    ↓
计算选区边界（min/max）
    ↓
根据 delta 添加或移除方块
    ↓
notifySelectionChanged()
    ↓
触发渲染更新
```

### 面手柄缩放

```
拖拽 X 轴面手柄
    ↓
expandSelection(Axis.X, +3)
    ↓
在 maxX 外侧添加 3 层方块
    ↓
选区自动更新，渲染刷新
```

### 角点手柄缩放

```
拖拽角点手柄
    ↓
applyResize(Vec3d axisDelta)
    ↓
分别沿 X、Y、Z 轴调用 expandSelection
    ↓
同时沿三个轴添加或移除方块
```

### 平移操作流程

```
用户拖拽 AxisHandle/PlaneHandle
    ↓
TransformApplier.applyMove(delta)
    ↓
SelectionManager.offsetSelection(offset)
    ↓
计算新位置并更新选区
    ↓
notifySelectionChanged()
    ↓
触发渲染更新
```

## 📋 特性详解

| 功能 | 说明 |
|------|------|
| **正向扩展** | 当 `delta > 0` 时，在最大边界外侧新增层 |
| **负向收缩** | 当 `delta < 0` 时，移除对应方向的外层方块 |
| **自动边界检测** | 每次操作都会重新计算当前选区的 `min/max` |
| **自动通知** | 通过 `notifySelectionChanged()` 自动触发渲染更新 |
| **可扩展机制** | 可通过事件或监听机制让 `SelectionRenderer` 自动刷新预览 |

## 🚀 技术细节

### 扩展逻辑

**X 轴扩展**（delta > 0）:
```java
for (int x = maxX + 1; x <= maxX + delta; x++) {
    for (int y = minY; y <= maxY; y++) {
        for (int z = minZ; z <= maxZ; z++) {
            selectionSet.add(new BlockPos(x, y, z));
        }
    }
}
```

**X 轴收缩**（delta < 0）:
```java
for (int x = maxX; x > maxX + delta; x--) {
    final int removeX = x;
    selectionSet.removeIf(p -> p.getX() == removeX);
}
```

### 平移逻辑

```java
Set<BlockPos> moved = new HashSet<>();
for (BlockPos pos : selectionSet.asUnmodifiableSet()) {
    moved.add(pos.add(offset));
}
selectionSet.clear();
selectionSet.addAll(moved);
```

### 通知机制

每次选区操作后调用 `notifySelectionChanged()`:
- 触发选择变化监听器
- 发布选择变化事件
- 自动触发渲染更新

## 💡 拓展建议

### 实时预览

如果想在 Gizmo 拖拽时有更平滑的"实时预览"，可以在 SelectionRenderer 加入：

```java
public void setPreviewBox(Box box) {
    this.previewBox = box;
    this.previewActive = true;
}

public void clearPreview() {
    this.previewActive = false;
}
```

然后在 GizmoRenderer.onMouseMove() 中，每次调用：

```java
selectionRenderer.setPreviewBox(currentBox);
```

实现边拖拽边渲染选区边界。

### 观察者机制

可以添加观察者机制，让 SelectionRenderer 自动监听选区变化：

```java
// 在 SelectionRenderer 初始化时
SelectionManager.get().addSelectionChangeListener(state -> {
    // 自动刷新渲染
    markDirty();
});
```

## 📚 相关文件

- `src/main/java/com/chronoblocks/core/selection/SelectionManager.java` - 已更新（新增 expandSelection 和 offsetSelection）
- `src/main/java/com/chronoblocks/client/gizmo/logic/TransformApplier.java` - 已更新（使用 SelectionManager 统一方法）
- `GIZMO_RESIZE_HANDLE_COMPLETE.md` - ResizeHandle 完成文档
- `GIZMO_SELECTION_RENDERER_INTEGRATION.md` - SelectionRenderer 集成文档

## ✅ 检查清单

- [x] `expandSelection(Axis, int)` 方法已实现
- [x] `offsetSelection(BlockPos)` 方法已实现
- [x] TransformApplier 已更新使用新方法
- [x] 自动通知选择变化（触发渲染更新）
- [x] 编译无错误
- [x] 代码风格一致

---

**完成时间**: 2025-01-XX  
**版本**: SelectionManager v3.0  
**状态**: ✅ 已完成并集成

