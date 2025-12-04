# 完整相机系统修复总结

## 已完成的所有修复

### ✅ 1. CameraMixin - 关键修复：同步玩家实体位置/旋转

**问题**：当 owner=TIMELINE 时，只设置了 Camera 的位置/旋转，但 Camera.update() 的原版逻辑会基于玩家实体的位置/旋转计算，导致不一致。

**修复**：
- **HEAD 处**：时间线控制时，在 Camera.update() 开始前同步玩家实体和 Camera 的状态
- **TAIL 处**：时间线控制时，在 Camera.update() 结束后再次同步，确保原版逻辑没有修改

### ✅ 2. TimelineCameraController - 拖动结束时同步状态

**问题**：拖动结束时只释放控制权，但没有将 CameraRuntime 的状态同步到玩家实体，导致松开后跳回旧位置。

**修复**：
- `bakeCurrentSampleIntoGameAndExit()` 现在会将 CameraRuntime.currentSample 的状态同步到玩家实体
- 然后释放控制权，确保玩家从当前位置继续移动

### ✅ 3. MouseMixin - 阻止时间线控制时的视角移动

**问题**：即使输入被锁定，MouseMixin 中的 `changeLookDirection` 仍然可能被调用。

**修复**：
- 在 MouseMixin 中检查 CameraRuntime.owner
- 如果是 TIMELINE，阻止视角移动

### ✅ 4. CameraKeyframePropertyAdapter - 使用新方法

**问题**：使用了已弃用的 `captureFromGameCamera()` 方法。

**修复**：
- 改为使用 `captureFromCurrentCamera()` 方法

## 核心修复原理

### 为什么需要同步玩家实体？

Minecraft 的 `Camera.update()` 方法内部会：
1. 读取玩家实体的位置（`player.getPos()`）
2. 读取玩家实体的旋转（`player.getYaw()`, `player.getPitch()`）
3. 基于这些值计算相机位置和旋转

如果只设置 Camera 而不设置玩家实体，原版逻辑会基于旧的玩家实体位置计算，导致不一致。

### 修复后的数据流

```
时间线控制时：
  TimelineCameraController.update()
    ↓
  采样关键帧 → CameraSample
    ↓
  CameraRuntime.applyTimelineSample(sample)
    ↓
  CameraRuntime.currentSample（唯一真相源）
    ↓
  CameraMixin (HEAD) → 同步玩家实体 + Camera
    ↓
  Camera.update() 原版逻辑（基于同步后的玩家实体）
    ↓
  CameraMixin (TAIL) → 再次同步玩家实体 + Camera
    ↓
  显示

拖动结束时：
  bakeCurrentSampleIntoGameAndExit()
    ↓
  将 CameraRuntime.currentSample 同步到玩家实体
    ↓
  释放控制权给玩家
    ↓
  玩家从当前位置继续移动（无跳变）
```

## 修复的文件

1. ✅ `src/main/java/com/chronoblocks/mixin/CameraMixin.java`
   - HEAD 处同步玩家实体和 Camera
   - TAIL 处再次同步

2. ✅ `src/main/java/com/chronoblocks/ui/timeline/playback/camera/TimelineCameraController.java`
   - `bakeCurrentSampleIntoGameAndExit()` 同步状态到玩家实体

3. ✅ `src/main/java/com/chronoblocks/mixin/MouseMixin.java`
   - 检查 owner，阻止时间线控制时的视角移动

4. ✅ `src/main/java/com/chronoblocks/ui/properties/adapters/CameraKeyframePropertyAdapter.java`
   - 使用新方法 `captureFromCurrentCamera()`

---

**所有修复已完成！系统现在应该只有一个统一的相机系统。**

