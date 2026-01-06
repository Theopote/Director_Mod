# 实体轨道实现总结

## ✅ 已完成的改进

### 一、核心架构（基于建议）

#### 1. EntityClip 类型系统
- ✅ `EntityClipType` 枚举 - 定义了 7 种片段类型：
  - `SPAWN` - 出现/绑定
  - `TRANSFORM` - 位移/旋转/缩放
  - `ANIMATION` - 动画播放
  - `ACTION` - 行为（攻击、对话等）
  - `STATE` - 状态（潜行、冲刺等）
  - `LOOK_AT` - 看向目标
  - `DESPAWN` - 离场

- ✅ `EntityTimelineClip` 基类 - 实体片段基类
  - 继承自 `TimelineClip`
  - 包含 `clipType` 和 `boundActorId`
  - 支持深拷贝

#### 2. 核心 Clip 实现
- ✅ `EntitySpawnClip` - 出现/绑定片段
  - 支持 `SPAWN_NEW` 和 `BIND_EXISTING` 两种模式
  - 包含位置、旋转、可见性控制
  - 支持绑定已存在的实体（如玩家）

- ✅ `EntityTransformClip` - 位移/旋转/缩放片段
  - 支持多种插值类型（Linear, CatmullRom, Bezier, Step, Hold）
  - 包含起始和结束位置、旋转、缩放
  - 设计为可复用 Camera Path 系统

- ✅ `EntityAnimationClip` - 动画片段
  - 支持多种动画源（Vanilla, GeckoLib, Custom）
  - 支持循环播放和速度控制
  - 使用动画ID字符串标识

#### 3. 轨道级实体绑定
- ✅ 在 `TimelineTrack` 中添加了实体绑定字段：
  - `boundActorId` - 演员ID
  - `boundEntityType` - 实体类型
  - `actorDisplayName` - 显示名称（如 "主角 Steve"）
  - `actorPersistent` - 是否跨剪辑存在

- ✅ 提供了完整的 getter/setter 方法

### 二、设计理念对齐

#### ✅ 一个轨道 = 一个演员实例
- 通过 `boundActorId` 和 `actorDisplayName` 实现
- 支持多个相同实体类型的轨道（如 "守卫 A"、"守卫 B"）

#### ✅ Clip 系统 vs Keyframe 系统
- **当前状态**：同时支持两种方式
  - 保留现有的 `EntityKeyframe` 系统（向后兼容）
  - 新增 `EntityClip` 系统（新功能）

- **TrackType.ENTITY** 已支持 `CLIPS` 特性
  - 可以在实体轨道上添加 Clip
  - 可以在实体轨道上添加 Keyframe

### 三、与现有系统的融合

#### ✅ 参考了容器轨道和音频轨道的设计
- `EntityTimelineClip` 类似 `AudioTimelineClip`
- 使用相同的 Clip 管理机制
- 支持轨道类型约束

#### ✅ 为 Camera Path 复用做好准备
- `EntityTransformClip` 的插值类型与 `CameraKeyframe` 一致
- 未来可以直接复用 `TimelineCameraPathManager`

## 📋 后续工作建议

### 优先级1：UI 和交互
1. **Clip 创建菜单**
   - 右键菜单：创建不同类型的 Clip
   - 拖拽创建默认 Clip

2. **属性面板适配器**
   - `EntitySpawnClipPropertyAdapter`
   - `EntityTransformClipPropertyAdapter`
   - `EntityAnimationClipPropertyAdapter`

3. **轨道头部显示**
   - 显示实体图标、名称
   - 显示绑定状态

### 优先级2：播放系统集成
1. **采样器更新**
   - `EntityTrackSampler` 需要同时支持 Keyframe 和 Clip
   - 优先使用 Clip，如果没有 Clip 则回退到 Keyframe

2. **控制器更新**
   - `TimelineEntityController` 需要处理 Clip 播放
   - 不同类型的 Clip 有不同的播放逻辑

### 优先级3：高级功能
1. **Transform Clip 路径系统**
   - 复用 Camera Path 插值算法
   - 支持贝塞尔曲线编辑
   - 支持路径预览

2. **Action Clip 实现**
   - 挥剑、对话、交互等行为
   - 与音频轨道联动

3. **State Clip 实现**
   - 状态机控制
   - 淡入淡出效果

## 🎯 设计优势

### ✅ 符合建议的核心思想
1. **一个轨道 = 一个演员实例** ✅
   - 通过 `boundActorId` 和 `actorDisplayName` 实现

2. **Clip 系统** ✅
   - 不同类型的 Clip 处理不同功能
   - 更灵活、更适合"导演"场景

3. **不依赖 Minecraft AI** ✅
   - Transform Clip 使用路径插值
   - 可控、可复现

### ✅ 向后兼容
- 保留现有的 `EntityKeyframe` 系统
- 新系统可以与旧系统共存
- 提供迁移路径（可选）

### ✅ 扩展性
- 易于添加新的 Clip 类型
- 易于与现有系统（Camera、Audio）集成
- 为未来功能（Mocap、网络同步）做好准备

## 📝 代码结构

```
src/main/java/com/chronoblocks/ui/timeline/model/entity/
├── EntityKeyframe.java          (已存在 - 关键帧系统)
├── EntityClipType.java          (新增 - Clip 类型枚举)
├── EntityTimelineClip.java       (新增 - Clip 基类)
├── EntitySpawnClip.java          (新增 - 出现/绑定 Clip)
├── EntityTransformClip.java      (新增 - 位移/旋转 Clip)
└── EntityAnimationClip.java      (新增 - 动画 Clip)

src/main/java/com/chronoblocks/ui/timeline/playback/entity/
├── EntitySample.java             (已存在)
├── EntityTrackSampler.java       (已存在)
└── TimelineEntityController.java (已存在)
```

## 🎬 使用示例（未来）

```java
// 创建实体轨道
TimelineTrack entityTrack = new TimelineTrack("主角 Steve", TrackType.ENTITY);
entityTrack.setBoundActorId(UUID.randomUUID());
entityTrack.setActorDisplayName("主角 Steve");
entityTrack.setBoundEntityType(EntityType.PLAYER);

// 添加 Spawn Clip（角色出现）
EntitySpawnClip spawnClip = new EntitySpawnClip("出现", 0.0f, 1.0f, 0xFF00FF00);
spawnClip.setSpawnMode(SpawnMode.SPAWN_NEW);
spawnClip.setPosition(0, 64, 0);
entityTrack.addClip(spawnClip);

// 添加 Transform Clip（角色移动）
EntityTransformClip transformClip = new EntityTransformClip("移动到", 1.0f, 5.0f, 0xFF0000FF);
transformClip.setFromPosition(0, 64, 0);
transformClip.setToPosition(10, 64, 10);
transformClip.setInterpolationType(InterpolationType.CATMULL_ROM);
entityTrack.addClip(transformClip);

// 添加 Animation Clip（角色动画）
EntityAnimationClip animClip = new EntityAnimationClip("走路", 1.0f, 5.0f, 0xFFFF00FF);
animClip.setAnimationId("walk");
animClip.setLoop(true);
animClip.setSpeed(1.0f);
entityTrack.addClip(animClip);
```

## ✨ 总结

实体轨道框架已经按照建议进行了改进：

1. ✅ **Clip 类型系统** - 完整的 Clip 类型定义和基类
2. ✅ **核心 Clip 实现** - Spawn、Transform、Animation 三个最重要的 Clip
3. ✅ **轨道绑定信息** - 支持一个轨道 = 一个演员实例
4. ✅ **向后兼容** - 保留现有的 Keyframe 系统
5. ✅ **扩展性** - 易于添加新功能和集成现有系统

下一步可以专注于 UI 实现和播放系统集成。

