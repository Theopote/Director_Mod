# ChronoBlocks 动画采样与容器应用系统 v4.5

> 🎬 **核心功能**: 将时间线关键帧数据采样并实时应用到 Minecraft 方块容器

## 📦 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│          动画采样与应用系统架构                                │
└─────────────────────────────────────────────────────────────┘

播放循环 (Playback Loop)
├── ChronoBlocksUIManager
│   └── updateAnimationSystem() [每帧]
│       └── PlaybackController.update(deltaTime)
│
播放控制器 (Playback Controller)
├── PlaybackController
│   ├── update(deltaTime)
│   │   └── sampleAndApplyAnimation(time, clip)
│   │       ├── Track.sample(time) → Keyframe
│   │       └── AnimationApplicator.applyAnimation(track, keyframe)
│   └── play/pause/stop/seek()
│
动画应用器 (Animation Applicator)
├── AnimationApplicator
│   ├── applyAnimation(track, keyframe)
│   │   ├── findContainer(targetId)
│   │   ├── createTransformFromKeyframe()
│   │   └── applyTransformToContainer()
│   ├── getContainerTransform(containerId)
│   └── resetAllTransforms()
│
容器管理器 (Container Manager)
├── ContainerManager
│   ├── getContainer(id/name)
│   ├── getAllContainers()
│   └── [容器生命周期管理]
│
数据结构 (Data Structures)
├── Keyframe
│   ├── position: float[3]
│   ├── rotation: float[3]
│   ├── scale: float[3]
│   └── opacity: float
│
├── ContainerTransform
│   ├── position: float[3]
│   ├── rotation: float[3]
│   ├── scale: float[3]
│   └── opacity: float
│
└── Container
    ├── blocks: Set<BlockPos>
    ├── center: BlockPos
    └── [当前变换状态]
```

## 🔄 完整数据流程

### 1. 播放帧更新

```
[Frame Start]
    ↓
ChronoBlocksUIManager.render()
    ↓
updateAnimationSystem()
    ├── 计算 deltaTime
    └── PlaybackController.update(deltaTime)
        ├── currentTime += deltaTime * speed
        ├── 检查循环/结束
        └── sampleAndApplyAnimation(time, clip)
            ↓
            ├── 遍历所有 Track
            ├── Track.sample(time) → Keyframe
            └── AnimationApplicator.applyAnimation(track, keyframe)
```

### 2. 动画采样

```
Track.sample(time)
    ├── 查找时间点周围的两个关键帧
    │   ├── 如果 time <= 第一帧：返回第一帧
    │   ├── 如果 time >= 最后帧：返回最后帧
    │   └── 否则：找到 time 前后的两个关键帧
    │
    └── 线性插值
        ├── position = lerp(keyframeA.position, keyframeB.position, t)
        ├── rotation = lerp(keyframeA.rotation, keyframeB.rotation, t)
        ├── scale = lerp(keyframeA.scale, keyframeB.scale, t)
        └── opacity = lerp(keyframeA.opacity, keyframeB.opacity, t)
```

### 3. 动画应用

```
AnimationApplicator.applyAnimation(track, keyframe)
    ├── 1. 查找目标容器
    │   ├── 从 track.targetId 获取容器 ID
    │   ├── 尝试作为 UUID 查找
    │   └── 失败则作为名称查找
    │
    ├── 2. 创建变换
    │   ├── ContainerTransform transform = new ContainerTransform()
    │   ├── transform.setPosition(keyframe.position)
    │   ├── transform.setRotation(keyframe.rotation)
    │   ├── transform.setScale(keyframe.scale)
    │   └── transform.setOpacity(keyframe.opacity)
    │
    └── 3. 应用变换
        ├── 存储到 containerTransforms 缓存
        └── [将来] 实际应用到 Minecraft 方块渲染
```

## 🧩 核心类详解

### 1. ContainerTransform（容器变换）

**职责**: 存储容器的完整变换状态

```java
public class ContainerTransform {
    // 位置 (方块单位)
    private final float[] position = new float[]{0f, 0f, 0f};
    
    // 旋转 (度数)
    private final float[] rotation = new float[]{0f, 0f, 0f};
    
    // 缩放 (倍数)
    private final float[] scale = new float[]{1f, 1f, 1f};
    
    // 不透明度 (0.0 ~ 1.0)
    private float opacity = 1.0f;
}
```

**使用示例**:
```java
// 创建变换
ContainerTransform transform = new ContainerTransform();
transform.setPosition(10f, 5f, -20f);
transform.setRotation(0f, 45f, 0f);  // Y轴旋转45度
transform.setScale(1.5f, 1.5f, 1.5f); // 放大1.5倍
transform.setOpacity(0.8f);            // 80%不透明

// 检查状态
if (transform.isIdentity()) {
    System.out.println("无变换");
}

// 重置
transform.reset();
```

### 2. AnimationApplicator（动画应用器）

**职责**: 核心动画采样和应用引擎

```java
public class AnimationApplicator {
    private static final AnimationApplicator INSTANCE;
    
    // 核心方法
    public void applyAnimation(Track track, Keyframe sample);
    public ContainerTransform getContainerTransform(UUID containerId);
    public void resetAllTransforms();
    
    // 调试方法
    public String getStatistics();
    public String getDebugInfo();
}
```

**使用示例**:
```java
// 获取实例
AnimationApplicator applicator = AnimationApplicator.get();

// 设置容器管理器（在初始化时）
applicator.setContainerManager(containerManager);

// 应用动画（在播放循环中）
Track track = clip.getTracks().get(0);
Keyframe keyframe = track.sample(currentTime);
applicator.applyAnimation(track, keyframe);

// 查询容器变换
UUID containerId = container.getId();
ContainerTransform transform = applicator.getContainerTransform(containerId);
System.out.println("Position: " + Arrays.toString(transform.getPosition()));

// 调试信息
System.out.println(applicator.getStatistics());
System.out.println(applicator.getDebugInfo());

// 重置所有变换
applicator.resetAllTransforms();
```

### 3. ContainerManager（容器管理器）

**职责**: 管理所有容器的生命周期

**新增功能（v4.5）**:
```java
// 单例访问
ContainerManager manager = ContainerManager.getInstance();

// 在 ContainerPanel 初始化时设置
ContainerManager.setInstance(containerManager);
```

**完整示例**:
```java
// 创建容器
ContainerManager manager = new ContainerManager();
Container cityCore = manager.createContainer("City Core");
Container trees = manager.createContainer("Tree Cluster");

// 查找容器
Container found = manager.getContainer(cityCore.getId());
Container byName = manager.getContainer("City Core");

// 获取所有容器
List<Container> all = manager.getAllContainers();
```

## 💡 完整使用示例

### 示例 1: 创建简单移动动画

```java
// 1. 创建容器
ContainerManager containerManager = new ContainerManager();
Container cube = containerManager.createContainer("Moving Cube");

// 2. 创建动画剪辑
AnimationClip clip = new AnimationClip("Move Animation");

// 3. 创建轨道并绑定到容器
Track track = new Track("Position Track", cube.getId().toString(), true);

// 4. 添加关键帧
Keyframe kf0 = new Keyframe(0f);  // 0秒：起始位置
kf0.setPosition(0f, 0f, 0f);

Keyframe kf1 = new Keyframe(2f);  // 2秒：移动到(10, 0, 0)
kf1.setPosition(10f, 0f, 0f);

Keyframe kf2 = new Keyframe(4f);  // 4秒：返回原点
kf2.setPosition(0f, 0f, 0f);

track.addKeyframe(kf0);
track.addKeyframe(kf1);
track.addKeyframe(kf2);

// 5. 添加轨道到剪辑
clip.addTrack(track);

// 6. 设置到全局状态
AnimationState.get().setClip(clip);

// 7. 播放
PlaybackController.get().play();

// 8. 查询当前变换
ContainerTransform transform = AnimationApplicator.get()
    .getContainerTransform(cube.getId());
System.out.println("Current position: " + Arrays.toString(transform.getPosition()));
```

### 示例 2: 创建旋转 + 缩放动画

```java
// 创建轨道
Track track = new Track("Transform Track", container.getId().toString(), true);

// 关键帧 0: 初始状态
Keyframe kf0 = new Keyframe(0f);
kf0.setPosition(0f, 0f, 0f);
kf0.setRotation(0f, 0f, 0f);
kf0.setScale(1f, 1f, 1f);
kf0.setOpacity(1f);

// 关键帧 1: 旋转 + 放大
Keyframe kf1 = new Keyframe(2f);
kf1.setPosition(0f, 5f, 0f);      // 上升
kf1.setRotation(0f, 180f, 0f);    // Y轴旋转180度
kf1.setScale(2f, 2f, 2f);         // 放大2倍
kf1.setOpacity(0.5f);             // 半透明

// 关键帧 2: 返回初始状态
Keyframe kf2 = new Keyframe(4f);
kf2.setPosition(0f, 0f, 0f);
kf2.setRotation(0f, 360f, 0f);
kf2.setScale(1f, 1f, 1f);
kf2.setOpacity(1f);

track.addKeyframe(kf0);
track.addKeyframe(kf1);
track.addKeyframe(kf2);
```

### 示例 3: 多轨道动画

```java
AnimationClip clip = new AnimationClip("Complex Animation");

// 轨道 1: 主建筑移动
Track buildingTrack = new Track("Building", building.getId().toString(), true);
// ... 添加关键帧 ...
clip.addTrack(buildingTrack);

// 轨道 2: 树木缩放
Track treeTrack = new Track("Trees", trees.getId().toString(), true);
// ... 添加关键帧 ...
clip.addTrack(treeTrack);

// 轨道 3: 粒子效果不透明度
Track particleTrack = new Track("Particles", particles.getId().toString(), true);
// ... 添加关键帧 ...
clip.addTrack(particleTrack);

// 所有轨道同时播放
AnimationState.get().setClip(clip);
PlaybackController.get().play();
```

## 🔧 调试工具

### 1. 统计信息

```java
AnimationApplicator applicator = AnimationApplicator.get();
System.out.println(applicator.getStatistics());

// 输出示例:
// AnimationApplicator Stats: Applied=1200, Failed=5, Active=3
```

### 2. 详细调试信息

```java
System.out.println(applicator.getDebugInfo());

// 输出示例:
// === AnimationApplicator Debug Info ===
// Total Applied Frames: 1200
// Failed Applications: 5
// Active Transforms: 3
// Container Manager: Initialized
//
// Active Container Transforms:
//   - Moving Cube (uuid-123): pos=(5.50,0.00,0.00), rot=(0.0,0.0,0.0), scale=(1.00,1.00,1.00), opacity=1.00
//   - Tree Cluster (uuid-456): pos=(0.00,0.00,0.00), rot=(0.0,45.0,0.0), scale=(1.50,1.50,1.50), opacity=0.80
```

### 3. 监控特定容器

```java
// 打印容器当前变换
UUID containerId = container.getId();
ContainerTransform transform = applicator.getContainerTransform(containerId);
System.out.printf("Container: %s\n", container.getName());
System.out.printf("  Position: (%.2f, %.2f, %.2f)\n", 
    transform.getX(), transform.getY(), transform.getZ());
System.out.printf("  Rotation: (%.1f, %.1f, %.1f)\n", 
    transform.getPitch(), transform.getYaw(), transform.getRoll());
System.out.printf("  Scale: (%.2f, %.2f, %.2f)\n", 
    transform.getScaleX(), transform.getScaleY(), transform.getScaleZ());
System.out.printf("  Opacity: %.2f\n", transform.getOpacity());
```

### 4. 检查是否有变换

```java
UUID containerId = container.getId();
if (applicator.hasTransform(containerId)) {
    System.out.println("Container has active transform");
} else {
    System.out.println("Container is in default state");
}
```

## 📊 性能考虑

### 1. 采样性能

- **每帧采样**: 每个 Track 每帧采样一次
- **线性插值**: O(log n) 二分查找 + O(1) 插值
- **缓存**: 变换结果缓存在 Map 中

### 2. 内存占用

```
每个 ContainerTransform:
- position: 12 bytes (3 x float)
- rotation: 12 bytes (3 x float)
- scale: 12 bytes (3 x float)
- opacity: 4 bytes (1 x float)
= 40 bytes per transform
```

### 3. 优化建议

```java
// ✅ 推荐：复用 Keyframe 对象
Keyframe kf = new Keyframe();
for (float t = 0; t < duration; t += 0.1f) {
    track.sample(t);  // 内部复用
}

// ❌ 避免：频繁创建新对象
for (float t = 0; t < duration; t += 0.1f) {
    Keyframe kf = new Keyframe(t);  // 创建浪费
}

// ✅ 推荐：批量重置
applicator.resetAllTransforms();

// ❌ 避免：逐个重置
for (Container c : containers) {
    applicator.resetContainerTransform(c.getId());
}
```

## 🚀 未来扩展

### 1. 实际渲染应用

```java
// 将来在 applyTransformToContainer() 中实现
private void applyTransformToContainer(Container container, ContainerTransform transform) {
    // 1. 更新容器中心点
    BlockPos newCenter = calculateTransformedCenter(container.getCenter(), transform);
    
    // 2. 应用旋转矩阵到所有方块
    for (BlockPos block : container.getBlocks()) {
        BlockPos rotated = applyRotation(block, transform.getRotation());
        // ... 更新方块位置 ...
    }
    
    // 3. 应用缩放
    applyScale(container, transform.getScale());
    
    // 4. 设置透明度
    setContainerOpacity(container, transform.getOpacity());
}
```

### 2. 曲线插值

```java
// 支持非线性插值
public enum InterpolationType {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    BEZIER
}

track.setInterpolationType(InterpolationType.EASE_IN_OUT);
```

### 3. 路径动画

```java
// 沿路径移动
Path3D path = new Path3D();
path.addPoint(0f, 0f, 0f);
path.addPoint(10f, 5f, 0f);
path.addPoint(20f, 0f, 0f);

track.setPath(path);
```

### 4. 物理模拟

```java
// 添加重力、碰撞等物理效果
PhysicsSimulator physics = new PhysicsSimulator();
physics.setGravity(9.8f);
physics.applyToContainer(container);
```

## 📋 总结

✅ **完整的动画采样系统** - 从 Keyframe 到 Container 的完整流程  
✅ **单一数据源** - AnimationApplicator 统一管理所有变换  
✅ **高性能采样** - 线性插值 + 二分查找  
✅ **调试友好** - 丰富的统计和调试信息  
✅ **易于扩展** - 清晰的架构，方便添加新功能  

现在你可以：
1. ✅ 创建包含关键帧的动画剪辑
2. ✅ 播放动画并实时采样
3. ✅ 将变换应用到容器
4. ✅ 查询容器当前状态
5. ✅ 调试动画系统

**下一步**: 实现实际的 Minecraft 方块渲染变换！🎮

---

**版本**: v4.5  
**作者**: ChronoBlocks Team  
**日期**: 2025-10-15

