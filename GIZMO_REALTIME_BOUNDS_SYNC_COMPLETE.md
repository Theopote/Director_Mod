# 🧭 SelectionManager v3.1 - 实时边界同步完成

## ✅ 已完成的工作

### 1. SelectionManager v3.1 扩展

**文件**: `src/main/java/com/chronoblocks/core/selection/SelectionManager.java`

**新增功能**:

#### 实时预览同步配置

- ✅ `autoPreviewEnabled` - 控制是否启用实时预览同步（默认：true）
- ✅ `isAutoPreviewEnabled()` - 获取自动预览状态
- ✅ `setAutoPreviewEnabled(boolean)` - 设置自动预览状态

#### 当前选区边界计算

- ✅ `getCurrentBounds()` - 获取当前选区的包围盒（Box）
  - 用于 Gizmo 系统获取选区边界
  - 用于 SelectionRenderer 预览框同步
  - 返回 Box，如果选区为空则返回 null

#### 自动预览同步

- ✅ `expandSelection()` - 自动更新预览框
  - 操作后重新计算 Box
  - 自动调用 `SelectionRenderer.setPreviewBox()`

- ✅ `offsetSelection()` - 自动更新预览框
  - 平移后重新计算 Box
  - 自动调用 `SelectionRenderer.setPreviewBox()`

- ✅ `clearSelection()` - 自动清除预览框
  - 清除选区时自动调用 `SelectionRenderer.clearPreview()`

### 2. 跨包访问处理

**实现细节**:
- 使用完全限定名访问 `SelectionRenderer`（避免循环依赖）
- 使用 try-catch 处理服务器端不可用的情况
- 优雅降级：如果 SelectionRenderer 不可用，仅记录调试日志

## 🔄 完整工作流程

### 实时可视化反馈链

```
Gizmo 拖拽 ResizeHandle
    ↓
TransformApplier.applyResize(axis, delta)
    ↓
SelectionManager.expandSelection(axis, deltaInt)
    ↓
修改选区数据（添加/移除方块）
    ↓
自动计算新边界：getCurrentBounds()
    ↓
自动更新预览框：SelectionRenderer.setPreviewBox(newBounds)
    ↓
玩家立刻看到预览变化 ✅
```

### 拖拽结束流程

```
用户释放鼠标
    ↓
GizmoInteractionController.endDrag()
    ↓
（可选）SelectionRenderer.clearPreview()
    ↓
选区已正式更新，预览框可继续显示
```

## 📋 功能详解

### expandSelection() 自动同步

```java
// 1. 执行选区操作
expandSelection(Axis.X, +2);

// 2. 自动计算新边界
Box newBounds = getCurrentBounds();

// 3. 自动更新预览框
SelectionRenderer.getInstance().setPreviewBox(newBounds);

// ✅ 玩家立刻看到扩展后的选区轮廓
```

### offsetSelection() 自动同步

```java
// 1. 执行平移操作
offsetSelection(new BlockPos(3, 0, -1));

// 2. 自动计算新边界
Box newBounds = getCurrentBounds();

// 3. 自动更新预览框
SelectionRenderer.getInstance().setPreviewBox(newBounds);

// ✅ 玩家立刻看到平移后的选区轮廓
```

### clearSelection() 自动清除

```java
// 1. 清除选区
clearSelection();

// 2. 自动清除预览框
SelectionRenderer.getInstance().clearPreview();

// ✅ 预览框消失
```

## 🚀 技术细节

### 跨包访问

由于 `SelectionManager` 在 `core` 包中，而 `SelectionRenderer` 在 `client` 包中，使用：

```java
// 使用完全限定名，避免循环依赖
com.chronoblocks.client.renderer.SelectionRenderer.getInstance().setPreviewBox(newBounds);
```

### 服务器端兼容

使用 try-catch 处理服务器端不可用的情况：

```java
try {
    SelectionRenderer.getInstance().setPreviewBox(newBounds);
} catch (Exception e) {
    // 如果 SelectionRenderer 不可用（服务器端），忽略错误
    LOGGER.debug("[SelectionManager] 设置预览框失败（可能不在客户端）: {}", e.getMessage());
}
```

### 边界计算优化

使用 `getCurrentBounds()` 统一计算边界，避免重复代码：

```java
// 统一使用 getCurrentBounds() 计算边界
Box bounds = getCurrentBounds();
int minX = (int) bounds.minX;
int minY = (int) bounds.minY;
int minZ = (int) bounds.minZ;
int maxX = (int) bounds.maxX - 1; // Box 的 max 是 exclusive
int maxY = (int) bounds.maxY - 1;
int maxZ = (int) bounds.maxZ - 1;
```

## 💡 使用示例

### 启用/禁用自动预览

```java
// 启用自动预览同步（默认已启用）
SelectionManager.get().setAutoPreviewEnabled(true);

// 禁用自动预览同步
SelectionManager.get().setAutoPreviewEnabled(false);

// 检查状态
if (SelectionManager.get().isAutoPreviewEnabled()) {
    // 自动预览已启用
}
```

### 手动获取边界

```java
// 获取当前选区边界（供 Gizmo 或其他系统使用）
Box bounds = SelectionManager.get().getCurrentBounds();
if (bounds != null) {
    // 使用边界...
}
```

### Gizmo 拖拽（无需手动调用）

```java
// Gizmo 拖拽时，只需调用 expandSelection()
SelectionManager.get().expandSelection(Axis.X, +2);

// ✅ 预览框自动更新，无需手动调用 setPreviewBox()
```

## 📊 改进要点一览

| 功能模块 | 说明 |
|---------|------|
| `autoPreviewEnabled` | 控制是否启用实时预览同步 |
| `expandSelection()` | 自动计算新 `Box` 并同步到 `SelectionRenderer` |
| `getCurrentBounds()` | 返回当前选区边界盒（供 Gizmo、Renderer 共用） |
| `offsetSelection()` | 平移后同步预览框 |
| `clearSelection()` | 清除时自动清除预览框 |

## 🎯 效果演示

### 拖拽 ResizeHandle 时

1. Gizmo 计算 delta
2. 调用 `SelectionManager.expandSelection(axis, delta)`
3. **自动**：
   - 修改选区数据
   - 重新计算 Box
   - 调用 `SelectionRenderer.setPreviewBox()`
4. 屏幕立刻显示扩展后的选区轮廓 ✅

### 拖拽松开后

可以选择：
- **保持显示**：预览框继续显示当前选区边界
- **清除预览**：手动调用 `SelectionRenderer.clearPreview()`

## 📚 相关文件

- `src/main/java/com/chronoblocks/core/selection/SelectionManager.java` - 已更新（新增实时边界同步）
- `src/main/java/com/chronoblocks/client/renderer/SelectionRenderer.java` - 已支持预览框
- `GIZMO_SELECTION_MANAGER_V3_COMPLETE.md` - SelectionManager v3.0 完成文档
- `GIZMO_PREVIEW_BOX_COMPLETE.md` - 预览框功能完成文档

## ✅ 检查清单

- [x] `autoPreviewEnabled` 字段已添加
- [x] `getCurrentBounds()` 方法已实现
- [x] `expandSelection()` 自动同步预览框
- [x] `offsetSelection()` 自动同步预览框
- [x] `clearSelection()` 自动清除预览框
- [x] `isAutoPreviewEnabled()` 方法已实现
- [x] `setAutoPreviewEnabled()` 方法已实现
- [x] 跨包访问处理（使用完全限定名）
- [x] 服务器端兼容（try-catch）
- [x] 编译无错误
- [x] 代码风格一致

---

**完成时间**: 2025-01-XX  
**版本**: SelectionManager v3.1  
**状态**: ✅ 已完成并集成

