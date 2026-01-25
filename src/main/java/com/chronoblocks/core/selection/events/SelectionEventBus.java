package com.chronoblocks.core.selection.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 🎯 选择事件总线
 * <p>
 * 负责管理选择相关事件的发布、订阅和处理。
 * 支持事件优先级、事件过滤和异步处理。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class SelectionEventBus {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionEventBus.class);
    
    /** 单例实例 */
    private static volatile SelectionEventBus INSTANCE;
    
    /** 事件监听器映射表 */
    private final Map<Class<? extends SelectionEvent>, List<EventListener<? extends SelectionEvent>>> listeners = new ConcurrentHashMap<>();
    
    /** 全局事件监听器 */
    private final List<EventListener<SelectionEvent>> globalListeners = new CopyOnWriteArrayList<>();
    
    /** 事件过滤器 */
    private final List<EventFilter> filters = new CopyOnWriteArrayList<>();
    
    /** 是否启用调试模式 */
    private final boolean debug = Boolean.getBoolean("chronoblocks.selection.events.debug");
    
    private SelectionEventBus() {
        LOGGER.info("[SelectionEventBus] 事件总线初始化完成");
    }
    
    /**
     * 获取单例实例
     */
    public static SelectionEventBus getInstance() {
        if (INSTANCE == null) {
            synchronized (SelectionEventBus.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SelectionEventBus();
                }
            }
        }
        return INSTANCE;
    }
    
    /**
     * 订阅特定类型的事件
     */
    public <T extends SelectionEvent> void subscribe(Class<T> eventType, Consumer<T> listener) {
        subscribe(eventType, listener, 100); // 默认优先级
    }
    
    /**
     * 订阅特定类型的事件（带优先级）
     */
    public <T extends SelectionEvent> void subscribe(Class<T> eventType, Consumer<T> listener, int priority) {
        EventListener<T> eventListener = new EventListener<>(listener, priority);
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(eventListener);
        
        // 按优先级排序
        List<EventListener<? extends SelectionEvent>> eventListeners = listeners.get(eventType);
        eventListeners.sort(Comparator.comparingInt(EventListener::getPriority));
        
        if (debug) {
            LOGGER.debug("[SelectionEventBus] 订阅事件: {} 优先级: {}", eventType.getSimpleName(), priority);
        }
    }
    
    /**
     * 订阅所有事件
     */
    public void subscribeGlobal(Consumer<SelectionEvent> listener) {
        subscribeGlobal(listener, 100); // 默认优先级
    }
    
    /**
     * 订阅所有事件（带优先级）
     */
    public void subscribeGlobal(Consumer<SelectionEvent> listener, int priority) {
        EventListener<SelectionEvent> eventListener = new EventListener<>(listener, priority);
        globalListeners.add(eventListener);
        
        // 按优先级排序
        globalListeners.sort(Comparator.comparingInt(EventListener::getPriority));
        
        if (debug) {
            LOGGER.debug("[SelectionEventBus] 订阅全局事件 优先级: {}", priority);
        }
    }
    
    /**
     * 取消订阅事件
     */
    public <T extends SelectionEvent> void unsubscribe(Class<T> eventType, Consumer<T> listener) {
        List<EventListener<? extends SelectionEvent>> eventListeners = listeners.get(eventType);
        if (eventListeners != null) {
            eventListeners.removeIf(el -> el.getListener() == listener);
            
            if (debug) {
                LOGGER.debug("[SelectionEventBus] 取消订阅事件: {}", eventType.getSimpleName());
            }
        }
    }
    
    /**
     * 取消订阅全局事件
     */
    public void unsubscribeGlobal(Consumer<SelectionEvent> listener) {
        globalListeners.removeIf(el -> el.getListener() == listener);
        
        if (debug) {
            LOGGER.debug("[SelectionEventBus] 取消订阅全局事件");
        }
    }
    
    /**
     * 添加事件过滤器
     */
    public void addFilter(EventFilter filter) {
        filters.add(filter);
        
        if (debug) {
            LOGGER.debug("[SelectionEventBus] 添加事件过滤器: {}", filter.getClass().getSimpleName());
        }
    }
    
    /**
     * 移除事件过滤器
     */
    public void removeFilter(EventFilter filter) {
        filters.remove(filter);
        
        if (debug) {
            LOGGER.debug("[SelectionEventBus] 移除事件过滤器: {}", filter.getClass().getSimpleName());
        }
    }
    
    /**
     * 发布事件
     */
    public void publish(SelectionEvent event) {
        if (event == null) {
            LOGGER.warn("[SelectionEventBus] 尝试发布空事件");
            return;
        }
        
        if (event.isConsumed()) {
            if (debug) {
                LOGGER.debug("[SelectionEventBus] 事件已被消费，跳过处理: {}", event);
            }
            return;
        }
        
        // 应用事件过滤器
        for (EventFilter filter : filters) {
            if (!filter.shouldProcess(event)) {
                if (debug) {
                    LOGGER.debug("[SelectionEventBus] 事件被过滤器阻止: {}", event);
                }
                return;
            }
        }
        
        if (debug) {
            LOGGER.debug("[SelectionEventBus] 发布事件: {}", event);
        }
        
        // 处理全局监听器
        for (EventListener<SelectionEvent> listener : globalListeners) {
            try {
                listener.handle(event);
            } catch (Exception e) {
                LOGGER.error("[SelectionEventBus] 全局事件监听器执行失败", e);
            }
        }
        
        // 处理特定类型监听器
        List<EventListener<? extends SelectionEvent>> eventListeners = listeners.get(event.getClass());
        if (eventListeners != null) {
            for (EventListener<? extends SelectionEvent> listener : eventListeners) {
                try {
                    listener.handle(event);
                } catch (Exception e) {
                    LOGGER.error("[SelectionEventBus] 事件监听器执行失败: {}", event.getClass().getSimpleName(), e);
                }
            }
        }
    }
    
    /**
     * 清空所有监听器
     */
    public void clear() {
        listeners.clear();
        globalListeners.clear();
        filters.clear();
        
        if (debug) {
            LOGGER.debug("[SelectionEventBus] 清空所有监听器和过滤器");
        }
    }
    
    /**
     * 获取监听器统计信息
     */
    public Map<String, Integer> getListenerStats() {
        Map<String, Integer> stats = new HashMap<>();
        
        for (Map.Entry<Class<? extends SelectionEvent>, List<EventListener<? extends SelectionEvent>>> entry : listeners.entrySet()) {
            stats.put(entry.getKey().getSimpleName(), entry.getValue().size());
        }
        
        stats.put("Global", globalListeners.size());
        stats.put("Filters", filters.size());
        
        return stats;
    }
    
    /**
     * 事件监听器包装类
     */
    private static class EventListener<T extends SelectionEvent> {
        private final Consumer<T> listener;
        private final int priority;
        
        public EventListener(Consumer<T> listener, int priority) {
            this.listener = listener;
            this.priority = priority;
        }
        
        public void handle(SelectionEvent event) {
            @SuppressWarnings("unchecked")
            T typedEvent = (T) event;
            listener.accept(typedEvent);
        }
        
        public Consumer<T> getListener() {
            return listener;
        }
        
        public int getPriority() {
            return priority;
        }
    }
    
    /**
     * 事件过滤器接口
     */
    public interface EventFilter {
        /**
         * 判断是否应该处理该事件
         */
        boolean shouldProcess(SelectionEvent event);
    }
}
