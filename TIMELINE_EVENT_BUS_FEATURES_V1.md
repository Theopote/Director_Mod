# 🎯 TimelineEventBus 增强版功能特性 v1.0

**异步队列 + 线程池 + 优雅关闭**

---

## 🆕 新增特性

### 1️⃣ 异步队列工作线程

```java
// TimelineEvent 类型的事件自动使用队列
TimelineEvent event = new TimelineEvent(TimelineEventTypes.PLAY);
TimelineEventBus.get().postAsync(event); // 加入队列

// 查询队列大小
int pending = TimelineEventBus.get().getPendingEventCount();
```

**优势：**
- ✅ 避免创建大量临时线程
- ✅ 按顺序处理事件（FIFO）
- ✅ 自动启动/停止工作线程
- ✅ 使用 BlockingQueue 避免忙等待

### 2️⃣ 线程池管理

```java
// CachedThreadPool 自动管理线程
// - TimelineEvent → 队列工作线程
// - 其他事件类型 → 线程池直接派发
// - 延迟事件 → 线程池执行
```

**优势：**
- ✅ 线程复用，减少创建开销
- ✅ Daemon线程，不阻止JVM退出
- ✅ 自动扩缩容

### 3️⃣ 优雅关闭

```java
// 应用关闭时
TimelineEventBus.get().shutdown();

// 等待最多5秒让任务完成
// 超时则强制关闭
```

**优势：**
- ✅ 不丢失队列中的事件
- ✅ 等待异步任务完成
- ✅ 超时保护

---

## 📊 性能对比

### 异步派发性能

| 实现方式 | 创建线程 | 内存占用 | 顺序保证 | 性能 |
|---------|---------|---------|---------|------|
| **旧方式**（每次创建线程） | 每次1个 | 高 | ❌ | 一般 |
| **新方式**（队列工作线程） | 复用 | 低 | ✅ | 优秀 |

### 线程使用对比

```
旧方式：
postAsync() → new Thread() → 执行 → 销毁
postAsync() → new Thread() → 执行 → 销毁
postAsync() → new Thread() → 执行 → 销毁
// 大量事件时创建大量线程

新方式：
postAsync() → eventQueue.offer() ┐
postAsync() → eventQueue.offer() ├→ [工作线程] → poll() → 执行
postAsync() → eventQueue.offer() ┘
// 只有1个工作线程处理所有TimelineEvent
```

---

## 🔧 完整API

### 异步相关

| 方法 | 参数 | 功能 |
|------|------|------|
| `postAsync()` | event | 异步派发（TimelineEvent用队列，其他用线程池） |
| `postDelayed()` | event, delayMs | 延迟派发（使用线程池） |
| `startQueueWorker()` | - | 启动队列工作线程（私有，自动调用） |
| `stopQueueWorker()` | - | 停止队列工作线程 |
| `getPendingEventCount()` | - | 获取队列中待处理事件数 |
| `shutdown()` | - | 优雅关闭（停止线程池） |
| `isShutdown()` | - | 查询是否已关闭 |

---

## 📚 使用示例

### 示例 1：异步队列工作流

```java
// ========== 发布大量事件 ==========
for (int i = 0; i < 1000; i++) {
    TimelineEvent event = new TimelineEvent(TimelineEventTypes.FRAME_CHANGED)
        .withData("frame", i);
    
    // 自动加入队列，由单个工作线程按序处理
    TimelineEventBus.get().postAsync(event);
}

// ========== 查询队列状态 ==========
int pending = TimelineEventBus.get().getPendingEventCount();
System.out.println("待处理事件: " + pending);

// ========== 调试信息 ==========
System.out.println(TimelineEventBus.get().getDebugInfo());
// 输出: TimelineEventBus [事件类型: 3, 总监听器: 5, 已派发: 234, 队列: 15, 工作线程: 运行中, 调试: 是]
```

### 示例 2：应用关闭时优雅停止

```java
public class ChronoBlocksApplication {
    
    public void shutdown() {
        LOGGER.info("正在关闭应用...");
        
        // 1. 停止队列工作线程
        TimelineEventBus.get().stopQueueWorker();
        
        // 2. 等待队列中的事件处理完
        while (TimelineEventBus.get().getPendingEventCount() > 0) {
            Thread.sleep(100);
        }
        
        // 3. 关闭事件总线
        TimelineEventBus.get().shutdown();
        
        // 4. 其他清理...
        LOGGER.info("应用已关闭");
    }
}
```

### 示例 3：性能监控

```java
public class EventBusMonitor {
    
    public void startMonitoring() {
        // 每秒输出统计信息
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            TimelineEventBus bus = TimelineEventBus.get();
            
            System.out.println("=== EventBus 统计 ===");
            System.out.println("已派发事件: " + bus.getTotalEventsPosted());
            System.out.println("队列大小: " + bus.getPendingEventCount());
            System.out.println("监听器总数: " + bus.getTotalListenerCount());
            System.out.println("工作线程: " + (bus.isShutdown() ? "已停止" : "运行中"));
            
        }, 0, 1, TimeUnit.SECONDS);
    }
}
```

---

## 🐛 调试增强

### 新增调试信息

```java
TimelineEventBus bus = TimelineEventBus.get();

// 完整调试信息（包含队列和工作线程状态）
System.out.println(bus.getDebugInfo());
// 输出: TimelineEventBus [事件类型: 5, 总监听器: 12, 已派发: 500, 队列: 3, 工作线程: 运行中, 调试: 是]

// 队列状态
System.out.println("待处理事件: " + bus.getPendingEventCount());

// 关闭状态
System.out.println("是否已关闭: " + bus.isShutdown());
```

---

## ⚡ 性能优化建议

### 1️⃣ 批量派发

```java
// 避免：在循环中逐个派发
for (Keyframe kf : keyframes) {
    TimelineEvent event = new TimelineEvent(TimelineEventTypes.KEYFRAME_UPDATED)
        .withData("keyframe", kf);
    TimelineEventBus.get().postAsync(event);
}

// 推荐：批量派发单个事件
TimelineEvent batchEvent = new TimelineEvent(TimelineEventTypes.PROPERTY_BATCH_UPDATE)
    .withData("keyframes", keyframes);
TimelineEventBus.get().postAsync(batchEvent);
```

### 2️⃣ 使用合适的派发模式

```java
// 同步派发：需要立即执行且监听器很少时
TimelineEventBus.get().post(event);

// 异步派发（队列）：TimelineEvent 类型，需要顺序保证时
TimelineEventBus.get().postAsync(timelineEvent);

// 异步派发（线程池）：其他类型，无需顺序保证时
TimelineEventBus.get().postAsync(customEvent);

// 延迟派发：定时任务
TimelineEventBus.get().postDelayed(event, 1000);
```

### 3️⃣ 监听器优化

```java
// 避免：在监听器中执行耗时操作
TimelineEventBus.get().subscribe(TimelineEvent.class, event -> {
    heavyComputation(); // 阻塞队列工作线程
});

// 推荐：监听器中异步处理
TimelineEventBus.get().subscribe(TimelineEvent.class, event -> {
    CompletableFuture.runAsync(() -> heavyComputation());
});
```

---

## ✅ 增强版特性总结

### 核心改进

| 特性 | 旧版本 | 增强版 |
|------|--------|--------|
| **异步派发** | 每次创建线程 | 队列工作线程 |
| **线程管理** | 无管理 | CachedThreadPool |
| **顺序保证** | ❌ | ✅ (TimelineEvent) |
| **关闭机制** | ❌ | ✅ 优雅关闭 |
| **队列监控** | ❌ | ✅ getPendingEventCount() |
| **代码行数** | ~400 | ~520 |

### 线程安全增强

| 组件 | 技术 | 保证 |
|------|------|------|
| **监听器表** | ConcurrentHashMap | 并发读写 |
| **监听器列表** | CopyOnWriteArrayList | 迭代安全 |
| **工作线程标志** | AtomicBoolean | 原子操作 |
| **事件队列** | LinkedBlockingQueue | 阻塞安全 |

---

## 🎉 最终交付

**✅ TimelineEventBus 增强版功能：**

**核心功能：**
- 泛型事件支持
- 同步/异步/延迟派发
- **异步队列工作线程** ⭐
- **BlockingQueue管理** ⭐
- **优雅关闭机制** ⭐
- 一次性监听器
- 完整线程安全
- 调试和统计API

**代码统计：**
- ~520行增强代码
- 完整的异步队列支持
- 线程池管理
- 优雅关闭机制

**🚀 ChronoBlocks TimelineEventBus 增强版 - 完全交付！**

