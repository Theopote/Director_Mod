package com.director.mixin;

import com.director.core.DirectorServices;
import com.director.ui.DirectorScreen;
import com.director.ui.imgui.ImGuiRenderer;
import com.director.ui.input.InputFocusManager;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 键盘输入 Mixin：Director 打开时的快捷键唯一入口（{@link com.director.ui.input.ShortcutDispatcher}），
 * 并在主窗口向 ImGui 单次投递按键/字符（不安装 GLFW 回调，避免双倍字符）。
 */
@Mixin(Keyboard.class)
public class KeyboardMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("Director/KeyboardMixin");
    @Unique
    private static final boolean DEBUG = Boolean.getBoolean("director.input.debug");

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onKey(long window, int action, KeyInput input, CallbackInfo ci) {
        InputFocusManager focusManager = InputFocusManager.getInstance();
        boolean textInputActive = false;
        try {
            textInputActive = imgui.ImGui.getIO() != null && imgui.ImGui.getIO().getWantTextInput();
        } catch (Exception ignored) {
            // ImGui 帧间状态不可用时，沿用既有捕获逻辑
        }

        feedImGuiKey(window, input, action);

        if (!textInputActive && input.key() == GLFW.GLFW_KEY_ESCAPE
                && MinecraftClient.getInstance().currentScreen instanceof DirectorScreen
                && com.director.ui.lighting.PointLightController.get().isActive()) {
            com.director.ui.lighting.PointLightController.get().stop();
            ci.cancel();
            return;
        }
        if (!textInputActive) {
            if ((input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0
                    && (input.key() == GLFW.GLFW_KEY_Z || input.key() == GLFW.GLFW_KEY_Y)) {
                com.director.ui.lighting.PointLightParameterEditing.finish();
            }
            if (com.director.ui.lighting.PointLightController.get().isActive()
                    && com.director.client.gizmo.GizmoManager.get().isDraggingHandle()
                    && (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0
                    && (input.key() == GLFW.GLFW_KEY_Z || input.key() == GLFW.GLFW_KEY_Y)) {
                com.director.ui.lighting.PointLightController.get().cancelDrag();
                com.director.client.gizmo.GizmoManager.get().deactivate();
            }
            dispatchDirectorShortcut(input, action);
        }

        if (focusManager.shouldCaptureKeyboard()) {
            if (DEBUG) {
                LOGGER.debug("[KeyboardMixin] 拦截键盘事件: key={}, action={}, mods={}",
                        input.key(), action, input.modifiers());
            }
            ci.cancel();
        }
    }

    @Unique
    private static void feedImGuiKey(long window, KeyInput input, int action) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || !(client.currentScreen instanceof DirectorScreen)) {
            return;
        }
        try {
            ImGuiRenderer imgui = DirectorServices.get().imgui();
            if (imgui != null && imgui.isInitialized()) {
                imgui.feedKeyEvent(window, input.key(), input.scancode(), action, input.modifiers());
            }
        } catch (Exception e) {
            if (DEBUG) {
                LOGGER.debug("[KeyboardMixin] 无法转发按键到 ImGui: {}", e.getMessage());
            }
        }
    }

    @Unique
    private static void dispatchDirectorShortcut(KeyInput input, int action) {
        try {
            var client = MinecraftClient.getInstance();
            if (client == null || !(client.currentScreen instanceof DirectorScreen directorScreen)) {
                return;
            }
            var uiManager = directorScreen.getUIManager();
            if (uiManager == null) {
                return;
            }
            var shortcutDispatcher = uiManager.getShortcutDispatcher();
            if (shortcutDispatcher != null) {
                shortcutDispatcher.handleGLFWKeyEvent(input.key(), action, input.modifiers());
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

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.currentScreen instanceof DirectorScreen) {
            try {
                ImGuiRenderer imgui = DirectorServices.get().imgui();
                if (imgui != null && imgui.isInitialized() && input.isValidChar()) {
                    imgui.feedCharEvent(window, input.codepoint());
                }
            } catch (Exception e) {
                if (DEBUG) {
                    LOGGER.debug("[KeyboardMixin] 无法转发字符到 ImGui: {}", e.getMessage());
                }
            }
        }

        if (focusManager.shouldCaptureKeyboard()) {
            if (DEBUG) {
                LOGGER.debug("[KeyboardMixin] 拦截字符输入事件: codePoint={}", input.codepoint());
            }
            ci.cancel();
        }
    }
}
