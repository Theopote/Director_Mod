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
 * 键盘输入 Mixin：Director 打开时的快捷键唯一入口（{@link com.director.ui.input.ShortcutDispatcher}）。
 * <p>
 * 快捷键在 DirectorScreen 激活且非文本输入时经 GLFW 层分发；仅当 {@link InputFocusManager#shouldCaptureKeyboard()}
 * 为 true 时才取消 Minecraft 按键处理（ImGui 仍通过 GLFW 回调接收输入）。
 */
@Mixin(Keyboard.class)
public class KeyboardMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("Director/KeyboardMixin");
    @Unique
    private static final boolean DEBUG = Boolean.getBoolean("director.input.debug");

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onKey(long window, int scanCode, KeyInput input, CallbackInfo ci) {
        InputFocusManager focusManager = InputFocusManager.getInstance();
        boolean textInputActive = false;
        try {
            textInputActive = imgui.ImGui.getIO() != null && imgui.ImGui.getIO().getWantTextInput();
        } catch (Exception ignored) {
            // ImGui 帧间状态不可用时，沿用既有捕获逻辑
        }

        if (!textInputActive) {
            dispatchDirectorShortcut(input);
        }

        if (focusManager.shouldCaptureKeyboard()) {
            if (DEBUG) {
                LOGGER.debug("[KeyboardMixin] 拦截键盘事件: key={}, scancode={}, mods={} (阻止游戏处理，ImGui通过GLFW回调接收)",
                        input.key(), input.scancode(), input.modifiers());
            }
            ci.cancel();
        }
    }

    @Unique
    private static void dispatchDirectorShortcut(KeyInput input) {
        try {
            var client = net.minecraft.client.MinecraftClient.getInstance();
            if (client == null || !(client.currentScreen instanceof com.director.ui.DirectorScreen directorScreen)) {
                return;
            }
            var uiManager = directorScreen.getUIManager();
            if (uiManager == null) {
                return;
            }
            var shortcutDispatcher = uiManager.getShortcutDispatcher();
            if (shortcutDispatcher != null) {
                shortcutDispatcher.handleGLFWKeyEvent(
                        input.key(), org.lwjgl.glfw.GLFW.GLFW_PRESS, input.modifiers());
            }
        } catch (Exception e) {
            if (DEBUG) {
                LOGGER.debug("[KeyboardMixin] 无法分发快捷键: {}", e.getMessage());
            }
        }
    }

    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
    private void onChar(long window, CharInput input, CallbackInfo ci) {
        InputFocusManager focusManager = InputFocusManager.getInstance();

        if (focusManager.shouldCaptureKeyboard()) {
            if (DEBUG) {
                LOGGER.debug("[KeyboardMixin] 拦截字符输入事件: codePoint={} (阻止游戏处理，ImGui通过GLFW回调接收)",
                        input.codepoint());
            }
            ci.cancel();
        }
    }
}
