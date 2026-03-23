package com.director.mixin;

import com.director.ui.input.InputFocusManager;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 键盘输入Mixin
 * <p>
 * 拦截键盘输入事件，根据InputFocusManager的状态决定是否允许游戏处理输入
 */
@Mixin(Keyboard.class)
public class KeyboardMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("Director/KeyboardMixin");
    @Unique
    private static final boolean DEBUG = Boolean.getBoolean("director.input.debug");

    /**
     * 拦截键盘按键事件
     * <p>
     * 当Director窗口打开且应该捕获键盘时，阻止游戏处理键盘输入
     * 注意：ImGui 通过 GLFW 回调直接接收输入，不需要我们转发
     */
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onKey(long window, int scanCode, KeyInput input, CallbackInfo ci) {
        InputFocusManager focusManager = InputFocusManager.getInstance();

        // 如果Director窗口激活且应该捕获键盘
        if (focusManager.shouldCaptureKeyboard()) {
            // ✅ 优先使用新的 ShortcutDispatcher（Facade 架构）
            // 尝试获取 DirectorScreen 的 UIManager 并调用 ShortcutDispatcher
            boolean handledByNewSystem = false;
            try {
                // 通过 MinecraftClient 获取当前 Screen
                var client = net.minecraft.client.MinecraftClient.getInstance();
                if (client != null && client.currentScreen instanceof com.director.ui.DirectorScreen directorScreen) {
                    var uiManager = directorScreen.getUIManager();
                    if (uiManager != null) {
                        var shortcutDispatcher = uiManager.getShortcutDispatcher();
                        if (shortcutDispatcher != null) {
                            int keyCode = input.key();
                            int mods = input.modifiers();
                            // 使用新的 ShortcutDispatcher 处理快捷键
                            shortcutDispatcher.handleGLFWKeyEvent(keyCode, org.lwjgl.glfw.GLFW.GLFW_PRESS, mods);
                            handledByNewSystem = true;
                        }
                    }
                }
            } catch (Exception e) {
                if (DEBUG) {
                    LOGGER.debug("[KeyboardMixin] 无法获取 ShortcutDispatcher: {}", e.getMessage());
                }
            }
            
            // 如果新系统没有处理，回退到旧的 GlobalShortcutManager
            if (!handledByNewSystem) {
                try {
                    var mgr = com.director.ui.input.GlobalShortcutManager.getInstance();
                    int keyCode = input.key();
                    int mods = input.modifiers();
                    boolean ctrl = (mods & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0;
                    boolean shift = (mods & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
                    boolean alt = (mods & org.lwjgl.glfw.GLFW.GLFW_MOD_ALT) != 0;
                    mgr.handleGLFWInput(keyCode, ctrl, shift, alt);
                } catch (Exception ignored) {}
            }

            if (DEBUG) {
                LOGGER.debug("[KeyboardMixin] 拦截键盘事件: key={}, scancode={}, mods={} (阻止游戏处理，ImGui通过GLFW回调接收)",
                        input.key(), input.scancode(), input.modifiers());
            }
            // 取消Minecraft的处理，但ImGui已经通过GLFW回调接收到了输入
            ci.cancel();
        }
    }

    /**
     * 拦截字符输入事件
     * <p>
     * 用于文本输入框等场景
     * 注意：ImGui 通过 GLFW 回调直接接收输入，不需要我们转发
     */
    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
    private void onChar(long window, CharInput input, CallbackInfo ci) {
        InputFocusManager focusManager = InputFocusManager.getInstance();

        // 如果Director窗口激活且应该捕获键盘
        if (focusManager.shouldCaptureKeyboard()) {
            if (DEBUG) {
                LOGGER.debug("[KeyboardMixin] 拦截字符输入事件: codePoint={} (阻止游戏处理，ImGui通过GLFW回调接收)",
                        input.codepoint());
            }
            // 取消Minecraft的处理，但ImGui已经通过GLFW回调接收到了输入
            ci.cancel();
        }
    }
}
