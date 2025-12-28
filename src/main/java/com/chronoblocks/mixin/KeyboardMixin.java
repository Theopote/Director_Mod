package com.chronoblocks.mixin;

import com.chronoblocks.ui.input.InputFocusManager;
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
    private static final Logger LOGGER = LoggerFactory.getLogger("ChronoBlocks/KeyboardMixin");
    @Unique
    private static final boolean DEBUG = Boolean.getBoolean("chronoblocks.input.debug");

    /**
     * 拦截键盘按键事件
     * <p>
     * 当ChronoBlocks窗口打开且应该捕获键盘时，阻止游戏处理键盘输入
     * 注意：ImGui 通过 GLFW 回调直接接收输入，不需要我们转发
     */
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onKey(long window, int scanCode, KeyInput input, CallbackInfo ci) {
        InputFocusManager focusManager = InputFocusManager.getInstance();

        // 如果ChronoBlocks窗口激活且应该捕获键盘
        if (focusManager.shouldCaptureKeyboard()) {
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

        // 如果ChronoBlocks窗口激活且应该捕获键盘
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