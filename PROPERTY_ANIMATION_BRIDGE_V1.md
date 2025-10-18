# 🧩 ChronoBlocks 属性动画桥接系统 v1.0

**时间线与属性动画的连接器**

**完成日期：** 2025-10-18  
**版本：** v1.0  
**状态：** ✅ 核心功能已实现

---

## 📦 系统概览

PropertyAnimationTimelineBridge 是 ChronoBlocks 动画系统的核心桥接层，负责：

- ✅ 将时间线轨道（TimelineTrack）映射到动画通道（AnimationChannel）
- ✅ 将时间线关键帧（Keyframe）转换为插值关键帧
- ✅ 同步时间线编辑器和动画控制器的状态
- ✅ 实现编辑器预览模式和播放模式的双向同步

---

## 🏗️ 架构设计

### 三层架构

```
┌─────────────────────────────────────────────────┐
│         时间线层 (Timeline Layer)                │
│  - TimelineTrack (轨道)                         │
│  - TimelineClip (片段)                          │
│  - Keyframe[] (关键帧)                          │
└────────────────┬────────────────────────────────┘
                 │ 桥接映射
┌────────────────▼────────────────────────────────┐
│      PropertyAnimationTimelineBridge (桥接层)   │
│  - 轨道 → 通道映射                               │
│  - 关键帧转换                                    │
│  - 双向同步                                      │
│  - 目标对象绑定                                  │
└────────────────┬────────────────────────────────┘
                 │ 驱动
┌────────────────▼────────────────────────────────┐
│      PropertyAnimationController (控制层)       │
│  - 时间管理                                      │
│  - 插值计算                                      │
│  - 属性更新                                      │
└─────────────────────────────────────────────────┘
```

### 数据流向

```
用户编辑时间线
      ↓
TimelineTrack 修改
      ↓
onTrackModified() 回调
      ↓
重建 AnimationChannel
      ↓
更新关键帧数据
      ↓
插值计算 + 应用属性
      ↓
目标对象属性更新
      ↓
场景渲染更新
```

---

## 🎯 核心功能

### 1️⃣ 序列绑定

```java
PropertyAnimationController controller = new PropertyAnimationController();
PropertyAnimationTimelineBridge bridge = new PropertyAnimationTimelineBridge(controller);

// 绑定整个序列
List<TimelineTrack> tracks = timeline.getTracks();
bridge.bindSequence("主动画序列", tracks, 10.0f, true);
```

### 2️⃣ 轨道绑定

```java
// 绑定单个轨道
TimelineTrack track = timeline.getTrack("相机FOV");
bridge.bindTrack(track);

// 绑定轨道到目标对象的属性
Camera camera = scene.getCamera();
bridge.bindTrackToProperty(track, camera, "fov");
```

### 3️⃣ 实时同步

```java
// 编辑器预览模式 - 拖动时间线光标
float currentTime = timelinePanel.getCurrentTime();
bridge.updateFromTimeline(currentTime);

// 播放模式 - 自动驱动
controller.play();
while (running) {
    controller.tick(); // 控制器自动更新
    render();
}
```

### 4️⃣ 事件响应

```java
// 当用户修改轨道时
track.addKeyframe(2.5f);
bridge.onTrackModified(track); // 自动重建通道

// 当用户修改 Clip 时
clip.setStartTime(1.0f);
bridge.onClipModified(track); // 更新通道
```

---

## 📚 完整使用示例

### 示例 1：相机FOV动画

```java
// ========== 创建组件 ==========
PropertyAnimationController controller = new PropertyAnimationController();
PropertyAnimationTimelineBridge bridge = new PropertyAnimationTimelineBridge(controller);

// ========== 创建时间线轨道 ==========
TimelineTrack fovTrack = new TimelineTrack("相机FOV");

// 创建 Clip 并添加关键帧
TimelineClip clip1 = new TimelineClip("FOV变化", 0.0f, 4.0f, 0xFF4488FF);
clip1.addKeyframe(0.0f);  // 起始关键帧
clip1.addKeyframe(2.0f);  // 中间关键帧
clip1.addKeyframe(4.0f);  // 结束关键帧
fovTrack.addClip(clip1);

// ========== 绑定轨道 ==========
bridge.bindTrack(fovTrack);

// 绑定到相机的 FOV 属性
Camera camera = scene.getCamera();
bridge.bindTrackToProperty(fovTrack, camera, "fov");

// ========== 配置和播放 ==========
controller.setDuration(4.0f);
controller.setLoop(true);
controller.play();

// ========== 主循环 ==========
while (running) {
    controller.tick();
    render();
}
```

### 示例 2：容器位置动画

```java
// ========== 创建X轴位置轨道 ==========
TimelineTrack posXTrack = new TimelineTrack("容器X位置");
TimelineClip posXClip = new TimelineClip("X移动", 0.0f, 5.0f, 0xFF88FF44);
posXClip.addKeyframe(0.0f);
posXClip.addKeyframe(2.5f);
posXClip.addKeyframe(5.0f);
posXTrack.addClip(posXClip);

// 绑定到容器的 X 坐标
Container container = scene.getContainer("主容器");
bridge.bindTrack(posXTrack);
bridge.bindTrackToProperty(posXTrack, container, "positionX");

// ========== 创建Y轴位置轨道 ==========
TimelineTrack posYTrack = new TimelineTrack("容器Y位置");
TimelineClip posYClip = new TimelineClip("Y移动", 0.0f, 5.0f, 0xFFFF8844);
posYClip.addKeyframe(0.0f);
posYClip.addKeyframe(2.5f);
posYClip.addKeyframe(5.0f);
posYTrack.addClip(posYClip);

bridge.bindTrack(posYTrack);
bridge.bindTrackToProperty(posYTrack, container, "positionY");

// ========== 启动动画 ==========
bridge.play();
```

### 示例 3：编辑器预览模式

```java
// ========== 在时间线面板中 ==========
class TimelinePanel {
    private PropertyAnimationTimelineBridge bridge;
    private float currentTime = 0.0f;
    
    public void render() {
        // 渲染时间线UI
        renderTimelineRuler();
        renderTracks();
        
        // 用户拖动播放头
        if (ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
            float mouseX = ImGui.getMousePosX();
            currentTime = screenXToTime(mouseX);
            
            // 实时预览该时间点的状态
            bridge.updateFromTimeline(currentTime);
        }
        
        // 播放按钮
        if (ImGui.button("播放")) {
            bridge.play();
        }
        
        ImGui.sameLine();
        if (ImGui.button("暂停")) {
            bridge.pause();
        }
        
        ImGui.sameLine();
        if (ImGui.button("停止")) {
            bridge.stop();
        }
        
        // 速度控制
        float[] speed = {bridge.getController().getSpeed()};
        if (ImGui.dragFloat("速度", speed, 0.1f, 0.1f, 5.0f)) {
            bridge.setSpeed(speed[0]);
        }
    }
    
    public void tick() {
        // 如果正在播放，更新控制器
        if (bridge.isPlaying()) {
            bridge.getController().tick();
            currentTime = bridge.getCurrentTime();
        }
    }
}
```

---

## 🔧 API 参考

### PropertyAnimationTimelineBridge

#### 序列绑定

| 方法 | 参数 | 功能 |
|------|------|------|
| `bindSequence()` | sequenceName, tracks, duration, loop | 绑定整个时间线序列 |
| `bindTrack()` | track | 绑定单个轨道 |
| `bindTrackToProperty()` | track, target, propertyName | 绑定轨道到对象属性 |

#### 实时更新

| 方法 | 参数 | 功能 |
|------|------|------|
| `updateFromTimeline()` | timelineTime | 同步时间线时间（编辑器预览） |
| `onTrackModified()` | track | 轨道修改事件 |
| `onClipModified()` | track | Clip修改事件 |

#### 播放控制

| 方法 | 参数 | 功能 |
|------|------|------|
| `play()` | - | 开始播放 |
| `pause()` | - | 暂停播放 |
| `stop()` | - | 停止播放 |
| `setSpeed()` | speed | 设置播放速度 |
| `isPlaying()` | - | 查询播放状态 |

#### 状态查询

| 方法 | 返回值 | 功能 |
|------|--------|------|
| `getCurrentSequenceName()` | String | 当前序列名称 |
| `getSequenceDuration()` | float | 序列时长 |
| `isSequenceLoop()` | boolean | 是否循环 |
| `getController()` | PropertyAnimationController | 获取控制器 |
| `getTrackMap()` | Map<String, TimelineTrack> | 轨道映射表 |
| `getChannelCount()` | int | 通道数量 |
| `getCurrentTime()` | float | 当前播放时间 |
| `getDebugInfo()` | String | 调试信息 |

#### 清理

| 方法 | 参数 | 功能 |
|------|------|------|
| `clear()` | - | 清空所有绑定 |

---

## 🎨 集成场景

### 场景 1：时间线编辑器

```java
public class TimelineEditor {
    private PropertyAnimationTimelineBridge bridge;
    
    public void init() {
        PropertyAnimationController controller = new PropertyAnimationController();
        bridge = new PropertyAnimationTimelineBridge(controller);
        
        // 加载时间线数据
        TimelineData data = loadTimelineData();
        bridge.bindSequence(
            data.getName(),
            data.getTracks(),
            data.getDuration(),
            data.isLoop()
        );
    }
    
    public void onTrackEdited(TimelineTrack track) {
        // 用户编辑了轨道（添加/删除关键帧）
        bridge.onTrackModified(track);
        
        // 更新预览
        float currentTime = timelinePanel.getCurrentTime();
        bridge.updateFromTimeline(currentTime);
    }
}
```

### 场景 2：场景预览

```java
public class ScenePreview {
    private PropertyAnimationTimelineBridge bridge;
    
    public void setupAnimation() {
        // 创建桥接器
        PropertyAnimationController controller = new PropertyAnimationController();
        bridge = new PropertyAnimationTimelineBridge(controller);
        
        // 绑定场景对象
        Camera camera = scene.getCamera();
        TimelineTrack cameraTrack = timeline.getTrack("相机");
        bridge.bindTrack(cameraTrack);
        bridge.bindTrackToProperty(cameraTrack, camera, "fov");
        
        // 开始播放
        bridge.play();
    }
    
    public void update(float deltaTime) {
        // 自动更新动画
        bridge.getController().tick();
        
        // 渲染场景
        render();
    }
}
```

### 场景 3：实时编辑 + 预览

```java
public class LiveAnimationEditor {
    private PropertyAnimationTimelineBridge bridge;
    private float previewTime = 0.0f;
    private boolean isPlaying = false;
    
    public void render() {
        // 时间线UI
        ImGui.begin("时间线编辑器");
        
        // 播放控制
        if (ImGui.button(isPlaying ? "⏸ 暂停" : "▶ 播放")) {
            if (isPlaying) {
                bridge.pause();
            } else {
                bridge.play();
            }
            isPlaying = !isPlaying;
        }
        
        // 时间滑块
        float[] time = {previewTime};
        if (ImGui.sliderFloat("时间", time, 0.0f, bridge.getSequenceDuration())) {
            previewTime = time[0];
            if (!isPlaying) {
                bridge.updateFromTimeline(previewTime);
            }
        }
        
        // 轨道编辑
        for (String trackId : bridge.getTrackMap().keySet()) {
            TimelineTrack track = bridge.getTrackMap().get(trackId);
            if (ImGui.button("添加关键帧##" + trackId)) {
                // 添加关键帧到当前时间
                for (TimelineClip clip : track.getClips()) {
                    clip.addKeyframe(previewTime);
                }
                bridge.onTrackModified(track);
            }
        }
        
        ImGui.end();
    }
    
    public void update() {
        if (isPlaying) {
            bridge.getController().tick();
            previewTime = bridge.getCurrentTime();
        }
    }
}
```

---

## 📊 性能考量

### 性能指标

| 指标 | 建议值 | 说明 |
|------|--------|------|
| **轨道数量** | < 50 | 每个序列 |
| **关键帧数量** | < 500 | 每个轨道 |
| **更新频率** | 60 FPS | 编辑器预览 |
| **绑定延迟** | < 10ms | 序列绑定时间 |
| **重建延迟** | < 5ms | 单轨道重建 |

### 优化建议

1. **批量更新**
   ```java
   // 避免频繁调用 onTrackModified
   // 使用批量更新模式
   bridge.pause();
   for (TimelineTrack track : modifiedTracks) {
       bridge.onTrackModified(track);
   }
   bridge.updateFromTimeline(currentTime);
   ```

2. **延迟重建**
   ```java
   // 在编辑过程中延迟重建通道
   // 只在松开鼠标时重建
   if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
       bridge.onTrackModified(currentTrack);
   }
   ```

3. **缓存映射**
   ```java
   // 缓存轨道到通道的映射
   // 避免重复查找
   String trackId = bridge.generateTrackId(track);
   cachedChannelMap.put(trackId, track);
   ```

---

## 🐛 调试支持

### 调试信息

```java
// 打印桥接器状态
System.out.println(bridge.getDebugInfo());
// 输出: PropertyAnimationTimelineBridge [序列: 主动画序列, 轨道: 5, 通道: 5, 播放: 是, 时间: 2.50/10.00]

// 遍历所有轨道
for (Map.Entry<String, TimelineTrack> entry : bridge.getTrackMap().entrySet()) {
    System.out.println("轨道: " + entry.getValue().getName());
    System.out.println("  Clips: " + entry.getValue().getClips().size());
}

// 检查控制器状态
PropertyAnimationController controller = bridge.getController();
System.out.println("通道数: " + controller.getChannelCount());
System.out.println("播放中: " + controller.isPlaying());
System.out.println("速度: " + controller.getSpeed() + "x");
```

### 日志输出

```
[Bridge] PropertyAnimationTimelineBridge 已创建
[Bridge] 绑定时间线序列: 主动画序列 (时长: 10.0s, 循环: true)
[Bridge] 绑定轨道: 相机FOV (ID: track_相机FOV_12345678)
[Bridge] 轨道 相机FOV 绑定到 Camera.fov
[Bridge] 注册轨道通道: 相机FOV -> track_相机FOV_12345678 (关键帧数: 3)
[Bridge] 开始播放动画
[Bridge] 属性同步: Camera.fov = 75.5
```

---

## ✅ 完成清单

### 核心功能（✅ 完成）

- ✅ PropertyAnimationTimelineBridge（~420行）
- ✅ 序列绑定机制
- ✅ 轨道到通道映射
- ✅ 关键帧转换
- ✅ 双向同步支持
- ✅ 实时预览模式
- ✅ 播放控制集成
- ✅ 事件响应系统
- ✅ 目标对象绑定
- ✅ 调试支持

### 文档（✅ 完成）

- ✅ 架构设计文档
- ✅ API 参考
- ✅ 使用示例
- ✅ 集成指南
- ✅ 性能考量
- ✅ 调试支持

---

## 🚀 未来扩展

### 短期计划

- [ ] 多属性通道支持（Vector3f, Color等）
- [ ] 关键帧值编辑器
- [ ] 缓动函数可视化编辑
- [ ] 轨道混合模式

### 长期计划

- [ ] 关键帧曲线编辑器
- [ ] 多轨道同步控制
- [ ] 动画预设库
- [ ] 动画导出/导入

---

## 📝 总结

✅ **PropertyAnimationTimelineBridge 已完全实现：**

**核心功能：**
- 时间线 → 动画控制器桥接
- 轨道 → 通道自动映射
- 关键帧数据转换
- 双向同步支持
- 实时预览 + 播放模式

**代码统计：**
- ~420行核心代码
- 完整的API文档
- 3个完整使用示例
- 详细的调试支持

**集成完整度：**
- ✅ 与 PropertyAnimationController 完全集成
- ✅ 与 PropertyAnimationInterpolator 完全集成
- ✅ 与 TimelineTrack/TimelineClip 完全集成
- ✅ 支持编辑器预览模式
- ✅ 支持播放控制

**🎉 ChronoBlocks 属性动画桥接系统 v1.0 - 完全交付！**

