package com.director.mixin;

import com.director.client.camera.CameraRenderOverride;
import com.director.mixin.accessor.CameraAccessorMixin;
import com.director.timeline.playback.camera.CameraSample;
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
 * CameraMixin — 只读/写入 {@link CameraRenderOverride}，不依赖 playback Controller。
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
        CameraRenderOverride state = CameraRenderOverride.get();
        Camera self = (Camera) (Object) this;

        Vec3d pos = self.getPos();
        float yaw = self.getYaw();
        float pitch = self.getPitch();

        float fov;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.gameRenderer instanceof com.director.mixin.accessor.GameRendererAccessor accessor) {
            fov = accessor.director$invokeGetFov(self, tickDelta, true);
        } else {
            fov = (float) client.options.getFov().getValue();
        }
        CameraRenderOverride.reportPlayerView(pos, yaw, pitch, fov);

        if (state.isPlayerOwner()) {
            return;
        }

        CameraSample sample = state.sample();
        if (sample == null) {
            return;
        }

        CameraAccessorMixin accessor = (CameraAccessorMixin) this;
        accessor.director$invokeSetPos(sample.position().x, sample.position().y, sample.position().z);
        accessor.director$invokeSetRotation(sample.yaw(), sample.pitch());

        if (sample.roll() != 0f) {
            Quaternionf rotation = self.getRotation();
            rotation.rotateZ((float) Math.toRadians(sample.roll()));
            self.getHorizontalPlane().set(0, 0, 1).rotate(rotation);
            self.getVerticalPlane().set(0, 1, 0).rotate(rotation);
            self.getDiagonalPlane().set(-1, 0, 0).rotate(rotation);
        }
    }
}
