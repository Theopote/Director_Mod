# 相机系统实现总结 - 参考 Flashback 设计理念

## 参考项目

[Flashback by Moulberry](https://github.com/Moulberry/Flashback) - 一个 Minecraft 录制/回放模组，允许玩家录制游戏过程、回放并创建电影镜头。

## Flashback 的核心设计原则

1. **单一真相源**：所有相机状态都存储在一个统一的状态管理中
2. **完全控制**：录制/回放时完全控制相机，不允许原版逻辑干扰
3. **状态一致性**：录制时的状态和回放时的状态完全一致
4. **无缝切换**：在录制模式和正常游戏模式之间无缝切换

## 我们的实现（完全符合 Flashback 理念）

### 架构设计

```
┌──────────────────────────────────────────────────────┐
│         CameraRuntime.currentSample                  │
│              (唯一的真相源)                           │
│  - 位置、旋转、FOV、DOF 等所有状态                    │
└──────────────────────────────────────────────────────┘
           ↑                         ↓
    写入状态                   读取状态并应用
           │                         │
    ┌──────┴──────┐         ┌───────┴───────┐
    │             │         │               │
TimelineCamera 玩家输入   CameraMixin    GameRendererMixin
Controller               (HEAD/TAIL)     (FOV/Roll)
采样关键帧       同步写入   应用位置旋转     应用 FOV/Roll
写入 Runtime     到 Runtime
```

### 关键实现

#### 1. CameraRuntime - 单一真相源

```java
public final class CameraRuntime {
    private CameraSample currentSample = null;  // 唯一的相机状态
    private Owner owner = Owner.PLAYER;         // 控制权
    
    // 所有操作都通过这个类，确保只有一份状态
}
```

#### 2. CameraMixin - 双重应用机制

**HEAD 处（在 Camera.update() 开始前）：**
- 如果 owner=TIMELINE，先应用 CameraRuntime 状态
- 确保原版逻辑看到的是正确的位置

**TAIL 处（在 Camera.update() 结束后）：**
- 如果 owner=TIMELINE，再次应用 CameraRuntime 状态（确保原版逻辑没有修改）
- 如果 owner=PLAYER，将原生 Camera 状态同步到 CameraRuntime

**为什么需要双重应用？**
- 确保原版逻辑无法干扰时间线控制的相机
- 即使原版逻辑在中间修改了相机，我们也会在最后覆盖回去

#### 3. 输入锁定机制

```java
public synchronized void setOwner(Owner newOwner) {
    if (newOwner == Owner.TIMELINE) {
        lockPlayerInput(mc);  // 自动锁定所有输入
    } else {
        unlockPlayerInput(mc); // 自动解锁输入
    }
}
```

#### 4. 关键帧捕捉

```java
public void captureFromCurrentCamera(CameraKeyframe keyframe) {
    // 直接从 CameraRuntime 读取，不使用原生 Camera
    CameraSample sample = CameraRuntime.getInstance().getCurrentSample();
    // ... 设置关键帧
}
```

### 与 Flashback 的对应关系

| Flashback 的设计 | 我们的实现 | 说明 |
|-----------------|-----------|------|
| 统一的状态管理 | CameraRuntime | 所有相机状态都在这里 |
| 完全控制相机 | HEAD + TAIL 双重应用 | 确保原版逻辑无法干扰 |
| 状态一致性 | 关键帧和播放都从 CameraRuntime 读取 | 使用同一套数据 |
| 输入锁定 | setOwner() 自动管理 | 时间线控制时自动锁定 |

## 实现优势

1. ✅ **单一数据源**：CameraRuntime.currentSample 是唯一的真相源
2. ✅ **完全控制**：HEAD 和 TAIL 双重应用，确保原版逻辑无法干扰
3. ✅ **状态一致性**：关键帧捕捉和播放使用同一套数据
4. ✅ **输入锁定**：自动管理，无需手动处理
5. ✅ **无缝切换**：玩家控制和时间线控制之间无缝切换

## 数据流

### 玩家控制时
```
玩家输入 → 原生 Camera（原版逻辑更新）
  ↓
CameraMixin (TAIL) → 同步到 CameraRuntime.currentSample
  ↓
CameraRuntime.currentSample（唯一真相源）
```

### 时间线控制时
```
TimelineCameraController.update()
  ↓
采样关键帧 → CameraSample
  ↓
CameraRuntime.applyTimelineSample(sample)
  ↓
CameraRuntime.currentSample（唯一真相源）
  ↓
CameraMixin (HEAD) → 应用到原生 Camera
  ↓
原版逻辑执行（但输入已锁定，无法修改）
  ↓
CameraMixin (TAIL) → 再次应用 CameraRuntime 状态
```

## 核心改进点

1. **彻底移除两套摄像机系统**
   - 不再有"游戏摄像机"和"电影摄像机"的概念
   - 只有 CameraRuntime 一个数据源

2. **双重应用机制**
   - HEAD 处先应用，确保原版逻辑看到正确状态
   - TAIL 处再次应用，确保原版逻辑无法修改

3. **完全从 CameraRuntime 读取**
   - 关键帧捕捉直接从 CameraRuntime 读取
   - 播放时也从 CameraRuntime 读取
   - 确保完全一致

---

**参考来源**：[Flashback by Moulberry](https://github.com/Moulberry/Flashback)

**实现状态**：✅ 完成，完全符合 Flashback 的设计理念

