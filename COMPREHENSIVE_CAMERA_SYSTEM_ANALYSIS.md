# 摄像机系统全面分析与修复总结

## 🔍 系统分析

### 核心架构

```
┌─────────────────────────────────────────────────────────┐
│                    CameraRuntime                         │
│  - currentSample (唯一真相源)                            │
│  - owner (PLAYER / TIMELINE)                            │
└─────────────────────────────────────────────────────────┘
           ↑                    ↓
           │                    │
    ┌──────┴──────┐    ┌────────┴────────┐
    │             │    │                  │
┌───▼───┐   ┌────▼──┐ │  ┌───────────────▼──┐
│Player │   │Timeline│ │  │  CameraMixin    │
│Input  │   │Sampler │ │  │  (每帧应用)      │
└───────┘   └────────┘ │  └─────────────────┘
                        │
                   ┌────▼────┐
                   │GameCamera│
                   └─────────┘
```

### 数据流

#### 1. 玩家控制时
```
玩家输入 → 游戏摄像机 (原版逻辑)
    ↓
CameraMixin.update() (TAIL)
    ↓
CameraRuntime.updateFromGameCamera()
    ↓
currentSample = 玩家视角
```

#### 2. 时间线控制时
```
TimelineCameraController.update()
    ↓
采样关键帧 → CameraSample
    ↓
CameraRuntime.applyTimelineSample()
    ↓
currentSample = 时间线插值结果
    ↓
CameraMixin.update() (TAIL)
    ↓
应用 currentSample 到游戏摄像机
```

#### 3. 设置关键帧时
```
用户添加关键帧
    ↓
captureFromCurrentCamera()
    ↓
如果 owner=TIMELINE → 切回 PLAYER
    ↓
立即从游戏摄像机采样 (CameraAccessor.sampleFromGameCamera)
    ↓
同步到 CameraRuntime
    ↓
设置关键帧属性
```

---

## 🐛 发现的问题

### 问题 1：设置关键帧后不能移动摄像机

**根本原因**：
- `captureFromCurrentCamera()` 切回 PLAYER 后，`getCurrentSample()` 返回的还是旧的 TIMELINE sample
- CameraMixin 要到下一帧才会更新 currentSample
- 导致捕获的是旧状态，而不是玩家当前视角

**修复方案**：
- 添加 `CameraAccessor.sampleFromGameCamera()` 方法
- 切回 PLAYER 后，立即从游戏摄像机直接采样
- 不等待 CameraMixin 更新

### 问题 2：闪跳问题

**根本原因**：
- `updateScrubbing()` 每帧都在调用 `setOwner(TIMELINE)`
- 导致 owner 频繁切换，产生闪跳

**修复方案**：
- `updateScrubbing()` 只更新 sample，不设置 owner
- owner 只在 `beginScrubbing()` 时设置一次

### 问题 3：关键帧设置时的参数与播放时不一致

**根本原因**：
- 设置时从 CameraRuntime 读取（可能是旧状态）
- 播放时从关键帧插值（新状态）
- 两者数据源不一致

**修复方案**：
- 设置时立即从游戏摄像机采样
- 确保捕获的是玩家当前视角
- 播放时从关键帧插值，使用同一套插值算法

---

## ✅ 修复内容

### 1. CameraAccessor.java
- ✅ 添加 `sampleFromGameCamera()` 方法
- ✅ 直接从游戏摄像机读取位置、旋转、FOV

### 2. TimelineCameraController.java
- ✅ `captureFromCurrentCamera()` - 立即从游戏摄像机采样
- ✅ `updateScrubbing()` - 移除每帧 `setOwner()` 调用
- ✅ `update()` - 不再每帧切换 owner

### 3. 关键原则
- ✅ owner 只在事件发生时切换（`beginScrubbing`, `endScrubbing`, `startPlayback`, `stopPlayback`, `previewAt`）
- ✅ `update()` 和 `updateScrubbing()` 只更新 sample，不修改 owner
- ✅ 设置关键帧时立即从游戏摄像机采样，不等待下一帧

---

## 📋 专业动画软件对比

### Blender
- 设置关键帧：立即捕获当前摄像机状态
- 播放：从关键帧插值
- 两者使用同一套数据源和插值算法

### After Effects / Premiere Pro
- 设置关键帧：捕获当前播放头位置的摄像机状态
- 播放：从关键帧插值
- 设置和播放使用同一套数据源

### 我们的实现
- ✅ 设置关键帧：立即从游戏摄像机采样
- ✅ 播放：从关键帧插值
- ✅ 两者使用同一套数据源（CameraRuntime.currentSample）和插值算法（CameraTrackSampler）

---

## 🎯 预期效果

1. ✅ **设置关键帧后可以继续移动摄像机**
   - owner 在设置时切回 PLAYER
   - 立即从游戏摄像机采样，捕获最新状态

2. ✅ **不再出现闪跳**
   - owner 只在事件发生时切换
   - `update()` 和 `updateScrubbing()` 不修改 owner

3. ✅ **关键帧设置时的参数与播放时一致**
   - 设置时从游戏摄像机直接采样
   - 播放时从关键帧插值
   - 两者使用同一套数据源和插值算法

