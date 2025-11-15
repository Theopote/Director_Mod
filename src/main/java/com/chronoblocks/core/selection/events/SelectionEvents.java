package com.chronoblocks.core.selection.events;

import com.chronoblocks.core.selection.SelectionChangeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/**
 * 🛰️ SelectionEvents
 * 
 * —— ChronoBlocks 全局选区事件总线
 * 
 * 用于：
 *  - 通知 SelectionRenderer、GizmoRenderer、UI 面板等刷新状态；
 *  - 监听选区变化（更新历史、预览、动画状态等）；
 *  - 解耦 SelectionManager 与渲染/UI 层。
 * 
 * 使用示例：
 * <pre>{@code
 * // 注册监听器
 * SelectionEvents.register(type -> {
 *     if (type == SelectionChangeType.SELECTION_UPDATED) {
 *         updateUI();
 *     }
 * });
 * 
 * // 触发事件
 * SelectionEvents.fireChanged(SelectionChangeType.SELECTION_UPDATED);
 * }</pre>
 *
 * @author ChronoBlocks
 * @version 1.0
 */
public final class SelectionEvents {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionEvents.class);
    
    /** 全局监听器集合（线程安全） */
    private static final Set<Consumer<SelectionChangeType>> LISTENERS = new CopyOnWriteArraySet<>();
    
    private SelectionEvents() {}
    
    /**
     * 注册监听器
     * 
     * @param listener 选区变化监听器
     */
    public static void register(Consumer<SelectionChangeType> listener) {
        if (listener == null) return;
        LISTENERS.add(listener);
        LOGGER.debug("[SelectionEvents] 注册监听器：{}", listener);
    }
    
    /**
     * 移除监听器
     * 
     * @param listener 要移除的监听器
     */
    public static void unregister(Consumer<SelectionChangeType> listener) {
        if (listener == null) return;
        LISTENERS.remove(listener);
        LOGGER.debug("[SelectionEvents] 移除监听器：{}", listener);
    }
    
    /**
     * 广播事件（例如 Selection 更新 / 清空）
     * 
     * @param type 选区变化类型
     */
    public static void fireChanged(SelectionChangeType type) {
        if (type == null) return;
        
        LOGGER.debug("[SelectionEvents] 广播事件：{}", type);
        
        for (Consumer<SelectionChangeType> listener : LISTENERS) {
            try {
                listener.accept(type);
            } catch (Exception e) {
                LOGGER.warn("[SelectionEvents] 监听器执行错误：{}", listener, e);
            }
        }
    }
    
    /**
     * 清除所有监听器（调试/卸载用）
     */
    public static void clearAll() {
        LISTENERS.clear();
        LOGGER.debug("[SelectionEvents] 已清空所有监听器");
    }
    
    /**
     * 获取当前监听器数量
     * 
     * @return 监听器数量
     */
    public static int getListenerCount() {
        return LISTENERS.size();
    }
}
