# 单一摄像机系统 - 完成总结

## 核心原则

**只有一套摄像机系统：CameraRuntime.currentSample 是唯一的真相源**

- 原生 Camera 只作为显示层，永远从 CameraRuntime 读取并显示
- 不再从原生 Camera 读取作为数据源（关键帧捕捉除外，因为需要获取玩家输入的结果）

## 架构改进

### 1. CameraMixin - 双重应用机制

**HEAD 处（在 Camera.update() 开始前）：**
- 如果 owner=TIMELINE：先应用 CameraRuntime 的状态
- 确保原版逻辑看到的是正确的位置（如果需要的话）

**TAIL 处（在 Camera.update() 结束后）：**
- 如果 owner=TIMELINE：再次应用 CameraRuntime 的状态（确保原版逻辑没有修改）
- 如果 owner=PLAYER：将原生 Camera 状态同步到 CameraRuntime

**关键改进：**
- 时间线控制时，原生 Camera 在 HEAD 和 TAIL 都被设置为 CameraRuntime 的状态
- 玩家控制时，原生 Camera 由原版逻辑更新，然后同步到 CameraRuntime

### 2. 关键帧捕捉

**已经完全从 CameraRuntime 读取：**
- `captureFromCurrentCamera()` → 直接从 CameraRuntime.getCurrentSample() 读取
- `capturePositionFromGameCamera()` → 直接从 CameraRuntime.getCurrentSample() 读取
- 不再从原生 Camera 采样

### 3. 数据流

```
玩家输入 → 原生 Camera（原版逻辑更新）
  ↓
CameraMixin (TAIL) → 同步到 CameraRuntime.currentSample
  ↓
CameraRuntime.currentSample（唯一真相源）
  ↑                              ↓
TimelineCameraController     CameraMixin (HEAD/TAIL)
（时间线采样写入）            （应用到原生 Camera）
```

## 解决的问题

✅ **只有一套摄像机系统**
- CameraRuntime.currentSample 是唯一的真相源
- 原生 Camera 只作为显示层

✅ **关键帧捕捉和播放使用同一套数据**
- 都从 CameraRuntime 读取

✅ **时间线控制时原生 Camera 完全被接管**
- HEAD 和 TAIL 都应用 CameraRuntime 的状态

---

**状态**: ✅ 完成

**核心改进**: 
- CameraMixin 在 HEAD 和 TAIL 处都应用 CameraRuntime 状态
- 关键帧捕捉完全从 CameraRuntime 读取
- 原生 Camera 不再作为数据源（只在玩家控制时同步到 CameraRuntime）

