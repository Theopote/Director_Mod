# EntityClipSampler 实现总结

## ✅ 已完成

### 一、EntityClipSampler 类

**位置**：`src/main/java/com/chronoblocks/ui/timeline/playback/entity/EntityClipSampler.java`

#### 核心功能
1. **采样流程**：
   - 在给定时间找到所有覆盖的 clips
   - 按类型分类处理（Spawn, Transform, Animation, Action, State, LookAt, Despawn）
   - 将结果叠加到 EntitySample

2. **支持多个 clips 同时覆盖**：
   - Transform 和 Animation 可以重叠
   - State Clip 可以叠加
   - 不同类型的 clip 有不同的处理逻辑

#### 各类型 Clip 的处理逻辑

1. **EntitySpawnClip**（优先级最高）
   - 作为基础状态
   - 应用位置、旋转、可见性
   - 只在开始时生效

2. **EntityTransformClip**
   - 支持插值（from/to）
   - 支持 Linear、Smooth、Step 插值类型
   - 位置、旋转、缩放分别插值
   - 旋转使用角度插值（处理 360° 环绕）

3. **EntityAnimationClip**
   - 直接应用动画信息
   - 存储到 EntitySample 的扩展字段

4. **EntityStateClip**（可叠加）
   - 布尔状态使用 OR 逻辑（任何一个为 true 则应用）
   - 可见性取最小值（最不透明）

5. **EntityLookAtClip**
   - 直接应用看向信息
   - 存储目标模式、坐标点、实体UUID、强度

6. **EntityDespawnClip**
   - 在接近结束时生效
   - 支持隐藏或删除两种模式

7. **EntityActionClip**
   - 标记需要处理（实际触发逻辑在控制器中）

### 二、EntitySample 扩展

**位置**：`src/main/java/com/chronoblocks/ui/timeline/playback/entity/EntitySample.java`

#### 新增字段

1. **动画相关**：
   - `animationId` - 动画ID
   - `animationSource` - 动画源类型（临时复用 EntityType）
   - `animationLoop` - 动画循环
   - `animationSpeed` - 动画速度

2. **LookAt 相关**：
   - `lookAtMode` - 目标模式（"WORLD_POINT", "ENTITY_UUID", "CAMERA"）
   - `lookAtPoint` - 世界坐标点
   - `lookAtEntityUuid` - 目标实体UUID
   - `lookAtStrength` - 头部跟随强度

3. **状态相关**：
   - `sneaking` - 潜行
   - `sprinting` - 冲刺
   - `glowing` - 发光
   - `invulnerable` - 无敌

#### 更新方法

- `copyFrom()` - 已更新以复制所有新字段

### 三、设计特点

#### ✅ 与 EntityTrackSampler 并存
- `EntityTrackSampler` - 处理 EntityKeyframe（关键帧系统）
- `EntityClipSampler` - 处理 EntityClip（Clip 系统）
- 两者可以并存，优先使用 Clip，如果没有 Clip 则回退到 Keyframe

#### ✅ 支持 Clip 叠加
- 多个 clips 可以同时覆盖一个时间点
- 不同类型的 clip 有不同的叠加逻辑
- State Clip 使用 OR 逻辑叠加

#### ✅ 插值支持
- Transform Clip 支持 Linear、Smooth、Step 插值
- 旋转使用角度插值（处理 360° 环绕）
- 位置和缩放使用线性插值

### 四、使用示例

```java
// 创建实体轨道
EntityTrack actorTrack = new EntityTrack("主角 Steve");
actorTrack.setBinding(EntityBinding.spawnNew("minecraft:player"));

// 添加 clips
EntitySpawnClip spawnClip = new EntitySpawnClip("出现", 0.0f, 1.0f);
spawnClip.setPosition(0, 64, 0);
actorTrack.addClip(spawnClip);

EntityTransformClip transformClip = new EntityTransformClip("移动到", 1.0f, 5.0f);
transformClip.setFromPos(0, 64, 0);
transformClip.setToPos(10, 64, 10);
transformClip.setInterpolation(TimelineInterpolation.SMOOTH);
actorTrack.addClip(transformClip);

EntityAnimationClip animClip = new EntityAnimationClip("走路", 1.0f, 5.0f);
animClip.setAnimationId("walk");
animClip.setLoop(true);
actorTrack.addClip(animClip);

EntityStateClip stateClip = new EntityStateClip("发光", 2.0f, 6.0f);
stateClip.setGlowing(true);
actorTrack.addClip(stateClip);

// 创建采样器
EntityClipSampler sampler = new EntityClipSampler(actorTrack);

// 在时间 3.0 秒采样
EntitySample sample = sampler.sampleAt(3.0f);

// sample 包含：
// - position: 插值后的位置（在 fromPos 和 toPos 之间）
// - rotation: 插值后的旋转
// - animationId: "walk"
// - glowing: true
// - visibility: 1.0f
```

### 五、后续工作

#### 优先级1：TimelineEntityController 集成
1. **更新 TimelineEntityController**
   - 支持 EntityClipSampler
   - 优先使用 Clip，如果没有 Clip 则回退到 Keyframe
   - 处理 Action Clip 的触发逻辑

2. **动画播放**
   - 根据 EntitySample 的 animationId 播放动画
   - 支持动画循环和速度控制

3. **LookAt 实现**
   - 根据 EntitySample 的 lookAtMode 计算看向方向
   - 支持世界坐标、实体、摄像机三种模式

#### 优先级2：路径系统集成
1. **Transform Clip 路径支持**
   - 如果 followPath 为 true 且 pathId 不为 null，使用路径系统
   - 复用 Camera Path 插值算法

2. **路径预览**
   - 在编辑器中预览路径
   - 支持贝塞尔曲线编辑

#### 优先级3：高级功能
1. **State Clip 叠加优化**
   - 实现真正的状态叠加逻辑
   - 支持状态优先级

2. **Action Clip 触发系统**
   - 实现 Action Clip 的触发逻辑
   - 支持多次触发（triggerOnce = false）

## ✨ 总结

EntityClipSampler 已实现完成：

1. ✅ **核心采样逻辑** - 在时间上找覆盖的 clips，叠加到 EntitySample
2. ✅ **各类型 Clip 处理** - 所有 7 种 Clip 类型都有对应的处理逻辑
3. ✅ **EntitySample 扩展** - 添加了动画、LookAt、状态等扩展字段
4. ✅ **插值支持** - Transform Clip 支持多种插值类型
5. ✅ **叠加逻辑** - State Clip 支持叠加，多个 clips 可以同时生效

下一步可以集成到 TimelineEntityController 中，实现完整的播放系统。

