# 关键帧设置问题修复总结

## 🔍 发现的问题

### 问题 1：菜单构建时提前捕获状态 ✅ 已修复

**位置**：`TimelineContextMenuFactory.java` 第284-286行

**问题描述**：
- 关键帧对象在**构建菜单时**就创建并捕获了状态
- 用户可能在菜单打开后、点击前移动了摄像机
- 导致捕获的是菜单打开时的状态，而不是用户点击时的状态

**修复方案**：
- 改为使用 `item()` 方法，在用户点击时才执行 lambda
- 在执行时获取当前时间并捕获状态
- 确保捕获的是用户点击时的当前视角

**修复代码**：
```java
case CAMERA -> {
    // ⭐ 修复：在用户点击菜单项时才创建关键帧并捕获状态
    builder
        .submenu("摄像机关键帧")
            .item("添加摄像机关键帧", () -> {
                // 在执行时获取当前时间
                float currentTime = model.getCurrentTime();
                String timeLabel = formatTime(currentTime);
                CameraKeyframe keyframe = new CameraKeyframe("Camera @" + timeLabel, currentTime);
                // 在执行时捕获当前相机状态
                TimelineCameraController.getInstance().captureFromCurrentCamera(keyframe);
                // 执行命令
                commandHistory.executeCommand(new AddAutomationKeyframeCommand(model, track, keyframe));
            })
        .end();
}
```

---

## ✅ 其他路径验证

### TimelinePanel.onAddKeyframeToTrack() ✅ 正确

**位置**：`TimelinePanel.java` 第954-956行

**状态**：✅ 正确
- 这个方法在用户点击轨道头部的"+"按钮时直接调用
- 捕获是在执行时立即进行的，时机正确

### TimelineModel.addCameraKeyframe() ✅ 正确

**位置**：`TimelineModel.java` 第467-469行

**状态**：✅ 正确
- 这个方法在执行时捕获，时机正确

### CameraKeyframePropertyAdapter ✅ 正确

**位置**：`CameraKeyframePropertyAdapter.java` 第70-74行

**状态**：✅ 正确
- 属性面板的"从当前视角捕获"按钮是在用户点击时执行的
- 捕获时机正确

---

## 📋 专业软件关键帧设置流程对比

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

## 🎯 修复后的行为

修复后，关键帧设置的行为与专业软件一致：

1. ✅ **捕获时机正确**：在用户点击菜单/按钮时立即捕获
2. ✅ **时间一致**：关键帧时间 = 捕获时的播放头位置
3. ✅ **状态准确**：捕获的状态反映"当前时间点看到的视角"

---

## 🔧 技术细节

### 关键帧捕获流程：

1. 用户触发添加关键帧（菜单/按钮/快捷键）
2. **立即获取当前时间**：`model.getCurrentTime()`
3. **创建关键帧对象**：使用当前时间
4. **立即捕获当前状态**：`captureFromCurrentCamera(keyframe)`
   - 从 `CameraRuntime.getCurrentSample()` 获取状态
   - 如果时间线正在播放/拖动，这是插值结果
   - 如果时间线未播放，这是玩家当前视角
5. **执行命令添加关键帧**

### 捕获状态说明：

`CameraRuntime.getCurrentSample()` 返回的是：
- **如果 owner=PLAYER**：玩家当前的视角（由 CameraMixin 更新）
- **如果 owner=TIMELINE**：时间线当前时间点的插值结果

这确保了：
- 播放时添加关键帧：捕获的是播放头当前位置的视角
- 静止时添加关键帧：捕获的是玩家当前的视角

---

## ✅ 验证检查清单

- [x] 菜单构建时不再提前捕获
- [x] 用户点击时才捕获状态
- [x] 捕获时机与播放头位置一致
- [x] 其他添加路径验证正确
- [x] 属性面板捕获按钮正常工作

---

## 📝 后续建议

1. **添加日志**：在捕获时记录关键信息，便于调试
2. **用户提示**：如果捕获时时间线正在播放，可以给用户提示
3. **预览功能**：添加关键帧后可以立即预览该位置

