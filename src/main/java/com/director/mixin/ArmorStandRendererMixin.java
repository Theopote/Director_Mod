package com.director.mixin;

import com.director.actor.preview.ActorPreviewManager;
import com.director.actor.preview.PreviewActorEntity;
import com.director.actor.skin.ActorSkinTextureManager;
import com.director.bridge.DirectorActorIdHolder;
import net.minecraft.client.render.entity.ArmorStandEntityRenderer;
import net.minecraft.client.render.entity.state.ArmorStandEntityRenderState;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * ArmorStandRendererMixin
 *
 * <p>为 PreviewActorEntity 应用自定义皮肤纹理。
 */
@Mixin(ArmorStandEntityRenderer.class)
public abstract class ArmorStandRendererMixin {

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/decoration/ArmorStandEntity;Lnet/minecraft/client/render/entity/state/ArmorStandEntityRenderState;F)V",
            at = @At("TAIL"))
    private void director$storeActorIdInRenderState(ArmorStandEntity entity, ArmorStandEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (state instanceof DirectorActorIdHolder holder) {
            if (entity instanceof PreviewActorEntity) {
                UUID actorId = ActorPreviewManager.getInstance().getActorId(entity);
                holder.director$setActorId(actorId);
            } else {
                holder.director$setActorId(null);
            }
        }
    }

    @Inject(method = "getTexture(Lnet/minecraft/client/render/entity/state/ArmorStandEntityRenderState;)Lnet/minecraft/util/Identifier;",
            at = @At("HEAD"),
            cancellable = true)
    private void director$overridePreviewSkin(ArmorStandEntityRenderState state, CallbackInfoReturnable<Identifier> cir) {
        UUID actorId = null;
        if (state instanceof DirectorActorIdHolder holder) {
            actorId = holder.director$getActorId();
        }
        if (actorId == null) {
            return;
        }
        Identifier skin = ActorSkinTextureManager.getOrLoad(actorId);
        if (skin != null) {
            cir.setReturnValue(skin);
        }
    }
}

