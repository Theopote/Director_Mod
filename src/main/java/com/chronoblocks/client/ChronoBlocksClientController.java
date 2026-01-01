package com.chronoblocks.client;

import com.chronoblocks.client.input.ChronoBlocksInputSystem;
import net.minecraft.client.MinecraftClient;
import com.chronoblocks.ui.ChronoBlocksScreen;
import com.chronoblocks.ChronoBlocksMod;

/**
 * ChronoBlocks客户端控制器
 * 负责管理ChronoBlocks与Minecraft客户端的交互，包括：
 * - 十字星显示控制
 * - 方块交互控制
 * - 鼠标控制
 * - ImGui输入系统集成
 */
public class ChronoBlocksClientController {
    
    private static volatile ChronoBlocksClientController instance;
    private final MinecraftClient client;
    
    // 状态管理
    private boolean chronoBlocksModeActive = false;
    private long lastDeactivateLogMs = 0L;
    
    private ChronoBlocksClientController() {
        this.client = MinecraftClient.getInstance();
    }
    
    /**
     * 获取单例实例
     */
    public static ChronoBlocksClientController getInstance() {
        if (instance == null) {
            synchronized (ChronoBlocksClientController.class) {
                if (instance == null) {
                    instance = new ChronoBlocksClientController();
                }
            }
        }
        return instance;
    }
    
    /**
     * 激活ChronoBlocks模式
     */
    public void activateChronoBlocksMode() {
        if (chronoBlocksModeActive || client.player == null) {
            return;
        }
        
        try {
            chronoBlocksModeActive = true;
            ChronoBlocksMod.LOGGER.info("ChronoBlocks模式已激活 - 十字星已隐藏，游戏交互已禁用");
            
        } catch (Exception e) {
            ChronoBlocksMod.LOGGER.error("激活ChronoBlocks模式时出错", e);
        }
    }
    
    /**
     * 停用ChronoBlocks模式（导演模式关闭时调用）
     */
    public void deactivateChronoBlocksMode() {
        // UI 打开期间：不要允许外部系统（相机 owner 切换 / 鼠标输入）反复停用模式。
        // 否则会导致“停用→清季节→恢复输入→Screen.tick 再激活”的抖动风暴，FPS 直接掉个位数。
        if (client != null && client.currentScreen instanceof ChronoBlocksScreen) {
            return; // soft-deactivate ignored while UI is open
        }
        forceDeactivateChronoBlocksMode();
    }

    /**
     * 强制停用 ChronoBlocks 模式：用于真正退出导演模式/关闭 UI。
     * 注意：这个方法会执行恢复输入与清理季节效果等重操作。
     */
    public void forceDeactivateChronoBlocksMode() {
        if (!chronoBlocksModeActive) {
            return;
        }
        
        try {
            chronoBlocksModeActive = false;
            // 恢复输入系统默认状态
            ChronoBlocksInputSystem.restoreDefaults();
            
            // ⭐ 清除季节效果（导演模式关闭时）
            if (client != null && client.world != null) {
                try {
                    com.chronoblocks.season.SeasonSnowManager.get().restoreAll(client.world);
                    com.chronoblocks.season.WaterFreezeManager.get().clearAll();
                    // 节流：避免异常路径下重复刷屏（同时减少字符串构造成本）
                    long now = System.currentTimeMillis();
                    if (now - lastDeactivateLogMs > 2000L) {
                        lastDeactivateLogMs = now;
                        ChronoBlocksMod.LOGGER.info("[ChronoBlocks] 导演模式关闭，已清除所有季节效果（积雪 + 冰冻水面）");
                    }
                } catch (Exception e) {
                    ChronoBlocksMod.LOGGER.warn("[ChronoBlocks] 清除季节效果时出错", e);
                }
            }
            
            long now = System.currentTimeMillis();
            if (now - lastDeactivateLogMs > 2000L) {
                lastDeactivateLogMs = now;
                ChronoBlocksMod.LOGGER.info("ChronoBlocks模式已停用 - 十字星已恢复，游戏交互已启用");
            }
            
        } catch (Exception e) {
            ChronoBlocksMod.LOGGER.error("停用ChronoBlocks模式时出错", e);
        }
    }
    
    /**
     * 检查是否应该隐藏十字星
     * 这个方法会被Mixin调用
     */
    public boolean shouldHideCrosshair() {
        // 检查是否有ChronoBlocks界面打开
        if (client != null && client.currentScreen instanceof ChronoBlocksScreen) {
            return true; // 始终在ChronoBlocks界面打开时隐藏十字星
        }
        
        // 或者如果明确激活了ChronoBlocks模式也隐藏
        return chronoBlocksModeActive;
    }
    
    /**
     * 检查是否应该禁用方块交互
     * 这个方法会被Mixin调用
     */
    public boolean shouldDisableBlockInteraction() {
        // 检查是否有ChronoBlocks界面打开
        if (client != null && client.currentScreen instanceof ChronoBlocksScreen) {
            return true; // 始终在ChronoBlocks界面打开时禁用方块交互
        }
        
        // 或者如果明确激活了ChronoBlocks模式也禁用
        return chronoBlocksModeActive;
    }
    
    /**
     * 检查ChronoBlocks模式是否激活
     * 新增方法用于统一状态检查
     */
    public boolean isChronoBlocksModeActive() {
        return chronoBlocksModeActive;
    }

    /**
     * 外部开关：临时隐藏十字星/拦截交互（例如时间线接管相机时）。
     */
    public void setHideCrosshair(boolean hide) {
        // UI 打开期间十字星本就应隐藏（shouldHideCrosshair 会直接返回 true），
        // 不需要也不允许在这里反复切换模式状态（会导致抖动风暴）。
        if (client != null && client.currentScreen instanceof ChronoBlocksScreen) {
            return;
        }
        if (hide) {
            activateChronoBlocksMode();
        } else {
            deactivateChronoBlocksMode();
        }
    }

}
