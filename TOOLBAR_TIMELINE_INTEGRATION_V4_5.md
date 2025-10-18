# ChronoBlocks Toolbar + Timeline 一体化交互系统 v4.5

> 🎯 **核心概念**: Toolbar 负责操作命令，Timeline 负责视觉反馈，PlaybackController 驱动状态变化

## 📦 系统架构概览

```
┌─────────────────────────────────────────────────────────────┐
│           ChronoBlocks 一体化动画系统架构                      │
└─────────────────────────────────────────────────────────────┘

用户交互层 (UI Layer)
├── ToolbarPanel          ──┐
│   ├── 工具选择            │
│   ├── 播放控制            │ → PlaybackController
│   └── 状态显示            │
├── TimelinePanel         ──┘
│   ├── 时间线视图          │
│   ├── 拖动时间光标        │ → PlaybackController.seek()
│   └── 显示播放进度        │
└── PropertiesPanel
    └── 动画属性编辑

控制层 (Controller Layer)
├── PlaybackController    (核心播放引擎)
│   ├── play() / pause() / stop()
│   ├── seek(time)
│   ├── toggleLoop()
│   └── update(deltaTime)  ← ChronoBlocksUIManager 每帧调用
│
└── ChronoBlocksUIManager (系统协调器)
    └── updateAnimationSystem() → 驱动 PlaybackController

数据层 (Data Layer)
└── AnimationState        (全局状态中心)
    ├── playing: boolean
    ├── currentTime: float
    ├── duration: float
    └── clip: AnimationClip
```

## 🔄 数据流详解

### 1. 播放控制流程

```
用户点击 Toolbar "Play" 按钮
    ↓
ToolbarPanel.renderPlaybackControls()
    ↓
PlaybackController.play()
    ↓
AnimationState.setPlaying(true)
    ↓
ChronoBlocksUIManager.updateAnimationSystem()  (每帧)
    ↓
PlaybackController.update(deltaTime)
    ↓
AnimationState.setCurrentTime(newTime)
    ↓
TimelinePanel.doRender()
    ↓
显示更新的时间光标位置
```

### 2. 时间跳转流程

```
用户拖动 Timeline 时间光标
    ↓
TimelinePanel 处理鼠标拖动
    ↓
TimelineModel.setCurrentTime(time)
    ↓
AnimationState.setCurrentTime(time)
    ↓
Toolbar 状态显示立即更新
```

### 3. 循环播放流程

```
动画播放到结束
    ↓
PlaybackController.update() 检测到 time >= duration
    ↓
if (loop) {
    AnimationState.setCurrentTime(0)  // 回到起点
} else {
    AnimationState.setPlaying(false)   // 停止播放
}
```

## 🧩 模块职责划分

| 模块 | 职责 | 输入 | 输出 |
|------|------|------|------|
| **ToolbarPanel** | 控制动画播放、暂停、停止、Loop、当前工具选择 | 用户点击事件 | 调用 PlaybackController |
| **TimelinePanel** | 显示时间线进度、拖动容器、播放光标 | AnimationState 的 currentTime | 发送 setCurrentTime() |
| **PlaybackController** | 播放控制核心循环（Play/Pause/Stop/Loop） | Toolbar 指令、deltaTime | 更新 AnimationState |
| **AnimationState** | 存储当前时间、播放速度、持续时间等状态 | Controller 驱动 | TimelinePanel 读取 |
| **ChronoBlocksUIManager** | 框架协调（帧驱动更新） | 渲染循环 | 调用 PlaybackController.update() |

## 🔧 核心接口定义

### 1. AnimationState（全局动画状态）

```java
public class AnimationState {
    private static final AnimationState INSTANCE = new AnimationState();
    
    // ========== 播放状态 ==========
    private boolean playing = false;
    private float currentTime = 0f;
    private float duration = 10.0f;
    
    // ========== 核心方法 ==========
    public static AnimationState get() { return INSTANCE; }
    
    public boolean isPlaying() { return playing; }
    public void setPlaying(boolean playing) { this.playing = playing; }
    
    public float getCurrentTime() { return currentTime; }
    public void setCurrentTime(float time) { 
        this.currentTime = Math.max(0f, Math.min(time, duration)); 
    }
    
    public float getDuration() { return duration; }
    public void setDuration(float duration) { this.duration = duration; }
}
```

### 2. PlaybackController（播放控制器）

```java
public class PlaybackController {
    private static final PlaybackController INSTANCE = new PlaybackController();
    
    private boolean loop = false;
    private float speed = 1.0f;
    
    public static PlaybackController get() { return INSTANCE; }
    
    // ========== 播放控制 ==========
    public void play() { 
        AnimationState.get().setPlaying(true); 
    }
    
    public void pause() { 
        AnimationState.get().setPlaying(false); 
    }
    
    public void stop() {
        AnimationState state = AnimationState.get();
        state.setPlaying(false);
        state.setCurrentTime(0f);
    }
    
    public void seek(float time) {
        AnimationState.get().setCurrentTime(time);
    }
    
    public void toggleLoop() { loop = !loop; }
    
    // ========== 帧更新驱动 ==========
    public void update(float deltaSeconds) {
        AnimationState state = AnimationState.get();
        if (!state.isPlaying()) return;
        
        float t = state.getCurrentTime() + deltaSeconds * speed;
        float duration = state.getDuration();
        
        if (t > duration) {
            if (loop) {
                t = 0f;  // 循环回起点
            } else {
                t = duration;
                state.setPlaying(false);  // 停止播放
            }
        }
        
        state.setCurrentTime(t);
        
        // 采样并应用动画...
    }
}
```

### 3. TimelineModel（Timeline 数据模型）

```java
public class TimelineModel {
    // ========== 播放控制（委托给 PlaybackController）==========
    
    public void play() {
        PlaybackController.get().play();
    }
    
    public void pause() {
        PlaybackController.get().pause();
    }
    
    public void togglePlay() {
        if (isPlaying()) pause();
        else play();
    }
    
    // ========== 状态同步（委托给 AnimationState）==========
    
    public boolean isPlaying() { 
        return AnimationState.get().isPlaying(); 
    }
    
    public float getCurrentTime() { 
        return AnimationState.get().getCurrentTime(); 
    }
    
    public void setCurrentTime(float time) { 
        AnimationState.get().setCurrentTime(time);
    }
    
    public float getDuration() { 
        return AnimationState.get().getDuration(); 
    }
}
```

### 4. ChronoBlocksUIManager（系统协调器）

```java
public class ChronoBlocksUIManager {
    private long lastUpdateTime = System.currentTimeMillis();
    
    /**
     * 更新动画系统（每帧调用）
     */
    private void updateAnimationSystem() {
        long currentTime = System.currentTimeMillis();
        float deltaSeconds = (currentTime - lastUpdateTime) / 1000.0f;
        lastUpdateTime = currentTime;
        
        // 限制最大帧间隔
        deltaSeconds = Math.min(deltaSeconds, 0.1f);
        
        // 驱动播放控制器
        PlaybackController.get().update(deltaSeconds);
    }
    
    public void render() {
        // 🔄 系统级更新循环
        updateAnimationSystem();
        
        // 渲染所有面板...
    }
}
```

### 5. ToolbarPanel（工具栏面板）

```java
public class ToolbarPanel extends AbstractPanel {
    private void renderPlaybackControls() {
        PlaybackController pc = PlaybackController.get();
        
        // Play / Pause
        if (pc.isPlaying()) {
            if (ImGui.button("Pause##play")) pc.pause();
        } else {
            if (ImGui.button("Play##play")) pc.play();
        }
        
        // Stop
        if (ImGui.button("Stop##play")) pc.stop();
        
        // Loop
        if (ImGui.checkbox("Loop##loop", isLoopEnabled)) {
            isLoopEnabled = !isLoopEnabled;
            pc.toggleLoop();
        }
    }
    
    private void renderStatusIndicators() {
        AnimationState state = AnimationState.get();
        float time = state.getCurrentTime();
        float duration = state.getDuration();
        float fps = ImGui.getIO().getFramerate();

        ImGui.text(String.format("⏱ %.2f / %.2fs", time, duration));
        ImGui.text(String.format("FPS: %.0f", fps));
    }
}
```

## 💡 交互示例

### 示例 1: 用户点击播放

```
[用户操作]
点击 Toolbar 的 "Play" 按钮

[系统响应]
1. ToolbarPanel.renderPlaybackControls() 捕获点击
2. PlaybackController.play() 被调用
3. AnimationState.setPlaying(true)
4. ChronoBlocksUIManager.updateAnimationSystem() 每帧更新
5. PlaybackController.update(deltaTime) 增加 currentTime
6. TimelinePanel 读取 AnimationState.getCurrentTime()
7. 播放光标实时移动
```

### 示例 2: 用户拖动时间光标

```
[用户操作]
在 Timeline 上拖动时间光标到 5.5 秒

[系统响应]
1. TimelinePanel 捕获鼠标拖动
2. TimelineModel.setCurrentTime(5.5)
3. AnimationState.setCurrentTime(5.5)
4. ToolbarPanel 的状态显示立即更新为 "⏱ 5.50 / 10.00s"
5. 所有面板保持同步
```

### 示例 3: 循环播放

```
[场景]
动画时长 10 秒，启用循环，播放到结束

[系统响应]
1. PlaybackController.update() 检测到 time >= 10.0
2. 检查 loop == true
3. AnimationState.setCurrentTime(0)
4. 动画从头开始继续播放
5. Timeline 光标跳回起点
```

## 🎯 系统优势

### 1. ✅ 单一数据源（Single Source of Truth）
- **AnimationState** 是唯一的状态存储
- 所有面板读取同一份数据
- 避免状态不同步问题

### 2. ✅ 职责分离（Separation of Concerns）
- **ToolbarPanel**: 命令触发
- **TimelinePanel**: 视觉反馈
- **PlaybackController**: 业务逻辑
- **AnimationState**: 数据存储

### 3. ✅ 统一更新循环（Unified Update Loop）
- **ChronoBlocksUIManager** 统一驱动
- 每帧调用一次 `updateAnimationSystem()`
- 保证时间增量计算准确

### 4. ✅ 易于扩展（Easy to Extend）
- 新增面板只需读取 `AnimationState`
- 新增控制只需调用 `PlaybackController`
- 新增动画功能在 `PlaybackController.update()` 中实现

### 5. ✅ 专业级交互（Professional UX）
- 类似 Blender、Unity Timeline、Unreal Sequencer
- 实时同步，零延迟
- 流畅的播放体验

## 🔍 调试建议

### 查看当前状态

```java
AnimationState state = AnimationState.get();
System.out.println("Playing: " + state.isPlaying());
System.out.println("Time: " + state.getCurrentTime());
System.out.println("Duration: " + state.getDuration());
```

### 监控播放控制器

```java
PlaybackController pc = PlaybackController.get();
System.out.println("Loop: " + pc.isLoop());
System.out.println("Speed: " + pc.getSpeed());
```

### 验证时间同步

在 Toolbar 和 Timeline 中同时显示时间，确保数值一致：
- Toolbar: `⏱ 3.25 / 10.00s`
- Timeline: 光标位置应在 3.25 秒刻度处

## 🚀 未来扩展方向

### 1. 时间线标记（Markers）
```java
// AnimationState 中添加
private List<Marker> markers = new ArrayList<>();

public void addMarker(float time, String label) {
    markers.add(new Marker(time, label));
}
```

### 2. 播放速度控制
```java
// Toolbar 添加速度滑块
float[] speed = new float[]{pc.getSpeed()};
if (ImGui.sliderFloat("Speed", speed, 0.1f, 3.0f, "%.1fx")) {
    pc.setSpeed(speed[0]);
}
```

### 3. 关键帧编辑
```java
// Timeline 支持添加关键帧
if (ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
    float time = screenToTime(mouseX);
    addKeyframeAtTime(time);
}
```

### 4. 撤销/重做支持
```java
// CommandManager 集成
commandManager.execute(new SetTimeCommand(oldTime, newTime));
```

## 📋 总结

ChronoBlocks v4.5 一体化交互系统实现了：

✅ **统一的动画状态管理** - AnimationState 单例
✅ **集中的播放控制逻辑** - PlaybackController 单例
✅ **自动的帧驱动更新** - ChronoBlocksUIManager 驱动
✅ **完整的面板同步** - Toolbar ⇄ Timeline ⇄ AnimationState
✅ **专业的用户体验** - 实时反馈，流畅交互

这是一个完整的、生产级的动画编辑器核心架构！🎉

---

**版本**: v4.5  
**作者**: ChronoBlocks Team  
**日期**: 2025-10-15

