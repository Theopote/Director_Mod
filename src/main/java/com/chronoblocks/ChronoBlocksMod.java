package com.chronoblocks;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chronoblocks.config.ChronoBlocksConfig;
import com.chronoblocks.network.ChronoBlocksNetwork;
import com.chronoblocks.command.ChronoBlocksCommands;
import com.chronoblocks.compatibility.ModCompatibilityChecker;
import com.chronoblocks.compatibility.VersionCompatibilityChecker;
import com.chronoblocks.i18n.ChronoBlocksI18n;
import com.chronoblocks.core.container.visibility.ContainerServices;

import net.minecraft.util.Identifier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ChronoBlocksMod - 主模组入口点 (服务器/通用端)
 * <p>
 * 初始化核心系统、注册表和数据层钩子，在客户端和服务器之间共享。
 * 
 * <h3>主要功能：</h3>
 * <ul>
 *   <li><strong>配置管理</strong> - 加载和管理模组配置</li>
 *   <li><strong>网络注册</strong> - 注册数据包处理器和网络事件</li>
 *   <li><strong>命令注册</strong> - 注册服务器端命令</li>
 *   <li><strong>兼容性检查</strong> - 检查模组兼容性和依赖</li>
 *   <li><strong>错误处理</strong> - 提供健壮的初始化错误处理</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 2.0
 * @since 1.0
 */
public class ChronoBlocksMod implements ModInitializer {
    public static final String MOD_ID = "chronoblocks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    // 模组元数据缓存
    private static volatile String modVersion = null;
    private static volatile String fabricVersion = null;
    private static volatile String minecraftVersion = null;
    private static volatile String fabricLoaderVersion = null;
    
    // 初始化状态管理
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicInteger initStep = new AtomicInteger(0);
    private static final AtomicBoolean initFailed = new AtomicBoolean(false);
    
    // 初始化步骤常量
    private static final int STEP_CONFIG = 1;
    private static final int STEP_I18N = 2;
    private static final int STEP_COMPATIBILITY = 3;
    private static final int STEP_NETWORK = 4;
    private static final int STEP_COMMANDS = 5;
    private static final int STEP_ITEMS = 6;
    private static final int STEP_COMPLETE = 7;
    
    /**
     * 创建模组资源标识符的辅助方法
     * <p>
     * 统一资源定位，减少打字错误，提供类型安全的资源 ID 创建。
     * 
     * @param path 资源路径
     * @return 完整的资源标识符
     * 
     * &#064;example
     * <pre>{@code
     * // 创建物品 ID
     * Identifier toolId = ChronoBlocksMod.id("chronoblocks_tool");
     * 
     * // 创建方块 ID
     * Identifier blockId = ChronoBlocksMod.id("chronoblocks_block");
     * 
     * // 创建实体 ID
     * Identifier entityId = ChronoBlocksMod.id("chronoblocks_entity");
     * }</pre>
     */
    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
    
    /**
     * 获取模组版本信息
     * 
     * @return 模组版本字符串
     */
    public static String getModVersion() {
        if (modVersion == null) {
            synchronized (ChronoBlocksMod.class) {
                if (modVersion == null) {
                    try {
                        modVersion = ChronoBlocksMod.class.getPackage().getImplementationVersion();
                        if (modVersion == null) {
                            modVersion = "开发版本";
                        }
                    } catch (Exception e) {
                        modVersion = "未知版本";
                    }
                }
            }
        }
        return modVersion;
    }
    
    /**
     * 获取 Fabric API 版本信息
     * 
     * @return Fabric API 版本字符串
     */
    public static String getFabricVersion() {
        if (fabricVersion == null) {
            synchronized (ChronoBlocksMod.class) {
                if (fabricVersion == null) {
                    try {
                        fabricVersion = FabricLoader.getInstance().getModContainer("fabric")
                            .map(container -> container.getMetadata().getVersion().getFriendlyString())
                            .orElse("未知");
                    } catch (Exception e) {
                        fabricVersion = "未知";
                    }
                }
            }
        }
        return fabricVersion;
    }
    
    /**
     * 获取 Minecraft 版本信息
     * 
     * @return Minecraft 版本字符串
     */
    public static String getMinecraftVersion() {
        if (minecraftVersion == null) {
            synchronized (ChronoBlocksMod.class) {
                if (minecraftVersion == null) {
                    try {
                        minecraftVersion = FabricLoader.getInstance().getModContainer("minecraft")
                            .map(container -> container.getMetadata().getVersion().getFriendlyString())
                            .orElse("未知");
                    } catch (Exception e) {
                        minecraftVersion = "未知";
                    }
                }
            }
        }
        return minecraftVersion;
    }
    
    /**
     * 获取 Fabric Loader 版本信息
     * 
     * @return Fabric Loader 版本字符串
     */
    public static String getFabricLoaderVersion() {
        if (fabricLoaderVersion == null) {
            synchronized (ChronoBlocksMod.class) {
                if (fabricLoaderVersion == null) {
                    try {
                        fabricLoaderVersion = FabricLoader.getInstance().getModContainer("fabricloader")
                            .map(container -> container.getMetadata().getVersion().getFriendlyString())
                            .orElse("未知");
                    } catch (Exception e) {
                        fabricLoaderVersion = "未知";
                    }
                }
            }
        }
        return fabricLoaderVersion;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("[ChronoBlocks] 开始初始化通用端...");
        LOGGER.info("[ChronoBlocks] 模组版本: {}", getModVersion());
        LOGGER.info("[ChronoBlocks] Minecraft 版本: {}", getMinecraftVersion());
        LOGGER.info("[ChronoBlocks] Fabric Loader 版本: {}", getFabricLoaderVersion());
        LOGGER.info("[ChronoBlocks] Fabric API 版本: {}", getFabricVersion());
        LOGGER.info("[ChronoBlocks] Java 版本: {}", System.getProperty("java.version"));
        LOGGER.info("[ChronoBlocks] 操作系统: {} {}", System.getProperty("os.name"), System.getProperty("os.arch"));
        
        // 防止重复初始化
        if (initialized.get()) {
            LOGGER.warn("[ChronoBlocks] 检测到重复初始化，跳过");
            return;
        }
        
        try {
            // 步骤 1: 初始化配置系统
            initializeConfig();
            
            // 步骤 2: 初始化国际化系统
            initializeI18n();
            
            // 步骤 3: 检查模组兼容性
            checkModCompatibility();
            
            // 步骤 4: 初始化网络系统
            initializeNetwork();
            
            // 步骤 5: 注册命令
            registerCommands();
            
            // 步骤 6: 注册物品和方块
            registerItems();
            
            // 步骤 7: 注册服务器生命周期事件
            registerServerLifecycleEvents();
            
            // 步骤 8: 完成初始化
            completeInitialization();
            
        } catch (Exception e) {
            handleInitializationError(e);
        }
    }
    
    /**
     * 初始化配置系统
     */
    private void initializeConfig() {
        LOGGER.info("[ChronoBlocks] 步骤 {}/{}: 初始化配置系统...", STEP_CONFIG, STEP_COMPLETE);
        
        try {
            ChronoBlocksConfig.initialize();
            initStep.set(STEP_CONFIG);
            LOGGER.info("[ChronoBlocks] 配置系统初始化完成");
            
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] 配置系统初始化失败: {}", e.getMessage(), e);
            throw new RuntimeException("配置系统初始化失败", e);
        }
    }
    
    /**
     * 初始化国际化系统
     */
    private void initializeI18n() {
        LOGGER.info("[ChronoBlocks] 步骤 {}/{}: 初始化国际化系统...", STEP_I18N, STEP_COMPLETE);
        
        try {
            ChronoBlocksI18n.initialize();
            initStep.set(STEP_I18N);
            LOGGER.info("[ChronoBlocks] 国际化系统初始化完成");
            
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] 国际化系统初始化失败: {}", e.getMessage(), e);
            // 国际化系统失败不应该阻止模组加载
            LOGGER.warn("[ChronoBlocks] 继续加载模组，但国际化功能可能不可用");
        }
    }
    
    /**
     * 检查模组兼容性
     */
    private void checkModCompatibility() {
        LOGGER.info("[ChronoBlocks] 步骤 {}/{}: 检查模组兼容性...", STEP_COMPATIBILITY, STEP_COMPLETE);
        
        try {
            // 首先检查版本兼容性
            VersionCompatibilityChecker.checkAllCompatibility();
            
            // 然后检查其他模组兼容性
            ModCompatibilityChecker.initialize();
            initStep.set(STEP_COMPATIBILITY);
            
            // 检查是否有不兼容的必需模组
            if (ModCompatibilityChecker.hasIncompatibleRequiredMods()) {
                LOGGER.error("[ChronoBlocks] 发现不兼容的必需模组，模组可能无法正常工作");
                // 不抛出异常，允许模组继续加载但记录警告
            }
            
            // 检查是否有版本兼容性问题
            if (VersionCompatibilityChecker.hasCompatibilityIssues()) {
                LOGGER.warn("[ChronoBlocks] 发现版本兼容性问题，请查看上述警告信息");
            }
            
            LOGGER.info("[ChronoBlocks] 模组兼容性检查完成");
            
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] 模组兼容性检查失败: {}", e.getMessage(), e);
            // 兼容性检查失败不应该阻止模组加载
            LOGGER.warn("[ChronoBlocks] 继续加载模组，但兼容性检查不可用");
        }
    }
    
    
    /**
     * 初始化网络系统
     */
    private void initializeNetwork() {
        LOGGER.info("[ChronoBlocks] 步骤 {}/{}: 初始化网络系统...", STEP_NETWORK, STEP_COMPLETE);
        
        try {
            ChronoBlocksNetwork.initialize();
            initStep.set(STEP_NETWORK);
            LOGGER.info("[ChronoBlocks] 网络系统初始化完成");
            
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] 网络系统初始化失败: {}", e.getMessage(), e);
            throw new RuntimeException("网络系统初始化失败", e);
        }
    }
    
    /**
     * 注册命令
     */
    private void registerCommands() {
        LOGGER.info("[ChronoBlocks] 步骤 {}/{}: 注册命令...", STEP_COMMANDS, STEP_COMPLETE);
        
        try {
            ChronoBlocksCommands.initialize();
            initStep.set(STEP_COMMANDS);
            LOGGER.info("[ChronoBlocks] 命令注册完成");
            
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] 命令注册失败: {}", e.getMessage(), e);
            throw new RuntimeException("命令注册失败", e);
        }
    }
    
    /**
     * 注册物品和方块
     */
    private void registerItems() {
        LOGGER.info("[ChronoBlocks] 步骤 {}/{}: 注册物品和方块...", STEP_ITEMS, STEP_COMPLETE);
        
        try {
        com.chronoblocks.registry.ModItems.register();
            initStep.set(STEP_ITEMS);
            LOGGER.info("[ChronoBlocks] 物品和方块注册完成");
            
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] 物品和方块注册失败: {}", e.getMessage(), e);
            throw new RuntimeException("物品和方块注册失败", e);
        }
    }
    
    /**
     * 注册服务器生命周期事件
     */
    private void registerServerLifecycleEvents() {
        LOGGER.info("[ChronoBlocks] 注册服务器生命周期事件...");
        
        // 服务器启动时初始化ContainerVisibilityService
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ContainerServices.init(server);
            LOGGER.debug("[ChronoBlocks] ContainerVisibilityService初始化完成");
        });
        
        // 服务器停止时清理
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            var visibilityService = ContainerServices.visibility();
            if (visibilityService != null) {
                visibilityService.clearAll();
            }
            // 清理所有线程池，避免在服务器关闭时提交任务导致异常
            cleanupThreadPools();
        });
        
        LOGGER.info("[ChronoBlocks] 服务器生命周期事件注册完成");
    }
    
    /**
     * 清理所有线程池，在服务器关闭时调用
     * 避免在服务器关闭时提交任务到已关闭的线程池导致异常
     */
    private void cleanupThreadPools() {
        try {
            // 清理 SelectionBatchProcessor 的线程池
            try {
                com.chronoblocks.core.selection.pipeline.SelectionBatchProcessor.get().shutdown();
            } catch (Exception e) {
                LOGGER.warn("[ChronoBlocks] 清理 SelectionBatchProcessor 线程池失败", e);
            }
            
            // 清理 LassoSelectTask 的线程池
            try {
                java.util.concurrent.ExecutorService executor = com.chronoblocks.core.selection.tools.lassoselecttool.LassoSelectTask.EXECUTOR;
                if (!executor.isShutdown()) {
                    executor.shutdownNow();
                }
            } catch (Exception e) {
                LOGGER.warn("[ChronoBlocks] 清理 LassoSelectTask 线程池失败", e);
            }
            
            // 注意：MagicWandTool.THREAD_POOL 是私有的，且使用 daemon 线程
            // 会在 JVM 关闭时自动终止，无需手动清理
            
            // 清理 WaterFreezeManager 的线程池
            try {
                com.chronoblocks.season.WaterFreezeManager.get().shutdown();
            } catch (Exception e) {
                LOGGER.warn("[ChronoBlocks] 清理 WaterFreezeManager 线程池失败", e);
            }
            
            // 清理 SeasonSnowManager 的线程池
            try {
                com.chronoblocks.season.SeasonSnowManager.get().shutdown();
            } catch (Exception e) {
                LOGGER.warn("[ChronoBlocks] 清理 SeasonSnowManager 线程池失败", e);
            }
            
            LOGGER.debug("[ChronoBlocks] 所有线程池清理完成");
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] 清理线程池时发生错误", e);
        }
    }
    
    /**
     * 完成初始化
     */
    private void completeInitialization() {
        LOGGER.info("[ChronoBlocks] 步骤 {}/{}: 完成初始化...", STEP_COMPLETE, STEP_COMPLETE);
        
        try {
            // 标记为已初始化
            initialized.set(true);
            initStep.set(STEP_COMPLETE);
            
            // 记录初始化完成信息
            logInitializationSummary();
            
            LOGGER.info("[ChronoBlocks] 通用端初始化完成！");
            
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] 完成初始化时发生错误: {}", e.getMessage(), e);
            throw new RuntimeException("完成初始化失败", e);
        }
    }
    
    /**
     * 处理初始化错误
     */
    private void handleInitializationError(Exception e) {
        initFailed.set(true);
        LOGGER.error("[ChronoBlocks] 初始化失败 (步骤 {}/{}): {}", 
                    initStep.get(), STEP_COMPLETE, e.getMessage(), e);
        
        // 根据失败步骤提供不同的错误信息
        String errorMessage = getErrorMessageForStep(initStep.get());
        LOGGER.error("[ChronoBlocks] 错误详情: {}", errorMessage);
        
        // 记录系统信息用于调试
        logSystemInfo();
        
        // 不重新抛出异常，允许模组部分加载
        LOGGER.warn("[ChronoBlocks] 模组将以受限模式继续运行");
    }
    
    /**
     * 获取步骤错误信息
     */
    private String getErrorMessageForStep(int step) {
        return switch (step) {
            case STEP_CONFIG -> "配置系统初始化失败，请检查配置文件权限";
            case STEP_I18N -> "国际化系统初始化失败，请检查翻译文件";
            case STEP_COMPATIBILITY -> "模组兼容性检查失败，请检查模组依赖";
            case STEP_NETWORK -> "网络系统初始化失败，请检查网络配置";
            case STEP_COMMANDS -> "命令注册失败，请检查命令权限";
            case STEP_ITEMS -> "物品注册失败，请检查注册表";
            default -> "未知初始化步骤失败";
        };
    }
    
    /**
     * 记录初始化摘要
     */
    private void logInitializationSummary() {
        LOGGER.info("[ChronoBlocks] === 初始化摘要 ===");
        LOGGER.info("[ChronoBlocks] 模组ID: {}", MOD_ID);
        LOGGER.info("[ChronoBlocks] 模组版本: {}", getModVersion());
        LOGGER.info("[ChronoBlocks] Minecraft版本: {}", getMinecraftVersion());
        LOGGER.info("[ChronoBlocks] Fabric Loader版本: {}", getFabricLoaderVersion());
        LOGGER.info("[ChronoBlocks] Fabric API版本: {}", getFabricVersion());
        LOGGER.info("[ChronoBlocks] Java版本: {}", System.getProperty("java.version"));
        LOGGER.info("[ChronoBlocks] 操作系统: {} {}", System.getProperty("os.name"), System.getProperty("os.arch"));
        LOGGER.info("[ChronoBlocks] 可用处理器: {}", Runtime.getRuntime().availableProcessors());
        LOGGER.info("[ChronoBlocks] 最大内存: {} MB", Runtime.getRuntime().maxMemory() / 1024 / 1024);
        
        // 记录兼容性摘要
        LOGGER.info("[ChronoBlocks] 模组兼容性: {}", ModCompatibilityChecker.getCompatibilitySummary());

        // 记录配置摘要
        if (ChronoBlocksConfig.getConfig() != null) {
            LOGGER.info("[ChronoBlocks] 配置版本: {}", ChronoBlocksConfig.getConfig().configVersion);
            LOGGER.info("[ChronoBlocks] 调试模式: {}", ChronoBlocksConfig.isDebugMode());
            LOGGER.info("[ChronoBlocks] 性能优化: {}", ChronoBlocksConfig.getConfig().performance.enableOptimizations);
            LOGGER.info("[ChronoBlocks] UI主题: {}", ChronoBlocksConfig.getConfig().ui.defaultTheme);
        }
        
        // 记录国际化摘要
        LOGGER.info("[ChronoBlocks] 国际化系统: {}", ChronoBlocksI18n.getStatus());
        if (ChronoBlocksI18n.isInitialized()) {
            LOGGER.info("[ChronoBlocks] 当前语言: {}", ChronoBlocksI18n.getCurrentLanguage());
        }
        
        // 记录网络摘要
        LOGGER.info("[ChronoBlocks] 网络数据包: UI_OPEN, UI_CLOSE, CONFIG_SYNC, PROJECT_DATA");
        
        // 记录命令摘要
        LOGGER.info("[ChronoBlocks] 可用命令: /chronoblocks, /cb");
    }
    
    /**
     * 记录系统信息
     */
    private void logSystemInfo() {
        LOGGER.debug("[ChronoBlocks] === 系统信息 ===");
        LOGGER.debug("[ChronoBlocks] 可用处理器: {}", Runtime.getRuntime().availableProcessors());
        LOGGER.debug("[ChronoBlocks] 最大内存: {} MB", Runtime.getRuntime().maxMemory() / 1024 / 1024);
        LOGGER.debug("[ChronoBlocks] 总内存: {} MB", Runtime.getRuntime().totalMemory() / 1024 / 1024);
        LOGGER.debug("[ChronoBlocks] 空闲内存: {} MB", Runtime.getRuntime().freeMemory() / 1024 / 1024);
    }
    
    
    /**
     * 检查模组是否已初始化
     */
    public static boolean isInitialized() {
        return initialized.get();
    }
}