# 🧭 ChronoGizmo 系统 - 完整实现文档

## 📋 概述

ChronoGizmo 是 ChronoBlocks 模组的可视化交互控件系统，提供类似于 Unity、Blender、Maya、UE、WorldEdit 等专业编辑器的 Gizmo（操纵杆/控制手柄）功能。

### ✨ 核心特性

- ✅ **Transform Gizmo** - 操作整体选区的平移/缩放/旋转
- ✅ **Bounds Gizmo** - 操作选区体积（面/角点）
- ✅ **Pivot Gizmo** - 选择/移动选区的旋转中心
- ✅ **Animation Gizmo** - 动画编辑状态下控制关键帧插值路径（未来扩展）

## 🏗️ 架构设计

```
ChronoGizmoSystem
├── GizmoManager                 # 注册/激活/渲染/输入分发
├── GizmoContext                 # 与具体工具绑定的上下文
├── GizmoRenderer                # 渲染箭头、平面、控制柄
├── GizmoHandle                  # 每个控制手柄的逻辑与几何
│    ├── AxisHandle (X/Y/Z)
│    ├── PlaneHandle (XY/XZ/YZ)
│    ├── CornerHandle (BoundingBox corner)
│    └── CenterHandle (Pivot)
├── GizmoInteractionController   # 光线检测 / 拖动 / 状态机
└── 工具类
     ├── RaycastUtil             # 射线检测工具
     ├── TransformUtil           # 变换工具
     ├── SnapUtil                # 吸附工具
     └── GizmoVoxelizer          # 体素化工具
```

## 📦 文件结构

```
src/main/java/com/chronoblocks/client/gizmo/
├── GizmoType.java                      # Gizmo 类型枚举
├── GizmoState.java                     # Gizmo 交互状态
├── Axis.java                           # 坐标轴枚举
├── HitResult.java                      # 射线检测结果
├── GizmoContext.java                   # Gizmo 上下文
├── GizmoManager.java                   # Gizmo 管理器（单例）
├── GizmoInteractionController.java    # 交互控制器
├── GizmoRenderer.java                 # 渲染器
├── GizmoIntegrationExample.java       # 集成示例
└── handles/
     ├── GizmoHandle.java               # Handle 接口
     ├── AbstractGizmoHandle.java      # Handle 抽象基类
     ├── AxisHandle.java                # 轴手柄
     ├── PlaneHandle.java               # 平面手柄
     ├── CornerHandle.java              # 角点手柄
     └── CenterHandle.java              # 中心手柄
└── util/
     ├── RaycastUtil.java               # 射线检测工具
     ├── TransformUtil.java             # 变换工具
     ├── SnapUtil.java                  # 吸附工具
     └── GizmoVoxelizer.java            # 体素化工具
```

## 🔧 核心组件

### 1. GizmoManager（单例）

主管理器，负责 Gizmo 的激活、更新和渲染。

```java
GizmoManager gm = GizmoManager.get();

// 激活 Gizmo
gm.activate(GizmoType.BOUNDS, selectionBox, pivot);

// 每帧更新
gm.tick(rayOrigin, rayDir, mouseDown, mouseReleased);

// 渲染
gm.render(time, cameraPos, matrices, vcp);

// 停用
gm.deactivate();
```

### 2. GizmoContext

每个 Gizmo 的上下文，包含目标 Box、轴心点、活动状态等信息。

### 3. GizmoHandle 体系

所有控制手柄都实现 `GizmoHandle` 接口：

- **AxisHandle** - 沿单轴平移（X/Y/Z）
- **PlaneHandle** - 在平面上双向平移（XY/XZ/YZ）
- **CornerHandle** - 从角点调整 Box 大小
- **CenterHandle** - 移动旋转中心点

### 4. GizmoInteractionController

负责：
- 光线检测与 Handle 选中
- 拖拽状态机（Start / Update / End）
- 将拖拽增量应用到 Box 变换

### 5. GizmoRenderer

负责渲染：
- 轴手柄（红色 X、绿色 Y、蓝色 Z）
- 平面手柄（半透明网格）
- 角点手柄（黄色立方体）
- 中心手柄（白色球体）
- 悬停高亮效果（白色 + 脉冲动画）

## 🚀 使用方法

### 基本集成

#### 1. 从 BoxSelectTool 激活 Gizmo

```java
// 在 BoxSelectTool.onDragEnd(...) 中
Box selectionBox = makeBox(dragStart, end);
Vec3d pivot = new Vec3d(
    (selectionBox.minX + selectionBox.maxX) / 2.0,
    (selectionBox.minY + selectionBox.maxY) / 2.0,
    (selectionBox.minZ + selectionBox.maxZ) / 2.0
);

GizmoManager.get().activate(GizmoType.BOUNDS, selectionBox, pivot);
```

#### 2. 在主循环中更新和渲染

```java
// 在 ChronoBlocksScreen 或 WorldRenderer 中
public void render(float tickDelta) {
    // ... 其他渲染代码 ...
    
    GizmoManager gm = GizmoManager.get();
    
    // 获取射线
    Vec3d rayOrigin = camera.getPos();
    Vec3d rayDir = calculateRayDirection(mouseX, mouseY);
    
    // 更新 Gizmo
    gm.tick(rayOrigin, rayDir, mouseDown, mouseReleased);
    
    // 渲染 Gizmo（在 AFTER_ENTITIES 阶段）
    MatrixStack matrices = ...;
    VertexConsumerProvider vcp = ...;
    gm.render(time, cameraPos, matrices, vcp);
}
```

#### 3. 自定义应用回调

```java
GizmoManager.get().setOnApply(ctx -> {
    // 自定义应用逻辑
    Box newBox = ctx.getTargetBox();
    
    // 写回 SelectionManager
    Set<BlockPos> blocks = GizmoVoxelizer.voxelize(newBox);
    SelectionManager.get().clearSelection();
    SelectionManager.get().getSelectionSet().addAll(blocks);
    
    // 或触发动画关键帧
    // AnimationManager.get().createKeyframe(newBox);
});
```

## 🎨 视觉效果

| 元素 | 样式 | 动态效果 |
|------|------|----------|
| X 轴 | 红色箭头 | 选中时变亮并脉冲 |
| Y 轴 | 绿色箭头 | 选中时变亮并脉冲 |
| Z 轴 | 蓝色箭头 | 选中时变亮并脉冲 |
| 平面 | 网格线条（半透明） | 拖动时闪烁 |
| 角点 | 黄色立方体 | Hover 时发光 |
| 中心 | 白色球体 | 选中时白色高亮 |

## 🔄 交互流程

1. **进入 Gizmo 模式**
   - 工具（如 BoxSelectTool）调用 `GizmoManager.activate()`
   - 系统创建 Handle 列表

2. **光线检测与选中**
   - 玩家视角 → 光线投射到所有 Handle
   - 找到最近命中的 Handle
   - 触发 `onHover(handle)` → 渲染高亮

3. **拖拽逻辑**
   - 点击 → 进入拖拽模式
   - 计算拖拽起点（Ray 与平面交点）
   - 持续更新拖拽向量 Δp（根据摄像机朝向约束）
   - 将 Δp 应用到选区变换：
     - AxisHandle → 选区平移
     - PlaneHandle → 双向平移
     - CornerHandle → 改变 Box 大小（保持对角点固定）
     - CenterHandle → 改变 pivot
   - 实时更新 SelectionRenderer

4. **拖拽结束**
   - 生成新的 SelectionState
   - 调用 `SelectionHistoryManager.record()`（如果存在）
   - 如果处于动画编辑状态，自动创建关键帧变换 Clip

## 🧩 与现有系统的集成点

| 模块 | 交互说明 |
|------|----------|
| `SelectionRenderer` | 用于绘制当前选区边框 / Gizmo 辅助元素 |
| `SelectionManager` | 提供当前选区数据与变换接口 |
| `SelectionHistoryManager` | 记录每次 Gizmo 操作（拖动/缩放） |
| `TransformSystem` | （可选）将 Gizmo 操作同步到动画关键帧 |
| `ToolPropertiesPanel` | 提供 "启用Gizmo" 开关与模式切换选项 |

## 🔮 未来拓展

当 ChronoBlocks 进入「时间线编辑模式」时，Gizmo 将进一步支持：

- **Keyframe Preview Gizmo** - 显示物体位置关键帧与插值路径
- **轨迹编辑** - 拖动路径节点改变曲线
- **多物体选区 Gizmo** - 组移动 / 组旋转
- **锁定轴 / 约束面模式** - 限制变换方向
- **镜像编辑** - Mirror Transform

## 📝 注意事项

1. **射线计算**：需要使用正确的相机坐标系转换，参考 `🎯三大问题完整解决方案.md`
2. **渲染顺序**：Gizmo 应在 `AFTER_ENTITIES` 或 `AFTER_TRANSLUCENT` 阶段渲染
3. **性能优化**：Handle 检测可以提前使用视锥剔除优化
4. **状态管理**：Gizmo 激活时应该暂停其他工具的输入处理

## 🐛 已知限制

1. 当前实现中，`CornerHandle` 和 `CenterHandle` 的拖拽逻辑使用了简化方法（固定距离投影），未来可以改进为更精确的平面投影
2. Handle 位置更新在 `updateHandlePositions()` 中使用简化实现，应该重新创建 Handle 列表以确保一致性

## 📚 参考资料

- `GizmoIntegrationExample.java` - 完整的集成示例
- `🎯三大问题完整解决方案.md` - 射线计算的正确方法
- `SELECTION_TOOL_SYSTEM_COMPLETE_V1.md` - 选择工具系统文档

---

**版本**: 1.0  
**作者**: ChronoBlocks Team  
**最后更新**: 2025-01-XX

