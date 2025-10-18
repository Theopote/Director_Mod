package com.chronoblocks.mixin;

import com.chronoblocks.ui.ChronoBlocksScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.PlayerInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 键盘输入Mixin
 * <p>
 * 处理WASD等移动键，当鼠标在UI外时允许玩家移动
 */
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin extends Input {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onMovementTick(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        if (client.currentScreen instanceof ChronoBlocksScreen screen) {
            // 直接检查鼠标是否在UI上
            boolean mouseOverGui = screen.isMouseOverChronoBlocksGui(client.mouse.getX(), client.mouse.getY());
            
            // 如果鼠标在UI外，允许移动输入
            if (!mouseOverGui) {
                long window = client.getWindow().getHandle();
                
                // 直接设置移动状态
                this.movementForward = 0.0F;
                this.movementSideways = 0.0F;
                
                // 检查按键并更新移动
                if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_W)) this.movementForward += 1.0F;
                if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_S)) this.movementForward -= 1.0F;
                if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_D)) this.movementSideways -= 1.0F;
                if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_A)) this.movementSideways += 1.0F;
                
                // 更新玩家输入状态
                boolean forward = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_W);
                boolean backward = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_S);
                boolean left = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_A);
                boolean right = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_D);
                boolean jump = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_SPACE);
                boolean sneak = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_SHIFT);
                boolean sprint = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_CONTROL);
                
                // 更新 PlayerInput
                this.playerInput = new PlayerInput(forward, backward, left, right, jump, sneak, sprint);
                
                // 处理慢速移动（潜行）
                if (sneak) {
                    this.movementSideways *= 0.3F;
                    this.movementForward *= 0.3F;
                }
                
                ci.cancel(); // 阻止原版处理
            }
        }
    }
}