package com.director.client;

import com.director.client.input.DirectorInputSystem;
import net.minecraft.client.MinecraftClient;
import com.director.ui.DirectorScreen;
import com.director.DirectorMod;

/**
 * Director客户端控制器
 * 负责管理Director与Minecraft客户端的交互，包括：
 * - 十字星显示控制
 * - 方块交互控制
 * - 鼠标控制
 * - ImGui输入系统集成
 */
public class DirectorClientController {
    
    private static volatile DirectorClientController instance;
    private final MinecraftClient client;
    
    // 状态管理
    private boolean directorModeActive = false;
    private long lastDeactivateLogMs = 0L;
    
    private DirectorClientController() {
        this.client = MinecraftClient.getInstance();
    }
    
    /**
     * 获取单例实例
     */
    public static DirectorClientController getInstance() {
        if (instance == null) {
            synchronized (DirectorClientController.class) {
                if (instance == null) {
                    instance = new DirectorClientController();
                }
            }
        }
        return instance;
    }
    
    /**
     * 激活Director模式
     */
    public void activateDirectorMode() {
        if (directorModeActive || client.player == null) {
            return;
        }
        
        try {
            directorModeActive = true;
            suppressVanillaWeatherNow();
            DirectorMod.LOGGER.info("Director模式已激活 - 十字星已隐藏，游戏交互已禁用");
            
        } catch (Exception e) {
            DirectorMod.LOGGER.error("激活Director模式时出错", e);
        }
    }
    
    /**
     * 停用Director模式（导演模式关闭时调用）
     */
    public void deactivateDirectorMode() {
        // UI 打开期间：不要允许外部系统（相机 owner 切换 / 鼠标输入）反复停用模式。
        // 否则会导致“停用→清季节→恢复输入→Screen.tick 再激活”的抖动风暴，FPS 直接掉个位数。
        if (client != null && client.currentScreen instanceof DirectorScreen) {
            return; // soft-deactivate ignored while UI is open
        }
        forceDeactivateDirectorMode();
    }

    /**
     * 强制停用 Director 模式：用于真正退出导演模式/关闭 UI。
     * 注意：这个方法会执行恢复输入与清理季节效果等重操作。
     */
    public void forceDeactivateDirectorMode() {
        if (!directorModeActive) {
            return;
        }
        
        try {
            directorModeActive = false;
            // 恢复输入系统默认状态
            DirectorInputSystem.restoreDefaults();
            
            // ⭐ 清除季节效果（导演模式关闭时）
            if (client != null && client.world != null) {
                try {
                    com.director.season.SeasonSnowManager.get().restoreAll(client.world);
                    com.director.season.WaterFreezeManager.get().clearAll();
                    // 节流：避免异常路径下重复刷屏（同时减少字符串构造成本）
                    long now = System.currentTimeMillis();
                    if (now - lastDeactivateLogMs > 2000L) {
                        lastDeactivateLogMs = now;
                        DirectorMod.LOGGER.info("[Director] 导演模式关闭，已清除所有季节效果（积雪 + 冰冻水面）");
                    }
                } catch (Exception e) {
                    DirectorMod.LOGGER.warn("[Director] 清除季节效果时出错", e);
                }
            }
            
            long now = System.currentTimeMillis();
            if (now - lastDeactivateLogMs > 2000L) {
                lastDeactivateLogMs = now;
                DirectorMod.LOGGER.info("Director模式已停用 - 十字星已恢复，游戏交互已启用");
            }
            
        } catch (Exception e) {
            DirectorMod.LOGGER.error("停用Director模式时出错", e);
        }
    }
    
    /**
     * 检查是否应该隐藏十字星
     * 这个方法会被Mixin调用
     */
    public boolean shouldHideCrosshair() {
        // 检查是否有Director界面打开
        if (client != null && client.currentScreen instanceof DirectorScreen) {
            return true; // 始终在Director界面打开时隐藏十字星
        }
        
        // 或者如果明确激活了Director模式也隐藏
        return directorModeActive;
    }
    
    /**
     * 检查是否应该禁用方块交互
     * 这个方法会被Mixin调用
     */
    public boolean shouldDisableBlockInteraction() {
        // 检查是否有Director界面打开
        if (client != null && client.currentScreen instanceof DirectorScreen) {
            return true; // 始终在Director界面打开时禁用方块交互
        }
        
        // 或者如果明确激活了Director模式也禁用
        return directorModeActive;
    }
    
    /**
     * 检查Director模式是否激活
     * 新增方法用于统一状态检查
     */
    public boolean isDirectorModeActive() {
        return directorModeActive;
    }

    /**
     * 进入 Director 模式时立即清空客户端天气状态，避免等到下一次 screen tick
     * 才把原版降雨压下去，从而出现短暂下雨与亮度闪烁。
     */
    private void suppressVanillaWeatherNow() {
        if (client == null || client.world == null) {
            return;
        }
        try {
            client.world.setRainGradient(0.0f);
            client.world.setThunderGradient(0.0f);
            client.world.getLevelProperties().setRaining(false);
        } catch (Exception e) {
            DirectorMod.LOGGER.debug("[Director] 立即压制原版天气失败", e);
        }
    }

    /**
     * 外部开关：临时隐藏十字星/拦截交互（例如时间线接管相机时）。
     */
    public void setHideCrosshair(boolean hide) {
        // UI 打开期间十字星本就应隐藏（shouldHideCrosshair 会直接返回 true），
        // 不需要也不允许在这里反复切换模式状态（会导致抖动风暴）。
        if (client != null && client.currentScreen instanceof DirectorScreen) {
            return;
        }
        if (hide) {
            activateDirectorMode();
        } else {
            deactivateDirectorMode();
        }
    }

}
