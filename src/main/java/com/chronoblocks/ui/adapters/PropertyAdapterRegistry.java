package com.chronoblocks.ui.adapters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 📚 PropertyAdapterRegistry - 属性适配器注册表
 *
 * <p>ChronoBlocks 属性系统的核心管理器，负责所有属性适配器的注册、查找和生命周期管理。
 *
 * <h3>✨ 核心功能：</h3>
 * <ul>
 *     <li>适配器注册：支持单个或批量注册</li>
 *     <li>智能查找：基于类型匹配和优先级排序</li>
 *     <li>继承支持：自动匹配父类和接口的适配器</li>
 *     <li>缓存优化：缓存查找结果以提升性能</li>
 *     <li>线程安全：使用 ConcurrentHashMap 确保并发安全</li>
 *     <li>生命周期管理：自动调用适配器的初始化和清理方法</li>
 * </ul>
 *
 * <h3>📖 使用示例：</h3>
 * <pre>{@code
 * // 获取单例
 * PropertyAdapterRegistry registry = PropertyAdapterRegistry.get();
 *
 * // 注册适配器
 * registry.register(new KeyframeAdapter());
 * registry.register(new ContainerAdapter());
 *
 * // 查找适配器
 * PropertyAdapter<Keyframe> adapter = registry.findAdapter(Keyframe.class);
 *
 * // 批量注册
 * registry.registerAll(
 *     new KeyframeAdapter(),
 *     new ContainerAdapter(),
 *     new EffectAdapter()
 * );
 *
 * // 清理所有适配器
 * registry.clear();
 * }</pre>
 *
 * <h3>🔧 高级特性：</h3>
 * <ul>
 *     <li>优先级系统：支持多个适配器竞争同一类型</li>
 *     <li>动态注册：运行时动态添加/移除适配器</li>
 *     <li>查找缓存：自动缓存查找结果以优化性能</li>
 *     <li>统计信息：提供注册表统计和调试信息</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 4.5
 * @since 4.5
 */
public class PropertyAdapterRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyAdapterRegistry.class);

    /** 单例实例 */
    private static volatile PropertyAdapterRegistry instance;

    /** 适配器存储：类型 -> 适配器列表（按优先级排序） */
    private final Map<Class<?>, List<PropertyAdapter<?>>> adapters;

    /** 查找缓存：类型 -> 最佳适配器 */
    private final Map<Class<?>, PropertyAdapter<?>> adapterCache;

    /** 所有已注册的适配器集合（用于生命周期管理） */
    private final List<PropertyAdapter<?>> allAdapters;

    /** 注册表统计信息 */
    private final RegistryStatistics statistics;

    /**
     * 私有构造函数（单例模式）
     */
    private PropertyAdapterRegistry() {
        this.adapters = new ConcurrentHashMap<>();
        this.adapterCache = new ConcurrentHashMap<>();
        this.allAdapters = new CopyOnWriteArrayList<>();
        this.statistics = new RegistryStatistics();
        
        LOGGER.info("[PropertyAdapterRegistry] 属性适配器注册表已初始化");
    }

    /**
     * 获取单例实例
     *
     * @return 注册表实例
     */
    public static PropertyAdapterRegistry get() {
        if (instance == null) {
            synchronized (PropertyAdapterRegistry.class) {
                if (instance == null) {
                    instance = new PropertyAdapterRegistry();
                }
            }
        }
        return instance;
    }

    /**
     * 注册单个适配器
     * <p>自动调用适配器的 {@link PropertyAdapter#initialize()} 方法。
     *
     * @param adapter 要注册的适配器
     * @param <T>     适配器支持的类型
     * @return 当前注册表实例（支持链式调用）
     */
    public <T> PropertyAdapterRegistry register(PropertyAdapter<T> adapter) {
        if (adapter == null) {
            LOGGER.warn("[PropertyAdapterRegistry] 尝试注册空适配器");
            return this;
        }

        // 检查适配器是否启用
        if (!adapter.isEnabled()) {
            LOGGER.debug("[PropertyAdapterRegistry] 跳过已禁用的适配器: {}", adapter.getDisplayName());
            return this;
        }

        Class<T> targetType = adapter.getTargetType();
        
        // 检查是否已注册
        if (isAlreadyRegistered(adapter)) {
            LOGGER.debug("[PropertyAdapterRegistry] 适配器已注册: {}", adapter.getDisplayName());
            return this;
        }

        // 添加到存储
        adapters.computeIfAbsent(targetType, k -> new CopyOnWriteArrayList<>()).add(adapter);
        allAdapters.add(adapter);

        // 排序（按优先级降序）
        sortAdapters(targetType);

        // 清除相关缓存
        invalidateCache(targetType);

        // 初始化适配器
        try {
            adapter.initialize();
            statistics.recordRegistration(targetType, adapter);
            LOGGER.info("[PropertyAdapterRegistry] 已注册适配器: {} (类型: {}, 优先级: {})",
                    adapter.getDisplayName(), targetType.getSimpleName(), adapter.getPriority());
        } catch (Exception e) {
            LOGGER.error("[PropertyAdapterRegistry] 适配器初始化失败: {}", adapter.getDisplayName(), e);
            // 初始化失败，移除适配器
            unregister(adapter);
        }

        return this;
    }

    /**
     * 批量注册适配器
     *
     * @param adapters 适配器数组
     * @return 当前注册表实例（支持链式调用）
     */
    @SafeVarargs
    public final PropertyAdapterRegistry registerAll(PropertyAdapter<?>... adapters) {
        for (PropertyAdapter<?> adapter : adapters) {
            register(adapter);
        }
        return this;
    }

    /**
     * 批量注册适配器
     *
     * @param adapters 适配器集合
     * @return 当前注册表实例（支持链式调用）
     */
    public PropertyAdapterRegistry registerAll(Collection<? extends PropertyAdapter<?>> adapters) {
        adapters.forEach(this::register);
        return this;
    }

    /**
     * 注销适配器
     * <p>自动调用适配器的 {@link PropertyAdapter#dispose()} 方法。
     *
     * @param adapter 要注销的适配器
     * @param <T>     适配器支持的类型
     * @return true 如果成功注销
     */
    public <T> boolean unregister(PropertyAdapter<T> adapter) {
        if (adapter == null) {
            return false;
        }

        Class<T> targetType = adapter.getTargetType();
        List<PropertyAdapter<?>> list = adapters.get(targetType);

        boolean removed = false;
        if (list != null) {
            removed = list.remove(adapter);
            if (list.isEmpty()) {
                adapters.remove(targetType);
            }
        }

        allAdapters.remove(adapter);
        invalidateCache(targetType);

        if (removed) {
            try {
                adapter.dispose();
                statistics.recordUnregistration(targetType, adapter);
                LOGGER.info("[PropertyAdapterRegistry] 已注销适配器: {}", adapter.getDisplayName());
            } catch (Exception e) {
                LOGGER.error("[PropertyAdapterRegistry] 适配器清理失败: {}", adapter.getDisplayName(), e);
            }
        }

        return removed;
    }

    /**
     * 根据类型查找最佳适配器
     * <p>查找策略：
     * <ol>
     *     <li>优先查找精确匹配的适配器</li>
     *     <li>其次查找支持该类型的适配器（通过 {@link PropertyAdapter#supports}）</li>
     *     <li>最后查找父类和接口的适配器</li>
     *     <li>返回优先级最高的适配器</li>
     * </ol>
     *
     * @param type 对象类型
     * @param <T>  类型参数
     * @return 最佳适配器，如果没有找到则返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> PropertyAdapter<T> findAdapter(Class<T> type) {
        if (type == null) {
            return null;
        }

        // 检查缓存
        PropertyAdapter<?> cached = adapterCache.get(type);
        if (cached != null) {
            statistics.recordCacheHit();
            return (PropertyAdapter<T>) cached;
        }

        statistics.recordCacheMiss();

        // 1. 精确匹配
        PropertyAdapter<T> adapter = findExactMatch(type);
        if (adapter != null) {
            adapterCache.put(type, adapter);
            LOGGER.debug("[PropertyAdapterRegistry] 找到精确匹配: {} -> {}",
                    type.getSimpleName(), adapter.getDisplayName());
            return adapter;
        }

        // 2. 通过 supports() 方法匹配
        adapter = findBySupports(type);
        if (adapter != null) {
            adapterCache.put(type, adapter);
            LOGGER.debug("[PropertyAdapterRegistry] 找到支持匹配: {} -> {}",
                    type.getSimpleName(), adapter.getDisplayName());
            return adapter;
        }

        // 3. 继承关系匹配
        adapter = findByInheritance(type);
        if (adapter != null) {
            adapterCache.put(type, adapter);
            LOGGER.debug("[PropertyAdapterRegistry] 找到继承匹配: {} -> {}",
                    type.getSimpleName(), adapter.getDisplayName());
            return adapter;
        }

        LOGGER.debug("[PropertyAdapterRegistry] 未找到适配器: {}", type.getSimpleName());
        return null;
    }

    /**
     * 根据对象实例查找适配器
     *
     * @param obj 对象实例
     * @return 适配器，如果没有找到则返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> PropertyAdapter<T> findAdapter(T obj) {
        if (obj == null) {
            return null;
        }
        return (PropertyAdapter<T>) findAdapter(obj.getClass());
    }

    /**
     * 精确匹配查找
     */
    @SuppressWarnings("unchecked")
    private <T> PropertyAdapter<T> findExactMatch(Class<T> type) {
        List<PropertyAdapter<?>> list = adapters.get(type);
        if (list != null && !list.isEmpty()) {
            return (PropertyAdapter<T>) list.get(0); // 已按优先级排序
        }
        return null;
    }

    /**
     * 通过 supports() 方法查找
     */
    @SuppressWarnings("unchecked")
    private <T> PropertyAdapter<T> findBySupports(Class<T> type) {
        try {
            T mockInstance = createMockInstance(type);
            if (mockInstance == null) {
                return null;
            }

            return (PropertyAdapter<T>) allAdapters.stream()
                    .filter(adapter -> adapter.supports(mockInstance))
                    .max(Comparator.comparingInt(PropertyAdapter::getPriority))
                    .orElse(null);
        } catch (Exception e) {
            LOGGER.debug("[PropertyAdapterRegistry] 无法创建模拟实例: {}", type.getSimpleName());
            return null;
        }
    }

    /**
     * 通过继承关系查找
     */
    @SuppressWarnings("unchecked")
    private <T> PropertyAdapter<T> findByInheritance(Class<T> type) {
        // 查找父类
        Class<?> superClass = type.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            PropertyAdapter<?> adapter = findAdapter(superClass);
            if (adapter != null) {
                return (PropertyAdapter<T>) adapter;
            }
        }

        // 查找接口
        for (Class<?> interfaceClass : type.getInterfaces()) {
            PropertyAdapter<?> adapter = findAdapter(interfaceClass);
            if (adapter != null) {
                return (PropertyAdapter<T>) adapter;
            }
        }

        return null;
    }

    /**
     * 获取指定类型的所有适配器
     *
     * @param type 类型
     * @return 适配器列表（按优先级排序）
     */
    public List<PropertyAdapter<?>> getAllAdapters(Class<?> type) {
        List<PropertyAdapter<?>> list = adapters.get(type);
        return list != null ? new ArrayList<>(list) : Collections.emptyList();
    }

    /**
     * 获取所有已注册的适配器
     *
     * @return 适配器列表
     */
    public List<PropertyAdapter<?>> getAllAdapters() {
        return new ArrayList<>(allAdapters);
    }

    /**
     * 获取所有支持的类型
     *
     * @return 类型集合
     */
    public Set<Class<?>> getSupportedTypes() {
        return new HashSet<>(adapters.keySet());
    }

    /**
     * 检查是否有指定类型的适配器
     *
     * @param type 类型
     * @return true 如果有适配器
     */
    public boolean hasAdapter(Class<?> type) {
        return findAdapter(type) != null;
    }

    /**
     * 检查适配器是否已注册
     */
    private boolean isAlreadyRegistered(PropertyAdapter<?> adapter) {
        return allAdapters.stream()
                .anyMatch(a -> a.getClass() == adapter.getClass() 
                        && a.getTargetType() == adapter.getTargetType());
    }

    /**
     * 排序适配器（按优先级降序）
     */
    private void sortAdapters(Class<?> type) {
        List<PropertyAdapter<?>> list = adapters.get(type);
        if (list != null && list.size() > 1) {
            list.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        }
    }

    /**
     * 清除缓存
     */
    private void invalidateCache(Class<?> type) {
        adapterCache.remove(type);
        
        // 同时清除可能相关的子类缓存
        adapterCache.keySet().removeIf(cachedType -> 
                type.isAssignableFrom(cachedType) || cachedType.isAssignableFrom(type));
    }

    /**
     * 清除所有缓存
     */
    public void clearCache() {
        adapterCache.clear();
        statistics.resetCacheStats();
        LOGGER.debug("[PropertyAdapterRegistry] 缓存已清除");
    }

    /**
     * 清空注册表
     * <p>注销所有适配器并清除缓存。
     */
    public void clear() {
        LOGGER.info("[PropertyAdapterRegistry] 正在清空注册表...");

        // 复制列表避免并发修改
        List<PropertyAdapter<?>> adaptersCopy = new ArrayList<>(allAdapters);

        // 依次注销
        for (PropertyAdapter<?> adapter : adaptersCopy) {
            unregister(adapter);
        }

        adapters.clear();
        adapterCache.clear();
        allAdapters.clear();
        statistics.reset();

        LOGGER.info("[PropertyAdapterRegistry] 注册表已清空");
    }

    /**
     * 获取注册表统计信息
     *
     * @return 统计信息对象
     */
    public RegistryStatistics getStatistics() {
        return statistics;
    }

    /**
     * 创建模拟实例（用于 supports() 测试）
     */
    private <T> T createMockInstance(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return String.format("PropertyAdapterRegistry{types=%d, adapters=%d, cacheSize=%d}",
                adapters.size(), allAdapters.size(), adapterCache.size());
    }

    /**
     * 📊 注册表统计信息
     */
    public static class RegistryStatistics {
        private final Map<Class<?>, Integer> registrationCounts = new ConcurrentHashMap<>();
        private int totalRegistrations = 0;
        private int totalUnregistrations = 0;
        private int cacheHits = 0;
        private int cacheMisses = 0;

        void recordRegistration(Class<?> type, PropertyAdapter<?> adapter) {
            registrationCounts.merge(type, 1, Integer::sum);
            totalRegistrations++;
        }

        void recordUnregistration(Class<?> type, PropertyAdapter<?> adapter) {
            totalUnregistrations++;
        }

        void recordCacheHit() {
            cacheHits++;
        }

        void recordCacheMiss() {
            cacheMisses++;
        }

        void resetCacheStats() {
            cacheHits = 0;
            cacheMisses = 0;
        }

        void reset() {
            registrationCounts.clear();
            totalRegistrations = 0;
            totalUnregistrations = 0;
            cacheHits = 0;
            cacheMisses = 0;
        }

        public int getTotalRegistrations() {
            return totalRegistrations;
        }

        public int getTotalUnregistrations() {
            return totalUnregistrations;
        }

        public int getActiveAdapters() {
            return totalRegistrations - totalUnregistrations;
        }

        public int getCacheHits() {
            return cacheHits;
        }

        public int getCacheMisses() {
            return cacheMisses;
        }

        public double getCacheHitRate() {
            int total = cacheHits + cacheMisses;
            return total > 0 ? (double) cacheHits / total * 100.0 : 0.0;
        }

        public Map<Class<?>, Integer> getRegistrationCounts() {
            return new HashMap<>(registrationCounts);
        }

        @Override
        public String toString() {
            return String.format(
                    "Statistics{active=%d, registrations=%d, unregistrations=%d, " +
                    "cacheHits=%d, cacheMisses=%d, hitRate=%.1f%%}",
                    getActiveAdapters(), totalRegistrations, totalUnregistrations,
                    cacheHits, cacheMisses, getCacheHitRate()
            );
        }
    }
}

