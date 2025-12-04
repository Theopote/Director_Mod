# CameraRuntime v4.0 重构完成总结

## 三个问题的根源和解决方案

### 问题 1：设置的关键帧镜头和实际播放的不一样
**根源**：
- 采样用的是一套数据源（game camera / 动态 FOV）
- 播放用的是另一套数据源（不同坐标系/FOV 来源）
- 导致捕捉和播放的坐标系/FOV 不一致

**解决方案**：
- ✅ 关键帧捕捉直接从 `CameraRuntime.getCurrentSample()` 读取
- ✅ 播放时也使用 `CameraRuntime.currentSample`
- ✅ 所有操作使用同一套数据源，确保一致性

### 问题 2：拖动播放头时显示的镜头与松开鼠标/暂停时不一样
**根源**：
- 拖动时进入"电影模式"，松开切回"玩家模式+原版 FOV"
- 两套状态之间有跳变，owner 切换时状态不同步

**解决方案**：
- ✅ 只有一个 `currentSample`，不存在"恢复旧快照"的逻辑
- ✅ owner 切换只在 `TimelineCameraController.update()` 中发生
- ✅ FOV/位置只有一份状态，不会再出现跳变

### 问题 3：关闭 ChronoBlocks 窗口时玩家不能移动
**根源**：
- 时间线控制相机时锁了输入，但 UI 关闭时没有统一调用"释放相机控制权"
- `CameraRuntime` 里锁输入的逻辑还没被撤销

**解决方案**：
- ✅ `onTimelineUiClosed()` 调用 `CameraRuntime.reset()`
- ✅ `reset()` 方法：owner = PLAYER + 解锁输入 + 清空状态
- ✅ 玩家回到一个干净的 PLAYER 状态

## 核心架构改进

### 1. CameraRuntime.java - 彻底简化
- ✅ 删除所有旧概念（mode、快照、缓存等）
- ✅ 只保留 `owner` 和 `currentSample`
- ✅ 添加输入锁定/解锁逻辑到 `setOwner()` 方法
- ✅ `updateFromGameCamera()` 用于玩家控制时写回状态
- ✅ `applyTimelineSample()` 用于时间线写入采样结果

### 2. CameraMixin.java - 统一读/写
- ✅ 每帧 `Camera.update()` 后：
  - owner=PLAYER：写回游戏相机状态到 CameraRuntime
  - owner=TIMELINE：应用 CameraRuntime 的 currentSample 覆盖位置/旋转

### 3. TimelineCameraController.java - 简化职责
- ✅ 只负责采样 + 写入 CameraRuntime + 切换 owner
- ✅ `update()` 方法根据 playing/scrubbing/previewing 决定 owner
- ✅ `captureFromCurrentCamera()` 直接从 CameraRuntime 读取
- ✅ 删除 `lockPlayerInput()` 方法（已在 CameraRuntime 中）

### 4. GameRendererMixin.java - FOV 覆盖
- ✅ 只在 owner=TIMELINE 时覆盖 FOV
- ✅ 使用 `runtime.isTimelineOwner()` 判断

## 关键修复点

1. **输入锁定统一在 CameraRuntime**：
   - `setOwner(TIMELINE)` → 自动锁定输入
   - `setOwner(PLAYER)` → 自动解锁输入
   - `reset()` → 完全重置并解锁

2. **关键帧捕捉统一数据源**：
   - `captureFromCurrentCamera()` → 从 CameraRuntime 读取
   - `capturePositionFromGameCamera()` → 从 CameraRuntime 读取
   - 确保捕捉和播放使用同一套数据

3. **关闭窗口完全释放**：
   - `onTimelineUiClosed()` → 设置 scrubbing = false + playing = false + reset()
   - `reset()` → owner = PLAYER + 解锁输入 + 清空状态

## 预期效果

✅ **设置关键帧时看到的画面 = 播放时的画面**（同一数据源）
✅ **拖动时画面 = 松开后画面**（同一状态源）
✅ **关闭窗口后玩家可以正常移动**（完全释放控制权）
✅ **不会再有 FOV 跳动**（只有一个状态源）
✅ **不会再有镜头方向不一致**（统一的坐标系）

---

**重构状态**: ✅ 完成

**所有核心修改已完成，可以开始测试！**

