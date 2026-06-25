package com.director.mixin;

import com.director.actor.preview.ActorPreviewManager;
import com.director.actor.preview.PreviewActorEntity;
import com.director.actor.skin.ActorSkinTextureManager;
import net.minecraft.client.render.entity.ArmorStandEntityRenderer;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * ArmorStandRendererMixin
 *
 * <p>为 PreviewActorEntity 应用自定义皮肤纹理。
 */
@Mixin(ArmorStandEntityRenderer.class)
public abstract class ArmorStandRendererMixin {

    @Inject(method = "getTexture(Lnet/minecraft/entity/decoration/ArmorStandEntity;)Lnet/minecraft/util/Identifier;",
            at = @At("HEAD"),
            cancellable = true)
    private void director$overridePreviewSkin(ArmorStandEntity entity, CallbackInfoReturnable<Identifier> cir) {
        if (!(entity instanceof PreviewActorEntity)) {
            return;
        }
        UUID actorId = ActorPreviewManager.getInstance().getActorId(entity);
        if (actorId == null) {
            return;
        }
        Identifier skin = ActorSkinTextureManager.getOrLoad(actorId);
        if (skin != null) {
            cir.setReturnValue(skin);
        }
    }
}

