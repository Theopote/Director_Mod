package com.chronoblocks.ui.adapters;

import com.chronoblocks.ui.adapters.impl.ContainerAdapter;
import com.chronoblocks.ui.adapters.impl.TrackAdapter;
import com.chronoblocks.ui.adapters.impl.ProjectAdapter;
import com.chronoblocks.ui.adapters.impl.EffectAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🔌 PropertyAdapterModule - 旧版属性适配器注册模块（未接入 UI）
 *
 * <p><b>已弃用：</b> 本模块向 {@link LegacyPropertyAdapterRegistry} 注册的适配器未被 PropertiesPanel
 * 或其它 UI 使用。实际生效的为 {@code com.chronoblocks.ui.properties.PropertyAdapterBootstrap} +
 * {@code com.chronoblocks.ui.properties.PropertyAdapterRegistry}（{@code IPropertyAdapter}）。
 *
 * <p>负责在应用启动时注册所有属性适配器到 {@link LegacyPropertyAdapterRegistry}。
 *
 * @author ChronoBlocks Team
 * @version 4.5
 * @since 4.5
 * @deprecated 未被 UI 使用；请使用 {@code com.chronoblocks.ui.properties.PropertyAdapterBootstrap}
 */
@Deprecated(since = "4.5", forRemoval = true)
@SuppressWarnings("removal")
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
            LegacyPropertyAdapterRegistry registry = LegacyPropertyAdapterRegistry.get();

            // 注册核心适配器
            registerCoreAdapters(registry);

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
    private static void registerCoreAdapters(LegacyPropertyAdapterRegistry registry) {
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
     * 记录注册表统计信息
     */
    private static void logRegistryStatistics(LegacyPropertyAdapterRegistry registry) {
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

        LegacyPropertyAdapterRegistry.get().clear();
        initialized = false;

        LOGGER.info("[PropertyAdapterModule] 模块已重置");
    }

    /**
     * 获取注册表实例
     *
     * @return 注册表实例
     */
    public static LegacyPropertyAdapterRegistry getRegistry() {
        if (!initialized) {
            LOGGER.warn("[PropertyAdapterModule] 系统未初始化，返回默认注册表");
        }
        return LegacyPropertyAdapterRegistry.get();
    }

}