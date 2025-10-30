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
     * 停用ChronoBlocks模式
     */
    public void deactivateChronoBlocksMode() {
        if (!chronoBlocksModeActive) {
            return;
        }
        
        try {
            chronoBlocksModeActive = false;
            // 恢复输入系统默认状态
            ChronoBlocksInputSystem.restoreDefaults();
            ChronoBlocksMod.LOGGER.info("ChronoBlocks模式已停用 - 十字星已恢复，游戏交互已启用");
            
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

}
