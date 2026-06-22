package com.director.mixin;

import com.director.client.camera.CameraRenderOverride;
import com.director.export.video.VideoExportManager;
import com.director.ui.DirectorScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
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
        
        if (client.currentScreen instanceof DirectorScreen screen) {
            // 🎥 导出期间：锁定玩家移动输入（省资源模式下屏幕镜头会跟随导出，禁止误操作干扰帧内容）
            if (VideoExportManager.getInstance().isExporting()) {
                this.movementVector = Vec2f.ZERO;
                this.playerInput = new PlayerInput(false, false, false, false, false, false, false);
                ci.cancel();
                return;
            }

            // 直接检查鼠标是否在UI上
            boolean mouseOverGui = screen.isMouseOverDirectorGui(client.mouse.getX(), client.mouse.getY());
            
            // 如果鼠标在UI外，允许移动输入
            if (!mouseOverGui) {
                var window = client.getWindow();
                boolean anyMovementKey = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_W)
                        || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_S)
                        || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_A)
                        || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_D)
                        || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_SPACE)
                        || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                        || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_CONTROL);
                if (anyMovementKey) {
                    CameraRenderOverride cameraState = CameraRenderOverride.get();
                    var controller = com.director.timeline.playback.camera.TimelineCameraController.getInstance();
                    if (cameraState.isTimelineOwner() && !cameraState.isTimelineActive()) {
                        controller.releaseCameraToPlayer();
                    }
                }

                float forwardAmt = 0.0F;
                float sidewaysAmt = 0.0F;
                
                // 检查按键并更新移动
                if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_W)) forwardAmt += 1.0F;
                if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_S)) forwardAmt -= 1.0F;
                if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_D)) sidewaysAmt -= 1.0F;
                if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_A)) sidewaysAmt += 1.0F;
                
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
                this.movementVector = new Vec2f(sidewaysAmt, forwardAmt);
                
                // 处理慢速移动（潜行）
                if (sneak) {
                    this.movementVector = this.movementVector.multiply(0.3F);
                }
                
                ci.cancel(); // 阻止原版处理
            } else {
                this.movementVector = Vec2f.ZERO;
                this.playerInput = new PlayerInput(false, false, false, false, false, false, false);
                ci.cancel();
            }
        }
    }
}
