# 两套摄像机系统分析报告

## 问题确认

### 当前确实存在两套摄像机系统

1. **Minecraft 原生 Camera**
   - 位置：`MinecraftClient.gameRenderer.getCamera()`
   - 被原版逻辑在 `Camera.update()` 内部自动更新
   - 更新时机：每帧，在 `Camera.update()` 方法内
   - 数据来源：
     - 玩家位置（`player.getPos()`）
     - 玩家旋转（`player.getYaw()`, `player.getPitch()`）
     - 鼠标输入（通过 `player.changeLookDirection()`）

2. **CameraRuntime.currentSample**
   - 位置：`CameraRuntime.getInstance().getCurrentSample()`
   - 由我们手动管理
   - 数据来源：
     - `CameraMixin` 从原生 Camera 读取（owner=PLAYER 时）
     - `TimelineCameraController` 从关键帧采样（owner=TIMELINE 时）

### 当前流程（问题所在）

```
每帧开始
  ↓
Camera.update() [原版逻辑执行]
  ↓
原版逻辑更新原生 Camera（玩家位置/旋转）
  ↓
CameraMixin.chronoblocks$applyUnifiedCamera() [TAIL]
  ↓
如果是 owner=PLAYER:
  - 从原生 Camera 读取状态
  - 写入 CameraRuntime.currentSample
  ↓
如果是 owner=TIMELINE:
  - 从 CameraRuntime.currentSample 读取
  - 覆盖原生 Camera 的位置/旋转
```

### 问题根源

1. **原生 Camera 仍然作为独立数据源**
   - 原版逻辑在 `Camera.update()` 内部更新它
   - 我们在 TAIL 处要么读取它（PLAYER）要么覆盖它（TIMELINE）
   - 这意味着**原生 Camera 和 CameraRuntime 是两个独立的状态源**

2. **数据流不一致**
   - owner=PLAYER：原生 Camera（被原版更新）→ CameraRuntime（我们读取）→ 显示
   - owner=TIMELINE：CameraRuntime（我们写入）→ 原生 Camera（我们覆盖）→ 显示
   - **这两条路径使用的是不同的数据源**

3. **可能导致的问题**
   - 关键帧捕捉时，如果从 CameraRuntime 读取，但 CameraRuntime 可能还没有被更新（还没到 TAIL）
   - 播放时，从 CameraRuntime 读取并覆盖原生 Camera，但原生 Camera 之前已经被原版逻辑更新过了
   - 两套系统的状态可能不同步

## 解决方案

### 核心原则：只有一台相机

**只有 CameraRuntime.currentSample 是唯一的真相源。**

原生 Camera 应该**只作为显示层**，永远从 CameraRuntime 读取并显示，不应该被原版逻辑直接更新。

### 需要的修改

1. **阻止原版逻辑在 Camera.update() 内部更新 Camera**
   - 需要在 `Camera.update()` 的 HEAD 处拦截
   - 如果 owner=TIMELINE，阻止原版逻辑执行

2. **确保所有更新都通过 CameraRuntime**
   - 玩家输入 → 写入 CameraRuntime
   - 时间线采样 → 写入 CameraRuntime
   - 原生 Camera → 永远从 CameraRuntime 读取

3. **关键帧捕捉直接从 CameraRuntime 读取**
   - 不再需要从原生 Camera 采样
   - 直接从 CameraRuntime.getCurrentSample() 读取

---

**结论**：确实存在两套摄像机系统，需要彻底统一为只有 CameraRuntime 一个数据源。

