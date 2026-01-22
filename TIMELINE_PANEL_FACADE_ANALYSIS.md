# TimelinePanel Facade 化评估报告

## 📋 总体结论

**该建议有极高的参考价值，且是必要的架构优化。**

TimelinePanel 当前存在"控制层污染"问题，需要按照"控制走 Facade，编辑走 Model"的原则进行拆分。

---

## ✅ 1. 必须 Facade 化的代码段（控制层污染）

### A. doRender() 中的播放子系统 tick（L398-L434）

**位置：** `TimelinePanel.java:398-434`

**当前代码：**
```java
// 1. 更新播放状态（每帧调用）
updatePlayback();

// 2. 根据播放头位置更新片段方块显示状态（每帧调用）
com.chronoblocks.ui.timeline.playback.TimelineClipBlockController.getInstance().update();
float delta = getRenderDelta();
TimelineCameraController.getInstance().updateOwnerAndSample(delta);

// ⭐ 时间轨道系统：每帧更新世界时间
TimelineWorldController.getInstance().update();

// ⭐ 音频轨道系统：每帧更新音频播放
com.chronoblocks.ui.timeline.playback.TimelineAudioController.getInstance().update();

// ⭐ 实体轨道系统 v2：每帧更新实体状态
com.chronoblocks.actor.runtime.ActorRuntimeController.getInstance().update();

// ⭐ Actor Gizmo 系统：每帧更新
com.chronoblocks.ui.actor.gizmo.ActorGizmoController.getInstance().update();

// ⭐ 天气渲染器：每帧更新天气粒子特效
com.chronoblocks.client.renderer.weather.WeatherRenderer.get().tick();

// ⭐ 天气粒子控制器：每帧持续生成雨雪粒子
com.chronoblocks.ui.timeline.playback.weather.WeatherParticleController.getInstance().tick();

// ⭐ v1.1 架构：天气事件子系统
com.chronoblocks.ui.timeline.playback.weather.TimelineWeatherController.getInstance().tickEvents(model.getCurrentTime());
```

**问题：**
- ❌ 这些是"播放引擎级"的驱动，属于 Playback Orchestrator 的职责
- ❌ TimelinePanel 不可见时播放逻辑会断掉/不一致
- ❌ Menu/Toolbar/Shortcut 通过 Facade 控制播放，但真正 tick 在 TimelinePanel → 行为分裂
- ❌ UI 卡顿会直接影响播放驱动

**建议改法：**
1. 将这段整体迁移到 `ChronoBlocksScreen.tick()` 或 `PlaybackSystem.tick(delta)`
2. 由 Facade 控制播放状态：`commands.playback().tick(delta)`
3. TimelinePanel 只保留"视图渲染 + 编辑交互"

**优先级：** 🔥🔥🔥 最高（架构核心问题）

---

### B. updatePlayback() 方法（L492-L540）

**位置：** `TimelinePanel.java:492-540`

**当前代码：**
```java
private void updatePlayback() {
    if (!model.isPlaying()) {
        return;
    }
    
    float deltaTime = ImGui.getIO().getDeltaTime();
    float newTime = model.getCurrentTime() + deltaTime * model.getPlaybackSpeed();
    float duration = model.getDuration();
    
    // 处理超出范围
    if (newTime > duration) {
        if (model.isLoopEnabled() && duration > 0) {
            newTime = newTime % duration;
        } else {
            newTime = duration;
            model.setPlaying(false);
            TimelineCameraController.getInstance().onPlaybackStopped();
        }
    }
    
    model.setCurrentTime(newTime);
    TimelineSeasonController.getInstance().onTimelineTick(newTime);
    com.chronoblocks.ui.timeline.playback.weather.TimelineWeatherController.getInstance().updateForPlayback(newTime);
    ensurePlayheadVisible();
}
```

**问题：**
- ❌ 时间推进、播放停止、播放速度、播放结束逻辑：都应属于 `commands.playback()` 的实现层
- ❌ `TimelineCameraController.onPlaybackStopped()` 应该由 Playback 子系统统一调用
- ❌ TimelinePanel 不应该负责播放引擎的 tick

**建议改法：**
1. 删除 `updatePlayback()` 方法（或让它只做 UI 跟随显示）
2. 真正的 tick 放到播放系统里（PlaybackController 或 PlaybackCommandsImpl）
3. TimelinePanel 只负责渲染和编辑交互

**优先级：** 🔥🔥🔥 最高（架构核心问题）

---

### C. TimelineControlBar 中的播放控制按钮（L164-L174）

**位置：** `TimelineControlBar.java:164-174`

**当前代码：**
```java
if (ImGui.button(Icons.SKIP_BACK, 0, UITheme.Spacing.BUTTON_HEIGHT)) {
    model.jumpToStart();
}
ImGui.sameLine();
if (ImGui.button(model.isPlaying() ? (Icons.PAUSE + " 暂停") : (Icons.PLAY + " 播放"), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
    model.togglePlay();
}
ImGui.sameLine();
if (ImGui.button(Icons.SKIP_FORWARD, 0, UITheme.Spacing.BUTTON_HEIGHT)) {
    model.jumpToEnd();
}
```

**问题：**
- ❌ 直接调用 `model.jumpToStart()`, `model.togglePlay()`, `model.jumpToEnd()`
- ❌ 与 Menu/Toolbar/Shortcut 的行为不一致（它们都走 Facade）
- ❌ 违反了"所有控制命令必须走 Facade"的原则

**建议改法：**
```java
if (ImGui.button(Icons.SKIP_BACK, 0, UITheme.Spacing.BUTTON_HEIGHT)) {
    commands.playback().goToStart();
}
ImGui.sameLine();
if (ImGui.button(model.isPlaying() ? (Icons.PAUSE + " 暂停") : (Icons.PLAY + " 播放"), 0, UITheme.Spacing.BUTTON_HEIGHT)) {
    commands.playback().togglePlayPause();
}
ImGui.sameLine();
if (ImGui.button(Icons.SKIP_FORWARD, 0, UITheme.Spacing.BUTTON_HEIGHT)) {
    commands.playback().goToEnd();
}
```

**注意：** TimelineControlBar 需要注入 `UICommandFacade`

**优先级：** 🔥🔥 高（必须修复）

---

### D. TimelineControlBar 中的播放速度设置（L344-L361）

**位置：** `TimelineControlBar.java:344-361`

**当前代码：**
```java
if (ImGui.combo("##playSpeedPreset", speedBuf, speedLabels)) {
    float newSpeed = switch (speedBuf.get()) {
        case 0 -> 0.5f;
        case 2 -> 2.0f;
        case 3 -> 3.0f;
        default -> 1.0f;
    };
    model.setPlaybackSpeed(newSpeed);
}
```

**问题：**
- ❌ 直接调用 `model.setPlaybackSpeed()`
- ❌ 应该通过 Facade 统一控制

**建议改法：**
```java
if (ImGui.combo("##playSpeedPreset", speedBuf, speedLabels)) {
    float newSpeed = switch (speedBuf.get()) {
        case 0 -> 0.5f;
        case 2 -> 2.0f;
        case 3 -> 3.0f;
        default -> 1.0f;
    };
    commands.playback().setPlaybackSpeed(newSpeed);
}
```

**优先级：** 🔥🔥 高（必须修复）

---

### E. 标尺点击时暂停播放（L962-L964）

**位置：** `TimelinePanel.java:962-964`

**当前代码：**
```java
// 如果正在播放，先暂停
if (model.isPlaying()) {
    model.setPlaying(false);
}
```

**问题：**
- ❌ 直接修改 `model.setPlaying(false)`
- ❌ 与 Facade 控制播放的行为不一致

**建议改法：**
```java
// 如果正在播放，先暂停
if (model.isPlaying()) {
    commands.playback().pause();
}
```

**注意：** TimelinePanel 需要注入 `UICommandFacade`

**优先级：** 🔥🔥 高（必须修复）

---

### F. 标尺点击跳转（L987-L988）- 建议 Facade

**位置：** `TimelinePanel.java:987-988`

**当前代码：**
```java
float clickTime = renderer.xToTime(rulerClickStartX, contentX);
interactionHandler.clickTimeRuler(rulerClickStartX, contentX);
```

**问题：**
- ⚠️ `clickTimeRuler()` 内部直接调用 `model.setCurrentTime()` 和多个 Controller 的更新方法
- ⚠️ 这是"离散 seek 命令"，建议统一走 Facade

**建议改法：**
```java
float clickTime = renderer.xToTime(rulerClickStartX, contentX);
// 通过 Facade 统一处理 seek（包括时间更新、季节/天气/相机采样一致化）
commands.playback().seek(clickTime);
```

**注意：** 需要在 `PlaybackCommands` 中添加 `seek(float time)` 方法

**优先级：** 🔥 中（强烈建议，但不是必须）

---

### G. TimelineControlBar 中的时间跳转滑块（L320-L326）

**位置：** `TimelineControlBar.java:320-326`

**当前代码：**
```java
if (ImGui.sliderFloat("##timePosition", tempCurrentTime, 0f, duration, timeFormat)) {
    float newTime = Math.max(0f, Math.min(duration, tempCurrentTime[0]));
    model.setCurrentTime(newTime);
    ensurePlayheadVisible();
}
```

**问题：**
- ⚠️ 直接调用 `model.setCurrentTime()`
- ⚠️ 这是"离散 seek 命令"，建议走 Facade

**建议改法：**
```java
if (ImGui.sliderFloat("##timePosition", tempCurrentTime, 0f, duration, timeFormat)) {
    float newTime = Math.max(0f, Math.min(duration, tempCurrentTime[0]));
    commands.playback().seek(newTime);
    ensurePlayheadVisible();
}
```

**优先级：** 🔥 中（强烈建议）

---

## 🚫 2. 禁止 Facade 化的代码段（必须留在 Model/Editor 层）

### H. 标尺拖动（Scrubbing）（L975-L976）

**位置：** `TimelinePanel.java:975-976`

**当前代码：**
```java
if (!model.isScrubbing()) {
    interactionHandler.startPlayheadDrag(rulerClickStartX, contentX);
}
```

**判定：** ✅ **禁止 Facade**

**原因：**
- 拖动是高频连续输入（每帧更新）
- 应该直接驱动 editor 状态与采样机制
- Facade 只负责离散命令（Play/Pause/Stop/Seek一次性）
- 如果走 Facade，会导致性能问题和接口膨胀

**保持现状：** ✅ 正确

---

### I. 轨道区域渲染、HitTest、拖拽、框选（L630-L884）

**位置：** `TimelinePanel.java:630-884` (renderTimelineCanvas 内)

**关键代码：**
```java
navigationHandler.handleMouseWheel(...);  // L751
viewportState.setScrollY(scrollY);  // L792
interactionHandler.handleCanvasInteractions(...);  // L853
interactionHandler.renderMarqueeOverlay(...);  // L857
```

**判定：** ✅ **禁止 Facade**

**原因：**
- 这是编辑器交互（编辑 ViewState/Selection/Drag）
- 应保持在 TimelinePanel+Handlers 内
- 属于"连续编辑操作"，不是"离散命令"

**保持现状：** ✅ 正确

---

### J. 双击关键帧增删 / 命令历史 / 数据脏标记（L1097-L1193）

**位置：** `TimelinePanel.java:1097-1193` (handleKeyframeDoubleClick)

**关键代码：**
```java
commandHistory.executeCommand(...);
markDataChanged();
// 针对 Audio/Entity/通用 keyframe 的编辑
```

**判定：** ✅ **禁止 Facade**

**原因：**
- 这属于"编辑动作"（Undo/Redo/CommandHistory）体系
- 是 Timeline Editor 内部职责，不是全局控制命令
- 如果 Facade 化，会导致接口膨胀和维护困难

**保持现状：** ✅ 正确

---

### K. TimelineControlBar 中的撤销/重做按钮（L139-L150）

**位置：** `TimelineControlBar.java:139-150`

**当前代码：**
```java
if (ImGui.button(Icons.UNDO + " 撤销", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
    commandHistory.undo();
}
// ...
if (ImGui.button(Icons.REDO + " 重做", 0, UITheme.Spacing.BUTTON_HEIGHT)) {
    commandHistory.redo();
}
```

**判定：** ⚠️ **边界情况**

**分析：**
- 撤销/重做是"编辑动作"，属于 Timeline Editor 内部职责
- 但为了与 Shortcut 系统统一（Ctrl+Z/Ctrl+Y 已走 Facade），可以考虑也走 Facade
- 或者保持现状，因为这是 Timeline 特定的编辑历史

**建议：**
- 如果 Timeline 的撤销/重做是全局的（影响所有编辑），应该走 Facade
- 如果只是 Timeline 内部的编辑历史，可以保持现状

**优先级：** 🔶 低（可选优化）

---

## ✅ 3. 可以保持现状的代码段

### L. 快捷键上下文启用/禁用（L390-L395）

**位置：** `TimelinePanel.java:390-395`

**当前代码：**
```java
boolean focused = ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows);
if (focused != globalTimelineShortcutsActive) {
    globalTimelineShortcutsActive = focused;
    GlobalShortcutManager.getInstance().setContextEnabled(
            GlobalShortcutManager.ShortcutContext.TIMELINE_EDITOR,
            globalTimelineShortcutsActive
    );
}
```

**判定：** ✅ **可以保持现状**

**原因：**
- 这属于 UI 焦点上下文管理，不是播放/模型业务
- 留在 TimelinePanel 是合理的
- 未来可以统一到更高层的 InputContext 管理，但不是必须

**保持现状：** ✅ 正确

---

### M. TimelineControlBar 中的总时长设置（L209-L213）

**位置：** `TimelineControlBar.java:209-213`

**当前代码：**
```java
if (ImGui.dragFloat("##duration", tempDuration, DURATION_DRAG_STEP, MIN_DURATION, MAX_DURATION, "%.1fs")) {
    float newDuration = Math.max(MIN_DURATION, Math.min(MAX_DURATION, tempDuration[0]));
    model.setDuration(newDuration);
}
```

**判定：** ⚠️ **边界情况**

**分析：**
- 总时长设置是"项目设置"，不是"播放控制"
- 可以考虑走 Facade（`commands.timeline().setDuration()`），但不是必须
- 如果 TimelineModel 的 setDuration 是安全的（只影响时间线范围），可以保持现状

**建议：** 保持现状，或可选地走 Facade

**优先级：** 🔶 低（可选优化）

---

## 📊 优先级总结

### 🔥🔥🔥 最高优先级（架构核心问题）

1. **移除 L398-L434 的播放子系统 tick** → 搬到全局播放 orchestrator
2. **移除/迁移 updatePlayback() (L492-L540)** → 归到 FacadeImpl/PlaybackController

### 🔥🔥 高优先级（必须修复）

3. **TimelineControlBar 播放控制按钮 (L164-L174)** → 改为 Facade
4. **TimelineControlBar 播放速度设置 (L344-L361)** → 改为 Facade
5. **标尺点击时暂停 (L962-L964)** → 改为 Facade

### 🔥 中优先级（强烈建议）

6. **标尺点击跳转 (L987-L988)** → 建议改为 Facade
7. **TimelineControlBar 时间跳转滑块 (L320-L326)** → 建议改为 Facade

### 🔶 低优先级（可选优化）

8. **TimelineControlBar 撤销/重做按钮** → 可选 Facade（如果希望与 Shortcut 统一）
9. **TimelineControlBar 总时长设置** → 可选 Facade

---

## 🎯 实施建议

### 阶段 1：核心架构修复（必须）

1. 创建 `PlaybackOrchestrator` 或扩展 `PlaybackCommandsImpl`，负责所有播放 tick
2. 将 L398-L434 的播放子系统 tick 迁移到全局位置
3. 将 `updatePlayback()` 逻辑迁移到播放系统

### 阶段 2：控制按钮 Facade 化（必须）

4. 在 TimelineControlBar 中注入 `UICommandFacade`
5. 修改播放控制按钮使用 Facade
6. 修改播放速度设置使用 Facade
7. 修改标尺点击暂停使用 Facade

### 阶段 3：Seek 命令统一（建议）

8. 在 `PlaybackCommands` 中添加 `seek(float time)` 方法
9. 修改标尺点击跳转和时间跳转滑块使用 Facade

---

## ✅ 最终评价

**该建议的价值：⭐⭐⭐⭐⭐（5/5）**

- ✅ 解决了 TimelinePanel 的"控制层污染"问题
- ✅ 明确了"控制 vs 编辑"的边界
- ✅ 提供了清晰的实施路径
- ✅ 避免了架构退化

**实施难度：⭐⭐⭐⭐（4/5）**

- 需要重构播放系统架构
- 需要仔细处理播放 tick 的迁移
- 需要确保所有 Controller 的更新逻辑正确

**建议：立即开始实施，优先完成阶段 1 和阶段 2**
