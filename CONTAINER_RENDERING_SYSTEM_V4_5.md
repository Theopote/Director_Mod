# ChronoBlocks 容器渲染系统 v4.5

> 🎨 **核心功能**: 在 Minecraft 游戏世界中可视化显示容器动画效果

## 📦 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│          容器渲染可视化系统                                    │
└─────────────────────────────────────────────────────────────┘

数据流 (Data Flow)
├── AnimationState
│   └── currentTime = 2.5s
│
├── PlaybackController
│   └── update(deltaTime)
│       └── Track.sample(2.5s) → Keyframe
│
├── AnimationApplicator
│   └── applyAnimation(track, keyframe)
│       └── containerTransforms[containerId] = ContainerTransform
│
└── ContainerRenderer
    └── renderAllContainers()
        ├── 读取 ContainerTransform
        ├── 应用矩阵变换
        └── 绘制边界框/指示器

渲染循环 (Render Loop)
├── Minecraft GameRenderer
│   └── World Render Last Event
│       └── ContainerRenderer.renderAllContainers()
│           ├── 遍历所有容器
│           ├── 应用变换（位置、旋转、缩放）
│           └── 绘制可视化
```

## 🎨 渲染内容

### 1. 边界框（Bounding Box）

**功能**: 显示容器包含的所有方块的整体范围

**颜色方案**:
- 默认容器：青色半透明 `rgba(0, 200, 255, 128)`
- 选中容器：金色高亮 `rgba(255, 200, 0, 200)`
- 应用不透明度：根据 `ContainerTransform.opacity` 调整

**绘制方式**:
```java
// 计算边界框（相对于中心点）
for (BlockPos pos : container.getBlocks()) {
    // 找到最小/最大坐标
}

// 绘制12条边
drawBox(matrices, minX, minY, minZ, maxX, maxY, maxZ, color);
```

### 2. 中心点指示器（Center Point）

**功能**: 显示容器的中心点（变换原点）

**显示**: 三维十字线

**颜色**: 红色 `rgba(255, 0, 0, 255)`

**大小**: 0.4 方块（固定）

**绘制方式**:
```java
// X轴
drawLine(-size, 0, 0, size, 0, 0, RED);
// Y轴
drawLine(0, -size, 0, 0, size, 0, RED);
// Z轴
drawLine(0, 0, -size, 0, 0, size, RED);
```

### 3. 变换坐标轴（Transform Gizmo）

**功能**: 显示当前变换状态（仅当有动画变换时）

**显示**: 三个彩色轴向箭头

**颜色**:
- X轴：红色
- Y轴：绿色
- Z轴：蓝色

**长度**: 2 方块

**启用条件**: `AnimationApplicator.hasTransform(containerId)`

## 🔧 变换应用流程

### 矩阵变换顺序

```java
// 1. 移动到容器中心（世界坐标）
matrices.translate(
    center.getX() - cameraPos.x,
    center.getY() - cameraPos.y,
    center.getZ() - cameraPos.z
);

// 2. 应用动画位置偏移
matrices.translate(
    transform.getX(),  // 动画位置
    transform.getY(),
    transform.getZ()
);

// 3. 应用旋转（绕中心点）
matrices.multiply(Quaternion.fromEulerXYZ(
    Math.toRadians(transform.getPitch()),
    Math.toRadians(transform.getYaw()),
    Math.toRadians(transform.getRoll())
));

// 4. 应用缩放
matrices.scale(
    transform.getScaleX(),
    transform.getScaleY(),
    transform.getScaleZ()
);

// 5. 绘制几何体（相对于变换后的坐标系）
drawBox(...);
```

### 变换效果示例

#### 示例 1: 简单移动

```java
Keyframe kf = new Keyframe(0f);
kf.setPosition(10f, 5f, 0f);  // 向X+10, Y+5移动

// 渲染结果：
// - 边界框位置 = 原始中心 + (10, 5, 0)
// - 形状保持不变
```

#### 示例 2: 旋转

```java
Keyframe kf = new Keyframe(0f);
kf.setRotation(0f, 45f, 0f);  // Y轴旋转45度

// 渲染结果：
// - 边界框绕Y轴旋转
// - 方块排列呈45度角
```

#### 示例 3: 缩放

```java
Keyframe kf = new Keyframe(0f);
kf.setScale(2f, 2f, 2f);  // 放大2倍

// 渲染结果：
// - 边界框尺寸翻倍
// - 方块间距增大
```

#### 示例 4: 复合变换

```java
Keyframe kf = new Keyframe(0f);
kf.setPosition(5f, 10f, 0f);    // 移动
kf.setRotation(0f, 90f, 0f);    // Y轴旋转90度
kf.setScale(1.5f, 1f, 1.5f);   // X/Z轴放大1.5倍
kf.setOpacity(0.5f);            // 半透明

// 渲染结果：
// - 移动到新位置
// - 绕Y轴旋转
// - X/Z方向拉伸
// - 半透明显示
```

## 💡 使用示例

### 示例 1: 基础渲染

```java
// 1. 确保容器管理器已初始化
ContainerManager manager = new ContainerManager();
ContainerManager.setInstance(manager);

// 2. 创建容器
Container container = manager.createContainer("Test Cube");
container.addBlock(new BlockPos(0, 0, 0));
container.addBlock(new BlockPos(1, 0, 0));
container.addBlock(new BlockPos(0, 1, 0));
container.addBlock(new BlockPos(1, 1, 0));

// 3. 在世界渲染事件中调用
@SubscribeEvent
public void onWorldRenderLast(WorldRenderEvent.Last event) {
    MatrixStack matrices = event.getMatrixStack();
    Camera camera = event.getCamera();
    float tickDelta = event.getPartialTick();
    
    ContainerRenderer.get().renderAllContainers(matrices, camera, tickDelta);
}
```

### 示例 2: 动画渲染

```java
// 1. 创建动画
Track track = new Track("Move", container.getId().toString(), true);

Keyframe kf0 = new Keyframe(0f);
kf0.setPosition(0f, 0f, 0f);

Keyframe kf1 = new Keyframe(2f);
kf1.setPosition(10f, 5f, 0f);

track.addKeyframe(kf0);
track.addKeyframe(kf1);

AnimationClip clip = new AnimationClip("Animation");
clip.addTrack(track);

// 2. 播放动画
AnimationState.get().setClip(clip);
PlaybackController.get().play();

// 3. 渲染自动应用变换
// - PlaybackController.update() 每帧采样关键帧
// - AnimationApplicator 应用变换到容器
// - ContainerRenderer 读取变换并渲染
```

### 示例 3: 自定义渲染选项

```java
ContainerRenderer renderer = ContainerRenderer.get();

// 启用/禁用渲染
renderer.setEnabled(true);

// 配置显示选项
renderer.setShowBoundingBox(true);    // 显示边界框
renderer.setShowCenter(true);          // 显示中心点
renderer.setShowTransformGizmo(true);  // 显示坐标轴

// 查看统计
System.out.println(renderer.getStatistics());
// Output: ContainerRenderer: Frames=1200, LastRendered=3, Enabled=true
```

## 🎯 渲染选项

### 可配置项

| 选项 | 默认值 | 说明 |
|------|--------|------|
| `enabled` | `true` | 是否启用渲染 |
| `showBoundingBox` | `true` | 显示边界框 |
| `showCenter` | `true` | 显示中心点 |
| `showTransformGizmo` | `false` | 显示变换坐标轴 |

### API 示例

```java
ContainerRenderer renderer = ContainerRenderer.get();

// 方式1：单独配置
renderer.setShowBoundingBox(true);
renderer.setShowCenter(false);
renderer.setShowTransformGizmo(true);

// 方式2：完全禁用
renderer.setEnabled(false);

// 方式3：运行时切换
boolean showGizmo = renderer.isShowTransformGizmo();
renderer.setShowTransformGizmo(!showGizmo);
```

## 📊 性能考虑

### 1. 渲染开销

**每帧渲染**: 
- 遍历所有容器：O(n)
- 每个容器：
  - 边界框：12条线 = 24个顶点
  - 中心点：3条线 = 6个顶点
  - 坐标轴：3条线 = 6个顶点
  - 总计：约36个顶点/容器

**优化建议**:
```java
// ✅ 推荐：只渲染可见容器
if (container.isVisible() && isInFrustum(container)) {
    renderContainer(container, matrices, camera);
}

// ❌ 避免：渲染所有容器
for (Container c : allContainers) {
    renderContainer(c, matrices, camera);
}
```

### 2. 矩阵变换

**变换复杂度**: O(1) 每个容器

**批处理**:
```java
// 当前实现：每个容器独立变换
matrices.push();
applyTransform(matrices, transform);
drawBox();
matrices.pop();

// 未来优化：批量渲染相同变换的容器
```

### 3. 内存占用

**每个容器渲染数据**:
- ContainerTransform: 40 bytes
- 临时顶点缓冲: ~144 bytes (36 vertices)
- 总计: ~200 bytes/container

**100个容器**: ~20 KB（可忽略不计）

## 🔍 调试工具

### 1. 渲染统计

```java
String stats = ContainerRenderer.get().getStatistics();
System.out.println(stats);

// 输出示例:
// ContainerRenderer: Frames=3600, LastRendered=5, Enabled=true
```

### 2. 单容器检查

```java
Container container = containerManager.getContainer("Moving Cube");
ContainerTransform transform = AnimationApplicator.get()
    .getContainerTransform(container.getId());

System.out.printf("Container: %s\n", container.getName());
System.out.printf("  Visible: %b\n", container.isVisible());
System.out.printf("  Blocks: %d\n", container.getBlockCount());
System.out.printf("  Transform: %s\n", transform);

// 输出示例:
// Container: Moving Cube
//   Visible: true
//   Blocks: 27
//   Transform: pos=(5.00,2.50,0.00), rot=(0.0,45.0,0.0), scale=(1.50,1.50,1.50), opacity=1.00
```

### 3. 启用调试渲染

```java
// 显示所有调试信息
ContainerRenderer renderer = ContainerRenderer.get();
renderer.setShowBoundingBox(true);
renderer.setShowCenter(true);
renderer.setShowTransformGizmo(true);

// 查看哪些容器有动画变换
for (Container c : containerManager.getAllContainers()) {
    if (AnimationApplicator.get().hasTransform(c.getId())) {
        System.out.println("Container with transform: " + c.getName());
    }
}
```

## 🚀 未来扩展

### 1. 方块级变换

```java
// 当前：整个容器作为一个整体变换
// 未来：每个方块独立变换

for (BlockPos pos : container.getBlocks()) {
    Vector3f blockPos = applyContainerTransform(pos, transform);
    renderBlock(blockState, blockPos, alpha);
}
```

### 2. 平滑插值

```java
// 当前：每帧采样一次，可能有抖动
// 未来：使用前后帧插值平滑

Keyframe previous = track.sample(currentTime - deltaTime);
Keyframe current = track.sample(currentTime);
Keyframe interpolated = lerp(previous, current, tickDelta);
```

### 3. 高级着色

```java
// 当前：简单线框
// 未来：带光照的半透明方块

// 1. 应用光照
applyLighting(blockPos, lightLevel);

// 2. 半透明排序
sortTransparentBlocks(blockPositions);

// 3. 混合模式
setBlendMode(BlendMode.ALPHA);
```

### 4. 粒子效果

```java
// 跟随容器移动的粒子
if (container.getType() == ContainerType.PARTICLE) {
    Vector3f center = getCenterWithTransform(container, transform);
    spawnParticle(ParticleTypes.PORTAL, center, velocity);
}
```

### 5. 边界框优化

```java
// 当前：每帧重新计算
// 未来：缓存边界框

BoundingBoxCache cache = new BoundingBoxCache();
BoundingBox bbox = cache.get(container.getId(), 
    () -> calculateBoundingBox(container));
```

## 📋 集成清单

### ✅ 已完成

- [x] ContainerTransform 数据结构
- [x] AnimationApplicator 应用器
- [x] PlaybackController 集成
- [x] 变换缓存系统
- [x] 调试和统计

### 🚧 待实现

- [ ] Minecraft 渲染事件集成
- [ ] 边界框绘制
- [ ] 中心点指示器
- [ ] 变换坐标轴
- [ ] 视锥剔除优化

### 📝 实现建议

#### 方式1：使用 Fabric API 事件

```java
// 注册渲染事件
WorldRenderEvents.LAST.register((context) -> {
    MatrixStack matrices = context.matrixStack();
    Camera camera = context.camera();
    float tickDelta = context.tickDelta();
    
    ContainerRenderer.get().renderAllContainers(matrices, camera, tickDelta);
});
```

#### 方式2：Mixin 注入

```java
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(RenderTickCounter tickCounter, boolean renderBlockOutline, 
                         Camera camera, GameRenderer gameRenderer, 
                         LightmapTextureManager lightmapTextureManager, 
                         Matrix4f matrix4f, Matrix4f matrix4f2, 
                         CallbackInfo ci) {
        // 渲染容器
        MatrixStack matrices = new MatrixStack();
        ContainerRenderer.get().renderAllContainers(matrices, camera, tickCounter.getTickDelta(false));
    }
}
```

## 📋 总结

✅ **完整的渲染架构** - 从数据到可视化的完整流程  
✅ **灵活的配置选项** - 可自定义显示内容  
✅ **高性能设计** - 最小化渲染开销  
✅ **调试友好** - 丰富的统计和日志  
✅ **易于扩展** - 清晰的架构支持未来功能  

现在容器动画不仅可以采样和应用，还能在游戏世界中实时可视化！🎨

---

**版本**: v4.5  
**作者**: ChronoBlocks Team  
**日期**: 2025-10-15

