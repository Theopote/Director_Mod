package com.director.mixin;

import com.director.actor.preview.*;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerActorRendererMixin {
    @Inject(method="updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",at=@At("TAIL"))
    private void director$pose(net.minecraft.entity.PlayerLikeEntity entity,PlayerEntityRenderState state,float delta,CallbackInfo ci) {
        PlayerActorRenderPoses.put(state,entity instanceof PreviewPlayerActorEntity preview ? preview.getFinalRigPose() : null);
    }
}
