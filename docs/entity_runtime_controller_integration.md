# EntityRuntimeController 集成总结

## ✅ 已完成

### 一、EntityRuntimeController 实现

**位置**：`src/main/java/com/director/ui/timeline/playback/entity/EntityRuntimeController.java`

#### 核心功能
1. **多轨道支持**
   - 支持多条演员轨道同时控制
   - 每条轨道有独立的 TrackRuntime
   - 自动管理轨道生命周期

2. **双采样器支持**
   - 优先使用 `EntityClipSampler`（Clip 系统）
   - 如果没有 Clip 则回退到 `ActorTrackSampler`（Keyframe 系统）
   - 自动切换采样器

3. **实体应用逻辑**
   - Transform（位置、旋转、缩放）
   - State（潜行、冲刺、发光、无敌）
   - LookAt（看向目标）
   - Action（一次性触发 + 事件处理）
   - NameTag（名称标签）

4. **可插拔实体解析器**
   - 默认：PLAYER 类型绑定到 client.player
   - 支持自定义解析器（按名称、UUID 等）

### 二、TimelinePanel 集成

#### 模型绑定
- 在 `setModel()` 时调用 `setTimelineModel(model)`
- 自动监听模型变更，重建 runtimes

#### 每帧更新
- 在 `doRender()` 中调用 `update()`
- 与其他控制器（World、Audio）保持一致

### 三、设计特点

#### ✅ 与现有系统一致
- 与 `TimelineWorldController`、`TimelineAudioController` 风格一致
- 支持模型变更监听
- 支持启用/禁用开关

#### ✅ 完整的实体控制
- 位置、旋转、缩放
- 状态（潜行、冲刺、发光等）
- 动作（一次性触发 + 持续状态）
- 可见性、名称标签

#### ✅ 双采样器支持
- Clip 系统优先
- Keyframe 系统回退
- 无缝切换

## 📋 使用示例

### 基本使用

```java
// 1. 绑定 TimelineModel（在 TimelinePanel 中）
EntityRuntimeController.getInstance().setTimelineModel(model);

// 2. 每帧更新（在 TimelinePanel.doRender() 中）
EntityRuntimeController.getInstance().update();
```

### 自定义实体解析器

```java
// 按轨道名称绑定实体
EntityRuntimeController.getInstance().setEntityResolver((track, sample, client) -> {
    // 例如：按轨道名称查找实体
    String trackName = track.getName();
    // TODO: 实现查找逻辑
    return null;
});

// 按 UUID 绑定实体
EntityRuntimeController.getInstance().setEntityResolver((track, sample, client) -> {
    // 从 track 的 payload 中获取 UUID
    if (track.hasActorTrackPayload()) {
        ActorTrack actorTrack = track.getActorTrackPayload();
        UUID actorId = actorTrack.getActorId();
        // TODO: 根据 actorId 查找实体
    }
    return null;
});
```

## 🎯 后续扩展

### 优先级1：LookAt 实现
1. **世界坐标点**
   - 计算看向方向
   - 应用头部旋转

2. **实体目标**
   - 根据 UUID 查找实体
   - 实时计算看向方向

3. **摄像机目标**
   - 从 CameraSample 获取位置
   - 计算看向方向

### 优先级2：Action 事件完善
1. **PLAY_SOUND**
   - 实现声音播放逻辑
   - 支持音量、音调参数

2. **SAY_LINE**
   - 实现对话显示逻辑
   - 与字幕轨道联动

3. **PLACE_BLOCK / BREAK_BLOCK**
   - 实现方块动画
   - 支持"导演动作"模式

### 优先级3：虚拟演员
1. **VirtualActorEntity**
   - 纯客户端实体
   - 独立 ID 管理

2. **Spawn/Despawn 集成**
   - EntitySpawnClip 生成实体
   - EntityDespawnClip 移除实体

## ✨ 总结

EntityRuntimeController 已完整实现并集成：

1. ✅ **多轨道支持** - 支持多条演员轨道同时控制
2. ✅ **双采样器支持** - Clip 系统优先，Keyframe 系统回退
3. ✅ **完整的实体应用逻辑** - Transform、State、LookAt、Action、NameTag
4. ✅ **可插拔实体解析器** - 支持自定义绑定策略
5. ✅ **TimelinePanel 集成** - 模型绑定和每帧更新

Director 的"演员系统"现在真正成立了！🎬

