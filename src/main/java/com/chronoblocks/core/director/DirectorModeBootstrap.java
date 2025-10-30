package com.chronoblocks.core.director;

import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.selection.SelectionManager;
import com.chronoblocks.core.selection.SelectionContextBridge;
import com.chronoblocks.world.snapshot.WorldSnapshotManager;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🎬 DirectorModeBootstrap v1.0
 * 导演模式引导程序
 * 
 * 负责：
 * - 初始化导演模式环境
 * - 设置核心子系统
 * - 提供导演模式入口点
 */
public class DirectorModeBootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectorModeBootstrap.class);
    
    private static boolean initialized = false;
    private static SelectionContextBridge contextBridge;
    
    /**
     * 初始化导演模式
     */
    public static void initialize() {
        if (initialized) {
            LOGGER.warn("[DirectorModeBootstrap] 导演模式已经初始化");
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            LOGGER.error("[DirectorModeBootstrap] 无法初始化导演模式：世界未加载");
            return;
        }
        
        LOGGER.info("[DirectorModeBootstrap] 🎬 初始化导演模式环境...");
        
        try {
            // 1️⃣ 初始化核心管理器
            SelectionManager selectionManager = SelectionManager.get();
            ContainerManager containerManager = new ContainerManager(); // 简化实现
            WorldSnapshotManager snapshotManager = new WorldSnapshotManager(client.world);
            
            // 2️⃣ 创建选择上下文桥接
            contextBridge = new SelectionContextBridge(selectionManager, containerManager, snapshotManager);
            
            // 3️⃣ 设置导演模式
            DirectorModeInitializer.enterDirectorMode();
            
            initialized = true;
            LOGGER.info("[DirectorModeBootstrap] ✅ 导演模式初始化完成");
            
        } catch (Exception e) {
            LOGGER.error("[DirectorModeBootstrap] 导演模式初始化失败", e);
        }
    }
    
    /**
     * 退出导演模式
     */
    public static void shutdown() {
        if (!initialized) {
            return;
        }
        
        LOGGER.info("[DirectorModeBootstrap] 🎬 退出导演模式...");
        
        try {
            // 1️⃣ 退出导演模式
            DirectorModeInitializer.exitDirectorMode();
            
            // 2️⃣ 清理资源
            contextBridge = null;
            
            initialized = false;
            LOGGER.info("[DirectorModeBootstrap] ✅ 导演模式退出完成");
            
        } catch (Exception e) {
            LOGGER.error("[DirectorModeBootstrap] 导演模式退出失败", e);
        }
    }
    
    /**
     * 获取选择上下文桥接
     */
    public static SelectionContextBridge getContextBridge() {
        return contextBridge;
    }
    
    /**
     * 检查是否已初始化
     */
    public static boolean isInitialized() {
        return initialized;
    }
    
    /**
     * 快速进入导演模式（用于快捷键或命令）
     */
    public static void quickEnter() {
        if (initialized) {
            LOGGER.info("[DirectorModeBootstrap] 导演模式已激活");
            return;
        }
        
        initialize();
    }
    
    /**
     * 快速退出导演模式（用于快捷键或命令）
     */
    public static void quickExit() {
        if (!initialized) {
            LOGGER.info("[DirectorModeBootstrap] 导演模式未激活");
            return;
        }
        
        shutdown();
    }
}
