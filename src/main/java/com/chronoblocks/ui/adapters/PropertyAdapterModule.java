package com.chronoblocks.ui.adapters;

import com.chronoblocks.ui.adapters.examples.SimpleKeyframeAdapter;
import com.chronoblocks.ui.adapters.impl.ContainerAdapter;
import com.chronoblocks.ui.adapters.impl.TrackAdapter;
import com.chronoblocks.ui.adapters.impl.ProjectAdapter;
import com.chronoblocks.ui.adapters.impl.EffectAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🔌 PropertyAdapterModule - 属性适配器注册模块
 *
 * <p>负责在应用启动时注册所有属性适配器到注册表。
 *
 * <h3>✨ 功能特性：</h3>
 * <ul>
 *     <li>自动注册所有内置适配器</li>
 *     <li>提供注册状态检查</li>
 *     <li>支持动态添加自定义适配器</li>
 *     <li>详细的日志记录</li>
 * </ul>
 *
 * <h3>📖 使用示例：</h3>
 * <pre>{@code
 * // 在应用启动时调用
 * public class ChronoBlocksInitializer {
 *     public static void initialize() {
 *         PropertyAdapterModule.initialize();
 *     }
 * }
 * }</pre>
 *
 * @author ChronoBlocks Team
 * @version 4.5
 * @since 4.5
 */
public class PropertyAdapterModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyAdapterModule.class);

    /** 是否已初始化 */
    private static boolean initialized = false;

    /**
     * 初始化属性适配器系统
     * <p>注册所有内置适配器到注册表。
     *
     * @return true 如果成功初始化
     */
    public static boolean initialize() {
        if (initialized) {
            LOGGER.warn("[PropertyAdapterModule] 已经初始化，跳过重复初始化");
            return true;
        }

        LOGGER.info("[PropertyAdapterModule] 正在初始化属性适配器系统...");

        try {
            PropertyAdapterRegistry registry = PropertyAdapterRegistry.get();

            // 注册核心适配器
            registerCoreAdapters(registry);

            // 注册示例适配器（可选）
            registerExampleAdapters(registry);

            // 显示统计信息
            logRegistryStatistics(registry);

            initialized = true;
            LOGGER.info("[PropertyAdapterModule] 属性适配器系统初始化完成");
            return true;

        } catch (Exception e) {
            LOGGER.error("[PropertyAdapterModule] 初始化失败", e);
            return false;
        }
    }

    /**
     * 注册核心适配器
     */
    private static void registerCoreAdapters(PropertyAdapterRegistry registry) {
        LOGGER.debug("[PropertyAdapterModule] 正在注册核心适配器...");

        // 容器适配器
        registry.register(new ContainerAdapter());
        LOGGER.debug("[PropertyAdapterModule] 已注册 ContainerAdapter");

        // 轨道适配器
        registry.register(new TrackAdapter());
        LOGGER.debug("[PropertyAdapterModule] 已注册 TrackAdapter");

        // 项目适配器
        registry.register(new ProjectAdapter());
        LOGGER.debug("[PropertyAdapterModule] 已注册 ProjectAdapter");

        // 特效适配器
        registry.register(new EffectAdapter());
        LOGGER.debug("[PropertyAdapterModule] 已注册 EffectAdapter");
    }

    /**
     * 注册示例适配器
     */
    private static void registerExampleAdapters(PropertyAdapterRegistry registry) {
        LOGGER.debug("[PropertyAdapterModule] 正在注册示例适配器...");

        // 关键帧适配器示例
        registry.register(new SimpleKeyframeAdapter());
        LOGGER.debug("[PropertyAdapterModule] 已注册 SimpleKeyframeAdapter");
    }

    /**
     * 记录注册表统计信息
     */
    private static void logRegistryStatistics(PropertyAdapterRegistry registry) {
        var stats = registry.getStatistics();

        LOGGER.info("[PropertyAdapterModule] 注册表统计:");
        LOGGER.info("[PropertyAdapterModule]   - 总注册数: {}", stats.getTotalRegistrations());
        LOGGER.info("[PropertyAdapterModule]   - 活跃适配器: {}", stats.getActiveAdapters());
        LOGGER.info("[PropertyAdapterModule]   - 支持类型: {}", registry.getSupportedTypes().size());

        // 列出所有支持的类型
        if (LOGGER.isDebugEnabled()) {
            registry.getSupportedTypes().forEach(type ->
                    LOGGER.debug("[PropertyAdapterModule]     • {}", type.getSimpleName()));
        }
    }

    /**
     * 检查是否已初始化
     *
     * @return true 如果已初始化
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * 重置模块（用于测试）
     * <p>清空注册表并重置初始化状态。
     */
    public static void reset() {
        LOGGER.warn("[PropertyAdapterModule] 正在重置模块...");

        PropertyAdapterRegistry.get().clear();
        initialized = false;

        LOGGER.info("[PropertyAdapterModule] 模块已重置");
    }

    /**
     * 注册自定义适配器
     * <p>允许外部模块注册额外的适配器。
     *
     * @param adapter 要注册的适配器
     * @return true 如果成功注册
     */
    public static boolean registerCustomAdapter(PropertyAdapter<?> adapter) {
        if (!initialized) {
            LOGGER.warn("[PropertyAdapterModule] 系统未初始化，先调用 initialize()");
            return false;
        }

        try {
            PropertyAdapterRegistry.get().register(adapter);
            LOGGER.info("[PropertyAdapterModule] 已注册自定义适配器: {}",
                    adapter.getDisplayName());
            return true;
        } catch (Exception e) {
            LOGGER.error("[PropertyAdapterModule] 注册自定义适配器失败", e);
            return false;
        }
    }

    /**
     * 获取注册表实例
     *
     * @return 注册表实例
     */
    public static PropertyAdapterRegistry getRegistry() {
        if (!initialized) {
            LOGGER.warn("[PropertyAdapterModule] 系统未初始化，返回默认注册表");
        }
        return PropertyAdapterRegistry.get();
    }

    /**
     * 打印注册表状态（调试用）
     */
    public static void printStatus() {
        if (!initialized) {
            System.out.println("[PropertyAdapterModule] 未初始化");
            return;
        }

        PropertyAdapterRegistry registry = PropertyAdapterRegistry.get();
        var stats = registry.getStatistics();

        System.out.println("════════════════════════════════════════");
        System.out.println("  ChronoBlocks 属性适配器系统状态");
        System.out.println("════════════════════════════════════════");
        System.out.println("初始化状态: ✅ 已初始化");
        System.out.println("总注册数:   " + stats.getTotalRegistrations());
        System.out.println("活跃适配器: " + stats.getActiveAdapters());
        System.out.println("支持类型:   " + registry.getSupportedTypes().size());
        System.out.println("缓存命中率: " + String.format("%.1f%%", stats.getCacheHitRate()));
        System.out.println("----------------------------------------");
        System.out.println("支持的类型:");
        registry.getSupportedTypes().forEach(type ->
                System.out.println("  • " + type.getSimpleName()));
        System.out.println("════════════════════════════════════════");
    }
}

