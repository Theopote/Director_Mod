package com.director.mixin;

import com.director.client.DirectorClientController;
import com.director.ui.DirectorScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 🎮 CursorMixin - 完全切断原版鼠标控制
 * ==============================================
 * 在Director模式下：
 * - 永远取消lockCursor，让GLFW光标保持NORMAL
 * - 确保ImGui完全接管鼠标输入
 * - 阻止Minecraft原版鼠标捕获
 */
@Mixin(Mouse.class)
public class CursorMixin {
    
    @Final
    @Shadow
    private MinecraftClient client;
    
    /**
     * 🚫 完全阻止原版鼠标锁定
     * 当Director界面打开时，永远取消锁定，让GLFW光标保持NORMAL
     */
    @Inject(method = "lockCursor", at = @At("HEAD"), cancellable = true)
    private void onLockCursor(CallbackInfo ci) {
        if (isDirectorActive()) {
            // 强制设置光标为NORMAL模式，完全阻止锁定
            long window = client.getWindow().getHandle();
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            ci.cancel(); // 🚫 完全取消原版锁定逻辑
        }
    }
    
    /**
     * 🔓 确保鼠标解锁状态
     * 在Director模式下强制保持光标可见
     */
    @Inject(method = "unlockCursor", at = @At("HEAD"))
    private void onUnlockCursor(CallbackInfo ci) {
        if (isDirectorActive()) {
            // 确保光标始终可见
            long window = client.getWindow().getHandle();
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
    }
    
    /**
     * 检查Director是否处于活动状态
     */
    @Unique
    private boolean isDirectorActive() {
        if (client == null) return false;
        
        // 检查Director界面是否打开
        if (client.currentScreen instanceof DirectorScreen) {
            return true;
        }
        
        // 检查Director模式是否激活
        DirectorClientController controller = DirectorClientController.getInstance();
        return controller != null && controller.isDirectorModeActive();
    }
}
