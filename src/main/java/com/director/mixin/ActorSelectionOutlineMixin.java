package com.director.mixin;
import com.director.actor.preview.ActorSelectionOutline;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class ActorSelectionOutlineMixin {
    @Inject(method="updateRenderState",at=@At("TAIL"))
    private void director$selection(Entity entity,EntityRenderState state,float delta,CallbackInfo ci) {
        ActorSelectionOutline.capture(entity,state);
    }
}
