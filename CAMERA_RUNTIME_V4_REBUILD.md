# CameraRuntime v4.0 重构方案

## 核心原则
- **只有一台相机**：只有一个 `currentSample`（位置/旋转/FOV/DOF...）
- **Owner 切换控制权**：`PLAYER` / `TIMELINE` 枚举明确控制权
- **不切换数据**：所有数据都写入同一个 `currentSample`
- **输入锁定在 CameraRuntime**：owner 切换时自动锁定/解锁输入

## 关键修改

### 1. CameraRuntime.java
- 删除所有旧概念（mode、快照、缓存等）
- 只保留 `owner` 和 `currentSample`
- 添加输入锁定/解锁逻辑到 `setOwner()` 方法
- `updateFromGameCamera()` 用于玩家控制时写回状态
- `applyTimelineSample()` 用于时间线写入采样结果

### 2. CameraMixin.java
- 每帧 `Camera.update()` 后：
  - 如果 owner=PLAYER：写回游戏相机状态到 CameraRuntime
  - 如果 owner=TIMELINE：应用 CameraRuntime 的 currentSample 覆盖位置/旋转

### 3. TimelineCameraController.java
- 简化职责：只负责采样 + 写入 CameraRuntime + 切换 owner
- `update()` 方法根据 playing/scrubbing/previewing 决定 owner
- `captureFromCurrentCamera()` 直接从 CameraRuntime 读取，不再从 game camera 采样

### 4. 关闭窗口逻辑
- `onTimelineUiClosed()` 必须：
  - 设置 scrubbing = false
  - 设置 playing = false
  - 调用 `CameraRuntime.reset()` → owner=PLAYER + 解锁输入

## 预期效果
1. ✅ 设置关键帧时看到的画面 = 播放时的画面（同一数据源）
2. ✅ 拖动时画面 = 松开后画面（同一状态源）
3. ✅ 关闭窗口后玩家可以正常移动（完全释放控制权）

