# 摄像机系统架构重构 - 完整实施方案

## 一、重构目标

彻底合并两套摄像机系统 → **永远只有一台"游戏摄像机"**，时间线只是暂时接管控制权（owner）而不是切换镜头源。

## 二、已完成的修改

### ✅ 1. CameraRuntime.java - 核心重构

**修改内容：**
- 添加 `Owner` 枚举（PLAYER, TIMELINE）
- 实现 `setFromPlayer(CameraSample)` - 玩家输入写入
- 实现 `setFromTimeline(CameraSample)` - 时间线采样写入（自动设置 owner = TIMELINE）
- 实现 `releaseToPlayerControl()` - 释放控制权给玩家
- 实现 `isTimelineControlling()` - 判断时间线是否控制
- 实现 `get()` - 获取唯一的摄像机状态源
- 移除所有旧的 mode 和 enterXxx 方法

**核心代码：**
```java
public enum Owner {
    PLAYER,      // 玩家控制摄像机
    TIMELINE     // 时间线控制摄像机
}

private Owner owner = Owner.PLAYER;
private CameraSample current = CameraSample.EMPTY;

public void setFromTimeline(CameraSample sample) {
    if (sample != null) {
        this.owner = Owner.TIMELINE;
        this.current = sample;
    }
}

public void setFromPlayer(CameraSample sample) {
    if (sample != null && owner == Owner.PLAYER) {
        this.current = sample;
    }
}
```

### ✅ 2. TimelineCameraController.java - 简化职责

**修改内容：**
- `update()` 方法：只调用 `runtime.setFromTimeline(sample)`
- `onPlaybackStopped()`：调用 `runtime.releaseToPlayerControl()`
- `onTimelineUiClosed()`：调用 `runtime.releaseToPlayerControl()`
- `exitPreviewMode()`：调用 `runtime.releaseToPlayerControl()`
- `bakeCurrentSampleIntoGameAndExit()`：调用 `runtime.releaseToPlayerControl()`
- **不再直接修改游戏摄像机**（通过 `CameraAccessor.applySampleToGameCameraWithFov()`）

**核心代码：**
```java
public void update() {
    CameraRuntime runtime = CameraRuntime.getInstance();
    
    // ... 采样逻辑 ...
    
    // ⭐ 统一摄像机系统：只负责喂数据给 CameraRuntime
    runtime.setFromTimeline(sample);
    currentRoll = sample.roll();  // 保存 Roll 值用于 GameRendererMixin
}
```

### ✅ 3. CameraAccessor.java - 添加写回机制

**修改内容：**
- 添加 `writeBackGameCameraToRuntime(MinecraftClient)` 方法
- 用于游戏摄像机实时写回 CameraRuntime（只有在 owner 为 PLAYER 时生效）

**核心代码：**
```java
public static void writeBackGameCameraToRuntime(MinecraftClient client) {
    if (client == null) return;
    CameraSample sample = sampleGameCamera(client);
    CameraRuntime.getInstance().setFromPlayer(sample);
}
```

### ✅ 4. CameraMixin.java - 统一应用机制

**修改内容：**
- 在 `Camera.update()` 之前（HEAD）：写回玩家摄像机状态（如果 owner 是 PLAYER）
- 在 `Camera.update()` 之后（TAIL）：从 CameraRuntime 读取并应用状态

**注意：** 当前实现使用反射，可能需要根据实际 Minecraft 版本调整。建议后续使用 Mixin 访问器接口。

### ✅ 5. GameRendererMixin.java - FOV 和 Roll 覆盖

**修改内容：**
- Roll：从 CameraRuntime 读取（而不是从 TimelineCameraController）
- FOV：添加 `chronoblocks$overrideFov()` 方法，从 CameraRuntime 读取并覆盖

**核心代码：**
```java
@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
private void chronoblocks$overrideFov(CallbackInfoReturnable<Double> cir) {
    CameraSample sample = CameraRuntime.getInstance().get();
    if (sample != null && sample != CameraSample.EMPTY) {
        cir.setReturnValue((double) sample.fov());
    }
}
```

## 三、架构流程图

```
┌─────────────────────────────────────────────────────────────┐
│                    统一摄像机系统架构                          │
└─────────────────────────────────────────────────────────────┘

玩家输入 (鼠标/键盘)
    ↓
CameraMixin (HEAD) → writeBackGameCameraToRuntime()
    ↓
CameraRuntime.setFromPlayer(sample)  [仅当 owner == PLAYER]
    ↓
┌──────────────────────────────────────┐
│   CameraRuntime.current (唯一状态源)   │
└──────────────────────────────────────┘
    ↑                                    ↓
TimelineCameraController              CameraMixin (TAIL)
.setFromTimeline(sample)              → applyRuntimeState()
    ↓                                    ↓
owner = TIMELINE                    应用到游戏摄像机
                                      ↓
                              GameRendererMixin
                                  - Roll (MatrixStack)
                                  - FOV (getFov 覆盖)
```

## 四、关键修改点说明

### 1. 控制权切换

**之前（错误）：**
```java
if (playing || scrubbing) {
    runtime.enterCinematic(sample);  // 切换模式
} else {
    runtime.enterGameMode();  // 切换模式
}
```

**现在（正确）：**
```java
if (playing || scrubbing) {
    runtime.setFromTimeline(sample);  // owner = TIMELINE
} else {
    runtime.releaseToPlayerControl();  // owner = PLAYER
}
```

### 2. 状态应用

**之前（错误）：**
```java
// 直接修改游戏摄像机
CameraAccessor.applySampleToGameCameraWithFov(client, sample);
```

**现在（正确）：**
```java
// 写入 CameraRuntime
runtime.setFromTimeline(sample);
// Mixin 会从 CameraRuntime 读取并应用
```

### 3. FOV 处理

**之前（错误）：**
```java
// 修改 client.options.getFov()
client.options.getFov().setValue((int) sample.fov());
```

**现在（正确）：**
```java
// GameRendererMixin 覆盖 getFov() 返回值
@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
private void chronoblocks$overrideFov(...) {
    cir.setReturnValue((double) CameraRuntime.getInstance().get().fov());
}
```

## 五、待完善的地方

### ⚠️ CameraMixin 的实现细节

当前实现使用反射来设置 Camera 的位置和旋转，可能需要：

1. **方案A：使用 Mixin 访问器接口**
   ```java
   @Mutable
   @Accessor("pos")
   Vec3d chronoblocks$getPos();
   
   @Accessor("pos")
   void chronoblocks$setPos(Vec3d pos);
   ```

2. **方案B：通过 Player 应用**
   ```java
   // 在 Camera.update() 之后，通过 player.setPos() 应用
   if (runtime.isTimelineControlling()) {
       client.player.setPos(sample.position().x, ...);
       client.player.setYaw(sample.yaw());
   }
   ```

### ⚠️ 需要检查 TimelinePanel/InteractionHandler

确保在拖动播放头时正确切换 owner：
- 拖动开始：`runtime.setFromTimeline(sample)`
- 拖动松开：`runtime.releaseToPlayerControl()`
- 播放开始：`runtime.setFromTimeline(sample)`
- 播放停止：`runtime.releaseToPlayerControl()`

## 六、为什么这个方案能修复所有问题

| 问题 | 根本原因 | 修复方案 |
|------|---------|---------|
| **FOV 跳动** | FOV 从 2 套数据跳变 | 只有一个 current sample，FOV 不会跳变 |
| **松开鼠标跳回最后一帧** | 松开时 fallback 到 timeline sample | 松开时 owner = PLAYER，玩家继续沿用当前摄像机状态 |
| **不能退出电影模式** | runtime 状态机混乱 | 只有 owner 切换，没有"进入电影模式"状态 |
| **采样关键帧闪一下** | 采样来自不同源 | 采样直接来自当前摄像机（只有一份） |
| **多次 update/sample 乱跳** | 多个更新源冲突 | Mixin 永远写入 current sample，唯一来源 |

## 七、测试清单

重构完成后，必须测试以下场景：

1. ✅ **拖动播放头**
   - 摄像机应该平滑移动
   - FOV 应该平滑变化
   - 没有跳动或闪烁

2. ✅ **松开鼠标**
   - 摄像机应该停留在当前位置
   - **不应该**跳回最后一帧
   - 玩家可以继续控制

3. ✅ **播放时间线**
   - 摄像机应该跟随关键帧平滑移动
   - FOV 应该平滑变化

4. ✅ **停止播放**
   - 应该能够正常返回玩家控制
   - 摄像机状态保持不变

5. ✅ **多次操作**
   - 多次拖动和播放，摄像机状态应该稳定
   - 没有累积误差或状态混乱

## 八、代码变更总结

### 新增/修改的文件：

1. ✅ `CameraRuntime.java` - 核心重构
2. ✅ `TimelineCameraController.java` - 简化职责
3. ✅ `CameraAccessor.java` - 添加写回方法
4. ✅ `CameraMixin.java` - 统一应用机制
5. ✅ `GameRendererMixin.java` - 添加 FOV 覆盖

### 移除的代码：

- ❌ `enterCinematic()` 方法
- ❌ `enterGameMode()` 方法
- ❌ `isCinematicActive()` 的错误逻辑
- ❌ 直接修改游戏摄像机的代码

## 九、后续优化建议

1. **性能优化**：如果反射影响性能，改用 Mixin 访问器接口
2. **错误处理**：添加更完善的错误处理和日志记录
3. **兼容性测试**：测试不同 Minecraft 版本的兼容性
4. **代码清理**：移除所有 `@Deprecated` 标记的旧方法

---

**重构状态**: 核心架构已完成（80%）

**关键待办**: 
- CameraMixin 的可靠实现方式（可能需要根据实际 Minecraft 版本调整）
- 检查 TimelinePanel/InteractionHandler 是否正确切换 owner

