# 🎬 ChronoBlocks 时间线播放同步器 v1.0

**多系统播放状态的统一管理**

**完成日期：** 2025-10-18  
**版本：** v1.0  
**状态：** ✅ 核心功能已实现

---

## 📦 系统概览

TimelinePlaybackSynchronizer 是 ChronoBlocks 动画系统的播放同步核心，负责：

- ✅ 统一管理时间线UI、动画控制器、场景系统的播放状态
- ✅ 广播播放事件到所有监听器
- ✅ 支持播放/暂停/停止/跳转操作
- ✅ 倍速播放和循环控制
- ✅ 线程安全的事件分发

---

## 🏗️ 架构设计

### 系统位置

```
┌──────────────────────────────────────────────────┐
│          时间线 UI (TimelinePanel)                │
│  - 播放按钮  - 进度条  - 倍速选择                 │
└────────────────┬─────────────────────────────────┘
                 │ 交互
┌────────────────▼─────────────────────────────────┐
│      TimelinePlaybackSynchronizer (同步核心)     │
│  - 状态管理  - 事件广播  - 倍速控制               │
└────┬───────────┬──────────────────────────────┬──┘
     │           │                              │
     ▼           ▼                              ▼
┌─────────┐ ┌────────────┐ ┌──────────────────────┐
│Animation│ │  Timeline  │ │  Scene/Container     │
│Controller│ │   Bridge   │ │     System           │
└─────────┘ └────────────┘ └──────────────────────┘
```

### 数据流向

```
用户点击播放按钮
      ↓
TimelinePanel.onPlayClicked()
      ↓
synchronizer.play()
      ↓
controller.play() + 广播事件
      ↓
监听器接收事件
      ↓
场景系统更新
```

---

## 🎯 核心功能

### 1️⃣ 播放控制

```java
// 创建同步器
PropertyAnimationController controller = new PropertyAnimationController();
PropertyAnimationTimelineBridge bridge = new PropertyAnimationTimelineBridge(controller);
TimelinePlaybackSynchronizer synchronizer = 
    new TimelinePlaybackSynchronizer(controller, bridge);

// 绑定序列
synchronizer.bindSequence("主动画序列", 10.0f, true);

// 播放控制
synchronizer.play();    // 开始播放
synchronizer.pause();   // 暂停
synchronizer.stop();    // 停止（重置到起点）
synchronizer.toggle();  // 切换播放/暂停
```

### 2️⃣ 循环控制

```java
// 设置循环
synchronizer.setLoop(true);

// 查询循环状态
boolean looping = synchronizer.isLoop();
```

### 3️⃣ 倍速控制

```java
// 设置倍速
synchronizer.setPlaybackSpeed(2.0f);  // 2倍速
synchronizer.setPlaybackSpeed(0.5f);  // 慢动作
synchronizer.setPlaybackSpeed(1.0f);  // 正常速度

// 查询速度
float speed = synchronizer.getPlaybackSpeed();
```

### 4️⃣ 时间跳转

```java
// 跳转到指定时间
synchronizer.seekTo(5.0f);  // 跳转到第5秒

// 获取当前时间
float currentTime = synchronizer.getCurrentTime();

// 获取播放进度（0.0 ~ 1.0）
float progress = synchronizer.getProgress();
```

### 5️⃣ 事件监听

```java
// 添加监听器
synchronizer.addListener(event -> {
    switch (event.state) {
        case PLAY:
            System.out.println("开始播放");
            break;
        case PAUSE:
            System.out.println("暂停");
            break;
        case STOP:
            System.out.println("停止");
            break;
        case SPEED_CHANGED:
            System.out.println("速度变化: " + event.speed + "x");
            break;
    }
});

// 移除监听器
synchronizer.removeListener(listener);

// 清空所有监听器
synchronizer.clearListeners();
```

### 6️⃣ 帧更新

```java
// 在主循环中调用
public void update(float deltaTime) {
    synchronizer.updateFrame(deltaTime);
    render();
}
```

---

## 📚 完整使用示例

### 示例 1：基础播放控制

```java
// ========== 初始化 ==========
PropertyAnimationController controller = new PropertyAnimationController();
PropertyAnimationTimelineBridge bridge = new PropertyAnimationTimelineBridge(controller);
TimelinePlaybackSynchronizer synchronizer = 
    new TimelinePlaybackSynchronizer(controller, bridge);

// ========== 绑定序列 ==========
synchronizer.bindSequence("主动画", 10.0f, true);

// ========== 添加监听器 ==========
synchronizer.addListener(event -> {
    System.out.println("播放状态: " + event.state);
    System.out.println("当前时间: " + event.currentTime);
    System.out.println("进度: " + (event.getProgress() * 100) + "%");
});

// ========== 播放控制 ==========
synchronizer.play();

// ========== 主循环 ==========
while (running) {
    synchronizer.updateFrame(deltaTime);
    
    // 渲染当前状态
    System.out.println(synchronizer.getDebugInfo());
    
    render();
}
```

### 示例 2：与时间线面板集成

```java
public class TimelinePanel {
    private TimelinePlaybackSynchronizer synchronizer;
    
    public void init() {
        // 创建同步器
        PropertyAnimationController controller = new PropertyAnimationController();
        PropertyAnimationTimelineBridge bridge = new PropertyAnimationTimelineBridge(controller);
        synchronizer = new TimelinePlaybackSynchronizer(controller, bridge);
        
        // 绑定序列
        synchronizer.bindSequence("场景1", 30.0f, false);
        
        // 监听播放状态
        synchronizer.addListener(this::onPlaybackStateChanged);
    }
    
    public void render() {
        ImGui.begin("时间线");
        
        // 播放控制按钮
        if (ImGui.button(synchronizer.isPlaying() ? "⏸ 暂停" : "▶ 播放", 80, 30)) {
            synchronizer.toggle();
        }
        
        ImGui.sameLine();
        if (ImGui.button("⏹ 停止", 80, 30)) {
            synchronizer.stop();
        }
        
        // 进度条
        float[] progress = {synchronizer.getProgress()};
        if (ImGui.sliderFloat("##progress", progress, 0.0f, 1.0f, "%.1f%%")) {
            float targetTime = progress[0] * synchronizer.getDuration();
            synchronizer.seekTo(targetTime);
        }
        
        // 时间显示
        ImGui.text(String.format("时间: %.2f / %.2f", 
            synchronizer.getCurrentTime(), 
            synchronizer.getDuration()));
        
        // 倍速控制
        float[] speed = {synchronizer.getPlaybackSpeed()};
        if (ImGui.dragFloat("速度", speed, 0.1f, 0.1f, 5.0f, "%.2fx")) {
            synchronizer.setPlaybackSpeed(speed[0]);
        }
        
        // 循环开关
        boolean[] loop = {synchronizer.isLoop()};
        if (ImGui.checkbox("循环播放", loop)) {
            synchronizer.setLoop(loop[0]);
        }
        
        ImGui.end();
    }
    
    public void update(float deltaTime) {
        synchronizer.updateFrame(deltaTime);
    }
    
    private void onPlaybackStateChanged(TimelinePlaybackEvent event) {
        // 更新UI状态
        if (event.isPlaying()) {
            updatePlayButton("⏸ 暂停");
        } else {
            updatePlayButton("▶ 播放");
        }
    }
}
```

### 示例 3：与场景系统集成

```java
public class SceneManager implements PlaybackStateListener {
    private TimelinePlaybackSynchronizer synchronizer;
    private List<AnimatedObject> objects = new ArrayList<>();
    
    public void init() {
        // ... 创建同步器 ...
        synchronizer.addListener(this);
    }
    
    @Override
    public void onPlaybackStateChanged(TimelinePlaybackEvent event) {
        switch (event.state) {
            case PLAY:
                // 通知所有对象开始动画
                for (AnimatedObject obj : objects) {
                    obj.startAnimation();
                }
                break;
                
            case PAUSE:
                // 暂停所有对象
                for (AnimatedObject obj : objects) {
                    obj.pauseAnimation();
                }
                break;
                
            case STOP:
                // 重置所有对象
                for (AnimatedObject obj : objects) {
                    obj.resetAnimation();
                }
                break;
                
            case SPEED_CHANGED:
                // 更新速度
                for (AnimatedObject obj : objects) {
                    obj.setAnimationSpeed(event.speed);
                }
                break;
        }
    }
}
```

### 示例 4：快捷键控制

```java
public class AnimationEditor {
    private TimelinePlaybackSynchronizer synchronizer;
    
    public void handleKeyPress(int key) {
        switch (key) {
            case GLFW.GLFW_KEY_SPACE:
                // 空格键：播放/暂停
                synchronizer.toggle();
                break;
                
            case GLFW.GLFW_KEY_HOME:
                // Home键：回到起点
                synchronizer.seekTo(0.0f);
                break;
                
            case GLFW.GLFW_KEY_END:
                // End键：跳到结尾
                synchronizer.seekTo(synchronizer.getDuration());
                break;
                
            case GLFW.GLFW_KEY_LEFT:
                // 左箭头：后退1秒
                float currentTime = synchronizer.getCurrentTime();
                synchronizer.seekTo(Math.max(0, currentTime - 1.0f));
                break;
                
            case GLFW.GLFW_KEY_RIGHT:
                // 右箭头：前进1秒
                float time = synchronizer.getCurrentTime();
                synchronizer.seekTo(Math.min(synchronizer.getDuration(), time + 1.0f));
                break;
                
            case GLFW.GLFW_KEY_1:
                // 1键：正常速度
                synchronizer.setPlaybackSpeed(1.0f);
                break;
                
            case GLFW.GLFW_KEY_2:
                // 2键：2倍速
                synchronizer.setPlaybackSpeed(2.0f);
                break;
                
            case GLFW.GLFW_KEY_0:
                // 0键：慢动作
                synchronizer.setPlaybackSpeed(0.5f);
                break;
                
            case GLFW.GLFW_KEY_L:
                // L键：切换循环
                synchronizer.setLoop(!synchronizer.isLoop());
                break;
        }
    }
}
```

---

## 🔧 API 参考

### TimelinePlaybackSynchronizer

#### 序列绑定

| 方法 | 参数 | 功能 |
|------|------|------|
| `bindSequence()` | sequenceName, duration, loop | 绑定时间线序列 |

#### 播放控制

| 方法 | 参数 | 功能 |
|------|------|------|
| `play()` | - | 开始播放 |
| `pause()` | - | 暂停播放 |
| `stop()` | - | 停止播放（重置） |
| `toggle()` | - | 切换播放/暂停 |
| `seekTo()` | time | 跳转到指定时间 |

#### 循环控制

| 方法 | 参数 | 功能 |
|------|------|------|
| `setLoop()` | loop | 设置循环播放 |
| `isLoop()` | - | 查询循环状态 |

#### 倍速控制

| 方法 | 参数 | 功能 |
|------|------|------|
| `setPlaybackSpeed()` | speed | 设置播放速度 |
| `getPlaybackSpeed()` | - | 查询播放速度 |

#### 帧更新

| 方法 | 参数 | 功能 |
|------|------|------|
| `updateFrame()` | deltaTime | 每帧更新 |

#### 事件监听

| 方法 | 参数 | 功能 |
|------|------|------|
| `addListener()` | listener | 添加监听器 |
| `removeListener()` | listener | 移除监听器 |
| `clearListeners()` | - | 清空所有监听器 |

#### 状态查询

| 方法 | 返回值 | 功能 |
|------|--------|------|
| `isPlaying()` | boolean | 是否播放中 |
| `getCurrentTime()` | float | 当前时间 |
| `getDuration()` | float | 总时长 |
| `getProgress()` | float | 播放进度（0~1） |
| `getCurrentSequenceName()` | String | 序列名称 |
| `getDebugInfo()` | String | 调试信息 |

#### 清理

| 方法 | 参数 | 功能 |
|------|------|------|
| `clear()` | - | 清空所有状态 |

### TimelinePlaybackEvent

| 字段 | 类型 | 说明 |
|------|------|------|
| `state` | PlaybackState | 播放状态 |
| `speed` | float | 播放速度 |
| `loop` | boolean | 是否循环 |
| `currentTime` | float | 当前时间 |
| `duration` | float | 总时长 |

| 方法 | 返回值 | 功能 |
|------|--------|------|
| `getProgress()` | float | 播放进度（0~1） |
| `isPlaying()` | boolean | 是否播放事件 |
| `isPaused()` | boolean | 是否暂停事件 |
| `isStopped()` | boolean | 是否停止事件 |

### PlaybackState 枚举

| 值 | 说明 |
|----|------|
| `PLAY` | 播放 |
| `PAUSE` | 暂停 |
| `STOP` | 停止 |
| `SEEK` | 跳转 |
| `SPEED_CHANGED` | 速度变化 |
| `LOOP_CHANGED` | 循环状态变化 |

---

## 📊 性能考量

### 性能指标

| 指标 | 建议值 | 说明 |
|------|--------|------|
| **更新频率** | 60 FPS | 主循环调用频率 |
| **事件延迟** | < 1ms | 事件分发延迟 |
| **监听器数量** | < 100 | 每个同步器 |
| **跳转延迟** | < 5ms | seekTo操作延迟 |

### 优化建议

1. **避免频繁跳转**
   ```java
   // 避免：在拖动时每帧跳转
   if (ImGui.isMouseDragging()) {
       synchronizer.seekTo(newTime); // 每帧调用
   }
   
   // 推荐：只在松开时跳转
   if (ImGui.isMouseReleased()) {
       synchronizer.seekTo(newTime); // 只调用一次
   }
   ```

2. **监听器性能**
   ```java
   // 避免：在监听器中执行耗时操作
   synchronizer.addListener(event -> {
       // 不要这样做
       heavyCalculation(); // 阻塞事件分发
   });
   
   // 推荐：异步处理
   synchronizer.addListener(event -> {
       executor.submit(() -> heavyCalculation());
   });
   ```

3. **状态查询优化**
   ```java
   // 缓存频繁查询的值
   private float cachedDuration;
   
   public void init() {
       cachedDuration = synchronizer.getDuration();
   }
   
   // 避免每帧查询
   // float duration = synchronizer.getDuration();
   ```

---

## 🐛 调试支持

### 调试信息

```java
// 打印同步器状态
System.out.println(synchronizer.getDebugInfo());
// 输出: TimelinePlaybackSynchronizer [序列: 主动画序列, 播放: 是, 时间: 2.50/10.00 (25.0%), 速度: 1.00x, 循环: 是, 监听器: 3]

// 监听所有事件
synchronizer.addListener(event -> {
    System.out.println(event); // 自动调用 toString()
});
```

### 日志输出

```
[PlaybackSync] TimelinePlaybackSynchronizer 已创建
[PlaybackSync] 绑定时间线序列: 主动画序列 (时长: 10.0s, 循环: true)
[PlaybackSync] ▶ 播放开始
[PlaybackSync] 广播状态: PLAY
[PlaybackSync] 循环回到起点
[PlaybackSync] ⏸ 暂停播放
[PlaybackSync] 广播状态: PAUSE
```

---

## ✅ 完成清单

### 核心功能（✅ 完成）

- ✅ TimelinePlaybackSynchronizer（~350行）
- ✅ TimelinePlaybackEvent（事件类）
- ✅ PlaybackState（状态枚举）
- ✅ PlaybackStateListener（监听器接口）
- ✅ 播放/暂停/停止控制
- ✅ 循环播放支持
- ✅ 倍速控制
- ✅ 时间跳转
- ✅ 事件监听系统
- ✅ 调试支持

### 文档（✅ 完成）

- ✅ 架构设计文档
- ✅ API 参考
- ✅ 4个完整使用示例
- ✅ 性能优化指南
- ✅ 调试支持

---

## 🚀 未来扩展

### 短期计划

- [ ] 播放标记点（Markers）
- [ ] 区域循环播放
- [ ] 播放历史记录
- [ ] 更多快捷键支持

### 长期计划

- [ ] 多时间线同步
- [ ] 主从同步模式
- [ ] 网络同步支持
- [ ] 录制/回放功能

---

## 📝 总结

✅ **TimelinePlaybackSynchronizer 已完全实现：**

**核心功能：**
- 统一播放状态管理
- 事件驱动架构
- 多系统同步
- 倍速和循环控制

**代码统计：**
- ~500行核心代码（4个文件）
- 完整的API文档
- 4个完整使用示例
- 详细的性能和调试指南

**集成完整度：**
- ✅ 与 PropertyAnimationController 完全集成
- ✅ 与 PropertyAnimationTimelineBridge 完全集成
- ✅ 与 Timeline UI 完全兼容
- ✅ 支持场景系统集成
- ✅ 线程安全的事件分发

**🎉 ChronoBlocks 时间线播放同步器 v1.0 - 完全交付！**

