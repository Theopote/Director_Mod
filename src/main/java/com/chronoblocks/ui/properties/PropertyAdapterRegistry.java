package com.chronoblocks.ui.properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 属性适配器注册中心 - ChronoBlocks Properties System v4.5
 * 
 * <p>管理所有属性适配器的注册和查找。
 * 使用线程安全的设计，支持运行时动态注册。
 * 
 * <h3>功能特性</h3>
 * <ul>
 *   <li>类型安全的适配器注册</li>
 *   <li>支持继承关系的类型匹配</li>
 *   <li>线程安全的并发访问</li>
 *   <li>详细的调试信息</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 注册适配器
 * PropertyAdapterRegistry.registerAdapter(Keyframe.class, new KeyframePropertyAdapter());
 * 
 * // 获取适配器
 * Keyframe kf = ...;
 * IPropertyAdapter<Keyframe> adapter = PropertyAdapterRegistry.getAdapterFor(kf);
 * if (adapter != null) {
 *     adapter.renderProperties(kf);
 * }
 * }</pre>
 * 
 * @version 4.5
 * @since 2025-10-15
 */
public final class PropertyAdapterRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyAdapterRegistry.class);
    
    // 使用 ConcurrentHashMap 保证线程安全
    private static final Map<Class<?>, IPropertyAdapter<?>> adapters = new ConcurrentHashMap<>();
    
    // 回退适配器（用于未注册类型）
    private static IPropertyAdapter<Object> fallbackAdapter = null;
    
    // 私有构造函数，防止实例化
    private PropertyAdapterRegistry() {}
    
    /**
     * 注册属性适配器
     * 
     * @param type 目标类型
     * @param adapter 适配器实例
     * @param <T> 类型参数
     * @throws IllegalArgumentException 如果 type 或 adapter 为 null
     */
    public static <T> void registerAdapter(Class<T> type, IPropertyAdapter<T> adapter) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }
        if (adapter == null) {
            throw new IllegalArgumentException("Adapter cannot be null");
        }
        
        adapters.put(type, adapter);
        LOGGER.debug("[PropertyAdapterRegistry] Registered adapter: {} → {}", 
            adapter.getClass().getSimpleName(), type.getSimpleName());
    }
    
    /**
     * 获取指定对象的属性适配器
     * 
     * <p>查找策略：
     * <ol>
     *   <li>首先尝试精确匹配对象的类型</li>
     *   <li>然后尝试匹配对象的父类和接口</li>
     *   <li>如果都找不到，返回 null</li>
     * </ol>
     * 
     * @param target 目标对象
     * @param <T> 对象类型
     * @return 适配器实例，如果未找到则返回 null
     */
    @SuppressWarnings("unchecked")
    public static <T> IPropertyAdapter<T> getAdapterFor(T target) {
        if (target == null) {
            LOGGER.trace("[PropertyAdapterRegistry] Target is null, returning null adapter");
            return null;
        }
        
        Class<?> targetClass = target.getClass();
        
        // 1. 精确匹配
        IPropertyAdapter<?> adapter = adapters.get(targetClass);
        if (adapter != null) {
            LOGGER.trace("[PropertyAdapterRegistry] Found exact match for {}", targetClass.getSimpleName());
            return (IPropertyAdapter<T>) adapter;
        }
        
        // 2. 查找兼容的适配器（支持继承和 supports() 方法）
        for (Map.Entry<Class<?>, IPropertyAdapter<?>> entry : adapters.entrySet()) {
            // 检查继承关系
            if (entry.getKey().isAssignableFrom(targetClass)) {
                LOGGER.trace("[PropertyAdapterRegistry] Found compatible adapter: {} for {}", 
                    entry.getKey().getSimpleName(), targetClass.getSimpleName());
                return (IPropertyAdapter<T>) entry.getValue();
            }
            
            // 检查 supports() 方法
            IPropertyAdapter<?> entryAdapter = entry.getValue();
            if (entryAdapter.supports(target)) {
                LOGGER.trace("[PropertyAdapterRegistry] Found adapter via supports(): {} for {}", 
                    entryAdapter.getClass().getSimpleName(), targetClass.getSimpleName());
                return (IPropertyAdapter<T>) entryAdapter;
            }
        }
        
        // 3. 使用回退适配器（如果有）
        if (fallbackAdapter != null) {
            LOGGER.debug("[PropertyAdapterRegistry] Using fallback adapter for {}", targetClass.getSimpleName());
            return (IPropertyAdapter<T>) fallbackAdapter;
        }
        
        LOGGER.debug("[PropertyAdapterRegistry] No adapter found for {}", targetClass.getSimpleName());
        return null;
    }
    
    /**
     * 获取指定类型的属性适配器
     * 
     * @param type 目标类型
     * @param <T> 类型参数
     * @return 适配器实例，如果未找到则返回 null
     */
    @SuppressWarnings("unchecked")
    public static <T> IPropertyAdapter<T> getAdapterForType(Class<T> type) {
        if (type == null) {
            return null;
        }
        return (IPropertyAdapter<T>) adapters.get(type);
    }
    
    /**
     * 检查是否已注册指定类型的适配器
     * 
     * @param type 要检查的类型
     * @return 如果已注册则返回 true
     */
    public static boolean hasAdapter(Class<?> type) {
        return type != null && adapters.containsKey(type);
    }
    
    /**
     * 移除指定类型的适配器
     * 
     * @param type 要移除的类型
     * @param <T> 类型参数
     * @return 被移除的适配器，如果不存在则返回 null
     */
    @SuppressWarnings("unchecked")
    public static <T> IPropertyAdapter<T> removeAdapter(Class<T> type) {
        if (type == null) {
            return null;
        }
        IPropertyAdapter<?> removed = adapters.remove(type);
        if (removed != null) {
            LOGGER.info("[PropertyAdapterRegistry] Removed adapter for {}", type.getSimpleName());
        }
        return (IPropertyAdapter<T>) removed;
    }
    
    /**
     * 清除所有已注册的适配器
     */
    public static void clear() {
        int count = adapters.size();
        adapters.clear();
        LOGGER.info("[PropertyAdapterRegistry] Cleared {} adapters", count);
    }
    
    /**
     * 获取已注册的适配器数量
     * 
     * @return 适配器数量
     */
    public static int getAdapterCount() {
        return adapters.size();
    }
    
    /**
     * 设置回退适配器
     * 
     * <p>当找不到特定类型的适配器时，将使用回退适配器。
     * 通常设置为 GenericPropertyAdapter。
     * 
     * @param adapter 回退适配器
     */
    public static void setFallbackAdapter(IPropertyAdapter<Object> adapter) {
        fallbackAdapter = adapter;
        if (adapter != null) {
            LOGGER.info("[PropertyAdapterRegistry] 已设置回退适配器: {}", 
                adapter.getClass().getSimpleName());
        } else {
            LOGGER.info("[PropertyAdapterRegistry] 已清除回退适配器");
        }
    }
    
    /**
     * 获取回退适配器
     * 
     * @return 回退适配器，如果未设置则返回 null
     */
    public static IPropertyAdapter<Object> getFallbackAdapter() {
        return fallbackAdapter;
    }
    
    /**
     * 检查是否设置了回退适配器
     * 
     * @return 如果设置了回退适配器则返回 true
     */
    public static boolean hasFallbackAdapter() {
        return fallbackAdapter != null;
    }
    
    /**
     * 获取调试信息
     * 
     * @return 包含所有已注册适配器信息的字符串
     */
    public static String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("PropertyAdapterRegistry [").append(adapters.size()).append(" adapters]:\n");
        adapters.forEach((type, adapter) -> {
            sb.append("  - ").append(type.getSimpleName())
              .append(" → ").append(adapter.getClass().getSimpleName())
              .append("\n");
        });
        if (fallbackAdapter != null) {
            sb.append("  Fallback: ").append(fallbackAdapter.getClass().getSimpleName()).append("\n");
        }
        return sb.toString();
    }
}

