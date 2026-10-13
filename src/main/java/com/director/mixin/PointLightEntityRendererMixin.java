package com.director.mixin;

import com.director.lighting.PointLightRuntime;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class PointLightEntityRendererMixin {
    @Inject(method="getLight",at=@At("RETURN"),cancellable=true)
    private void director$pointLight(Entity entity, float tickProgress, CallbackInfoReturnable<Integer> cir) {
        var pos=entity.getLerpedPos(tickProgress);
        cir.setReturnValue(PointLightRuntime.sample(cir.getReturnValue(),pos.x,pos.y+entity.getHeight()*.5,pos.z));
    }
}
