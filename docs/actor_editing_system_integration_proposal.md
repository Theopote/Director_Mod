# Actor 编辑系统整合提案

## 一、背景

用户提出了三个重要的编辑想法，与当前正在实现的 Action 涂抹式编辑系统需要有机整合：

1. **Gizmo 工具**：选择演员工具，在 Gizmo 肩头上对演员进行拖动、旋转等操作
2. **片段关键帧**：演员的片段是生命周期，可以在片段上设置关键帧，每个关键帧确定演员的状态（位置、行为），关键帧之间形成动画
3. **路径工具**：在 Minecraft 中绘制路径，让演员大致沿着这个路径移动位置，关键帧之间只能直线移动，路径要自由一些

## 二、核心设计原则

### 🎯 多层次编辑范式

Director 应该支持**三种不同层次的编辑方式**，它们互相补充、不冲突：

| 层次 | 工具 | 用途 | 数据模型 |
|------|------|------|----------|
| **直接操作** | Gizmo 工具 | 直观拖拽、旋转演员 | 实时更新 TransformClip 或关键帧 |
| **关键帧编辑** | Timeline 关键帧 | 精确控制时间点状态 | EntityClipKeyframe (位置、旋转、行为) |
| **路径编辑** | 路径工具 | 自由曲线路径 | Path 对象 + TransformClip 绑定 |
| **语义编辑** | Action 涂抹 | 快速定义动作语义 | ActorActionClip (状态区间) |

### 🎯 数据层统一

所有编辑操作最终都转换为**标准的数据结构**：

- **Gizmo 操作** → `EntityClipKeyframe` 或 `TransformClip` 更新
- **关键帧编辑** → `EntityClipKeyframe` 创建/修改
- **路径编辑** → `Path` 对象 + `TransformClip.pathId` 绑定
- **Action 涂抹** → `ActorActionClip` 创建/修改

## 三、三个想法的详细设计

### 1️⃣ Gizmo 工具（直接操作）

#### 设计目标
- 选择演员 → Gizmo 出现在演员位置
- 拖拽 Gizmo 肩头 → 实时更新演员位置/旋转
- 操作结果 → 自动创建/更新 TransformClip 或关键帧

#### 实现方案

**触发条件**：
- 使用"选择演员工具"选择演员
- 如果该演员在 Timeline 中有对应的 ActorTrack，显示 Gizmo

**Gizmo 操作**：
- **位置拖拽**（XYZ 轴）：
  - 如果当前时间在 TransformClip 范围内 → 更新 TransformClip 的 `toPos`
  - 如果当前时间不在任何 Clip 范围内 → 创建新的 TransformClip 或关键帧

- **旋转拖拽**（旋转环）：
  - 同样逻辑，更新 `toRot` 或创建关键帧

**数据更新策略**：
```java
// 伪代码
void onGizmoDrag(Vec3d newPosition) {
    float currentTime = timelineModel.getCurrentTime();
    ActorTrack track = findTrackForActor(selectedActor);
    
    // 方案 A：更新最近的 TransformClip
    TransformClip clip = track.findTransformClipAt(currentTime);
    if (clip != null) {
        // 更新 toPos（如果当前时间接近 clip.endTime）
        // 或者创建新的关键帧
    } else {
        // 创建新的 TransformClip
        TransformClip newClip = new TransformClip(
            getActorCurrentPosition(),
            newPosition,
            currentTime,
            currentTime + 1.0f  // 默认1秒
        );
        track.addClip(newClip);
    }
}
```

**与 Timeline 同步**：
- Gizmo 操作时，Timeline 播放头位置决定"编辑哪个时间点"
- 操作结果立即反映到 Timeline 上

### 2️⃣ 片段关键帧（精确编辑）

#### 设计目标
- 演员的片段是生命周期（EntityClip 表示时间段）
- 在片段上设置关键帧（EntityClipKeyframe）
- 每个关键帧确定演员的完整状态（位置、旋转、行为）
- 关键帧之间插值形成动画

#### 实现方案

**关键帧数据结构**：
```java
public class EntityClipKeyframe {
    float time;              // 相对于 Clip 的时间（0 = clip.startTime）
    Vec3d position;          // 位置
    Vec3d rotation;          // 旋转（yaw, pitch, roll）
    Vec3d scale;             // 缩放
    ActorActionType action;  // 行为语义（可选）
    float actionIntensity;   // 行为强度（可选）
}
```

**关键帧编辑流程**：
1. 在 Timeline 上选中 EntityClip
2. 双击时间轴 → 创建关键帧
3. 在 Properties 面板编辑关键帧属性
4. 关键帧之间自动插值（位置、旋转、缩放）

**与 TransformClip 的关系**：
- **方案 A**：关键帧完全替代 TransformClip
  - TransformClip 只是一个"容器"
  - 所有数据都在 Keyframe 里

- **方案 B**：关键帧作为 TransformClip 的增强
  - TransformClip 保留 `fromPos/toPos`（简单情况）
  - 如果有 Keyframe，使用 Keyframe 插值（复杂情况）

**推荐方案 B**：向后兼容 + 渐进增强

### 3️⃣ 路径工具（自由曲线）

#### 设计目标
- 在 Minecraft 世界中绘制路径（类似贝塞尔曲线）
- 演员沿着路径移动（比关键帧直线更自由）
- TransformClip 可以绑定到路径

#### 实现方案

**路径数据结构**：
```java
public class ActorPath {
    UUID id;
    String name;
    List<Vec3d> controlPoints;  // 控制点
    PathInterpolation interpolation;  // 插值类型（LINEAR, BEZIER, CATMULL_ROM）
    
    // 路径采样方法
    Vec3d sampleAt(float t);  // t: [0, 1]
    float getLength();        // 路径总长度
}
```

**TransformClip 绑定路径**：
```java
public class EntityTransformClip extends EntityClip {
    // 原有字段
    private Vector3f fromPos;
    private Vector3f toPos;
    
    // 新增：路径支持
    private String pathId = null;        // 绑定的路径ID
    private boolean followPath = false;  // 是否沿着路径移动
    private float pathStartT = 0f;       // 路径起始参数 [0, 1]
    private float pathEndT = 1f;         // 路径结束参数 [0, 1]
}
```

**路径编辑流程**：
1. 激活"路径工具"
2. 在 Minecraft 世界中点击放置控制点
3. 形成路径（可编辑控制点）
4. 创建 TransformClip 时，选择"绑定到路径"
5. 演员沿着路径移动（使用路径的 `sampleAt()` 方法）

**与关键帧的关系**：
- 路径是**全局工具**，可以在多个 Clip 间复用
- 关键帧是**Clip 内部**的精确控制
- 两者可以结合：
  - 路径提供大致方向
  - 关键帧提供精确调整

## 四、编辑模式的统一入口

### 工具栏设计

```
[ 选择演员 ] [ Gizmo 操作 ] [ 路径工具 ] [ Action 涂抹 ]
```

- **选择演员**：基础工具，选中后可以：
  - 显示 Gizmo（如果有时序数据）
  - 在 Properties 面板编辑属性
  - 在 Timeline 中定位对应轨道

- **Gizmo 操作**：激活后：
  - 选中的演员显示 Gizmo
  - 拖拽肩头更新位置/旋转
  - 实时更新 Timeline 数据

- **路径工具**：激活后：
  - 在 Minecraft 世界中绘制路径
  - 路径编辑模式（添加/删除控制点）
  - 路径可以绑定到 TransformClip

- **Action 涂抹**：激活后：
  - Timeline 进入涂抹模式
  - 拖拽创建 ActionClip
  - 使用 ActionBrushState 选择动作类型

### Timeline 模式切换

Timeline 支持多种模式，通过工具栏或快捷键切换：

- **选择模式**（默认）：选择 Clip、Keyframe
- **Action 涂抹模式**：拖拽创建 ActionClip
- **关键帧编辑模式**：双击添加关键帧

## 五、数据流整合

### Gizmo → Timeline

```
用户拖拽 Gizmo
  ↓
获取当前时间点（Timeline 播放头）
  ↓
查找/创建 TransformClip 或 Keyframe
  ↓
更新位置/旋转数据
  ↓
Timeline 立即反映变化
```

### 关键帧 → 动画

```
用户创建关键帧（在 EntityClip 上）
  ↓
EntityClipKeyframe 存储完整状态
  ↓
EntityClipSampler 在关键帧之间插值
  ↓
生成平滑动画
```

### 路径 → TransformClip

```
用户绘制路径（在 Minecraft 世界）
  ↓
Path 对象保存控制点
  ↓
TransformClip 绑定到 Path
  ↓
EntityClipSampler 使用 Path.sampleAt() 采样位置
  ↓
演员沿着路径移动
```

### Action 涂抹 → ActorActionClip

```
用户拖拽 Timeline（Action 涂抹模式）
  ↓
ActionClipIntervalResolver 处理区间切割
  ↓
创建/更新 ActorActionClip
  ↓
RigProfile 解释动作语义
  ↓
演员执行对应动作
```

## 六、实现优先级建议

### 阶段 1：完成 Action 涂抹式编辑 ✅
- [x] ActionBrushState
- [x] ActionClipIntervalResolver
- [x] TimelineInteractionManager 添加 PAINT_ACTION 模式
- [ ] TimelineInteractionHandler 涂抹逻辑
- [ ] UI Action Brush 选择器
- [ ] 拖拽预览渲染
- [ ] 交互护栏

### 阶段 2：Gizmo 工具增强
- [ ] Gizmo 选择演员后显示
- [ ] Gizmo 拖拽更新 TransformClip
- [ ] Gizmo 与 Timeline 时间同步
- [ ] Gizmo 操作实时反馈

### 阶段 3：关键帧系统
- [ ] EntityClipKeyframe 数据结构
- [ ] Timeline 双击添加关键帧
- [ ] 关键帧属性编辑（Properties 面板）
- [ ] 关键帧插值算法

### 阶段 4：路径工具
- [ ] Path 数据结构
- [ ] 路径绘制工具（Minecraft 世界中）
- [ ] TransformClip 绑定路径
- [ ] 路径采样算法

## 七、技术细节

### Gizmo 与 Timeline 同步

**关键点**：Gizmo 操作时，必须知道"编辑哪个时间点"

```java
void onGizmoDrag(Vec3d newPosition) {
    // 1. 获取当前 Timeline 时间
    float currentTime = timelineModel.getCurrentTime();
    
    // 2. 查找对应的 ActorTrack
    ActorTrack track = findTrackForActor(selectedActor);
    
    // 3. 查找或创建 TransformClip
    EntityTransformClip clip = track.findTransformClipAt(currentTime);
    if (clip == null) {
        // 创建新的 Clip
        clip = createClipForTime(currentTime);
        track.addClip(clip);
    }
    
    // 4. 更新 Clip 数据（根据时间位置决定更新 fromPos 还是 toPos）
    if (isNearStart(currentTime, clip)) {
        clip.setFromPos(newPosition);
    } else {
        clip.setToPos(newPosition);
    }
}
```

### 关键帧插值

**关键点**：关键帧之间需要平滑插值

```java
EntitySample sampleAt(float time, List<EntityClipKeyframe> keyframes) {
    // 1. 找到时间前后两个关键帧
    EntityClipKeyframe prev = findPrevKeyframe(time, keyframes);
    EntityClipKeyframe next = findNextKeyframe(time, keyframes);
    
    // 2. 计算插值参数 t [0, 1]
    float t = (time - prev.time) / (next.time - prev.time);
    
    // 3. 插值位置、旋转、缩放
    Vec3d position = lerp(prev.position, next.position, t);
    Vec3d rotation = slerp(prev.rotation, next.rotation, t);  // 球面插值
    Vec3d scale = lerp(prev.scale, next.scale, t);
    
    // 4. 返回采样结果
    return EntitySample.withTransform(position, rotation, scale);
}
```

### 路径采样

**关键点**：路径需要提供平滑的曲线采样

```java
Vec3d Path.sampleAt(float t) {
    // t: [0, 1]，归一化的路径参数
    
    switch (interpolation) {
        case LINEAR:
            return linearInterpolation(controlPoints, t);
        case BEZIER:
            return bezierInterpolation(controlPoints, t);
        case CATMULL_ROM:
            return catmullRomInterpolation(controlPoints, t);
    }
}

// TransformClip 使用路径
Vec3d EntityTransformClip.samplePositionAt(float time) {
    if (followPath && pathId != null) {
        Path path = PathRegistry.get(pathId);
        // 将 Clip 时间映射到路径参数 [pathStartT, pathEndT]
        float t = mapTimeToPathParam(time, pathStartT, pathEndT);
        return path.sampleAt(t);
    } else {
        // 使用原有的 fromPos/toPos 线性插值
        return lerp(fromPos, toPos, time);
    }
}
```

## 八、总结

这三个想法与 Action 涂抹式编辑系统是**互补**的：

- **Gizmo 工具**：直观的直接操作
- **关键帧编辑**：精确的时间点控制
- **路径工具**：自由曲线移动
- **Action 涂抹**：语义化的动作编辑

它们共同构成了 Director 的**多层次编辑系统**，让导演可以根据不同场景选择最合适的编辑方式。
