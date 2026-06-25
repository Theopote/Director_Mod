package com.director.mixin;

import com.director.bridge.DirectorActorIdHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

/**
 * 在 1.21+ 的 RenderState 渲染管线中，Renderer.getTexture() 只拿到 RenderState，
 * 拿不到原始 Entity，因此我们把 PreviewActorEntity 的 actorId 写入到 RenderState，
 * 供后续 getTexture() 覆盖使用。
 */
@Mixin(net.minecraft.client.render.entity.state.ArmorStandEntityRenderState.class)
public abstract class ArmorStandEntityRenderStateMixin implements DirectorActorIdHolder {

    @Unique
    private UUID director$actorId;

    @Override
    public UUID director$getActorId() {
        return director$actorId;
    }

    @Override
    public void director$setActorId(UUID actorId) {
        this.director$actorId = actorId;
    }

}

