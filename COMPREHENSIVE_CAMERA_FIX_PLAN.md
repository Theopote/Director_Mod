# 全面相机系统修复方案

## 发现的核心问题

### 问题 1：玩家实体位置/旋转未同步
**问题**：当 owner=TIMELINE 时，我们只设置了 Camera 的位置/旋转，但 Camera.update() 的原版逻辑会基于玩家实体（player）的位置/旋转来计算相机。如果玩家实体的位置和旋转还是旧的，会导致：
- 相机位置/旋转基于错误的玩家实体位置计算
- 关键帧捕捉时看到的画面 ≠ 播放时的画面

**修复**：
- ✅ 在 CameraMixin HEAD 处，同步玩家实体位置/旋转
- ✅ 在 CameraMixin TAIL 处，再次同步玩家实体位置/旋转

### 问题 2：拖动结束时状态丢失
**问题**：拖动结束时只释放控制权，但没有将 CameraRuntime 的状态同步到玩家实体，导致松开后跳回玩家实体的旧位置。

**修复**：
- ✅ 在 `bakeCurrentSampleIntoGameAndExit()` 中，将 CameraRuntime 状态同步到玩家实体

### 问题 3：MouseMixin 在时间线控制时仍允许视角移动
**问题**：即使输入被锁定，MouseMixin 中的 `changeLookDirection` 仍然可能被调用。

**修复**：
- ✅ 在 MouseMixin 中检查 CameraRuntime.owner，如果是 TIMELINE 则阻止视角移动

### 问题 4：拖动播放头时没有立即预览
**问题**：拖动开始时设置了 scrubbing=true，但可能没有立即应用摄像机状态。

**检查点**：
- TimelineCameraController.update() 每帧都会被调用
- 它会检测 scrubbing=true 并采样对应时间
- 应该是正常的，但需要确认时序

## 完整的修复检查清单

### ✅ 已修复
1. CameraMixin HEAD - 同步玩家实体位置/旋转
2. CameraMixin TAIL - 再次同步玩家实体位置/旋转
3. bakeCurrentSampleIntoGameAndExit - 同步状态到玩家实体
4. MouseMixin - 阻止时间线控制时的视角移动

### 🔍 需要检查
1. TimelineCameraController.update() 是否在正确时机被调用
2. 拖动播放头时，时间更新和摄像机更新的时序
3. 是否有其他地方在修改玩家位置/旋转

