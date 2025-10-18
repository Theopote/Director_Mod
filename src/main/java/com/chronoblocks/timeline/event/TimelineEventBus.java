package com.chronoblocks.timeline.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 🎯 TimelineEventBus
 * ------------------------------------------------------------
 * ChronoBlocks 全局事件总线（线程安全）
 *
 * <p>用于在 Timeline、属性面板、动画控制器、容器系统之间分发事件。
 *
 * <h3>支持功能：</h3>
 * <ul>
 *  <li>✅ 任意事件类型（泛型 T）</li>
 *  <li>✅ 异步/同步派发</li>
 *  <li>✅ 优先级订阅（先注册的先触发）</li>
 *  <li>✅ 一次性监听器（触发后自动移除）</li>
 *  <li>✅ 线程安全（ConcurrentHashMap + CopyOnWriteArrayList）</li>
 * </ul>
 *
 * <h3>架构位置：</h3>
 * <pre>
 * +---------------------------------------------+
 * | TimelineEventBus                            |
 * |---------------------------------------------|
 * | 事件类型 → [监听器列表]                      |
 * | 支持同步与异步派发                          |
 * +---------------------------------------------+
 *         ↑             ↑               ↑
 *  [Timeline UI]   [Animation Ctrl]   [Property Panel]
 * </pre>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 注册监听
 * TimelineEventBus.get().subscribe(TimelinePlaybackEvent.class, e -> {
 *     System.out.println("播放状态：" + e.state);
 * });
 * 
 * // 发送事件
 * TimelineEventBus.get().post(new TimelinePlaybackEvent(...));
 * }</pre>
 *
 * @author ChronoBlocks Team
 * @version 4.9
 * @since 2025-10-18
 */
public class TimelineEventBus {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineEventBus.class);

    private static final TimelineEventBus INSTANCE = new TimelineEventBus();

    /**
     * 监听器存储结构：
     * Class<?> → List<ListenerWrapper<?>>
     */
    private final Map<Class<?>, List<ListenerWrapper<?>>> listeners = new ConcurrentHashMap<>();

    /** 事件派发计数器（用于统计） */
    private long totalEventsPosted = 0;

    /** 是否启用调试日志 */
    private boolean debugMode = false;

    /** 异步派发线程池 */
    private final ExecutorService asyncExecutor;

    /** 事件队列（异步模式） */
    private final BlockingQueue<TimelineEvent> eventQueue = new LinkedBlockingQueue<>();

    /** 后台队列工作线程运行标志 */
    private final AtomicBoolean queueWorkerRunning = new AtomicBoolean(false);

    private TimelineEventBus() {
        this.asyncExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "TimelineEventBus-AsyncWorker");
            t.setDaemon(true);
            return t;
        });
        LOGGER.info("[EventBus] TimelineEventBus 已初始化（异步线程池已创建）");
    }

    /**
     * 获取单例实例
     * 
     * @return 事件总线实例
     */
    public static TimelineEventBus get() {
        return INSTANCE;
    }

    // =========================================================
    // 📡 注册与取消监听
    // =========================================================

    /**
     * 注册事件监听
     * 
     * @param <T> 事件类型
     * @param eventType 事件类
     * @param handler 事件处理器
     */
    public <T> void subscribe(Class<T> eventType, Consumer<T> handler) {
        subscribe(eventType, handler, false);
    }

    /**
     * 注册一次性监听器（触发后自动移除）
     * 
     * @param <T> 事件类型
     * @param eventType 事件类
     * @param handler 事件处理器
     */
    public <T> void subscribeOnce(Class<T> eventType, Consumer<T> handler) {
        subscribe(eventType, handler, true);
    }

    /**
     * 内部注册方法
     */
    private <T> void subscribe(Class<T> eventType, Consumer<T> handler, boolean once) {
        if (eventType == null || handler == null) {
            LOGGER.warn("[EventBus] 无效的注册参数：eventType={}, handler={}", eventType, handler);
            return;
        }

        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(new ListenerWrapper<>(handler, once));
        
        if (debugMode) {
            LOGGER.debug("[EventBus] 注册监听: {} (一次性: {})", eventType.getSimpleName(), once);
        }
    }

    /**
     * 取消事件监听
     * 
     * @param <T> 事件类型
     * @param eventType 事件类
     * @param handler 事件处理器
     */
    public <T> void unsubscribe(Class<T> eventType, Consumer<T> handler) {
        if (eventType == null || handler == null) {
            return;
        }

        List<ListenerWrapper<?>> list = listeners.get(eventType);
        if (list != null) {
            int beforeSize = list.size();
            list.removeIf(w -> w.handler == handler);
            int afterSize = list.size();
            
            if (beforeSize > afterSize && debugMode) {
                LOGGER.debug("[EventBus] 移除监听: {} (移除 {} 个)", 
                    eventType.getSimpleName(), beforeSize - afterSize);
            }
        }
    }

    /**
     * 取消指定事件类型的所有监听器
     * 
     * @param eventType 事件类
     */
    public void unsubscribeAll(Class<?> eventType) {
        if (eventType == null) {
            return;
        }

        List<ListenerWrapper<?>> list = listeners.remove(eventType);
        if (list != null && !list.isEmpty()) {
            LOGGER.debug("[EventBus] 移除 {} 的所有监听器 ({} 个)", 
                eventType.getSimpleName(), list.size());
        }
    }

    /**
     * 清空所有监听器
     */
    public void clear() {
        int totalListeners = listeners.values().stream()
            .mapToInt(List::size)
            .sum();
        
        listeners.clear();
        LOGGER.warn("[EventBus] 所有事件监听器已清空 (共 {} 个)", totalListeners);
    }

    // =========================================================
    // 🚀 派发事件
    // =========================================================

    /**
     * 同步派发（立即在当前线程触发）
     * 
     * @param <T> 事件类型
     * @param event 事件对象
     */
    public <T> void post(T event) {
        if (event == null) {
            LOGGER.warn("[EventBus] 尝试派发 null 事件");
            return;
        }

        Class<?> eventType = event.getClass();
        List<ListenerWrapper<?>> list = listeners.get(eventType);
        
        if (list == null || list.isEmpty()) {
            if (debugMode) {
                LOGGER.trace("[EventBus] 事件 {} 没有监听器", eventType.getSimpleName());
            }
            return;
        }

        totalEventsPosted++;
        int triggeredCount = 0;
        List<ListenerWrapper<?>> toRemove = new ArrayList<>();

        for (ListenerWrapper<?> wrapper : list) {
            try {
                @SuppressWarnings("unchecked")
                ListenerWrapper<T> w = (ListenerWrapper<T>) wrapper;
                w.handler.accept(event);
                triggeredCount++;
                
                if (w.once) {
                    toRemove.add(wrapper);
                }
            } catch (Exception e) {
                LOGGER.error("[EventBus] 事件 {} 触发异常: {}", 
                    eventType.getSimpleName(), e.getMessage(), e);
            }
        }

        // 移除一次性监听器
        if (!toRemove.isEmpty()) {
            list.removeAll(toRemove);
            if (debugMode) {
                LOGGER.debug("[EventBus] 移除 {} 个一次性监听器", toRemove.size());
            }
        }

        if (debugMode) {
            LOGGER.trace("[EventBus] 事件 {} 派发完成 (触发 {} 个监听器)", 
                eventType.getSimpleName(), triggeredCount);
        }
    }

    /**
     * 异步派发（将事件放入队列，由后台线程处理）
     * 
     * @param <T> 事件类型
     * @param event 事件对象
     */
    public <T> void postAsync(T event) {
        if (event == null) {
            LOGGER.warn("[EventBus] 尝试异步派发 null 事件");
            return;
        }

        // 如果是 TimelineEvent，放入队列
        if (event instanceof TimelineEvent) {
            eventQueue.offer((TimelineEvent) event);
            startQueueWorker();
            
            if (debugMode) {
                LOGGER.debug("[EventBus] 事件已加入异步队列: {}", event.getClass().getSimpleName());
            }
        } else {
            // 其他类型事件使用线程池直接派发
            asyncExecutor.execute(() -> post(event));
            
            if (debugMode) {
                LOGGER.debug("[EventBus] 异步派发事件（线程池）: {}", event.getClass().getSimpleName());
            }
        }
    }

    /**
     * 延迟派发（在指定毫秒后派发）
     * 
     * @param <T> 事件类型
     * @param event 事件对象
     * @param delayMillis 延迟毫秒数
     */
    public <T> void postDelayed(T event, long delayMillis) {
        if (event == null) {
            LOGGER.warn("[EventBus] 尝试延迟派发 null 事件");
            return;
        }

        asyncExecutor.execute(() -> {
            try {
                Thread.sleep(delayMillis);
                post(event);
            } catch (InterruptedException e) {
                LOGGER.warn("[EventBus] 延迟派发被中断: {}", e.getMessage());
                Thread.currentThread().interrupt();
            }
        });

        if (debugMode) {
            LOGGER.debug("[EventBus] 延迟 {}ms 派发事件: {}", 
                delayMillis, event.getClass().getSimpleName());
        }
    }

    // =========================================================
    // ⚙️ 异步队列循环
    // =========================================================

    /**
     * 启动异步队列工作线程
     */
    private void startQueueWorker() {
        if (queueWorkerRunning.compareAndSet(false, true)) {
            asyncExecutor.execute(() -> {
                LOGGER.debug("[EventBus] 队列工作线程已启动");
                while (queueWorkerRunning.get()) {
                    try {
                        TimelineEvent event = eventQueue.poll(100, TimeUnit.MILLISECONDS);
                        if (event != null) {
                            post(event);
                        }
                    } catch (InterruptedException e) {
                        LOGGER.trace("[EventBus] 队列工作线程被中断");
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                LOGGER.debug("[EventBus] 队列工作线程已停止");
            });
        }
    }

    /**
     * 停止异步队列工作线程
     */
    public void stopQueueWorker() {
        if (queueWorkerRunning.compareAndSet(true, false)) {
            LOGGER.info("[EventBus] 停止队列工作线程");
        }
    }

    /**
     * 获取队列中待处理的事件数量
     * 
     * @return 队列大小
     */
    public int getPendingEventCount() {
        return eventQueue.size();
    }

    // =========================================================
    // 🧩 内部包装类
    // =========================================================

    private static class ListenerWrapper<T> {
        final Consumer<T> handler;
        final boolean once;

        ListenerWrapper(Consumer<T> handler, boolean once) {
            this.handler = handler;
            this.once = once;
        }
    }

    // =========================================================
    // 🧭 工具方法
    // =========================================================

    /**
     * 查询某个事件类型是否有监听器
     * 
     * @param eventType 事件类
     * @return 如果有监听器则返回 true
     */
    public boolean hasListeners(Class<?> eventType) {
        if (eventType == null) {
            return false;
        }
        List<ListenerWrapper<?>> list = listeners.get(eventType);
        return list != null && !list.isEmpty();
    }

    /**
     * 获取监听器数量
     * 
     * @param eventType 事件类
     * @return 监听器数量
     */
    public int getListenerCount(Class<?> eventType) {
        if (eventType == null) {
            return 0;
        }
        List<ListenerWrapper<?>> list = listeners.get(eventType);
        return list == null ? 0 : list.size();
    }

    /**
     * 获取所有已注册的事件类型
     * 
     * @return 事件类型集合
     */
    public Set<Class<?>> getRegisteredEventTypes() {
        return Collections.unmodifiableSet(listeners.keySet());
    }

    /**
     * 获取总监听器数量
     * 
     * @return 所有事件类型的监听器总数
     */
    public int getTotalListenerCount() {
        return listeners.values().stream()
            .mapToInt(List::size)
            .sum();
    }

    /**
     * 获取已派发的事件总数
     * 
     * @return 事件派发计数
     */
    public long getTotalEventsPosted() {
        return totalEventsPosted;
    }

    /**
     * 重置事件计数器
     */
    public void resetEventCounter() {
        totalEventsPosted = 0;
        LOGGER.debug("[EventBus] 事件计数器已重置");
    }

    /**
     * 启用/禁用调试模式
     * 
     * @param enabled 是否启用
     */
    public void setDebugMode(boolean enabled) {
        this.debugMode = enabled;
        LOGGER.info("[EventBus] 调试模式: {}", enabled ? "启用" : "禁用");
    }

    /**
     * 获取调试模式状态
     * 
     * @return 是否启用调试模式
     */
    public boolean isDebugMode() {
        return debugMode;
    }

    /**
     * 获取调试信息
     * 
     * @return 调试信息字符串
     */
    public String getDebugInfo() {
        return String.format("TimelineEventBus [事件类型: %d, 总监听器: %d, 已派发: %d, 队列: %d, 工作线程: %s, 调试: %s]",
            listeners.size(),
            getTotalListenerCount(),
            totalEventsPosted,
            eventQueue.size(),
            queueWorkerRunning.get() ? "运行中" : "已停止",
            debugMode ? "是" : "否");
    }

    /**
     * 打印所有已注册的监听器（用于调试）
     */
    public void printAllListeners() {
        LOGGER.info("========== TimelineEventBus 监听器列表 ==========");
        if (listeners.isEmpty()) {
            LOGGER.info("  (无注册监听器)");
        } else {
            listeners.forEach((eventType, wrappers) -> {
                LOGGER.info("  {} -> {} 个监听器", eventType.getSimpleName(), wrappers.size());
            });
        }
        LOGGER.info("==================================================");
    }

    /**
     * 关闭事件总线（优雅停止所有异步任务）
     * 
     * <p>注意：此方法会关闭线程池，之后不应再使用异步派发。
     */
    public void shutdown() {
        stopQueueWorker();
        
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
                LOGGER.warn("[EventBus] 强制关闭异步线程池");
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        LOGGER.info("[EventBus] TimelineEventBus 已关闭");
    }

    /**
     * 查询事件总线是否已关闭
     * 
     * @return 如果已关闭则返回 true
     */
    public boolean isShutdown() {
        return asyncExecutor.isShutdown();
    }
}

