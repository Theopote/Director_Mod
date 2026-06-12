# Actor 和演员轨道 UI 隐藏总结

## 概述

由于演员功能过于复杂，需要花费大量时间开发，因此暂时隐藏所有与演员轨道和演员相关的 UI 面板，等待下一版本再进行开发。

## 已隐藏的 UI 组件

### 1. PropertiesPanel（属性面板）

**隐藏内容**：
- ✅ Actor 面板（`SelectionType.ACTOR`）→ 回退到 Project 面板
- ✅ ActorTrack 面板（`SelectionType.TRACK` 且类型为 `TrackType.ACTOR`）→ 回退到 Project 面板
- ✅ EntityClip 面板（`SelectionType.CLIP` 且为 `EntityClip` 类型）→ 回退到 Project 面板

**修改位置**：
- `PropertiesPanel.doRender()` - switch 语句中添加过滤逻辑

**代码示例**：
```java
case ACTOR -> // Actor 面板（ActorProjectPropertyAdapter）- ⚠️ 已隐藏，等待下一版本
        // renderActorPanel(selectionManager);
        renderProjectPanel(); // 回退到 Project 面板

case TRACK -> {
    // Track 面板（TimelineTrackPropertyAdapter）- ⚠️ 如果是 ActorTrack 则隐藏
    var track = selectionManager.getSelectedTrack();
    if (track != null && track.getType() == TrackType.ACTOR) {
        // ActorTrack 已隐藏，回退到 Project 面板
        renderProjectPanel();
    } else {
        renderTrackPanel(selectionManager);
    }
}

case CLIP -> {
    // Clip 面板（Entity*ClipPropertyAdapter）- ⚠️ 如果是 EntityClip 则隐藏
    Object clip = selectionManager.getSelectionPayload();
    if (clip instanceof EntityClip) {
        // EntityClip 已隐藏，回退到 Project 面板
        renderProjectPanel();
    } else {
        renderClipPanel(selectionManager);
    }
}
```

### 2. ToolRegistry（工具注册表）

**隐藏内容**：
- ✅ ActorSelectTool（演员选择工具）
- ✅ ActorPathTool（演员路径工具）

**修改位置**：
- `ToolRegistry.registerBuiltInExtraTools()` - 注释掉 Actor 工具注册
- 移除相关导入

**代码示例**：
```java
private void registerBuiltInExtraTools() {
    // ⚠️ Actor 工具已隐藏，等待下一版本
    // addExtraToolIfMissing(new ActorSelectTool());
    // addExtraToolIfMissing(new ActorPathTool());
}
```

### 3. TimelineRenderer（时间线渲染器）

**隐藏内容**：
- ✅ ActorTrack 轨道在时间线上的显示

**修改位置**：
- `TimelineRenderer.renderTrackArea()` - 在渲染循环中跳过 ENTITY 类型轨道

**代码示例**：
```java
for (int idx = startIndex; idx <= endIndex; idx++) {
    TimelineTrack track = model.getTrackAt(idx);
    if (track == null) {
        continue;
    }
    // ⚠️ ActorTrack 已隐藏，等待下一版本
    if (track.getType() == TrackType.ACTOR) {
        continue;
    }
    // ... 渲染逻辑 ...
}
```

## 保留的功能

以下功能**未被隐藏**，仍然可以正常工作：
- ✅ 其他类型的轨道（Container, Camera, Weather, Time, Audio）
- ✅ 其他类型的 Clip（ContainerClip, AudioClip 等）
- ✅ 其他类型的 Keyframe（CameraKeyframe, WeatherKeyframe 等）
- ✅ 其他工具（Selection 工具、World 工具等）

## 恢复方法

当需要恢复 Actor 功能时，只需：

1. **PropertiesPanel**：取消注释 `renderActorPanel()` 调用，移除 ActorTrack/EntityClip 的过滤逻辑
2. **ToolRegistry**：取消注释 Actor 工具注册，恢复导入
3. **TimelineRenderer**：移除 ENTITY 轨道的跳过逻辑

## 注意事项

- ⚠️ 这些修改只是**隐藏 UI**，**不删除代码**
- ⚠️ 数据模型（ActorTrack, EntityClip 等）仍然存在，只是不在 UI 中显示
- ⚠️ 如果项目中已有 ActorTrack 数据，它们仍然会被保存，只是不可见
- ⚠️ 相关功能代码（ActorGizmoController, ActorPlacementController 等）仍然存在，只是无法通过 UI 访问

## 完成时间

所有 UI 隐藏已完成 ✅
