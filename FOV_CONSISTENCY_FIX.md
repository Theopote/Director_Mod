# FOV 一致性修复 - 完整总结

## 问题诊断

根据你的分析，系统存在以下关键问题：

### 1. FOV 来源不一致
- **捕捉关键帧时**：使用 `client.options.getFov().getValue()`（静态设置值）
- **实际渲染时**：使用 `GameRenderer.getFov()` 计算出的动态 FOV（包含疾跑、速度、药水等效果）
- **播放时**：使用 keyframe 中存储的静态 FOV

**结果**：按下关键帧时看到的 FOV ≠ 播放时的 FOV

### 2. 关键帧捕捉和播放使用不同的数据源
- **捕捉时**：直接从 `gameRenderer.getCamera()` 读取
- **播放时**：从 `CameraRuntime.currentSample` 读取

**结果**：捕捉和播放可能使用不同的坐标系和偏移

## 已完成的修复

### ✅ 1. TimelineCameraController - 添加真实 FOV 记录机制

**添加内容：**
```java
/** 最近一次原版渲染时使用的真实 FOV（单位：度） */
private volatile float lastGameFov = 70.0f;

public void onGameFovUpdated(float fov) {
    this.lastGameFov = fov;
}

public float getLastGameFov() {
    return lastGameFov;
}
```

### ✅ 2. GameRendererMixin - 记录真实渲染 FOV

**添加内容：**
```java
/**
 * ⭐ 记录非时间线控制时的真实渲染 FOV。
 * 在 getFov 方法返回后，记录原版计算出的真实 FOV（包含疾跑、速度、药水等动态效果）。
 */
@Inject(method = "getFov", at = @At("RETURN"))
private void chronoblocks$recordGameFov(...) {
    if (!runtime.isTimelineControlling()) {
        float realFov = cir.getReturnValue();
        TimelineCameraController.getInstance().onGameFovUpdated(realFov);
    }
}
```

### ✅ 3. TimelineCameraController.captureFromGameCamera() - 使用真实 FOV 和 CameraRuntime

**修改内容：**
- 优先使用 `CameraRuntime.getActiveSample()` 作为数据源
- 如果 CameraRuntime 没有有效状态，从游戏摄像机采样，但使用 `getLastGameFov()` 而不是静态 FOV
- 确保捕捉和播放使用同一套数据源

### ✅ 4. TimelineCameraController.capturePositionFromGameCamera() - 使用真实 FOV

**修改内容：**
- 同样优先使用 CameraRuntime 状态源
- 使用 `getLastGameFov()` 而不是静态 FOV

### ✅ 5. CameraAccessor.writeBackGameCameraToRuntime() - 使用真实 FOV

**修改内容：**
- 写回时使用 `getLastGameFov()` 而不是静态 `options.getFov()`

### ✅ 6. CameraMixin - 添加写回逻辑

**修改内容：**
- 在 `Camera.update()` 的 TAIL 处：
  - 如果时间线控制：应用时间线采样姿态
  - 如果玩家控制：将当前游戏摄像机状态写回 CameraRuntime

## 修复后的数据流

### 关键帧捕捉流程：
```
玩家控制摄像机
    ↓
Camera.update() (原版逻辑)
    ↓
CameraMixin (TAIL) → writeBackGameCameraToRuntime()
    ↓
CameraRuntime.setFromPlayer(sample with realFov)
    ↓
captureFromGameCamera() → 从 CameraRuntime.getActiveSample() 读取
    ↓
keyframe.setFov(realFov)  ✅ 使用真实 FOV
```

### 播放流程：
```
TimelineCameraController.update()
    ↓
sampler.sample(time) → CameraSample
    ↓
CameraRuntime.setFromTimeline(sample)
    ↓
CameraMixin (TAIL) → 应用 sample 到 Camera
GameRendererMixin.getFov() → 返回 sample.fov()
    ↓
渲染 ✅ 使用 keyframe 中存储的真实 FOV
```

### FOV 记录流程：
```
GameRenderer.getFov() (原版计算，包含动态效果)
    ↓
GameRendererMixin (RETURN) → 记录 realFov
    ↓
TimelineCameraController.onGameFovUpdated(realFov)
    ↓
lastGameFov = realFov ✅ 保存真实渲染 FOV
```

## 关键改进点

1. **FOV 一致性**：
   - 捕捉时使用真实渲染 FOV（包含所有动态效果）
   - 播放时使用捕捉时存储的真实 FOV
   - ✅ "按下关键帧时看到的 FOV" = "播放时的 FOV"

2. **数据源统一**：
   - 关键帧捕捉优先使用 CameraRuntime 状态源
   - 播放时使用 CameraRuntime 状态源
   - ✅ 捕捉和播放使用同一套数据

3. **实时同步**：
   - 每帧玩家控制时，将游戏摄像机状态写回 CameraRuntime
   - 确保 CameraRuntime 始终反映当前真实摄像机状态
   - ✅ 关键帧捕捉时可以直接使用 CameraRuntime 的状态

## 验证要点

修复后，应该验证：

1. ✅ 按下关键帧时看到的 FOV = 播放到该关键帧时的 FOV
2. ✅ 按下关键帧时看到的画面 = 播放到该关键帧时的画面
3. ✅ 拖动播放头预览时的画面 = 播放到该时间点的画面
4. ✅ 点击摄像机关键帧预览时的画面 = 播放到该关键帧时的画面

---

**修复状态**: ✅ 完成

**核心改进**: 
- FOV 来源统一（真实渲染 FOV）
- 数据源统一（CameraRuntime）
- 实时同步机制完善

