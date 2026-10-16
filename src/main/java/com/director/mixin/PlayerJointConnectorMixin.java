package com.director.mixin;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.class)
public abstract class PlayerJointConnectorMixin {
    @Inject(method="renderCuboids",at=@At("HEAD"),cancellable=true)
    private void director$connector(MatrixStack.Entry entry,VertexConsumer consumer,int light,int overlay,int color,CallbackInfo ci) {
        if(!com.director.actor.preview.ActorSelectionOutline.accepts((ModelPart)(Object)this)) { ci.cancel(); return; }
        if(com.director.actor.preview.PlayerJointSeam.render((ModelPart)(Object)this,entry,consumer,light,overlay,color)) ci.cancel();
    }
}
