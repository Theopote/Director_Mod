package com.chronoblocks.ui.properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 属性适配器注册中心 - ChronoBlocks Properties System v4.5（单 Registry 唯一入口）
 *
 * <p><b>本类为属性适配器唯一注册中心。</b> 所有新 Adapter（含 Tool）只能通过
 * {@link PropertyAdapterBootstrap} 注册于此。{@code ui.adapters.LegacyPropertyAdapterRegistry}
 * 已冻结，勿向该处注册。
 *
 * <p>管理所有属性适配器的注册和查找，使用线程安全的设计，支持运行时动态注册。
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
        // 护栏：禁止 ui.adapters（Legacy）包下的适配器注册到此 Registry
        String pkg = adapter.getClass().getPackage() != null ? adapter.getClass().getPackage().getName() : "";
        if (pkg.equals("com.chronoblocks.ui.adapters") || pkg.startsWith("com.chronoblocks.ui.adapters.")) {
            throw new IllegalStateException("Legacy adapter registration is forbidden: "
                + adapter.getClass().getName() + " is in " + pkg + ". Use com.chronoblocks.ui.properties.PropertyAdapterRegistry only.");
        }
        
        adapters.put(type, adapter);
        LOGGER.debug("[PropertyAdapterRegistry] Registered adapter: {} → {}", 
            adapter.getClass().getSimpleName(), type.getSimpleName());
    }
    
    /**
     * 获取指定对象的属性适配器（不按 Context 过滤，等价于 {@code getAdapterFor(null, target)}）
     *
     * @param target 目标对象
     * @param <T> 对象类型
     * @return 适配器实例，如果未找到则返回 null
     */
    public static <T> IPropertyAdapter<T> getAdapterFor(T target) {
        return getAdapterFor(null, target);
    }

    /**
     * 获取指定上下文与目标对应的属性适配器
     *
     * <p>查找策略：
     * <ol>
     *   <li>精确匹配 target 的 Class，且 {@code supports(ctx,target)} 为 true → 直接返回</li>
     *   <li>收集所有通过继承或 supports 匹配且 {@code supports(ctx,target)} 为 true 的适配器，按 {@link IPropertyAdapter#getPriority()} 取最大者</li>
     *   <li>若未找到，使用回退适配器（若有且 supports 通过）</li>
     *   <li>否则返回 null</li>
     * </ol>
     * <p>当 {@code ctx == null} 时，使用 {@code supports(target)} 代替 {@code supports(ctx, target)}。
     *
     * @param ctx   当前 PropertyContext，可为 null
     * @param target 目标对象
     * @param <T> 对象类型
     * @return 适配器实例，如果未找到则返回 null
     */
    @SuppressWarnings("unchecked")
    public static <T> IPropertyAdapter<T> getAdapterFor(PropertyContext ctx, T target) {
        if (target == null) {
            LOGGER.trace("[PropertyAdapterRegistry] Target is null, returning null adapter");
            return null;
        }
        boolean useCtx = (ctx != null);

        Class<?> targetClass = target.getClass();

        // 1. 精确匹配
        IPropertyAdapter<?> adapter = adapters.get(targetClass);
        if (adapter != null) {
            if (useCtx ? adapter.supports(ctx, target) : adapter.supports(target)) {
                LOGGER.trace("[PropertyAdapterRegistry] Found exact match for {} (ctx={})", targetClass.getSimpleName(), ctx);
                return (IPropertyAdapter<T>) adapter;
            }
        }

        // 2. 兼容匹配：继承或 supports，且 supports(ctx,target)，再按 priority 择优
        List<IPropertyAdapter<?>> candidates = new ArrayList<>();
        for (Map.Entry<Class<?>, IPropertyAdapter<?>> entry : adapters.entrySet()) {
            Class<?> registeredType = entry.getKey();
            IPropertyAdapter<?> entryAdapter = entry.getValue();
            if (registeredType == targetClass) continue;
            if (!(registeredType.isAssignableFrom(targetClass) || entryAdapter.supports(target))) continue;
            if (!(!useCtx || entryAdapter.supports(ctx, target))) continue;
            candidates.add(entryAdapter);
        }
        if (!candidates.isEmpty()) {
            IPropertyAdapter<?> best = candidates.getFirst();
            int bestPriority = best.getPriority();
            for (int i = 1; i < candidates.size(); i++) {
                IPropertyAdapter<?> a = candidates.get(i);
                if (a.getPriority() > bestPriority) {
                    bestPriority = a.getPriority();
                    best = a;
                }
            }
            LOGGER.trace("[PropertyAdapterRegistry] Found adapter by priority: {} for {} (priority={}, ctx={})",
                best.getClass().getSimpleName(), targetClass.getSimpleName(), bestPriority, ctx);
            return (IPropertyAdapter<T>) best;
        }

        // 3. 回退适配器
        if (fallbackAdapter != null) {
            if (!useCtx || fallbackAdapter.supports(ctx, target)) {
                LOGGER.debug("[PropertyAdapterRegistry] Using fallback adapter for {} (ctx={})", targetClass.getSimpleName(), ctx);
                return (IPropertyAdapter<T>) fallbackAdapter;
            }
        }

        LOGGER.debug("[PropertyAdapterRegistry] No adapter found for {} (ctx={})", targetClass.getSimpleName(), ctx);
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
        adapters.forEach((type, adapter) -> sb.append("  - ").append(type.getSimpleName())
          .append(" → ").append(adapter.getClass().getSimpleName())
          .append("\n"));
        if (fallbackAdapter != null) {
            sb.append("  Fallback: ").append(fallbackAdapter.getClass().getSimpleName()).append("\n");
        }
        return sb.toString();
    }
}

