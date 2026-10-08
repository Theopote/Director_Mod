package com.director.actor.preview;

import com.director.actor.Actor;
import com.director.actor.rig.Pose;
import com.director.actor.rig.skeleton.*;
import com.director.actor.runtime.ActorRuntime;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector3f;

/** Client-only player silhouette. Timeline is the only source of motion. */
public final class PreviewPlayerActorEntity extends OtherClientPlayerEntity implements ActorPosePreview {
    private final Actor actor;
    private ActorRuntime runtime;
    private Pose pose;
    private RigPose rigPose;
    private final Vector3f scale=new Vector3f(1);
    public PreviewPlayerActorEntity(ClientWorld world,Actor actor) {
        super(world,new GameProfile(actor.getActorId(),"DirectorActor")); this.actor=actor;
    }
    @Override public net.minecraft.entity.player.SkinTextures getSkin() {
        var texture=com.director.actor.skin.ActorSkinTextureManager.getOrLoad(actor.getActorId());
        var fallback=super.getSkin();
        var model = switch (actor.getPlayerModel()) {
            case SLIM -> net.minecraft.entity.player.PlayerSkinType.SLIM;
            case WIDE -> net.minecraft.entity.player.PlayerSkinType.WIDE;
            case DEFAULT -> texture == null ? fallback.model() : net.minecraft.entity.player.PlayerSkinType.WIDE;
        };
        return texture == null
                ? new net.minecraft.entity.player.SkinTextures(fallback.body(), fallback.cape(), fallback.elytra(), model, fallback.secure())
                : net.minecraft.entity.player.SkinTextures.create(
                    new net.minecraft.util.AssetInfo.TextureAssetInfo(texture, texture), null, null, model);
    }
    @Override public void tick() {} // No vanilla movement, body control, or network interpolation.
    @Override public void tickMovement() {}
    @Override public void setActorRuntime(ActorRuntime value) { runtime=value; }
    @Override public ActorRuntime getActorRuntime() { return runtime; }
    @Override public void setFinalPose(Pose value) { pose=value; setFinalRigPose(value==null ? null : LegacyHumanoidPoseAdapter.convert(value)); }
    @Override public Pose getFinalPose() { return pose; }
    @Override public void setFinalRigPose(RigPose value) { rigPose=value==null ? null : value.copy(); }
    @Override public RigPose getFinalRigPose() { return rigPose==null ? null : rigPose.copy(); }
    @Override public void setActorTransformScale(Vector3f value) { if(value!=null && value.isFinite()) scale.set(value); }
    @Override public Vector3f getActorTransformScale() { return new Vector3f(scale); }
}
