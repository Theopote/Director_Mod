# UI 状态和事件流修复总结

## 🎯 修复目标

理顺 UI 层的状态和事件流，明确职责分离，避免 UI 和摄像机状态混乱。

## ✅ 已完成的修复

### 1. 明确职责分离

#### TimelineModel（数据状态）
- ✅ 添加 `isScrubbing()` 和 `setScrubbing()` 方法
- ✅ 管理 `currentTime`、`isPlaying`、`isScrubbing` 状态
- ✅ `setScrubbing()` 与 `setPlaying()` 互斥处理

#### TimelinePanel / TimelineInteractionHandler（UI 层）
- ✅ 只负责鼠标/键盘 → 修改 TimelineModel 状态
- ✅ 按约定转发事件给 TimelineCameraController：
  - `beginScrubbing(time)`
  - `updateScrubbing(time)`
  - `endScrubbing(time)`
  - `startPlayback()`
  - `stopPlayback()`

#### TimelineCameraController（摄像机控制）
- ✅ 不再自己"猜测 UI 在干嘛"
- ✅ 完全根据 UI 事件回调控制 CameraRuntime 的 owner
- ✅ 移除内部 `scrubbing` 变量，完全依赖 `TimelineModel.isScrubbing()`

---

### 2. 区分点击时间标尺和拖动播放头

#### 问题
- 之前：点击和拖动使用同一套逻辑，导致状态混乱

#### 修复
- ✅ 在 `TimelineInteractionHandler` 中添加 `clickTimeRuler()` 方法
- ✅ 在 `TimelinePanel.handleRulerInteractions()` 中区分：
  - **点击**：只跳转时间，调用 `clickTimeRuler()`，使用 `previewAt()` 单帧预览
  - **拖动**：进入 scrubbing 模式，调用 `startPlayheadDrag()`，连续预览

#### 实现细节
- 使用拖动阈值（5像素）判断是点击还是拖动
- 点击时：`model.setCurrentTime()` + `previewAt()`
- 拖动时：`model.setScrubbing(true)` + `beginScrubbing()`

---

### 3. 实现干净的 scrubbing 生命周期

#### beginScrubbing(time)
- ✅ 由 `TimelineInteractionHandler.startPlayheadDrag()` 调用
- ✅ 立即采样并应用摄像机状态
- ✅ 设置 `owner = TIMELINE`

#### updateScrubbing(time)
- ✅ 由 `TimelineInteractionHandler.dragPlayhead()` 调用
- ✅ 检查 `model.isScrubbing()` 状态（单一真相源）
- ✅ 连续更新摄像机状态

#### endScrubbing(time)
- ✅ 由 `TimelineInteractionHandler.endInteractionIfNeeded()` 调用
- ✅ 调用 `bakeCurrentSampleIntoGameAndExit()` 同步状态到玩家实体
- ✅ 释放控制权给玩家

---

### 4. 修复播放/暂停逻辑

#### TimelineModel.setPlaying()
- ✅ 调用 `TimelineCameraController.startPlayback()` / `stopPlayback()`
- ✅ 确保 `scrubbing` 和 `playing` 互斥

#### TimelineCameraController
- ✅ `startPlayback()`: 只清理预览状态，不设置 scrubbing
- ✅ `stopPlayback()`: 释放控制权给玩家
- ✅ `update()`: 使用 `model.isScrubbing()` 作为单一真相源

#### TimelinePanel.updatePlayback()
- ✅ 只在 `isPlaying()` 时更新时间
- ✅ 不主动处理 scrubbing（由事件驱动）

---

### 5. UI 关闭时的清理

#### TimelinePanel.dispose()
- ✅ 静默取消所有交互（`cancelAllInteractionsSilently()`）
- ✅ 强制 UI 状态归零（`setScrubbing(false)`）
- ✅ **不再通知 CameraController**（由 ChronoBlocksScreen 统一处理）

#### TimelineInteractionHandler.cancelAllInteractionsSilently()
- ✅ 静默取消交互，不触发摄像机回调
- ✅ 避免在已经恢复游戏输入后又发一轮摄像机状态切换

#### ChronoBlocksScreen
- ✅ `removed()`: 移除重复的 `onTimelineUiClosed()` 调用
- ✅ `close()`: 统一负责通知 `TimelineCameraController.onTimelineUiClosed()` **一次**

---

## 📋 核心约定总结

### 状态管理
- **TimelineModel**: 单一真相源，管理 `isPlaying`、`isScrubbing`、`currentTime`
- **TimelineCameraController**: 完全依赖 TimelineModel 的状态，不再维护内部 scrubbing 变量

### 事件流
```
UI 交互 → TimelineModel 状态变更 → TimelineCameraController 事件回调 → CameraRuntime
```

### 关键原则
1. **UI 只管 state + 事件，不直接碰 CameraRuntime**
2. **CameraRuntime 只认 CameraController**
3. **TimelineModel 是状态的单一真相源**

---

## 🔧 修改的文件

1. ✅ `TimelineModel.java` - 添加 `isScrubbing()` 状态
2. ✅ `TimelineInteractionHandler.java` - 添加 scrubbing 生命周期方法，区分点击和拖动
3. ✅ `TimelineInteractionManager.java` - 添加 `cancelAllInteractionsSilently()`
4. ✅ `TimelineCameraController.java` - 移除内部 scrubbing 变量，使用 model 状态
5. ✅ `TimelinePanel.java` - 添加 `dispose()`，修复 `handleRulerInteractions()`
6. ✅ `ChronoBlocksScreen.java` - 移除重复的 `onTimelineUiClosed()` 调用

---

## 🎯 预期效果

修复后应该解决以下问题：

1. ✅ **拖动播放头时预览和松开后的画面不一致**
   - 拖动和播放使用同一套采样方式
   - scrubbing 状态管理清晰，不会重复切换

2. ✅ **播放/暂停时的镜头与拖动预览不一致**
   - 播放和拖动使用相同的 `CameraTrackSampler` 和插值算法
   - 状态切换清晰，不会冲突

3. ✅ **关闭 ChronoBlocks 后玩家不能移动**
   - UI 关闭时只通知 CameraController 一次
   - 状态清理完整，不会残留

---

## 📝 后续建议

1. **测试验证**：验证拖动、播放、点击时间标尺的行为是否符合预期
2. **日志优化**：添加更详细的日志，便于调试状态切换
3. **错误处理**：添加状态不一致的检测和恢复机制

