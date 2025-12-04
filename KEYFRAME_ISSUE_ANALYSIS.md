# 关键帧设置问题详细分析

## 🔍 问题概述

关键帧设置存在时机和逻辑问题，导致捕获的状态与实际需要的不一致。

## 🐛 发现的主要问题

### 问题 1：菜单构建时捕获状态（严重问题）

**位置**：`TimelineContextMenuFactory.java` 第284-286行

```java
CameraKeyframe keyframe = new CameraKeyframe("Camera @" + formatTime(time), time);
// ⭐ 自动捕获完整的当前相机状态（位置、旋转、FOV等所有属性）
TimelineCameraController.getInstance().captureFromCurrentCamera(keyframe);
```

**问题描述**：
- 关键帧对象在**构建菜单时**就创建并捕获了状态
- 但菜单项是**延迟执行**的，用户可能在菜单打开后、点击前移动了摄像机
- 这导致捕获的是菜单打开时的状态，而不是用户点击时的状态

**专业软件的行为**：
- **Premiere Pro/After Effects**：关键帧在用户点击菜单项时创建和捕获
- **Blender**：关键帧在按下快捷键或点击按钮时立即捕获当前状态

**修复方案**：
应该在命令执行时（用户点击菜单项时）捕获状态，而不是在菜单构建时。

---

### 问题 2：关键帧时间与捕获状态不一致

**位置**：多处（`TimelinePanel.onAddKeyframeToTrack`, `TimelineContextMenuFactory`, `TimelineModel.addCameraKeyframe`）

**问题描述**：
- 关键帧时间使用 `model.getCurrentTime()` 设置
- 捕获状态使用 `CameraRuntime.getCurrentSample()` 获取
- 如果时间线正在播放或拖动，`getCurrentSample()` 可能是：
  - 时间线插值的结果（如果 owner=TIMELINE）
  - 玩家视角（如果 owner=PLAYER）
- 这导致关键帧的"时间"和"状态"可能不匹配

**专业软件的行为**：
- **Premiere Pro/After Effects**：
  - 添加关键帧时，捕获的是**当前播放头位置对应的时间点**的摄像机状态
  - 如果时间线正在播放，捕获的就是播放头当前位置的状态
- **Blender**：
  - 关键帧捕获的是**当前帧**的摄像机状态
  - 时间由当前帧号决定

**修复方案**：
关键帧应该捕获的是"在当前时间点看到的视角"，而不是"当前CameraRuntime中的状态"。

---

### 问题 3：捕获时机不正确

**当前流程**：
1. 用户右键打开菜单 → 菜单构建时创建关键帧对象并捕获状态
2. 用户点击菜单项 → 执行命令，使用已捕获的状态

**问题**：
- 步骤1和步骤2之间可能有时间差
- 如果时间线在播放，或者用户移动了摄像机，捕获的状态就过时了

**专业软件的流程**：
1. 用户移动到想要的视角
2. 用户添加关键帧（快捷键/按钮/菜单）
3. **立即捕获当前摄像机状态**
4. 将状态写入关键帧并添加到时间线

**修复方案**：
应该在用户实际点击/执行命令时立即捕获，而不是提前捕获。

---

### 问题 4：关键帧预览时状态更新问题

**位置**：`TimelineInteractionHandler.onAutomationKeyframeClicked()` 第426行

```java
com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance().previewAt(timeSeconds);
```

**问题描述**：
- 点击关键帧时调用 `previewAt()` 预览
- 但如果用户在这个预览状态下添加新关键帧，捕获的可能是预览状态的摄像机，而不是玩家实际控制的摄像机

---

## ✅ 正确的关键帧设置流程（参考专业软件）

### Premiere Pro / After Effects 的工作流程：

1. **用户移动到想要的视角**
   - 可以是玩家控制视角
   - 也可以是播放时间线时的视角

2. **用户添加关键帧**
   - 按快捷键
   - 点击按钮
   - 或使用菜单

3. **立即捕获当前状态**
   - 捕获当前播放头位置对应时间点的摄像机状态
   - 如果时间线正在播放，捕获播放头当前位置的状态
   - 如果时间线未播放，捕获当前玩家视角

4. **创建关键帧并设置属性**
   - 时间 = 当前播放头位置
   - 位置/旋转/FOV = 捕获的状态

### Blender 的工作流程：

1. **用户移动摄像机到想要的位置**
2. **用户按下 I 键或点击按钮**
3. **立即捕获当前摄像机状态**
4. **创建关键帧并添加到时间线**

---

## 🔧 修复建议

### 修复 1：延迟捕获状态

**修改 `TimelineContextMenuFactory.java`**：
- 不在菜单构建时捕获状态
- 在命令执行时（`AddAutomationKeyframeCommand`）捕获状态

### 修复 2：确保时间与状态一致

**修改捕获逻辑**：
- 如果时间线正在播放或拖动，捕获当前播放头位置的插值结果
- 如果时间线未播放，捕获玩家当前的视角
- 关键帧时间始终等于捕获状态时的时间线位置

### 修复 3：实时捕获

**修改添加关键帧的入口**：
- `onAddKeyframeToTrack()`: 在执行时捕获
- `TimelineContextMenuFactory`: 使用命令延迟捕获
- `TimelineModel.addCameraKeyframe()`: 在执行时捕获

---

## 📋 需要检查的代码位置

1. ✅ `TimelineContextMenuFactory.java` - 菜单构建时捕获（需要修复）
2. ✅ `TimelinePanel.onAddKeyframeToTrack()` - 应该已经在执行时捕获
3. ✅ `TimelineModel.addCameraKeyframe()` - 在执行时捕获
4. ✅ `CameraKeyframePropertyAdapter` - 属性面板的捕获按钮（应该是实时的）

---

## 🎯 总结

核心问题：**关键帧捕获的时机不对，应该在实际执行时捕获，而不是提前捕获**。

需要确保：
1. 捕获时机：用户点击菜单/按钮时立即捕获
2. 时间一致性：关键帧时间 = 捕获时的时间线位置
3. 状态准确性：捕获的状态应该反映"当前时间点看到的视角"

