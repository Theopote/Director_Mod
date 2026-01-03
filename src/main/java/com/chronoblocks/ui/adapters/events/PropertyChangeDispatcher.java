package com.chronoblocks.ui.adapters.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 🧩 PropertyChangeDispatcher - 属性事件异步调度器
 * -------------------------------------
 * 属性事件调度器，用于跨线程安全地将属性更新事件
 * 转发到主线程（游戏 / 渲染 / 编辑循环）。
 *
 * <h3>✨ 核心功能：</h3>
 * <ul>
 *     <li>🧵 跨线程安全：允许任意线程调用属性变更，但保证事件在"主帧"线程统一执行</li>
 *     <li>⏳ 延迟同步：可批量缓存事件，在下一次 tick 循环时派发</li>
 *     <li>🔧 可配置策略：可选择立即派发 / 批量延迟派发模式</li>
 *     <li>🧠 无缝集成：与 {@link PropertyChangeNotifier} 无缝集成</li>
 *     <li>🧹 自动清理：在关闭或刷新时清空待派发队列</li>
 * </ul>
 *
 * <h3>📖 使用示例：</h3>
 * <pre>{@code
 * // 1. 在 UI Adapter 中（ImGui 编辑）
 * float[] pos = target.getPosition();
 * Vector3f oldPos = new Vector3f(pos);
 * if (ImGui.dragFloat3("Position", pos, 0.1f)) {
 *     target.setPosition(pos);
 *     // 延迟派发（批量模式）
 *     PropertyChangeDispatcher.get().dispatchLater(
 *         target, "position", oldPos, new Vector3f(pos));
 * }
 *
 * // 2. 在主游戏循环中（每帧调用一次）
 * @Override
 * public void tick(float deltaTime) {
 *     // 刷新所有待派发的事件
 *     PropertyChangeDispatcher.get().flush();
 * }
 *
 * // 3. 监听事件
 * PropertyChangeNotifier.get().addTypeListener(Container.class, event -> {
 *     if (event.getPropertyName().startsWith("position")) {
 *         renderer.updateContainerPosition(event.getTargetAs(Container.class));
 *     }
 * });
 * }</pre>
 *
 * <h3>🔧 派发模式：</h3>
 * <table border="1" cellpadding="5">
 *   <tr>
 *     <th>模式</th>
 *     <th>方法</th>
 *     <th>时机</th>
 *     <th>用途</th>
 *   </tr>
 *   <tr>
 *     <td>立即派发</td>
 *     <td>{@link #dispatchNow}</td>
 *     <td>立即</td>
 *     <td>UI同线程、紧急更新</td>
 *   </tr>
 *   <tr>
 *     <td>延迟派发</td>
 *     <td>{@link #dispatchLater}</td>
 *     <td>下一帧</td>
 *     <td>跨线程、批量更新</td>
 *   </tr>
 * </table>
 *
 * @author ChronoBlocks Team
 * @version 4.5
 * @since 4.5
 */
public class PropertyChangeDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyChangeDispatcher.class);

    // ==================== 单例实例 ====================

    private static final PropertyChangeDispatcher INSTANCE = new PropertyChangeDispatcher();

    /**
     * 获取单例实例
     *
     * @return 调度器实例
     */
    public static PropertyChangeDispatcher get() {
        return INSTANCE;
    }

    // ==================== 内部数据结构 ====================

    /** 待派发事件队列（线程安全） */
    private final Queue<DispatchRequest> pendingQueue = new ConcurrentLinkedQueue<>();

    /** 是否启用批量模式 */
    private volatile boolean batchMode = true;

    /** 是否启用调试日志 */
    private volatile boolean debug;

    /** 统计：总派发次数 */
    private final AtomicLong totalDispatched = new AtomicLong(0);

    /** 统计：立即派发次数 */
    private final AtomicLong immediateDispatches = new AtomicLong(0);

    /** 统计：延迟派发次数 */
    private final AtomicLong deferredDispatches = new AtomicLong(0);

    /** 统计：批量刷新次数 */
    private final AtomicLong flushCount = new AtomicLong(0);

    /**
     * 私有构造函数
     */
    private PropertyChangeDispatcher() {
        this.debug = Boolean.getBoolean("chronoblocks.debug.dispatcher");
    }

    // ==================== 调度接口 ====================

    /**
     * 立即派发事件（线程安全）
     * <p>直接调用 {@link PropertyChangeNotifier} 通知，适用于 UI 同线程场景。
     *
     * @param target       目标对象
     * @param propertyName 属性名称
     * @param oldValue     旧值
     * @param newValue     新值
     */
    public void dispatchNow(Object target, String propertyName, Object oldValue, Object newValue) {
        if (target == null || propertyName == null) {
            return;
        }

        if (debug) {
            LOGGER.debug("[PropertyChangeDispatcher] 立即派发: {}.{}",
                    target.getClass().getSimpleName(), propertyName);
        }

        PropertyChangeNotifier.get().notifyChange(target, propertyName, oldValue, newValue);
        
        immediateDispatches.incrementAndGet();
        totalDispatched.incrementAndGet();
    }

    /**
     * 延迟派发事件（线程安全）
     * <p>在下一帧 {@link #flush()} 时统一分发。
     *
     * @param target       目标对象
     * @param propertyName 属性名称
     * @param oldValue     旧值
     * @param newValue     新值
     */
    public void dispatchLater(Object target, String propertyName, Object oldValue, Object newValue) {
        if (target == null || propertyName == null) {
            return;
        }

        DispatchRequest request = new DispatchRequest(target, propertyName, oldValue, newValue);
        pendingQueue.offer(request);

        if (debug) {
            LOGGER.trace("[PropertyChangeDispatcher] 队列事件: {}.{} (队列大小: {})",
                    target.getClass().getSimpleName(), propertyName, pendingQueue.size());
        }

        // 非批量模式下立即刷新
        if (!batchMode) {
            flush();
        }
    }

    /**
     * 刷新事件队列
     * <p>应在主线程每帧调用一次，派发所有待处理的事件。
     *
     * @return 本次刷新派发的事件数量
     */
    public int flush() {
        int count = 0;
        DispatchRequest request;

        while ((request = pendingQueue.poll()) != null) {
            if (debug) {
                LOGGER.debug("[PropertyChangeDispatcher] 刷新事件: {}.{}",
                        request.target.getClass().getSimpleName(), request.property);
            }

            try {
                PropertyChangeNotifier.get().notifyChange(
                        request.target,
                        request.property,
                        request.oldValue,
                        request.newValue
                );
                
                deferredDispatches.incrementAndGet();
                totalDispatched.incrementAndGet();
                count++;
                
            } catch (Exception e) {
                LOGGER.error("[PropertyChangeDispatcher] 派发事件失败: {}.{}",
                        request.target.getClass().getSimpleName(), request.property, e);
            }
        }

        if (count > 0) {
            flushCount.incrementAndGet();
            
            if (debug) {
                LOGGER.debug("[PropertyChangeDispatcher] 本次刷新派发 {} 个事件", count);
            }
        }

        return count;
    }

    /**
     * 清空所有未派发事件
     */
    public void clear() {
        int cleared = pendingQueue.size();
        pendingQueue.clear();

        if (cleared > 0) {
            LOGGER.info("[PropertyChangeDispatcher] 已清空 {} 个待派发事件", cleared);
        }
    }


    /**
     * 启用/禁用调试日志
     *
     * @param enabled 是否启用
     */
    public void setDebug(boolean enabled) {
        this.debug = enabled;
        LOGGER.info("[PropertyChangeDispatcher] 调试模式: {}", enabled ? "开启" : "关闭");
    }


    // ==================== 内部类 ====================

    /**
     * 派发请求（内部类）
     */
    private static class DispatchRequest {
        final Object target;
        final String property;
        final Object oldValue;
        final Object newValue;

        DispatchRequest(Object target, String property, Object oldValue, Object newValue) {
            this.target = target;
            this.property = property;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
    }

}

