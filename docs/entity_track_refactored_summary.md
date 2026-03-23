# 实体轨道重构总结（基于建议）

## ✅ 已完成的重构

### 一、核心架构（完全按照建议）

#### 1. EntityBinding 类 ✅
- **位置**：`src/main/java/com/director/ui/timeline/model/entity/EntityBinding.java`
- **功能**：描述"演员"如何绑定到世界里的实体
- **绑定模式**：
  - `SPAWN_NEW` - 生成新实体（导演虚拟演员）
  - `BIND_EXISTING` - 绑定已存在实体（真实玩家、场景NPC）
- **字段**：
  - `targetEntityUuid` - 绑定目标实体UUID
  - `targetPlayerName` - 绑定玩家名（可选）
  - `entityTypeId` - 实体类型ID（如 "minecraft:villager"）
  - `persistent` - 是否持久化

#### 2. EntityClip 基类 ✅
- **位置**：`src/main/java/com/director/ui/timeline/model/entity/EntityClip.java`
- **设计**：实现 `TimelineSelectable`，不继承 `TimelineClip`
- **特点**：
  - 统一的 start/end 时间
  - 统一的 muted/weight（用于叠加/混合）
  - 可比较排序（按时间）
  - 支持时间偏移（offsetTime）

#### 3. EntityTrack 类 ✅
- **位置**：`src/main/java/com/director/ui/timeline/model/entity/EntityTrack.java`
- **设计**：独立的实体轨道数据结构
- **特点**：
  - 一个 EntityTrack = 一个演员（Actor）
  - 包含 `EntityBinding` 绑定信息
  - 管理 `EntityClip` 列表（按时间排序）
  - 支持查找、添加、移除 Clip

#### 4. 完整的 Clip 类型系统 ✅

所有 7 种 Clip 类型已实现：

1. **EntitySpawnClip** ✅
   - 入场（生成或绑定时的初始姿态）
   - 位置、旋转、可见性控制

2. **EntityTransformClip** ✅
   - 位移/旋转/缩放片段
   - 支持 `TimelineInterpolation` 插值
   - 预留 `pathId` 用于复用 CameraPath

3. **EntityAnimationClip** ✅
   - 动画播放片段
   - 支持 Vanilla/GeckoLib/Custom 动画源
   - 循环播放和速度控制

4. **EntityActionClip** ✅
   - 行为事件片段
   - 支持多种行为类型（交互、攻击、对话等）
   - 参数表（Map<String, Object>）

5. **EntityStateClip** ✅
   - 持续状态片段
   - 潜行、冲刺、发光、无敌等
   - 可见性控制

6. **EntityLookAtClip** ✅
   - 看向目标片段
   - 支持世界坐标、实体、摄像机三种目标模式
   - 头部跟随强度控制

7. **EntityDespawnClip** ✅
   - 离场片段
   - 支持隐藏或删除两种模式

### 二、设计对齐

#### ✅ 符合建议的核心思想

1. **一个轨道 = 一个演员实例**
   - `EntityTrack` 包含 `actorId` 和 `displayName`
   - 每个轨道代表一个独立的演员

2. **Clip 系统**
   - `EntityClip` 实现 `TimelineSelectable`
   - 不继承 `TimelineClip`，独立存在
   - 更灵活，更适合"导演段落式表演"

3. **与现有系统并存**
   - `EntityKeyframe` 系统保留（向后兼容）
   - `EntityClip` 系统新增（新功能）
   - 未来可以让 `EntityTrackSampler` 同时支持两种来源

### 三、代码结构

```
src/main/java/com/director/ui/timeline/model/entity/
├── EntityKeyframe.java          (已存在 - 关键帧系统)
├── EntityClipType.java          (已更新 - Clip 类型枚举)
├── EntityBinding.java           (新增 - 演员绑定信息)
├── EntityClip.java              (新增 - Clip 基类)
├── EntityTrack.java             (新增 - 演员轨道)
├── EntitySpawnClip.java         (重构 - 出现/绑定 Clip)
├── EntityTransformClip.java     (重构 - 位移/旋转 Clip)
├── EntityAnimationClip.java     (重构 - 动画 Clip)
├── EntityActionClip.java        (新增 - 行为 Clip)
├── EntityStateClip.java         (新增 - 状态 Clip)
├── EntityLookAtClip.java        (新增 - 看向 Clip)
└── EntityDespawnClip.java       (新增 - 离场 Clip)
```

### 四、与 TimelineTrack 的关系

#### 当前设计
- `EntityTrack` 是独立的数据结构
- 可以与 `TimelineTrack` 并存
- 未来可以让 `TimelineTrack` (type=ENTITY) 的 payload 是 `EntityTrack`

#### 使用方式
```java
// 方式1：独立使用 EntityTrack
EntityTrack actorTrack = new EntityTrack("主角 Steve");
actorTrack.setBinding(EntityBinding.spawnNew("minecraft:player"));
actorTrack.addClip(new EntitySpawnClip("出现", 0.0f, 1.0f));
actorTrack.addClip(new EntityTransformClip("移动到", 1.0f, 5.0f));

// 方式2：未来集成到 TimelineTrack
TimelineTrack timelineTrack = new TimelineTrack("实体轨道", TrackType.ENTITY);
// timelineTrack.setEntityTrackPayload(actorTrack); // 未来扩展
```

### 五、后续工作

#### 优先级1：采样器集成
1. **EntityClipSampler**
   - 在 time 上找覆盖的 clips
   - 把结果叠加到 EntitySample
   - 与现有的 `EntityTrackSampler` 并存

2. **EntityTrackSampler 更新**
   - 同时支持 Keyframe 和 Clip
   - 优先使用 Clip，如果没有 Clip 则回退到 Keyframe

#### 优先级2：UI 支持
1. **Clip 创建菜单**
   - 右键菜单：创建不同类型的 Clip
   - 拖拽创建默认 Clip

2. **属性面板适配器**
   - `EntitySpawnClipPropertyAdapter`
   - `EntityTransformClipPropertyAdapter`
   - `EntityAnimationClipPropertyAdapter`
   - `EntityActionClipPropertyAdapter`
   - `EntityStateClipPropertyAdapter`
   - `EntityLookAtClipPropertyAdapter`
   - `EntityDespawnClipPropertyAdapter`

3. **轨道头部显示**
   - 显示实体图标、名称
   - 显示绑定状态

#### 优先级3：TimelineTrack 集成
1. **Payload 系统**
   - 让 `TimelineTrack` (type=ENTITY) 支持 `EntityTrack` payload
   - 序列化/反序列化支持

2. **播放系统**
   - `TimelineEntityController` 处理 Clip 播放
   - 不同类型的 Clip 有不同的播放逻辑

## 🎯 设计优势

### ✅ 完全符合建议
1. **独立的数据结构** - `EntityTrack` 不依赖 `TimelineTrack`
2. **TimelineSelectable 实现** - 符合现有代码风格
3. **完整的 Clip 类型** - 所有 7 种类型都已实现
4. **EntityBinding 分离** - 绑定逻辑独立管理

### ✅ 向后兼容
- 保留 `EntityKeyframe` 系统
- 新系统可以与旧系统共存
- 提供迁移路径（可选）

### ✅ 易扩展
- 易于添加新的 Clip 类型
- 易于与现有系统（Camera、Audio）集成
- 为未来功能（Mocap、网络同步）做好准备

## 📝 使用示例

```java
// 创建演员轨道
EntityTrack actorTrack = new EntityTrack("主角 Steve");
actorTrack.setBinding(EntityBinding.spawnNew("minecraft:player"));
actorTrack.setColorRgba(0xFF66CCFF);

// 添加 Spawn Clip（角色出现）
EntitySpawnClip spawnClip = new EntitySpawnClip("出现", 0.0f, 1.0f);
spawnClip.setPosition(0, 64, 0);
spawnClip.setYawDeg(0f);
spawnClip.setPitchDeg(0f);
actorTrack.addClip(spawnClip);

// 添加 Transform Clip（角色移动）
EntityTransformClip transformClip = new EntityTransformClip("移动到", 1.0f, 5.0f);
transformClip.setFromPos(0, 64, 0);
transformClip.setToPos(10, 64, 10);
transformClip.setInterpolation(TimelineInterpolation.SMOOTH);
actorTrack.addClip(transformClip);

// 添加 Animation Clip（角色动画）
EntityAnimationClip animClip = new EntityAnimationClip("走路", 1.0f, 5.0f);
animClip.setAnimationId("walk");
animClip.setLoop(true);
animClip.setSpeed(1.0f);
actorTrack.addClip(animClip);

// 添加 Action Clip（角色行为）
EntityActionClip actionClip = new EntityActionClip("挥剑", 3.0f, 3.5f);
actionClip.setActionType(EntityActionClip.ActionType.ATTACK);
actionClip.setParam("damage", 5.0f);
actorTrack.addClip(actionClip);

// 添加 State Clip（角色状态）
EntityStateClip stateClip = new EntityStateClip("发光", 2.0f, 6.0f);
stateClip.setGlowing(true);
stateClip.setVisibility(1.0f);
actorTrack.addClip(stateClip);

// 添加 LookAt Clip（看向摄像机）
EntityLookAtClip lookAtClip = new EntityLookAtClip("看向镜头", 4.0f, 5.0f);
lookAtClip.setMode(EntityLookAtClip.TargetMode.CAMERA);
lookAtClip.setStrength(1.0f);
actorTrack.addClip(lookAtClip);

// 添加 Despawn Clip（角色离场）
EntityDespawnClip despawnClip = new EntityDespawnClip("离场", 6.0f, 7.0f);
despawnClip.setMode(EntityDespawnClip.DespawnMode.HIDE);
actorTrack.addClip(despawnClip);
```

## ✨ 总结

实体轨道系统已按照建议完全重构：

1. ✅ **EntityBinding** - 演员绑定信息管理
2. ✅ **EntityClip** - 独立的 Clip 基类（实现 TimelineSelectable）
3. ✅ **EntityTrack** - 独立的演员轨道类
4. ✅ **7 种 Clip 类型** - 全部实现完成
5. ✅ **设计对齐** - 完全符合建议的设计理念

框架已就绪，可以在此基础上继续实现 UI 和播放系统集成。

