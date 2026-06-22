package com.director;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.director.config.DirectorConfig;
import com.director.command.DirectorCommands;
import com.director.compatibility.ModCompatibilityChecker;
import com.director.compatibility.VersionCompatibilityChecker;
import com.director.i18n.DirectorI18n;
import com.director.core.DirectorServices;
import com.director.core.container.visibility.ContainerServices;

import net.minecraft.util.Identifier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DirectorMod - 主模组入口点 (服务器/通用端)
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
 * @author Director Team
 * @version 2.0
 * @since 1.0
 */
public class DirectorMod implements ModInitializer {
    public static final String MOD_ID = "director";
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
    private static final int STEP_COMMANDS = 4;
    private static final int STEP_ITEMS = 5;
    private static final int STEP_COMPLETE = 6;
    
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
     * Identifier toolId = DirectorMod.id("director_tool");
     * 
     * // 创建方块 ID
     * Identifier blockId = DirectorMod.id("director_block");
     * 
     * // 创建实体 ID
     * Identifier entityId = DirectorMod.id("director_entity");
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
            synchronized (DirectorMod.class) {
                if (modVersion == null) {
                    try {
                        modVersion = DirectorMod.class.getPackage().getImplementationVersion();
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
            synchronized (DirectorMod.class) {
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
            synchronized (DirectorMod.class) {
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
            synchronized (DirectorMod.class) {
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
        LOGGER.info("[Director] 开始初始化通用端...");
        LOGGER.info("[Director] 模组版本: {}", getModVersion());
        LOGGER.info("[Director] Minecraft 版本: {}", getMinecraftVersion());
        LOGGER.info("[Director] Fabric Loader 版本: {}", getFabricLoaderVersion());
        LOGGER.info("[Director] Fabric API 版本: {}", getFabricVersion());
        LOGGER.info("[Director] Java 版本: {}", System.getProperty("java.version"));
        LOGGER.info("[Director] 操作系统: {} {}", System.getProperty("os.name"), System.getProperty("os.arch"));
        
        // 防止重复初始化
        if (initialized.get()) {
            LOGGER.warn("[Director] 检测到重复初始化，跳过");
            return;
        }
        
        try {
            // 步骤 1: 初始化配置系统
            initializeConfig();

            // 步骤 1b: 初始化核心服务注册表（容器、选区、时间线关联等）
            initializeDirectorServices();
            
            // 步骤 2: 初始化国际化系统
            initializeI18n();
            
            // 步骤 3: 检查模组兼容性
            checkModCompatibility();
            
            // 步骤 4: 注册命令
            registerCommands();
            
            // 步骤 5: 注册物品和方块
            registerItems();
            
            // 步骤 6: 注册服务器生命周期事件
            registerServerLifecycleEvents();
            
            // 步骤 7: 完成初始化
            completeInitialization();
            
        } catch (Exception e) {
            handleInitializationError(e);
        }
    }
    
    /**
     * 初始化配置系统
     */
    private void initializeConfig() {
        LOGGER.info("[Director] 步骤 {}/{}: 初始化配置系统...", STEP_CONFIG, STEP_COMPLETE);
        
        try {
            DirectorConfig.initialize();
            initStep.set(STEP_CONFIG);
            LOGGER.info("[Director] 配置系统初始化完成");
            
        } catch (Exception e) {
            LOGGER.error("[Director] 配置系统初始化失败: {}", e.getMessage(), e);
            throw new RuntimeException("配置系统初始化失败", e);
        }
    }
    
    /**
     * 显式初始化核心服务注册表。
     */
    private void initializeDirectorServices() {
        try {
            DirectorServices.initialize();
            LOGGER.info("[Director] 核心服务注册表初始化完成");
        } catch (Exception e) {
            LOGGER.error("[Director] 核心服务注册表初始化失败: {}", e.getMessage(), e);
            throw new RuntimeException("核心服务注册表初始化失败", e);
        }
    }

    /**
     * 初始化国际化系统
     */
    private void initializeI18n() {
        LOGGER.info("[Director] 步骤 {}/{}: 初始化国际化系统...", STEP_I18N, STEP_COMPLETE);
        
        try {
            DirectorI18n.initialize();
            initStep.set(STEP_I18N);
            LOGGER.info("[Director] 国际化系统初始化完成");
            
        } catch (Exception e) {
            LOGGER.error("[Director] 国际化系统初始化失败: {}", e.getMessage(), e);
            // 国际化系统失败不应该阻止模组加载
            LOGGER.warn("[Director] 继续加载模组，但国际化功能可能不可用");
        }
    }
    
    /**
     * 检查模组兼容性
     */
    private void checkModCompatibility() {
        LOGGER.info("[Director] 步骤 {}/{}: 检查模组兼容性...", STEP_COMPATIBILITY, STEP_COMPLETE);
        
        try {
            // 首先检查版本兼容性
            VersionCompatibilityChecker.checkAllCompatibility();
            
            // 然后检查其他模组兼容性
            ModCompatibilityChecker.initialize();
            initStep.set(STEP_COMPATIBILITY);
            
            // 检查是否有不兼容的必需模组
            if (ModCompatibilityChecker.hasIncompatibleRequiredMods()) {
                LOGGER.error("[Director] 发现不兼容的必需模组，模组可能无法正常工作");
                // 不抛出异常，允许模组继续加载但记录警告
            }
            
            // 检查是否有版本兼容性问题
            if (VersionCompatibilityChecker.hasCompatibilityIssues()) {
                LOGGER.warn("[Director] 发现版本兼容性问题，请查看上述警告信息");
            }
            
            LOGGER.info("[Director] 模组兼容性检查完成");
            
        } catch (Exception e) {
            LOGGER.error("[Director] 模组兼容性检查失败: {}", e.getMessage(), e);
            // 兼容性检查失败不应该阻止模组加载
            LOGGER.warn("[Director] 继续加载模组，但兼容性检查不可用");
        }
    }
    
    
    /**
     * 注册命令
     */
    private void registerCommands() {
        LOGGER.info("[Director] 步骤 {}/{}: 注册命令...", STEP_COMMANDS, STEP_COMPLETE);
        
        try {
            DirectorCommands.initialize();
            initStep.set(STEP_COMMANDS);
            LOGGER.info("[Director] 命令注册完成");
            
        } catch (Exception e) {
            LOGGER.error("[Director] 命令注册失败: {}", e.getMessage(), e);
            throw new RuntimeException("命令注册失败", e);
        }
    }
    
    /**
     * 注册物品和方块
     */
    private void registerItems() {
        LOGGER.info("[Director] 步骤 {}/{}: 注册物品和方块...", STEP_ITEMS, STEP_COMPLETE);
        
        try {
        com.director.registry.ModItems.register();
            initStep.set(STEP_ITEMS);
            LOGGER.info("[Director] 物品和方块注册完成");
            
        } catch (Exception e) {
            LOGGER.error("[Director] 物品和方块注册失败: {}", e.getMessage(), e);
            throw new RuntimeException("物品和方块注册失败", e);
        }
    }
    
    /**
     * 注册服务器生命周期事件
     */
    private void registerServerLifecycleEvents() {
        LOGGER.info("[Director] 注册服务器生命周期事件...");
        
        // 服务器启动时初始化ContainerVisibilityService
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ContainerServices.init(server);
            LOGGER.debug("[Director] ContainerVisibilityService初始化完成");
        });
        
        // 服务器停止时清理
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            com.director.core.container.persistence.ContainerPersistence.flushPendingAutoSave(
                    DirectorServices.get().containers());
            var visibilityService = DirectorServices.get().visibilityOrNull();
            if (visibilityService != null) {
                visibilityService.clearAll();
            }
            // 清理所有线程池，避免在服务器关闭时提交任务导致异常
            cleanupThreadPools();
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var visibilityService = DirectorServices.get().visibilityOrNull();
            if (visibilityService != null) {
                visibilityService.tick();
            }
            com.director.core.container.persistence.ContainerPersistence.tickAutoSave(
                    DirectorServices.get().containers());
        });
        
        LOGGER.info("[Director] 服务器生命周期事件注册完成");
    }
    
    /**
     * 清理所有线程池，在服务器关闭时调用
     * 避免在服务器关闭时提交任务到已关闭的线程池导致异常
     */
    private void cleanupThreadPools() {
        try {
            // 清理 SelectionBatchProcessor 的线程池
            try {
                com.director.core.selection.pipeline.SelectionBatchProcessor.get().shutdown();
            } catch (Exception e) {
                LOGGER.warn("[Director] 清理 SelectionBatchProcessor 线程池失败", e);
            }
            
            // 清理 LassoSelectTask 的线程池
            try {
                java.util.concurrent.ExecutorService executor = com.director.core.selection.tools.lassoselecttool.LassoSelectTask.EXECUTOR;
                if (!executor.isShutdown()) {
                    executor.shutdownNow();
                }
            } catch (Exception e) {
                LOGGER.warn("[Director] 清理 LassoSelectTask 线程池失败", e);
            }
            
            // 注意：MagicWandTool.THREAD_POOL 是私有的，且使用 daemon 线程
            // 会在 JVM 关闭时自动终止，无需手动清理
            
            // 清理 WaterFreezeManager 的线程池
            try {
                com.director.season.WaterFreezeManager.get().shutdown();
            } catch (Exception e) {
                LOGGER.warn("[Director] 清理 WaterFreezeManager 线程池失败", e);
            }
            
            // 清理 SeasonSnowManager 的线程池
            try {
                com.director.season.SeasonSnowManager.get().shutdown();
            } catch (Exception e) {
                LOGGER.warn("[Director] 清理 SeasonSnowManager 线程池失败", e);
            }
            
            LOGGER.debug("[Director] 所有线程池清理完成");
        } catch (Exception e) {
            LOGGER.error("[Director] 清理线程池时发生错误", e);
        }
    }
    
    /**
     * 完成初始化
     */
    private void completeInitialization() {
        LOGGER.info("[Director] 步骤 {}/{}: 完成初始化...", STEP_COMPLETE, STEP_COMPLETE);
        
        try {
            // 标记为已初始化
            initialized.set(true);
            initStep.set(STEP_COMPLETE);
            
            // 记录初始化完成信息
            logInitializationSummary();
            
            LOGGER.info("[Director] 通用端初始化完成！");
            
        } catch (Exception e) {
            LOGGER.error("[Director] 完成初始化时发生错误: {}", e.getMessage(), e);
            throw new RuntimeException("完成初始化失败", e);
        }
    }
    
    /**
     * 处理初始化错误
     */
    private void handleInitializationError(Exception e) {
        initFailed.set(true);
        LOGGER.error("[Director] 初始化失败 (步骤 {}/{}): {}", 
                    initStep.get(), STEP_COMPLETE, e.getMessage(), e);
        
        // 根据失败步骤提供不同的错误信息
        String errorMessage = getErrorMessageForStep(initStep.get());
        LOGGER.error("[Director] 错误详情: {}", errorMessage);
        
        // 记录系统信息用于调试
        logSystemInfo();
        
        // 不重新抛出异常，允许模组部分加载
        LOGGER.warn("[Director] 模组将以受限模式继续运行");
    }
    
    /**
     * 获取步骤错误信息
     */
    private String getErrorMessageForStep(int step) {
        return switch (step) {
            case STEP_CONFIG -> "配置系统初始化失败，请检查配置文件权限";
            case STEP_I18N -> "国际化系统初始化失败，请检查翻译文件";
            case STEP_COMPATIBILITY -> "模组兼容性检查失败，请检查模组依赖";
            case STEP_COMMANDS -> "命令注册失败，请检查命令权限";
            case STEP_ITEMS -> "物品注册失败，请检查注册表";
            default -> "未知初始化步骤失败";
        };
    }
    
    /**
     * 记录初始化摘要
     */
    private void logInitializationSummary() {
        LOGGER.info("[Director] === 初始化摘要 ===");
        LOGGER.info("[Director] 模组ID: {}", MOD_ID);
        LOGGER.info("[Director] 模组版本: {}", getModVersion());
        LOGGER.info("[Director] Minecraft版本: {}", getMinecraftVersion());
        LOGGER.info("[Director] Fabric Loader版本: {}", getFabricLoaderVersion());
        LOGGER.info("[Director] Fabric API版本: {}", getFabricVersion());
        LOGGER.info("[Director] Java版本: {}", System.getProperty("java.version"));
        LOGGER.info("[Director] 操作系统: {} {}", System.getProperty("os.name"), System.getProperty("os.arch"));
        LOGGER.info("[Director] 可用处理器: {}", Runtime.getRuntime().availableProcessors());
        LOGGER.info("[Director] 最大内存: {} MB", Runtime.getRuntime().maxMemory() / 1024 / 1024);
        
        // 记录兼容性摘要
        LOGGER.info("[Director] 模组兼容性: {}", ModCompatibilityChecker.getCompatibilitySummary());

        // 记录配置摘要
        if (DirectorConfig.getConfig() != null) {
            LOGGER.info("[Director] 配置版本: {}", DirectorConfig.getConfig().configVersion);
            LOGGER.info("[Director] 调试模式: {}", DirectorConfig.isDebugMode());
            LOGGER.info("[Director] 性能优化: {}", DirectorConfig.getConfig().performance.enableOptimizations);
            LOGGER.info("[Director] UI主题: {}", DirectorConfig.getConfig().ui.defaultTheme);
        }
        
        // 记录国际化摘要
        LOGGER.info("[Director] 国际化系统: {}", DirectorI18n.getStatus());
        if (DirectorI18n.isInitialized()) {
            LOGGER.info("[Director] 当前语言: {}", DirectorI18n.getCurrentLanguage());
        }
        
        // 记录网络摘要
        LOGGER.info("[Director] 网络数据包: UI_OPEN, UI_CLOSE, CONFIG_SYNC, PROJECT_DATA");
        
        // 记录命令摘要
        LOGGER.info("[Director] 可用命令: /director, /cb");
    }
    
    /**
     * 记录系统信息
     */
    private void logSystemInfo() {
        LOGGER.debug("[Director] === 系统信息 ===");
        LOGGER.debug("[Director] 可用处理器: {}", Runtime.getRuntime().availableProcessors());
        LOGGER.debug("[Director] 最大内存: {} MB", Runtime.getRuntime().maxMemory() / 1024 / 1024);
        LOGGER.debug("[Director] 总内存: {} MB", Runtime.getRuntime().totalMemory() / 1024 / 1024);
        LOGGER.debug("[Director] 空闲内存: {} MB", Runtime.getRuntime().freeMemory() / 1024 / 1024);
    }
    
    
    /**
     * 检查模组是否已初始化
     */
    public static boolean isInitialized() {
        return initialized.get();
    }
}
