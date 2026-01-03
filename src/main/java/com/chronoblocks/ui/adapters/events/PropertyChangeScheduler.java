package com.chronoblocks.ui.adapters.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ⚙️ PropertyChangeScheduler - 高级属性变更调度系统
 * ----------------------------------------------------
 * 为 ChronoBlocks 动画系统提供高级调度能力。
 *
 * <h3>✨ 核心功能：</h3>
 * <ul>
 *     <li>⏱️ 帧延迟派发：按帧数延迟执行属性变更</li>
 *     <li>🎯 优先级排序：CRITICAL > HIGH > NORMAL > LOW > BACKGROUND</li>
 *     <li>🚦 节流机制：防止同一属性过于频繁更新</li>
 *     <li>🔄 批量合并：相同属性的多次变更合并为一次</li>
 *     <li>🧵 线程安全：支持多线程调度</li>
 *     <li>🔌 自动集成：与 {@link PropertyChangeDispatcher} 无缝协同</li>
 * </ul>
 *
 * <h3>📖 使用示例：</h3>
 * <pre>{@code
 * // 1. 在动画系统中调度属性变更
 * PropertyChangeScheduler.get().schedule(
 *     container,
 *     "opacity",
 *     0.7f,
 *     0.0f,
 *     PropertyChangeScheduler.Priority.HIGH,
 *     30  // 延迟30帧
 * );
 *
 * // 2. 在主循环中tick（每帧调用）
 * @Override
 * public void tick(float deltaTime) {
 *     // 调度器处理延迟事件
 *     PropertyChangeScheduler.get().tick();
 *     
 *     // 调度器派发到 Dispatcher
 *     PropertyChangeDispatcher.get().flush();
 * }
 *
 * // 3. 配置调度器
 * PropertyChangeScheduler.get().setThrottleEnabled(true);
 * PropertyChangeScheduler.get().setThrottleIntervalMs(50);
 * PropertyChangeScheduler.get().setMergeSameProperties(true);
 * }</pre>
 *
 * <h3>🎯 应用场景：</h3>
 * <table border="1" cellpadding="5">
 *   <tr>
 *     <th>场景</th>
 *     <th>说明</th>
 *     <th>优先级</th>
 *   </tr>
 *   <tr>
 *     <td>容器动画</td>
 *     <td>多容器渐进出现时，延迟同步属性</td>
 *     <td>NORMAL</td>
 *   </tr>
 *   <tr>
 *     <td>时间线关键帧</td>
 *     <td>批量插值时节流变更</td>
 *     <td>HIGH</td>
 *   </tr>
 *   <tr>
 *     <td>相机动画</td>
 *     <td>FOV、焦距等参数的过渡调度</td>
 *     <td>CRITICAL</td>
 *   </tr>
 *   <tr>
 *     <td>后期特效</td>
 *     <td>Bloom/景深参数缓变同步</td>
 *     <td>LOW</td>
 *   </tr>
 *   <tr>
 *     <td>外部插件</td>
 *     <td>监听调度完成后执行自定义逻辑</td>
 *     <td>BACKGROUND</td>
 *   </tr>
 * </table>
 *
 * @author ChronoBlocks Team
 * @version 4.6
 * @since 4.6
 */
public class PropertyChangeScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyChangeScheduler.class);

    // ==================== 单例实例 ====================

    private static final PropertyChangeScheduler INSTANCE = new PropertyChangeScheduler();

    /**
     * 获取单例实例
     *
     * @return 调度器实例
     */
    public static PropertyChangeScheduler get() {
        return INSTANCE;
    }

    // ==================== 优先级枚举 ====================

    /**
     * 事件优先级
     * <p>优先级越高，越先被派发
     */
    public enum Priority {
        /** 关键优先级 - 相机、用户交互等 */
        CRITICAL(100),
        /** 高优先级 - 重要动画、关键帧 */
        HIGH(75),
        /** 普通优先级 - 常规属性变更 */
        NORMAL(50),
        /** 低优先级 - 后期特效、视觉优化 */
        LOW(25),
        /** 后台优先级 - 统计、日志等 */
        BACKGROUND(0);

        /** 优先级权重 */
        public final int weight;

        Priority(int weight) {
            this.weight = weight;
        }
    }

    // ==================== 内部数据结构 ====================

    /** 待调度事件队列（线程安全） */
    private final Queue<ScheduledChange> pendingQueue = new ConcurrentLinkedQueue<>();

    /** 节流映射表（线程安全） */
    private final Map<String, Long> throttleMap = new ConcurrentHashMap<>();

    /** 当前帧索引 */
    private volatile long currentFrame = 0;

    /** 是否启用调试日志 */
    private volatile boolean debug;

    /** 是否启用节流机制 */
    private volatile boolean enableThrottle = true;

    /** 节流间隔（毫秒） */
    private volatile long throttleIntervalMs = 50;

    /** 是否合并相同属性的变更 */
    private volatile boolean mergeSameProperties = true;

    /** 事件计数器（用于生成唯一ID） */
    private final AtomicLong eventCounter = new AtomicLong(0);

    /** 统计：总调度次数 */
    private final AtomicLong totalScheduled = new AtomicLong(0);

    /** 统计：总派发次数 */
    private final AtomicLong totalDispatched = new AtomicLong(0);

    /** 统计：节流拦截次数 */
    private final AtomicLong throttleBlocked = new AtomicLong(0);

    /** 统计：合并次数 */
    private final AtomicLong mergedCount = new AtomicLong(0);

    /**
     * 私有构造函数
     */
    private PropertyChangeScheduler() {
        this.debug = Boolean.getBoolean("chronoblocks.debug.scheduler");
    }


    /**
     * 调度一个属性变更事件
     *
     * @param target      目标对象
     * @param property    属性名称
     * @param oldValue    旧值
     * @param newValue    新值
     * @param priority    优先级
     * @param delayFrames 延迟帧数（0表示立即派发）
     */
    public void schedule(Object target, String property, Object oldValue, Object newValue,
                         Priority priority, int delayFrames) {
        if (target == null || property == null) {
            return;
        }

        if (priority == null) {
            priority = Priority.NORMAL;
        }

        long id = eventCounter.incrementAndGet();
        long dispatchFrame = currentFrame + Math.max(0, delayFrames);

        ScheduledChange change = new ScheduledChange(
                id,
                target,
                property,
                oldValue,
                newValue,
                priority,
                dispatchFrame
        );

        pendingQueue.offer(change);
        totalScheduled.incrementAndGet();

        if (debug) {
            LOGGER.debug("[PropertyChangeScheduler] 调度事件: {}.{} 优先级={} 延迟={}帧",
                    target.getClass().getSimpleName(), property, priority, delayFrames);
        }
    }

    /**
     * 每帧调用一次，用于处理和派发符合时机的事件
     *
     * @return 本次派发的事件数量
     */
    public int tick() {
        currentFrame++;

        List<ScheduledChange> readyList = new ArrayList<>();
        int queueSize = pendingQueue.size();

        // 收集所有准备派发的事件
        for (int i = 0; i < queueSize; i++) {
            ScheduledChange change = pendingQueue.poll();
            if (change == null) {
                break;
            }

            // 检查是否到达派发时机
            if (change.dispatchFrame > currentFrame) {
                // 还没到时间，重新入队
                pendingQueue.offer(change);
                continue;
            }

            // 节流检测
            if (enableThrottle && !canDispatch(change)) {
                throttleBlocked.incrementAndGet();
                continue;
            }

            readyList.add(change);
        }

        // 按优先级排序（从高到低）
        readyList.sort(Comparator.comparingInt(c -> -c.priority.weight));

        // 派发事件
        int dispatched = 0;
        for (ScheduledChange change : readyList) {
            if (dispatchChange(change)) {
                dispatched++;
            }
        }

        if (debug && dispatched > 0) {
            LOGGER.debug("[PropertyChangeScheduler] 帧 {} 派发了 {} 个事件", currentFrame, dispatched);
        }

        return dispatched;
    }

    /**
     * 清空所有待调度事件
     */
    public void clear() {
        int cleared = pendingQueue.size();
        pendingQueue.clear();
        throttleMap.clear();

        if (cleared > 0) {
            LOGGER.info("[PropertyChangeScheduler] 已清空 {} 个待调度事件", cleared);
        }
    }


    // ==================== 内部逻辑 ====================

    /**
     * 派发单个变更
     *
     * @param change 变更对象
     * @return true 如果成功派发
     */
    private boolean dispatchChange(ScheduledChange change) {
        // 属性合并：如果队列中还有相同属性的更新，只派发最新的
        if (mergeSameProperties) {
            Optional<ScheduledChange> newerChange = findNewerChange(change);
            if (newerChange.isPresent()) {
                mergedCount.incrementAndGet();
                
                if (debug) {
                    LOGGER.trace("[PropertyChangeScheduler] 合并事件: {}.{}",
                            change.target.getClass().getSimpleName(), change.property);
                }
                
                return false;  // 跳过这个旧的变更
            }
        }

        // 实际派发到 Dispatcher
        try {
            PropertyChangeDispatcher.get().dispatchLater(
                    change.target,
                    change.property,
                    change.oldValue,
                    change.newValue
            );

            totalDispatched.incrementAndGet();

            if (debug) {
                LOGGER.debug("[PropertyChangeScheduler] 派发事件: {}.{} 优先级={} 帧={}",
                        change.target.getClass().getSimpleName(),
                        change.property,
                        change.priority,
                        currentFrame);
            }

            return true;

        } catch (Exception e) {
            LOGGER.error("[PropertyChangeScheduler] 派发事件失败: {}.{}",
                    change.target.getClass().getSimpleName(), change.property, e);
            return false;
        }
    }

    /**
     * 检查是否可以派发（节流检测）
     *
     * @param change 变更对象
     * @return true 如果可以派发
     */
    private boolean canDispatch(ScheduledChange change) {
        String key = change.target.getClass().getSimpleName() + "#" + change.property;
        long now = System.currentTimeMillis();
        Long lastDispatchTime = throttleMap.get(key);

        if (lastDispatchTime != null && (now - lastDispatchTime) < throttleIntervalMs) {
            if (debug) {
                LOGGER.trace("[PropertyChangeScheduler] 节流拦截: {}", key);
            }
            return false;
        }

        throttleMap.put(key, now);
        return true;
    }

    /**
     * 查找队列中更新的相同属性变更
     *
     * @param baseChange 基准变更
     * @return 更新的变更（如果存在）
     */
    private Optional<ScheduledChange> findNewerChange(ScheduledChange baseChange) {
        return pendingQueue.stream()
                .filter(c -> c.target == baseChange.target &&
                            c.property.equals(baseChange.property) &&
                            c.id > baseChange.id)
                .findFirst();
    }


    /**
     * 设置调试模式
     *
     * @param enabled true 启用，false 禁用
     */
    public void setDebug(boolean enabled) {
        this.debug = enabled;
        LOGGER.info("[PropertyChangeScheduler] 调试模式: {}", enabled ? "开启" : "关闭");
    }



    // ==================== 内部类 ====================

    /**
     * 调度变更（内部类）
     */
    private static class ScheduledChange {
        final long id;
        final Object target;
        final String property;
        final Object oldValue;
        final Object newValue;
        final Priority priority;
        final long dispatchFrame;

        ScheduledChange(long id, Object target, String property,
                       Object oldValue, Object newValue,
                       Priority priority, long dispatchFrame) {
            this.id = id;
            this.target = target;
            this.property = property;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.priority = priority;
            this.dispatchFrame = dispatchFrame;
        }
    }

}
