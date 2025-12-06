# 摄像机关键帧设置问题修复总结

## 🎯 修复的三个核心问题

### 1. ✅ 设置第一个关键帧之后不能移动摄像机

**问题原因**：
- 点击关键帧预览时，`previewAt()` 会设置 `owner = TIMELINE`
- 如果用户在预览状态下添加新关键帧，owner 仍然是 TIMELINE
- 导致玩家无法移动摄像机

**修复方案**：
- 在 `captureFromCurrentCamera()` 中，如果当前 owner 是 TIMELINE，先切回 PLAYER
- 这样玩家可以继续移动摄像机设置下一个关键帧

**修复代码**：
```java
public void captureFromCurrentCamera(CameraKeyframe keyframe) {
    CameraRuntime runtime = CameraRuntime.getInstance();
    
    // ⭐ 关键修复：如果当前是 TIMELINE owner，先切回 PLAYER
    boolean wasTimelineOwner = runtime.getOwner() == CameraRuntime.Owner.TIMELINE;
    if (wasTimelineOwner) {
        if (this.previewing) {
            this.previewing = false;
        }
        runtime.setOwner(CameraRuntime.Owner.PLAYER);
    }
    
    // 获取当前相机状态（现在应该是玩家视角）
    CameraSample sample = runtime.getCurrentSample();
    // ... 设置关键帧属性
}
```

---

### 2. ✅ 闪跳问题修复

**问题原因**：
- `update()` 方法每帧都在调用 `setOwner()`，导致 owner 频繁切换
- 导致摄像机状态每帧都在切换，产生闪跳

**修复方案**：
- `update()` 方法不再每帧切换 owner
- owner 只在事件发生时切换（`startPlayback()`, `stopPlayback()`, `previewAt()`, `beginScrubbing()`, `endScrubbing()`）
- `update()` 只负责更新 sample，不修改 owner

**修复代码**：
```java
public void update() {
    // ... 检查状态 ...
    
    // ⭐ 关键修复：只应用 sample，不每帧切换 owner
    // owner 已在 startPlayback() 或 previewAt() 时设置为 TIMELINE
    runtime.applyTimelineSample(sample);
    // ❌ 不再调用 runtime.setOwner()，避免每帧切换导致闪跳
}
```

---

### 3. ✅ 关键帧设置时的参数与播放时一致

**问题原因**：
- `captureFromCurrentCamera()` 从 `CameraRuntime.getCurrentSample()` 读取
- 如果 owner 是 TIMELINE，`getCurrentSample()` 返回的是时间线插值结果
- 如果 owner 是 PLAYER，`getCurrentSample()` 返回的是玩家当前视角
- 需要确保设置关键帧时，捕获的是玩家当前视角

**修复方案**：
- 在 `captureFromCurrentCamera()` 中，确保 owner 是 PLAYER
- 这样捕获的是玩家当前视角，与播放时使用的时间线插值结果一致（因为播放时会重新采样）

**工作流程**：
1. 用户移动摄像机到想要的位置（owner = PLAYER）
2. 用户添加关键帧 → `captureFromCurrentCamera()` 被调用
3. 如果 owner 是 TIMELINE，先切回 PLAYER
4. 捕获 `CameraRuntime.getCurrentSample()`（现在是玩家视角）
5. 设置关键帧属性
6. 播放时，从关键帧插值得到 sample，与设置时捕获的视角一致

---

## 📋 其他修复

### 点击时间标尺不应该预览

**修复**：
- `clickTimeRuler()` 不再调用 `previewAt()`
- 因为预览会改变 owner，导致玩家无法移动摄像机
- 如果用户想预览，应该点击关键帧，而不是点击时间标尺

---

## 🔧 修改的文件

1. ✅ `TimelineCameraController.java`
   - `captureFromCurrentCamera()` - 确保设置关键帧时 owner 是 PLAYER
   - `update()` - 不再每帧切换 owner，避免闪跳
   - `startPlayback()` - 在事件发生时设置 owner
   - `previewAt()` - 在事件发生时设置 owner

2. ✅ `TimelineInteractionHandler.java`
   - `clickTimeRuler()` - 移除 `previewAt()` 调用

---

## 🎯 预期效果

1. ✅ **设置关键帧后可以继续移动摄像机**
   - owner 在设置关键帧时切回 PLAYER
   - 玩家可以继续移动摄像机设置下一个关键帧

2. ✅ **不再出现闪跳**
   - owner 只在事件发生时切换，不在每帧切换
   - 摄像机状态稳定

3. ✅ **关键帧设置时的参数与播放时一致**
   - 设置时捕获的是玩家当前视角
   - 播放时从关键帧插值得到 sample
   - 两者使用同一套数据源（CameraRuntime.currentSample）

