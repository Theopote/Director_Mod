package com.director.mixin;
import com.director.actor.preview.ActorSelectionOutline;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ModelCommandRenderer.class)
public abstract class ActorOutlineModelCommandMixin {
    @Redirect(method="render(Lnet/minecraft/client/render/command/OrderedRenderCommandQueueImpl$ModelCommand;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/client/render/OutlineVertexConsumerProvider;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/client/model/Model;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V",ordinal=1))
    private void director$outline(Model<?> model,MatrixStack matrices,VertexConsumer vertices,int light,int overlay,int color,
            OrderedRenderCommandQueueImpl.ModelCommand<?> command,RenderLayer layer,VertexConsumer normal,
            OutlineVertexConsumerProvider outlines,VertexConsumerProvider.Immediate crumbling) {
        ActorSelectionOutline.render(model,command.state(),matrices,vertices,light,overlay,color);
    }
}
