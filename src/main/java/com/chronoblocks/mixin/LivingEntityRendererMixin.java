package com.chronoblocks.mixin;

import com.chronoblocks.actor.preview.PreviewActorEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * EntityRendererMixin v1.1
 *
 * <p>修复 PreviewActorEntity 的渲染问题
 *
 * <p>问题：PreviewActorEntity 继承自 Entity，但渲染系统期望 EntityType.ARMOR_STAND 对应 ArmorStandEntity。
 * 当渲染系统尝试调用渲染器的 getAndUpdateRenderState() 时，会调用 updateRenderState()，
 * 在 updateRenderState() 中会将实体转换为 ArmorStandEntity，导致 ClassCastException。
 *
 * <p>解决方案：
 * 1. 拦截 EntityRenderer.getAndUpdateRenderState() 方法，如果实体是 PreviewActorEntity，返回一个默认的渲染状态
 * 2. 拦截 EntityRenderer.shouldRender() 方法，如果实体是 PreviewActorEntity，则使用 Entity 的默认逻辑
 * 3. 拦截 EntityRenderer.getBoundingBox() 方法，如果实体是 PreviewActorEntity，则返回默认 bounding box
 *
 * @author ChronoBlocks Team
 * @version 1.1
 */
@Mixin(EntityRenderer.class)
public class LivingEntityRendererMixin {

    // 注意：getAndUpdateRenderState() 的拦截已移除，因为我们在 WorldRenderer.fillEntityRenderStates() 中
    // 直接跳过了 PreviewActorEntity，所以这个方法不会被调用。

    /**
     * 拦截 shouldRender() 方法
     * <p>如果实体是 PreviewActorEntity，使用 Entity 的默认 bounding box，避免进入特定渲染器的代码路径
     *
     * @param entity 实体
     * @param frustum 视锥体
     * @param x X坐标
     * @param y Y坐标
     * @param z Z坐标
     * @param cir 回调信息
     */
    @Inject(
            method = "shouldRender",
            at = @At("HEAD"),
            cancellable = true
    )
    private void chronoblocks$handlePreviewActorEntity(
            Entity entity,
            Frustum frustum,
            double x,
            double y,
            double z,
            CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof PreviewActorEntity) {
            // 使用 Entity 的默认 shouldRender 逻辑（基于距离和可见性）
            // 这样可以避免进入特定渲染器的代码路径
            double distance = Math.sqrt(x * x + y * y + z * z);
            boolean shouldRender = entity.shouldRender(distance);
            cir.setReturnValue(shouldRender);
        }
    }

    /**
     * 拦截 getBoundingBox() 方法
     * <p>如果实体是 PreviewActorEntity，返回一个默认的 bounding box，避免类型转换错误
     * <p>注意：这个方法在 EntityRenderer 中定义为 protected，所以可以在这里拦截
     *
     * @param entity 实体
     * @param cir 回调信息
     */
    @Inject(
            method = "getBoundingBox",
            at = @At("HEAD"),
            cancellable = true
    )
    private void chronoblocks$handlePreviewActorEntityBoundingBox(
            Entity entity,
            CallbackInfoReturnable<Box> cir) {
        if (entity instanceof PreviewActorEntity) {
            // 返回一个默认的 bounding box（1x2x1，标准生物大小）
            Box defaultBox = new Box(
                    entity.getX() - 0.5,
                    entity.getY(),
                    entity.getZ() - 0.5,
                    entity.getX() + 0.5,
                    entity.getY() + 2.0,
                    entity.getZ() + 0.5
            );
            cir.setReturnValue(defaultBox);
        }
    }
}




