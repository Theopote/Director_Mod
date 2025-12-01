package com.chronoblocks.mixin;

import com.chronoblocks.ui.timeline.playback.camera.CameraSample;
import com.chronoblocks.ui.timeline.playback.camera.CameraRuntime;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

/**
 * CameraMixin v3.2
 *
 * 在 Camera.update 之后覆盖位置/朝向，从 CameraRuntime 读取当前 sample。
 *
 * ⭐ 关键修复：
 * - 之前 isCinematicActive() 的判断反了，导致"有电影模式时不覆盖，没电影模式时反而覆盖"
 * - 现在逻辑为：只有在 isCinematicActive() == true 时才覆盖 Camera
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Unique
    private static Method setPosMethod;
    @Unique
    private static Method setRotationMethod;

    static {
        try {
            setPosMethod = Camera.class.getDeclaredMethod("setPos", double.class, double.class, double.class);
            setPosMethod.setAccessible(true);
            setRotationMethod = Camera.class.getDeclaredMethod("setRotation", float.class, float.class);
            setRotationMethod.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to reflection Camera methods", e);
        }
    }

    /**
     * ★★ 更新摄像机后，再覆盖位置和角度，而不是取消 update。
     * 这样就不会破坏 FOV 计算、平滑等逻辑。
     */
    @Inject(method = "update", at = @At("TAIL"))
    private void chronoblocks$applyCinematicCamera(
            BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView,
            float tickDelta, CallbackInfo ci
    ) {
        CameraRuntime runtime = CameraRuntime.getInstance();
        // ✅ 只有在电影模式下才覆盖 Camera（使用统一的条件判断）
        if (!runtime.isCinematicEnabled()) {
            return;
        }

        CameraSample sample = runtime.getActiveSample();
        if (sample == null) return;

        // overwrite camera pos
        chronoblocks$setPos(sample.position().x, sample.position().y, sample.position().z);

        // overwrite yaw/pitch
        chronoblocks$setRotation(sample.yaw(), sample.pitch());
    }

    @Unique
    private void chronoblocks$setPos(double x, double y, double z) {
        try {
            setPosMethod.invoke(this, x, y, z);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Unique
    private void chronoblocks$setRotation(float yaw, float pitch) {
        try {
            setRotationMethod.invoke(this, yaw, pitch);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
