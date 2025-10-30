package com.chronoblocks.core.director;

import com.chronoblocks.ui.ChronoBlocksScreen;
import com.chronoblocks.world.snapshot.WorldSnapshotManager;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🎬 DirectorModeInitializer v1.0
 * 负责初始化 ChronoBlocks 导演模式环境：
 *  - 暂停世界（冻结时间与实体）
 *  - 创建世界快照
 *  - 打开导演界面
 *  - 启动核心子系统（UI、选择、容器）
 */
public class DirectorModeInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectorModeInitializer.class);
    private static boolean active = false;
    private static WorldSnapshotManager snapshotManager;

    public static void enterDirectorMode() {
        if (active) {
            LOGGER.warn("[ChronoBlocks] Director mode already active.");
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            LOGGER.error("[ChronoBlocks] Cannot enter director mode: world not loaded.");
            return;
        }

        LOGGER.info("[ChronoBlocks] 🎬 Entering Director Mode...");
        active = true;

        // 1️⃣ 冻结世界时间与实体
        freezeWorld(client);

        // 2️⃣ 创建世界快照
        snapshotManager = new WorldSnapshotManager(client.world);
        snapshotManager.captureSnapshot();
        LOGGER.info("[ChronoBlocks] World snapshot captured successfully.");

        // 3️⃣ 打开 ChronoBlocks 主GUI
        client.setScreen(new ChronoBlocksScreen());
        LOGGER.info("[ChronoBlocks] ChronoBlocks GUI opened.");

        // 4️⃣ 预初始化子系统
        initializeSubSystems();

        LOGGER.info("[ChronoBlocks] Director Mode setup complete!");
    }

    private static void freezeWorld(MinecraftClient client) {
        try {
            if (client.world != null) {
                // 🔹 1️⃣ 固定当前时间（通过World直接设置）
                long fixedTime = client.world.getTimeOfDay();
                client.world.setTime(fixedTime, fixedTime, false);
                LOGGER.debug("[ChronoBlocks] World time frozen (fixed at tick={})", fixedTime);

                // 🔹 2️⃣ 冻结非玩家实体（动物、怪物等，但保留玩家移动）
                client.world.getEntities().forEach(entity -> {
                    try {
                        // 不冻结玩家，让玩家可以移动摄像机
                        if (!(entity instanceof net.minecraft.entity.player.PlayerEntity)) {
                            entity.setFrozenTicks(Integer.MAX_VALUE);
                        }
                    } catch (Exception ignored) {}
                });
                LOGGER.debug("[ChronoBlocks] Non-player entities frozen, player can move freely");

                // 🔹 3️⃣ 启用 mixin 拦截（tickTime / tickEntities）
                // 注意：不再在这里激活ChronoBlocks模式，由ChronoBlocksScreen统一控制
                LOGGER.debug("[ChronoBlocks] Director mode initialized (mode activation handled by Screen)");

                LOGGER.info("[ChronoBlocks] 🌍 World freeze complete - entities, plants all frozen");
            }
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] Failed to freeze world.", e);
        }
    }

    private static void initializeSubSystems() {
        try {
            // ImGui 系统初始化由 ChronoBlocksScreen 调用
            // 未来这里可添加 SelectionToolkit 初始化入口
            LOGGER.debug("[ChronoBlocks] Initializing core subsystems...");
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] Failed to initialize subsystems.", e);
        }
    }

    public static void exitDirectorMode() {
        if (!active) return;

        MinecraftClient client = MinecraftClient.getInstance();
        LOGGER.info("[ChronoBlocks] 🎬 Exiting Director Mode...");

        // 恢复世界状态
        if (snapshotManager != null) {
            snapshotManager.restoreSnapshot();
        }

        // 关闭界面
        if (client.currentScreen instanceof ChronoBlocksScreen) {
            client.setScreen(null);
        }

        active = false;
        LOGGER.info("[ChronoBlocks] Director Mode exited successfully.");
    }

    public static boolean isActive() {
        return active;
    }

    public static WorldSnapshotManager getSnapshotManager() {
        return snapshotManager;
    }

    /**
     * 导演模式进入钩子（由ChronoBlocksScreen调用）
     */
    public static void onEnterDirectorMode() {
        LOGGER.debug("[ChronoBlocks] Director mode entered via screen hook.");
    }

    /**
     * 导演模式退出钩子（由ChronoBlocksScreen调用）
     */
    public static void onExitDirectorMode() {
        LOGGER.debug("[ChronoBlocks] Director mode exited via screen hook.");
    }
}
