# Actor 和演员轨道 UI 完全隐藏总结

## 概述

已完全隐藏所有与演员轨道和演员相关的 UI 面板和选项，等待下一版本再进行开发。

## 已隐藏的 UI 组件

### 1. PropertiesPanel（属性面板）

**隐藏内容**：
- ✅ Actor 面板（`SelectionType.ACTOR`）→ 回退到 Project 面板
- ✅ ActorTrack 面板（`SelectionType.TRACK` 且类型为 `TrackType.ACTOR`）→ 回退到 Project 面板
- ✅ EntityClip 面板（`SelectionType.CLIP` 且为 `EntityClip` 类型）→ 回退到 Project 面板

**修改位置**：
- `PropertiesPanel.doRender()` - switch 语句中添加过滤逻辑

### 2. ToolRegistry（工具注册表）

**隐藏内容**：
- ✅ ActorSelectTool（演员选择工具）
- ✅ ActorPathTool（演员路径工具）

**修改位置**：
- `ToolRegistry.registerBuiltInExtraTools()` - 注释掉 Actor 工具注册
- `ToolRegistry.java` - 注释掉相关导入

### 3. TimelineRenderer（时间线渲染器）

**隐藏内容**：
- ✅ ActorTrack 轨道在时间线上的显示

**修改位置**：
- `TimelineRenderer.renderTrackArea()` - 在渲染循环中跳过 ENTITY 类型轨道

### 4. TimelineTrackController（新建轨道菜单）

**隐藏内容**：
- ✅ "演员轨道"选项在新建轨道菜单中不显示

**修改位置**：
- `TimelineTrackController.renderAddTrackMenu()` - 过滤掉 `TrackType.ACTOR`

**代码示例**：
```java
for (TrackType type : TrackType.values()) {
    // ⚠️ ActorTrack 已隐藏，等待下一版本
    if (type == TrackType.ACTOR) {
        continue;
    }
    // ... 显示菜单项 ...
}
```

### 5. ActorsPanel（演员面板）

**隐藏内容**：
- ✅ ActorsPanel 不再创建和显示
- ✅ 在 Toggle Panels 菜单中不显示

**修改位置**：
- `UIConfigurationBuilder.addDefaultPanels()` - 从默认面板列表中移除
- `PanelFactory.registerDefaultCreators()` - 注释掉创建器注册
- `PanelFactory.DEFAULT_PANEL_TYPES` - 注释掉类型映射
- `PanelConfigurationLoader.createDefaultConfiguration()` - 从配置中移除
- `UIConfigurationBuilder.resolvePanelType()` - 注释掉类型解析
- `MenuBarPanel` Toggle Panels 菜单 - 过滤掉 "Actors" 面板

**代码示例**：
```java
// UIConfigurationBuilder.addDefaultPanels()
List<Class<? extends IPanel>> defaultTypes = Arrays.asList(
    MenuBarPanel.class,
    ToolbarPanel.class,
    ContainerPanel.class,
    // ⚠️ ActorsPanel 已隐藏，等待下一版本
    // ActorsPanel.class,
    PropertiesPanel.class,
    // ...
);

// MenuBarPanel Toggle Panels
for (IPanel panel : panelRegistry.getAllPanels()) {
    if (panel != this) {
        // ⚠️ ActorsPanel 已隐藏，等待下一版本
        if ("Actors".equals(panel.getPanelName())) {
            continue;
        }
        // ... 显示菜单项 ...
    }
}
```

## 修改的文件列表

1. ✅ `PropertiesPanel.java` - 隐藏 Actor/ActorTrack/EntityClip 面板
2. ✅ `ToolRegistry.java` - 隐藏 Actor 工具
3. ✅ `TimelineRenderer.java` - 隐藏 ActorTrack 显示
4. ✅ `TimelineTrackController.java` - 隐藏"演员轨道"选项
5. ✅ `UIConfigurationBuilder.java` - 移除 ActorsPanel 注册
6. ✅ `PanelFactory.java` - 移除 ActorsPanel 创建器和类型映射
7. ✅ `PanelConfigurationLoader.java` - 移除 ActorsPanel 配置
8. ✅ `MenuBarPanel.java` - 在 Toggle Panels 中过滤 ActorsPanel

## 保留的功能

以下功能**未被隐藏**，仍然可以正常工作：
- ✅ 其他类型的轨道（Container, Camera, Weather, Time, Audio）
- ✅ 其他类型的 Clip（ContainerClip, AudioClip 等）
- ✅ 其他类型的 Keyframe（CameraKeyframe, WeatherKeyframe 等）
- ✅ 其他工具（Selection 工具、World 工具等）
- ✅ 其他面板（PropertiesPanel, TimelinePanel, ContainerPanel 等）

## 恢复方法

当需要恢复 Actor 功能时，只需：

1. **PropertiesPanel**：取消注释相关代码，移除过滤逻辑
2. **ToolRegistry**：取消注释 Actor 工具注册，恢复导入
3. **TimelineRenderer**：移除 ENTITY 轨道的跳过逻辑
4. **TimelineTrackController**：移除 ENTITY 类型的过滤
5. **ActorsPanel 相关**：取消注释所有 ActorsPanel 的注册和创建代码
6. **MenuBarPanel**：移除 Toggle Panels 中的过滤逻辑

## 注意事项

- ⚠️ 这些修改只是**隐藏 UI**，**不删除代码**
- ⚠️ 数据模型（ActorTrack, EntityClip 等）仍然存在，只是不在 UI 中显示
- ⚠️ 如果项目中已有 ActorTrack 数据，它们仍然会被保存，只是不可见
- ⚠️ 相关功能代码（ActorGizmoController, ActorPlacementController 等）仍然存在，只是无法通过 UI 访问

## 完成时间

所有 UI 隐藏已完成 ✅
