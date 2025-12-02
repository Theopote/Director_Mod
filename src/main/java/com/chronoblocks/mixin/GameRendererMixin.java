package com.chronoblocks.mixin;

import com.chronoblocks.client.ChronoBlocksClientController;
import com.chronoblocks.ui.timeline.playback.camera.CameraRuntime;
import com.chronoblocks.ui.timeline.playback.camera.CameraSample;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 🎬 GameRendererMixin v4.0 - ChronoBlocks 相关渲染控制
 *
 * <p>功能：
 * <ul>
 *   <li>隐藏第一人称手臂与物品渲染（当需要时）</li>
 *   <li>控制玩家模型可见性</li>
 *   <li>在电影模式下覆盖 FOV</li>
 *   <li>⭐ 在电影模式下应用 Roll（侧倾角）旋转到视图矩阵</li>
 * </ul>
 * 
 * <p>⭐ Roll 实现：
 * <ul>
 *   <li>Minecraft 原版 Camera 不支持 Roll，只能通过旋转视图矩阵实现</li>
 *   <li>在 renderWorld 中，在摄像机变换应用到矩阵后，额外应用 Z 轴旋转</li>
 *   <li>这实现了完整的 6DOF（6 自由度）摄像机控制</li>
 * </ul>
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void onRenderHand(Camera camera, float tickDelta, org.joml.Matrix4f matrix4f, CallbackInfo ci) {
        if (ChronoBlocksClientController.getInstance().shouldHideCrosshair()) {
            ci.cancel();
        }
    }

    /**
     * ⭐ 应用摄像机 Roll（侧倾角）旋转到视图矩阵
     * 
     * <p>注入时机：在 renderWorld 方法中，在 MatrixStack 创建后、被使用前
     * 这样可以确保 Roll 旋转被应用到整个视图矩阵，影响所有渲染内容
     * 
     * <p>技术细节：
     * <ul>
     *   <li>Roll 是围绕 Z 轴（摄像机看向的方向）的旋转</li>
     *   <li>使用 RotationAxis.POSITIVE_Z 创建四元数旋转</li>
     *   <li>通过 MatrixStack.multiply() 将旋转应用到视图矩阵</li>
     * </ul>
     * 
     * <p>⭐ 正确的注入方式：
     * <ul>
     *   <li>renderWorld 方法签名：renderWorld(RenderTickCounter renderTickCounter)</li>
     *   <li>MatrixStack 是在方法内部创建的局部变量（第 689 行）</li>
     *   <li>需要在 MatrixStack 创建后、被使用前注入</li>
     *   <li>使用 @ModifyVariable 来修改局部变量 MatrixStack</li>
     * </ul>
     * 
     * <p>查询方法签名的方法：
     * <ol>
     *   <li>运行 <code>./gradlew genSources</code> 生成 Minecraft 源码</li>
     *   <li>查看 <code>build/tmp/mc-client-src/net/minecraft/client/render/GameRenderer.java</code></li>
     *   <li>找到目标方法，查看其参数列表和局部变量</li>
     *   <li>根据实际签名调整 Mixin 方法参数</li>
     * </ol>
     */
    @org.spongepowered.asm.mixin.injection.ModifyVariable(
        method = "renderWorld",
        at = @At(
            value = "STORE",
            ordinal = 0  // 第一个 MatrixStack 局部变量的存储操作
        ),
        ordinal = 0  // 第一个 MatrixStack 类型的局部变量
    )
    private MatrixStack chronoblocks$applyRollToMatrix(MatrixStack matrixStack) {
        CameraRuntime runtime = CameraRuntime.getInstance();
        // ✅ 只有电影模式才应用 Roll（使用统一的条件判断）
        if (!runtime.isCinematicEnabled()) {
            return matrixStack; // 非电影模式 → 不干预
        }

        CameraSample sample = runtime.getActiveSample();
        if (sample == null) {
            return matrixStack;
        }

        float roll = sample.roll();
        if (Math.abs(roll) > 0.001f) {  // 避免不必要的矩阵操作
            // ⭐ 应用 Roll 旋转：围绕 Z 轴（摄像机看向的方向）旋转
            // RotationAxis.POSITIVE_Z.rotationDegrees() 返回 Quaternionf，MatrixStack.multiply() 接受它
            matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
        }
        
        return matrixStack;
    }

    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void onRenderWorldHead(RenderTickCounter tickCounter, CallbackInfo ci) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        var controller = ChronoBlocksClientController.getInstance();
        if (mc.player != null) {
            mc.player.setInvisible(controller.shouldHideCrosshair());
        }
    }

    /**
     * ⭐ 统一摄像机系统 v2.2：仅在电影模式下覆盖 FOV
     * <p>
     * - 电影模式：使用 sample 的 FOV（时间线定义）
     * - 游戏模式：完全使用原版 FOV，不干预
     */
    @Inject(
            method = "getFov",
            at = @At("HEAD"),
            cancellable = true
    )
    private void chronoblocks$overrideFov(Camera camera, float tickDelta, boolean changingFov,
                                          CallbackInfoReturnable<Float> cir) {
        CameraRuntime runtime = CameraRuntime.getInstance();
        // ✅ 只有电影模式才覆盖 FOV（使用统一的条件判断）
        if (!runtime.isCinematicEnabled()) {
            return; // 非电影模式 → 使用原版 FOV
        }

        CameraSample sample = runtime.getActiveSample();
        if (sample != null) {
            cir.setReturnValue(sample.fov());
        }
    }
}
