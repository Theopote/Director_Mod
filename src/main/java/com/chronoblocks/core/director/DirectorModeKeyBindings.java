package com.chronoblocks.core.director;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * 🎬 DirectorModeKeyBindings v1.0
 * 导演模式快捷键绑定
 */
public class DirectorModeKeyBindings {
    
    // 导演模式快捷键（延迟初始化）
    public static KeyBinding TOGGLE_DIRECTOR_MODE;
    public static KeyBinding QUICK_ENTER_DIRECTOR;
    public static KeyBinding QUICK_EXIT_DIRECTOR;
    
    /**
     * 注册导演模式快捷键
     */
    public static void registerKeyBindings() {
        try {
            TOGGLE_DIRECTOR_MODE = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                    "key.chronoblocks.toggle_director_mode",
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_F12, // F12键
                    "category.chronoblocks.director"
                )
            );
            
            QUICK_ENTER_DIRECTOR = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                    "key.chronoblocks.quick_enter_director",
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_F11, // F11键
                    "category.chronoblocks.director"
                )
            );
            
            QUICK_EXIT_DIRECTOR = KeyBindingHelper.registerKeyBinding(
                new KeyBinding(
                    "key.chronoblocks.quick_exit_director",
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_F10, // F10键
                    "category.chronoblocks.director"
                )
            );
        } catch (Exception e) {
            // 如果注册失败，使用默认值
            System.err.println("[DirectorModeKeyBindings] 快捷键注册失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理快捷键输入
     */
    public static void handleKeyInput() {
        if (TOGGLE_DIRECTOR_MODE != null && TOGGLE_DIRECTOR_MODE.wasPressed()) {
            if (DirectorModeBootstrap.isInitialized()) {
                DirectorModeBootstrap.quickExit();
            } else {
                DirectorModeBootstrap.quickEnter();
            }
        }
        
        if (QUICK_ENTER_DIRECTOR != null && QUICK_ENTER_DIRECTOR.wasPressed()) {
            DirectorModeBootstrap.quickEnter();
        }
        
        if (QUICK_EXIT_DIRECTOR != null && QUICK_EXIT_DIRECTOR.wasPressed()) {
            DirectorModeBootstrap.quickExit();
        }
    }
}
