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
 * CameraMixin v3.0
 * 
 * <p>在 Camera.update 之后覆盖位置/朝向，从 CameraRuntime 读取当前 sample。
 * 
 * <p>⭐ 重要变更：
 * <ul>
 *   <li>不再使用 CinematicCamera，直接使用 CameraSample</li>
 *   <li>只从 CameraRuntime 读取状态，不再依赖 TimelineCameraController</li>
 * </ul>
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
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed to get Camera methods", e);
        }
    }

    /**
     * 在摄像机 update 时，如果处于 Cinematic 模式（PREVIEW 或 PLAYING），
     * 则完全使用轨道摄像机的数据，并取消原本的跟随实体逻辑。
     */
    @Inject(
        method = "update",
        at = @At("HEAD"),
        cancellable = true
    )
    private void chronoblocks$overrideCamera(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        CameraRuntime runtime = CameraRuntime.getInstance();
        if (!runtime.isCinematicActive()) {
            return;
        }

        CameraSample sample = runtime.getActiveSample();
        if (sample == null) {
            return;
        }

        // 位置
        this.chronoblocks$setPos(sample.position().x, sample.position().y, sample.position().z);

        // 旋转（Roll 需要在自定义矩阵阶段额外处理，这里先用 yaw/pitch）
        this.chronoblocks$setRotation(sample.yaw(), sample.pitch());

        // 阻止原始 update 逻辑（不再跟随实体或受玩家输入影响）
        ci.cancel();
    }
    
    @Unique
    private void chronoblocks$setPos(double x, double y, double z) {
        try {
            setPosMethod.invoke(this, x, y, z);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set camera position", e);
        }
    }
    
    @Unique
    private void chronoblocks$setRotation(float yaw, float pitch) {
        try {
            setRotationMethod.invoke(this, yaw, pitch);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set camera rotation", e);
        }
    }
}
