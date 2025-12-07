# 时间轨道系统 - 播放时时间插值修复

## 🔴 问题描述

**用户反馈：**
> 不管设置了哪个时间关键帧的时间，会影响整个世界的时间固定在这个时间点，我们希望不同的关键帧设置不同的时间点，这样播放的时候时间就能过渡了。

## 🔍 问题分析

### 根本原因

**`TimelinePanel.doRender()` 中没有调用 `TimelineWorldController.update()`！**

**问题流程：**

1. ✅ 用户设置关键帧时间：关键帧1 = 6000 ticks，关键帧2 = 12000 ticks
2. ✅ 用户预览关键帧：`previewAtKeyframeTime()` 被调用，时间被设置为 6000
3. ✅ `TimeTrackSampler.sample()` 能够正确插值：在两个关键帧之间，会根据时间线时间插值 worldTime
4. ❌ **但是**：播放时，`TimelineWorldController.update()` **从未被调用**
5. ❌ 结果：时间停留在预览时的值（6000），不会随着播放更新

### 代码证据

**`TimelinePanel.doRender()` 中的更新逻辑：**
```java
// 2. 根据播放头位置更新片段方块显示状态（每帧调用）
com.chronoblocks.ui.timeline.playback.TimelineClipBlockController.getInstance().update();
float delta = getRenderDelta();
TimelineCameraController.getInstance().updateOwnerAndSample(delta);
// ⚠️ 缺少：TimelineWorldController.getInstance().update();
```

**对比相机系统：**
- ✅ `TimelineCameraController.updateOwnerAndSample(delta)` - 每帧调用
- ❌ `TimelineWorldController.update()` - **未调用**

## ✅ 修复方案

### 修复内容

在 `TimelinePanel.doRender()` 中添加对 `TimelineWorldController.update()` 的调用：

```java
// 2. 根据播放头位置更新片段方块显示状态（每帧调用）
com.chronoblocks.ui.timeline.playback.TimelineClipBlockController.getInstance().update();
float delta = getRenderDelta();
TimelineCameraController.getInstance().updateOwnerAndSample(delta);

// ⭐ 时间轨道系统：每帧更新世界时间（播放/scrubbing时根据当前时间线时间插值）
TimelineWorldController.getInstance().update();
```

### 修复后的工作流程

**播放时：**

```
每帧循环：
  1. updatePlayback() - 更新播放头位置（时间线时间）
  2. TimelineWorldController.update() - 调用
     ↓
  3. TimelineWorldController.update() 检查：
     - 是否在播放或 scrubbing 状态
     - 如果是，获取当前时间线时间
     ↓
  4. applyTimeSample(currentTime) - 根据当前时间线时间采样
     ↓
  5. TimeTrackSampler.sample(currentTime) - 插值关键帧
     ↓
  6. 例如：当前时间线时间 = 3.0s
     - 关键帧1：时间=0.0s, worldTime=6000
     - 关键帧2：时间=5.0s, worldTime=12000
     - 插值系数 t = (3.0 - 0.0) / (5.0 - 0.0) = 0.6
     - 插值结果 worldTime = lerpTime(6000, 12000, 0.6) = 9600
     ↓
  7. applySampleInternal() - 应用插值结果到世界
     ↓
  8. applyWorldTime(9600) - 设置世界时间
     ↓
  9. maintainFrozenTime() - 每 tick 维持冻结时间
```

**关键帧之间时间过渡：**

```
时间线时间 | 关键帧1 (0.0s, 6000) | 关键帧2 (5.0s, 12000) | 插值结果
---------|---------------------|---------------------|----------
0.0s     | 6000                |                     | 6000
1.0s     |                     |                     | 7200  ← 插值
2.0s     |                     |                     | 8400  ← 插值
3.0s     |                     |                     | 9600  ← 插值
4.0s     |                     |                     | 10800 ← 插值
5.0s     |                     | 12000               | 12000
```

## 📊 预期效果

修复后：

1. **播放时时间会平滑过渡**
   - 关键帧1 = 6000 ticks (正午)
   - 关键帧2 = 12000 ticks (日落)
   - 播放时，时间会在 6000 → 12000 之间平滑过渡

2. **预览关键帧时保持不变**
   - 选中关键帧并修改参数时，仍然预览关键帧所在位置的时间
   - 这确保了设置的时间能够持久

3. **拖动画头时时间会更新**
   - Scrubbing 时，时间会根据播放头位置实时更新
   - 可以快速预览不同时间点的效果

## 🎯 测试验证

修复后，请测试：

1. **创建两个时间关键帧**
   - 关键帧1：时间=0.0s, worldTime=6000 (正午)
   - 关键帧2：时间=5.0s, worldTime=12000 (日落)

2. **播放时间线**
   - 点击播放按钮
   - 观察 F3 屏幕的 `timeOfDay`
   - 应该看到时间从 6000 平滑过渡到 12000

3. **检查日志**
   - 应该看到每帧都调用 `[TimelineWorld] 🔄 update() 调用`
   - 应该看到 `applyTimeSample()` 的采样结果在不断变化

## 📝 相关代码

- `TimelinePanel.doRender()` - 添加 `TimelineWorldController.update()` 调用
- `TimelineWorldController.update()` - 播放时根据时间线时间采样
- `TimeTrackSampler.sample()` - 插值关键帧之间的时间
- `TimeTrackSampler.lerpTime()` - 处理时间循环的插值

