package com.director.mixin;

import com.director.mixin.accessor.CameraAccessorMixin;
import com.director.ui.timeline.playback.camera.CameraRuntime;
import com.director.ui.timeline.playback.camera.CameraSample;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CameraMixin（统一相机系统版）
 * <p>
 * 核心逻辑：
 * - 在 Camera.update() 之后：
 *   1. 用原版 Camera 的位置 / 旋转 / FOV 更新 CameraRuntime（当 owner=PLAYER 时）
 *   2. 如果 owner=TIMELINE，则用 CameraRuntime.currentSample 覆盖 Camera 的位置 / 旋转
 * <p>
 * FOV 不在这里处理，而是在 GameRendererMixin.getFov(...) 中根据 owner 决定。
 * <p>
 * ⭐ 优化：使用 @Invoker (CameraAccessorMixin) 替代 Java 反射，
 *   避免映射名变化时的运行时崩溃，并消除 JNI 开销。
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Inject(method = "update", at = @At("TAIL"))
    private void director$applyUnifiedCamera(
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

        // ⭐ 修复 FOV 来源不一致：统一使用动态 FOV（含冲刺/飞行加成），
        // 与 CameraAccessor.sampleFromGameCamera() 保持一致。
        // 这样捕获关键帧时的 FOV 和玩家模式记录的 FOV 是同一个值。
        float fov;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.gameRenderer instanceof com.director.mixin.accessor.GameRendererAccessor accessor) {
            fov = accessor.director$invokeGetFov(self, tickDelta, true);
        } else {
            fov = (float) client.options.getFov().getValue();
        }
        runtime.updateFromGameCamera(pos, yaw, pitch, fov);

        // 2. 如果当前是玩家控制，就不用覆盖 Camera
        if (runtime.isPlayerOwner()) {
            return;
        }

        // 3. 时间线接管时：用 currentSample 覆盖位置 / 旋转
        CameraSample sample = runtime.getCurrentSample();
        if (sample == null) return;

        // ⭐ 使用 @Invoker 替代反射
        CameraAccessorMixin accessor = (CameraAccessorMixin) this;
        accessor.director$invokeSetPos(sample.position().x, sample.position().y, sample.position().z);
        accessor.director$invokeSetRotation(sample.yaw(), sample.pitch());

        // ⭐ Roll 角度支持：Camera.setRotation() 内部将 roll 硬编码为 0，
        //   这里在 setRotation 之后追加 Z 轴旋转来实现 roll。
        //   同时必须重新计算方向向量 (horizontalPlane / verticalPlane / diagonalPlane)，
        //   否则视锥体裁剪、碰撞检测等下游系统会使用错误的方向。
        if (sample.roll() != 0f) {
            Quaternionf rotation = self.getRotation();
            rotation.rotateZ((float) Math.toRadians(sample.roll()));
            // 从修改后的四元数重新计算方向向量（与 Camera.setRotation 内部逻辑一致）
            // HORIZONTAL=(0,0,1), VERTICAL=(0,1,0), DIAGONAL=(-1,0,0)
            self.getHorizontalPlane().set(0, 0, 1).rotate(rotation);
            self.getVerticalPlane().set(0, 1, 0).rotate(rotation);
            self.getDiagonalPlane().set(-1, 0, 0).rotate(rotation);
        }
    }
}
