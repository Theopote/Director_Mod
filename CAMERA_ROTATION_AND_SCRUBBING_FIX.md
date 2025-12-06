# 摄像机朝向和 Scrubbing 插值修复总结

## 🐛 问题分析

### 问题 1：关键帧设置时的朝向和播放时不一致

**根本原因**：
- `CameraAccessor.sampleFromGameCamera()` 从 `client.player.getYaw()` 和 `client.player.getPitch()` 读取
- 但 `CameraMixin` 从 `camera.getYaw()` 和 `camera.getPitch()` 读取（这是 Camera 对象）
- Minecraft 中，Camera 的 yaw/pitch 和 Player 的 yaw/pitch 可能有差异：
  - 第三人称视角时，Camera 和 Player 的旋转可能不同
  - Camera 可能应用了额外的偏移或插值
  - 导致设置关键帧时捕获的朝向和播放时不一致

**修复方案**：
- 修改 `CameraAccessor.sampleFromGameCamera()` 从 Camera 对象读取，而不是从 player 读取
- 确保设置关键帧时捕获的朝向和播放时一致（都使用 Camera 对象的值）

### 问题 2：拖动播放头到两个关键帧之间时没有显示插值镜头

**根本原因**：
- `beginScrubbing()` 只在 `!sampler.isEmpty()` 且采样成功时才设置 `owner = TIMELINE`
- 如果采样失败或没有关键帧，owner 不会被设置为 TIMELINE
- 导致 `updateScrubbing()` 更新 sample 后，CameraMixin 不会应用（因为 owner 不是 TIMELINE）

**修复方案**：
- `beginScrubbing()` 无论是否有关键帧，都设置 `owner = TIMELINE`
- `updateScrubbing()` 添加容错处理，确保 owner 是 TIMELINE
- 即使没有关键帧，也能正确进入 scrubbing 模式

---

## ✅ 修复内容

### 1. CameraAccessor.java

**修改**：`sampleFromGameCamera()` 方法

**之前**：
```java
// 从玩家实体获取位置和旋转
Vec3d pos = client.player.getPos();
float yaw = client.player.getYaw();
float pitch = client.player.getPitch();
```

**修复后**：
```java
// ⭐ 关键修复：从 Camera 对象读取，而不是从 player 读取
// Camera 的 yaw/pitch 是实际渲染时使用的值，确保和播放时一致
Camera camera = client.gameRenderer.getCamera();
Vec3d pos = camera.getPos();
float yaw = camera.getYaw();
float pitch = camera.getPitch();
```

**原因**：
- Camera 的 yaw/pitch 是实际渲染时使用的值
- Player 的 yaw/pitch 可能和 Camera 有差异（特别是在第三人称视角时）
- 确保设置关键帧时捕获的朝向和播放时一致

---

### 2. TimelineCameraController.java

#### 2.1 `beginScrubbing()` 方法

**修改**：无论是否有关键帧，都设置 `owner = TIMELINE`

**之前**：
```java
if (!sampler.isEmpty()) {
    CameraSample sample = sampler.sample(timeSeconds).orElse(null);
    if (sample != null) {
        runtime.applyTimelineSample(sample);
        runtime.setOwner(CameraRuntime.Owner.TIMELINE);
    }
}
// 如果没有关键帧或采样失败，owner 不会被设置
```

**修复后**：
```java
if (!sampler.isEmpty()) {
    CameraSample sample = sampler.sample(timeSeconds).orElse(null);
    if (sample != null) {
        runtime.applyTimelineSample(sample);
        runtime.setOwner(CameraRuntime.Owner.TIMELINE);
    } else {
        // 采样失败，但仍然设置 owner = TIMELINE
        runtime.setOwner(CameraRuntime.Owner.TIMELINE);
    }
} else {
    // 没有关键帧，但仍然设置 owner = TIMELINE
    runtime.setOwner(CameraRuntime.Owner.TIMELINE);
}
```

**原因**：
- 确保 scrubbing 状态正确，即使没有关键帧
- 即使采样失败，也能正确进入 scrubbing 模式

#### 2.2 `updateScrubbing()` 方法

**修改**：添加容错处理，确保 owner 是 TIMELINE

**修复后**：
```java
CameraRuntime runtime = CameraRuntime.getInstance();

// ⭐ 容错处理：确保 owner 是 TIMELINE（如果 beginScrubbing 没有正确设置）
if (runtime.getOwner() != CameraRuntime.Owner.TIMELINE) {
    runtime.setOwner(CameraRuntime.Owner.TIMELINE);
    LOGGER.warn("[TimelineCameraController] updateScrubbing: owner 不是 TIMELINE，已修复");
}
```

**原因**：
- 容错处理，确保即使 `beginScrubbing()` 没有正确设置 owner，也能正常工作
- 添加调试日志，便于排查问题

---

## 🎯 预期效果

1. ✅ **关键帧设置时的朝向和播放时一致**
   - 设置时从 Camera 对象读取（实际渲染值）
   - 播放时从关键帧插值（也使用 Camera 对象的值）
   - 两者使用同一套数据源

2. ✅ **拖动播放头到两个关键帧之间时显示插值镜头**
   - `beginScrubbing()` 确保设置 `owner = TIMELINE`
   - `updateScrubbing()` 更新 sample 并应用
   - CameraMixin 正确应用 sample 到 Camera

---

## 📋 技术细节

### Camera vs Player 的差异

在 Minecraft 中：
- **Player**：玩家实体的位置和旋转（用于物理计算、碰撞检测等）
- **Camera**：实际渲染时使用的摄像机（可能应用了额外的偏移、插值等）

特别是在：
- 第三人称视角时，Camera 和 Player 的旋转可能不同
- 摄像机平滑移动时，Camera 可能应用了插值
- 某些情况下，Camera 可能应用了额外的偏移

因此，设置关键帧时应该从 Camera 读取，而不是从 Player 读取。

---

## 🔧 修改的文件

1. ✅ `CameraAccessor.java` - 从 Camera 对象读取，而不是从 player 读取
2. ✅ `TimelineCameraController.java` - 修复 `beginScrubbing()` 和 `updateScrubbing()` 逻辑

