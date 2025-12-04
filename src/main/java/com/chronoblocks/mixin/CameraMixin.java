package com.chronoblocks.mixin;

import com.chronoblocks.ui.timeline.playback.camera.CameraRuntime;
import com.chronoblocks.ui.timeline.playback.camera.CameraSample;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

/**
 * CameraMixin（统一相机系统版）
 *
 * 核心逻辑：
 * - 在 Camera.update() 之后：
 *   1. 用原版 Camera 的位置 / 旋转 / FOV 更新 CameraRuntime（当 owner=PLAYER 时）
 *   2. 如果 owner=TIMELINE，则用 CameraRuntime.currentSample 覆盖 Camera 的位置 / 旋转
 *
 * FOV 不在这里处理，而是在 GameRendererMixin.getFov(...) 中根据 owner 决定。
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Unique
    private static Method chronoblocks$setPosMethod;
    @Unique
    private static Method chronoblocks$setRotationMethod;

    static {
        try {
            chronoblocks$setPosMethod = Camera.class.getDeclaredMethod("setPos", double.class, double.class, double.class);
            chronoblocks$setPosMethod.setAccessible(true);
            chronoblocks$setRotationMethod = Camera.class.getDeclaredMethod("setRotation", float.class, float.class);
            chronoblocks$setRotationMethod.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("ChronoBlocks: Failed to reflect Camera methods", e);
        }
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void chronoblocks$applyUnifiedCamera(
            BlockView area,
            Entity focusedEntity,
            boolean thirdPerson,
            boolean inverseView,
            float tickDelta,
            CallbackInfo ci
    ) {
        CameraRuntime runtime = CameraRuntime.getInstance();
        Camera self = (Camera) (Object) this;

        // 1. 从原版 Camera 读取当前视角，并写入 runtime（仅 owner=PLAYER 时有效）
        Vec3d pos = self.getPos();
        float yaw = self.getYaw();
        float pitch = self.getPitch();

        // 这里用玩家设置的基础 FOV，而不是动态 FOV，避免视角闪跳
        float baseFov = (float) MinecraftClient.getInstance().options.getFov().getValue();
        runtime.updateFromGameCamera(pos, yaw, pitch, baseFov);

        // 2. 如果当前是玩家控制，就不用覆盖 Camera
        if (runtime.isPlayerOwner()) {
            return;
        }

        // 3. 时间线接管时：用 currentSample 覆盖位置 / 旋转
        CameraSample sample = runtime.getCurrentSample();
        if (sample == null) return;

        chronoblocks$setPos(sample.position().x, sample.position().y, sample.position().z);
        chronoblocks$setRotation(sample.yaw(), sample.pitch());
    }

    @Unique
    private void chronoblocks$setPos(double x, double y, double z) {
        try {
            chronoblocks$setPosMethod.invoke(this, x, y, z);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Unique
    private void chronoblocks$setRotation(float yaw, float pitch) {
        try {
            chronoblocks$setRotationMethod.invoke(this, yaw, pitch);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
