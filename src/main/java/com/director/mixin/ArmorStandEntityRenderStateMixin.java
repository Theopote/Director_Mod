package com.director.mixin;

import com.director.bridge.DirectorActorIdHolder;
import com.director.bridge.DirectorActorScaleHolder;
import com.director.bridge.DirectorActorBoneScaleHolder;
import com.director.actor.rig.skeleton.HumanoidRig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

/**
 * 在 1.21+ 的 RenderState 渲染管线中，Renderer.getTexture() 只拿到 RenderState，
 * 拿不到原始 Entity，因此我们把 PreviewActorEntity 的 actorId 写入到 RenderState，
 * 供后续 getTexture() 覆盖使用。
 */
@Mixin(net.minecraft.client.render.entity.state.ArmorStandEntityRenderState.class)
public abstract class ArmorStandEntityRenderStateMixin implements DirectorActorIdHolder, DirectorActorScaleHolder,
        DirectorActorBoneScaleHolder {

    @Unique
    private UUID director$actorId;
    @Unique private float director$scaleX = 1f;
    @Unique private float director$scaleY = 1f;
    @Unique private float director$scaleZ = 1f;
    @Unique private final float[] director$boneScales = {
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f
    };

    @Override
    public UUID director$getActorId() {
        return director$actorId;
    }

    @Override
    public void director$setActorId(UUID actorId) {
        this.director$actorId = actorId;
    }

    @Override public void director$setActorScale(float x, float y, float z) {
        director$scaleX = x; director$scaleY = y; director$scaleZ = z;
    }
    @Override public float director$getActorScaleX() { return director$scaleX; }
    @Override public float director$getActorScaleY() { return director$scaleY; }
    @Override public float director$getActorScaleZ() { return director$scaleZ; }

    @Override
    public void director$setBoneScale(String boneId, float x, float y, float z) {
        int offset = director$boneOffset(boneId);
        if (offset < 0) return;
        director$boneScales[offset] = x;
        director$boneScales[offset + 1] = y;
        director$boneScales[offset + 2] = z;
    }

    @Override public float director$getBoneScaleX(String boneId) { return director$boneScale(boneId, 0); }
    @Override public float director$getBoneScaleY(String boneId) { return director$boneScale(boneId, 1); }
    @Override public float director$getBoneScaleZ(String boneId) { return director$boneScale(boneId, 2); }

    @Unique
    private float director$boneScale(String boneId, int component) {
        int offset = director$boneOffset(boneId);
        return offset < 0 ? 1f : director$boneScales[offset + component];
    }

    @Unique
    private static int director$boneOffset(String boneId) {
        return switch (boneId) {
            case HumanoidRig.BODY -> 0;
            case HumanoidRig.HEAD -> 3;
            case HumanoidRig.LEFT_ARM -> 6;
            case HumanoidRig.RIGHT_ARM -> 9;
            case HumanoidRig.LEFT_LEG -> 12;
            case HumanoidRig.RIGHT_LEG -> 15;
            default -> -1;
        };
    }

}
