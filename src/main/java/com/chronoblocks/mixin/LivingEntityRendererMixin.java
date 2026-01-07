package com.chronoblocks.mixin;

import com.chronoblocks.actor.preview.PreviewActorEntity;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * EntityRenderManagerMixin v1.0
 *
 * <p>修复 PreviewActorEntity 的渲染问题
 *
 * <p>问题：PreviewActorEntity 继承自 Entity，但渲染系统期望 EntityType.VILLAGER 等类型对应 LivingEntity 子类。
 * 当渲染系统尝试调用 LivingEntityRenderer.getBoundingBox() 时，会将实体转换为 LivingEntity，导致 ClassCastException。
 *
 * <p>解决方案：拦截 EntityRenderManager.shouldRender() 方法，如果实体是 PreviewActorEntity，则使用 Entity 的默认逻辑，避免进入 LivingEntityRenderer 的代码路径。
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
@Mixin(EntityRenderManager.class)
public class LivingEntityRendererMixin {

    /**
     * 拦截 shouldRender() 方法
     * <p>如果实体是 PreviewActorEntity，使用 Entity 的默认 shouldRender 逻辑，避免进入 LivingEntityRenderer 的代码路径
     *
     * @param entity 实体
     * @param distance 距离
     * @param cir 回调信息
     */
    @Inject(
            method = "shouldRender",
            at = @At("HEAD"),
            cancellable = true
    )
    private void chronoblocks$handlePreviewActorEntity(Entity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof PreviewActorEntity) {
            // 使用 Entity 的默认 shouldRender 逻辑（基于距离和可见性）
            // 这样可以避免进入 LivingEntityRenderer 的代码路径
            boolean shouldRender = entity.shouldRender(distance);
            cir.setReturnValue(shouldRender);
        }
    }
}

