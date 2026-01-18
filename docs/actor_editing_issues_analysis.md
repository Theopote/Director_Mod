# Actor 编辑系统问题分析

## 一、问题梳理

用户提出了三个需要梳理的问题：

1. **Gizmo 拖动问题**：使用选择演员工具选中演员时，Gizmo 不能被拖动
2. **路径工具深化**：演员路径工具需要深化，可以左键在 Minecraft 中通过点击绘制路径曲线，类似摄像机轨迹
3. **片段关键帧**：片段在轨道上的时候，可以给片段设置关键帧

## 二、问题 1：Gizmo 拖动问题

### 现状分析

**代码流程**：
```
用户选择演员
  ↓
ActorSelectionHub.selectActor(actorId)
  ↓
ActorGizmoController.setTargetActor(actorId)
  ↓
resolveTargetTrack()  // 解析 EntityTrack
  ↓
activateGizmo()  // 激活 Gizmo
```

**问题可能的原因**：
1. `GizmoManager` 没有正确激活
2. `TransformApplier` 没有正确设置到 `GizmoManager`
3. `ActorTransformApplier` 的 `applyMove()` 没有被调用
4. Gizmo 交互被其他系统拦截

### 解决方案

**需要检查的点**：
1. `ActorGizmoController.activateGizmo()` 是否正确调用了 `GizmoManager.activate()`
2. `GizmoManager` 是否使用了 `ActorTransformApplier`
3. 是否有其他输入处理拦截了鼠标事件

**可能的修复**：
- 确保 `activateGizmo()` 正确设置了 `TransformApplier`
- 检查 `GizmoInteractionController` 是否正确处理了 Actor 的 Gizmo
- 确保鼠标事件没有被 ImGui 或其他系统拦截

## 三、问题 2：路径工具深化

### 现状分析

**现有实现**：
- `ActorPathController` 存在，但使用右键点击来添加路径点
- 路径点存储在 `pathPoints` 列表中
- 最终会转换为 `TransformClip`（直线段）

**用户需求**：
- **左键点击**绘制路径（类似摄像机轨迹）
- **曲线插值**（Bezier / Catmull-Rom），而不是直线段
- **可视化预览**路径曲线

**参考实现**：
- `TimelineCameraPathManager` - 摄像机路径管理
  - 支持 `LINEAR`、`CATMULL_ROM`、`BEZIER`、`STEP` 插值
  - 关键帧存储路径点
  - `CameraPathDebugRenderer` 可视化路径

### 解决方案

**设计思路**：
1. **路径数据结构**：
   ```java
   public class ActorPath {
       UUID id;
       String name;
       List<Vec3d> controlPoints;  // 控制点
       PathInterpolation interpolation;  // 插值类型
       
       Vec3d sampleAt(float t);  // t: [0, 1]
   }
   ```

2. **路径编辑模式**：
   - 激活"路径工具"（工具栏按钮）
   - 左键点击 Minecraft 世界 → 添加控制点
   - 实时显示路径预览（曲线）
   - 右键点击 → 完成路径创建

3. **TransformClip 绑定**：
   ```java
   public class EntityTransformClip extends EntityClip {
       private String pathId = null;        // 绑定的路径ID
       private boolean followPath = false;  // 是否沿着路径移动
       private float pathStartT = 0f;       // 路径起始参数 [0, 1]
       private float pathEndT = 1f;         // 路径结束参数 [0, 1]
   }
   ```

4. **路径采样**：
   - `EntityClipSampler` 中，如果 `TransformClip.followPath == true`
   - 使用 `Path.sampleAt(t)` 采样位置
   - `t` 由 Clip 时间映射到路径参数 `[pathStartT, pathEndT]`

**实施步骤**：
1. 修改 `ActorPathController`：使用左键点击添加控制点
2. 添加路径插值算法（参考 `TimelineCameraPathManager`）
3. 添加路径可视化渲染（参考 `CameraPathDebugRenderer`）
4. 扩展 `EntityTransformClip` 支持路径绑定
5. 修改 `EntityClipSampler` 支持路径采样

## 四、问题 3：片段关键帧

### 现状分析

**现有实现**：
- `EntityClipKeyframe` 存在，继承自 `ClipKeyframe`
- `TimelineClip` 有 `addKeyframe()` 方法
- `EntityTimelineClip` 是 `TimelineClip` 和 `EntityClip` 的桥接

**问题**：
- 如何为 `EntityClip` 添加关键帧？
- 关键帧如何与 `EntityTimelineClip` 同步？
- 关键帧如何影响 `EntityClipSampler` 的采样？

**用户需求**：
- 在 Timeline 上选中 `EntityClip`
- 双击时间轴 → 添加关键帧
- 在 Properties 面板编辑关键帧属性（位置、旋转、行为）
- 关键帧之间自动插值

### 解决方案

**设计思路**：
1. **关键帧数据结构**（已存在）：
   ```java
   public class EntityClipKeyframe extends ClipKeyframe {
       Vector3f position;
       Vector3f rotation;
       Vector3f scale;
       ActorActionType actionType;
       float actionIntensity;
   }
   ```

2. **EntityClip 关键帧管理**：
   ```java
   public abstract class EntityClip {
       private final List<EntityClipKeyframe> keyframes = new ArrayList<>();
       
       public void addKeyframe(EntityClipKeyframe keyframe);
       public void removeKeyframe(UUID keyframeId);
       public List<EntityClipKeyframe> getKeyframes();
       public EntityClipKeyframe findKeyframeAt(float time, float epsilon);
   }
   ```

3. **同步机制**：
   - `EntityTimelineClip` 同时管理 `EntityClip` 和 `TimelineClip`
   - 当 `EntityClip` 添加关键帧时，同步到 `TimelineClip`
   - 当 `TimelineClip` 添加关键帧时，同步到 `EntityClip`

4. **采样逻辑**：
   - `EntityClipSampler` 在采样时，优先使用关键帧插值
   - 如果有关键帧，在关键帧之间插值
   - 如果没有关键帧，使用 `TransformClip` 的 `fromPos/toPos` 插值

**实施步骤**：
1. 在 `EntityClip` 中添加关键帧管理方法
2. 在 `EntityTimelineClip` 中实现关键帧同步
3. 修改 `EntityClipSampler` 支持关键帧插值
4. 在 Timeline 交互中添加"双击添加关键帧"逻辑
5. 创建 `EntityClipKeyframePropertyAdapter` 用于属性编辑

## 五、优先级建议

### 高优先级
1. **问题 1：Gizmo 拖动** - 直接影响用户体验
2. **问题 3：片段关键帧** - 核心编辑功能

### 中优先级
3. **问题 2：路径工具** - 增强功能，可以渐进实现

## 六、技术要点

### Gizmo 拖动检查清单
- [ ] `GizmoManager.activate()` 是否被调用
- [ ] `GizmoManager.setTransformApplier()` 是否设置了 `ActorTransformApplier`
- [ ] `GizmoInteractionController` 是否正确处理鼠标事件
- [ ] 鼠标事件是否被 ImGui 或其他系统拦截

### 路径工具检查清单
- [ ] `ActorPathController` 是否支持左键点击
- [ ] 路径插值算法是否实现（Bezier / Catmull-Rom）
- [ ] 路径可视化渲染是否实现
- [ ] `EntityTransformClip` 是否支持路径绑定
- [ ] `EntityClipSampler` 是否支持路径采样

### 关键帧检查清单
- [ ] `EntityClip` 是否有关键帧管理方法
- [ ] `EntityTimelineClip` 是否实现了关键帧同步
- [ ] `EntityClipSampler` 是否支持关键帧插值
- [ ] Timeline 交互是否支持"双击添加关键帧"
- [ ] 是否有关键帧属性编辑器
