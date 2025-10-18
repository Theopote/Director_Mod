package com.chronoblocks.mixin;

import com.chronoblocks.ui.ChronoBlocksScreen;
import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 鼠标输入Mixin
 * <p>
 * 拦截鼠标输入事件，根据光标位置决定是否允许游戏处理输入
 */
@Mixin(Mouse.class)
public class MouseMixin {

    @Shadow @Final private MinecraftClient client;
    @Shadow private double cursorDeltaX;
    @Shadow private double cursorDeltaY;
    
    @Unique
    private boolean chronoblocks$isDragging = false;
    
    @Unique
    private static final double LOOK_SENSITIVITY = 5.0;  // 🎯 提高灵敏度，让视角移动更快
    
    /**
     * 拦截光标移动事件
     * <p>
     * 当鼠标在UI外且按住中键时，处理视角移动
     */
    @Inject(method = "onCursorPos", at = @At("HEAD"))
    private void onCursorPos(long window, double x, double y, CallbackInfo ci) {
        if (client.currentScreen instanceof ChronoBlocksScreen screen) {
            // 直接检查鼠标是否在UI上
            boolean mouseOverGui = screen.isMouseOverChronoBlocksGui(x, y);
            
            // 如果鼠标在UI外且正在拖动（按住中键），处理视角移动
            if (!mouseOverGui && chronoblocks$isDragging) {
                double mouseSensitivity = client.options.getMouseSensitivity().getValue();
                
                if (client.player != null) {
                    client.player.changeLookDirection(
                        cursorDeltaX * mouseSensitivity * LOOK_SENSITIVITY,
                        cursorDeltaY * mouseSensitivity * LOOK_SENSITIVITY
                    );
                }
            }
        }
    }
    
    /**
     * 拦截鼠标按钮事件
     */
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        // 中键事件处理
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            if (action == GLFW.GLFW_PRESS) {
                chronoblocks$isDragging = true;
            } else if (action == GLFW.GLFW_RELEASE) {
                chronoblocks$isDragging = false;
            }
            return;
        }
        
        if (client.currentScreen instanceof ChronoBlocksScreen screen) {
            // 其他鼠标按钮：如果在UI上，拦截事件
            boolean mouseOverGui = screen.isMouseOverChronoBlocksGui(client.mouse.getX(), client.mouse.getY());
            if (mouseOverGui) {
                ci.cancel();
            }
        }
    }
    
    /**
     * 拦截鼠标滚轮事件
     */
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (client.currentScreen instanceof ChronoBlocksScreen screen) {
            boolean mouseOverGui = screen.isMouseOverChronoBlocksGui(client.mouse.getX(), client.mouse.getY());
            if (mouseOverGui) {
                ci.cancel();
            }
        }
    }
    
    /**
     * 阻止在UI上时锁定光标
     */
    @Inject(method = "lockCursor", at = @At("HEAD"), cancellable = true)
    private void onLockCursor(CallbackInfo ci) {
        if (client.currentScreen instanceof ChronoBlocksScreen screen) {
            boolean mouseOverGui = screen.isMouseOverChronoBlocksGui(client.mouse.getX(), client.mouse.getY());
            if (mouseOverGui) {
                ci.cancel();
            }
        }
    }
}