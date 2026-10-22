package com.director.ui.actor.edit;

import com.director.actor.ActorRegistryManager;
import com.director.actor.ActorBehaviorCapabilities;
import com.director.actor.ActorType;
import com.director.actor.prop.PropEventParameters;
import com.director.actor.action.ActorActionType;
import com.director.actor.rig.ActorRigProfile;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import org.joml.Vector3f;
import java.util.*;

/** Explicit selected clip editing, independent of which clip is active at the playhead. */
public final class ActorClipPropertyEditSession {
    private interface State { void apply(EntityClip clip); void validate(); }
    private record Transform(Vector3f fromPos, Vector3f toPos, Vector3f fromRot, Vector3f toRot,
                             Vector3f fromScale, Vector3f toScale, TimelineInterpolation interpolation,
                             String path, boolean follow) implements State {
        public void apply(EntityClip clip) {
            var c = (EntityTransformClip) clip;
            c.setFromPos(fromPos.x, fromPos.y, fromPos.z); c.setToPos(toPos.x, toPos.y, toPos.z);
            c.setFromRot(fromRot.x, fromRot.y, fromRot.z); c.setToRot(toRot.x, toRot.y, toRot.z);
            c.setFromScale(fromScale.x, fromScale.y, fromScale.z); c.setToScale(toScale.x, toScale.y, toScale.z);
            c.setInterpolation(interpolation); c.setPathId(path); c.setFollowPath(follow);
        }
        public void validate() {
            if (!fromPos.isFinite() || !toPos.isFinite() || !fromRot.isFinite() || !toRot.isFinite()
                    || !validScale(fromScale) || !validScale(toScale) || interpolation == null)
                throw new IllegalArgumentException("变换数值无效，缩放范围为 0.01–100");
        }
    }
    private record Look(EntityLookAtClip.TargetMode mode, Vector3f point, UUID target, float strength) implements State {
        public void apply(EntityClip clip) {
            var c = (EntityLookAtClip) clip; c.setMode(mode); c.setWorldPoint(point.x, point.y, point.z);
            c.setTargetEntityUuid(target); c.setStrength(strength);
        }
        public void validate() {
            if (mode == null || !point.isFinite() || !Float.isFinite(strength) || strength < 0 || strength > 1
                    || mode == EntityLookAtClip.TargetMode.ENTITY_UUID && target == null)
                throw new IllegalArgumentException("注视属性无效，请检查坐标、强度或目标实体 UUID");
        }
    }
    private record Action(ActorActionType type, float intensity, boolean loop, float start, float end) implements State {
        public void apply(EntityClip clip) {
            var c = (ActorActionClip) clip; c.setActionType(type); c.setIntensity(intensity); c.setLoop(loop); c.setTimeRange(start, end);
        }
        public void validate() {
            if (type == null || !Float.isFinite(intensity) || intensity < 0 || intensity > 1
                    || !Float.isFinite(start) || start < 0 || !Float.isFinite(end) || end - start < .1f - 1e-5f)
                throw new IllegalArgumentException("动作强度或时间范围无效");
        }
    }
    private record Relation(UUID target, ActorRelationClip.RelationType type, float distance, float angle,
                            float height, boolean look) implements State {
        public void apply(EntityClip clip) {
            var c = (ActorRelationClip) clip; c.setTargetActorId(target); c.setRelationType(type); c.setDistance(distance);
            c.setAngle(angle); c.setHeightOffset(height); c.setMaintainLookAt(look);
        }
        public void validate() {
            if (type == null || !Float.isFinite(distance) || distance < .1f || !Float.isFinite(angle) || !Float.isFinite(height))
                throw new IllegalArgumentException("关系参数无效");
        }
    }
    private record Event(EntityActionClip.ActionType type, boolean once, Map<String,Object> params) implements State {
        public void apply(EntityClip clip) {
            var c = (EntityActionClip) clip;
            var copied = PropEventParameters.copy(params,false);
            c.setActionType(type); c.setTriggerOnce(once); c.getParams().clear(); c.getParams().putAll(copied);
        }
        public void validate() { PropEventParameters.validate(type,params); }
    }
    private record Spawn(Vector3f position, float yaw, float pitch, float roll, Vector3f scale, boolean visible, float start, float end, String animationId, com.director.actor.rig.skeleton.PoseBlendSettings blend, ActorPresetBinding preset) implements State {
        public void apply(EntityClip clip) {
            var c = (EntitySpawnClip) clip; c.setPosition(position.x,position.y,position.z);
            c.setYawDeg(yaw); c.setPitchDeg(pitch); c.setRollDeg(roll); c.setScale(scale.x, scale.y, scale.z); c.setVisibleOnSpawn(visible); c.setTimeRange(start,end); c.setBoneAnimationId(animationId); c.setBlendSettings(blend); c.setPreset(preset);
        }
        public void validate() {
            if (!position.isFinite() || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll) || !validScale(scale) || pitch < -90 || pitch > 90
                    || !Float.isFinite(start) || start < 0 || !Float.isFinite(end) || end-start < .1f-1e-5f)
                throw new IllegalArgumentException("出生位置、朝向或时间范围无效");
        }
    }
    private record Animation(EntityAnimationClip.AnimationSource source, String id, boolean loop, float speed,
                             com.director.actor.rig.skeleton.PoseBlendSettings blend) implements State {
        public void apply(EntityClip clip) {
            var c=(EntityAnimationClip)clip; c.setSource(source); c.setAnimationId(id); c.setLoop(loop);
            c.setSpeed(speed); c.setBlendSettings(blend);
        }
        public void validate() {
            if(source==null || id==null || id.isBlank() || !Float.isFinite(speed) || speed<0 || speed>4)
                throw new IllegalArgumentException("动画参数无效");
        }
    }
    private record Status(boolean sneaking, boolean sprinting, boolean glowing, boolean invulnerable, float visibility) implements State {
        public void apply(EntityClip clip) {
            var c = (EntityStateClip) clip;
            c.setSneaking(sneaking); c.setSprinting(sprinting); c.setGlowing(glowing);
            c.setInvulnerable(invulnerable); c.setVisibility(visibility);
        }
        public void validate() {
            if (!Float.isFinite(visibility) || visibility < 0 || visibility > 1)
                throw new IllegalArgumentException("可见性必须是 0–1 的有限数值");
        }
    }
    private record Despawn(EntityDespawnClip.DespawnMode mode) implements State {
        public void apply(EntityClip clip) { ((EntityDespawnClip) clip).setMode(mode); }
        public void validate() { if (mode == null) throw new IllegalArgumentException("请选择离场方式"); }
    }
    private record PoseState(List<Boolean> overrides, List<Float> angles) implements State {
        public void apply(EntityClip clip) {
            var c = (ActorPoseClip) clip;
            c.setOverrideBody(overrides.get(0));
            c.setOverrideHead(overrides.get(1));
            c.setOverrideArms(overrides.get(2));
            c.setOverrideLegs(overrides.get(3));
            c.setBodyPitch(angles.get(0));
            c.setBodyYaw(angles.get(1));
            c.setBodyRoll(angles.get(2));
            c.setHeadPitch(angles.get(3));
            c.setHeadYaw(angles.get(4));
            c.setLeftArmPitch(angles.get(5));
            c.setLeftArmYaw(angles.get(6));
            c.setLeftArmRoll(angles.get(7));
            c.setRightArmPitch(angles.get(8));
            c.setRightArmYaw(angles.get(9));
            c.setRightArmRoll(angles.get(10));
            c.setLeftLegPitch(angles.get(11));
            c.setRightLegPitch(angles.get(12));
        }
        public void validate() {
            if (angles.stream().anyMatch(v -> !Float.isFinite(v))) throw new IllegalArgumentException("姿态角度必须是有限数值");
        }
    }
    private record Key(EntityClipKeyframe identity, float time, TimelineInterpolation interpolation,
                       Vector3f position, Vector3f rotation, Vector3f scale, EntityKeyframe.ActionType action,
                       float speed, boolean loop, float visibility, boolean active, String name,
                       boolean nameTag, EntityKeyframe.EntityType entityType) {
        static Key capture(EntityClipKeyframe key) {
            return new Key(key, key.getTime(), key.getInterpolation(), new Vector3f(key.getPosition()),
                    new Vector3f(key.getRotation()), new Vector3f(key.getScale()), key.getActionType(),
                    key.getActionSpeed(), key.isActionLoop(), key.getVisibility(), key.isActive(),
                    key.getDisplayName(), key.isShowNameTag(), key.getEntityType());
        }
    }
    private record Common(String name, float start, float end, boolean muted, float weight, List<Key> keys) {
        static Common capture(EntityClip clip) {
            return new Common(clip.getName(), clip.getStartTime(), clip.getEndTime(), clip.isMuted(),
                    clip.getWeight(), clip.getKeyframes().stream().map(Key::capture).toList());
        }
    }
    private final TimelineModel model;
    private final TimelineTrack owner;
    private final ActorTrack payload;
    private final UUID actor;
    private final EntityClip source, draft;
    private final EntityTimelineClip wrapper;
    private final State before;
    private final Common common;
    private final float time;
    private final ActorType actorType;
    private final ActorRigProfile profile;
    private final String customRig;
    private final long contextRevision;
    private boolean finished;

    public ActorClipPropertyEditSession(TimelineModel model, EntityClip source) {
        this.model = Objects.requireNonNull(model); this.source = Objects.requireNonNull(source);
        contextRevision = model.getActorEditContextRevision();
        wrapper = Objects.requireNonNull(source.getTimelineClip()); owner = Objects.requireNonNull(wrapper.getParentTrack());
        payload = Objects.requireNonNull(owner.getActorTrackPayload()); actor = Objects.requireNonNull(payload.getActorRef()).getActorId();
        time = model.getCurrentTime(); before = capture(source); common = Common.capture(source);
        var definition = ActorRegistryManager.getInstance().getActor(actor).orElseThrow();
        actorType = definition.getType(); profile = definition.getRigProfile(); customRig = definition.getCustomRigId();
        if (source instanceof EntityTransformClip) draft = new EntityTransformClip(source.getName(), common.start, common.end);
        else if (source instanceof EntityLookAtClip) draft = new EntityLookAtClip(source.getName(), common.start, common.end);
        else if (source instanceof ActorActionClip) draft = new ActorActionClip(source.getName(), common.start, common.end);
        else if (source instanceof ActorRelationClip) draft = new ActorRelationClip(source.getName(), common.start, common.end);
        else if (source instanceof EntityActionClip) draft = new EntityActionClip(source.getName(), common.start, common.end);
        else if (source instanceof EntitySpawnClip) draft = new EntitySpawnClip(source.getName(), common.start, common.end);
        else if (source instanceof ActorPoseClip) draft = new ActorPoseClip(source.getName(), common.start, common.end);
        else if (source instanceof EntityStateClip) draft = new EntityStateClip(source.getName(), common.start, common.end);
        else if (source instanceof EntityAnimationClip) draft = new EntityAnimationClip(source.getName(), common.start, common.end);
        else if (source instanceof EntityDespawnClip) draft = new EntityDespawnClip(source.getName(), common.start, common.end);
        else throw new IllegalArgumentException("不支持此片段类型");
        before.apply(draft);
        if (!isCurrent(model, source)) throw new IllegalArgumentException("请暂停播放并选择可编辑的演员片段");
    }
    public EntityClip draft() { return draft; }
    public boolean isCurrent(TimelineModel currentModel, EntityClip currentSource) {
        return !finished && contextRevision == model.getActorEditContextRevision() && currentModel == model && currentSource == source && time == model.getCurrentTime()
                && validBinding() && common.equals(Common.capture(source)) && before.equals(capture(source));
    }
    private boolean validBinding() {
        return !model.isPlaying() && Float.isFinite(model.getCurrentTime()) && model.getCurrentTime() >= 0
                && model.getAllTracks().contains(owner) && owner.getType() == TrackType.ACTOR
                && !owner.isLocked() && !owner.isMuted() && !source.isMuted()
                && owner.getActorTrackPayload() == payload && payload.getActorRef() != null
                && Objects.equals(actor, payload.getActorRef().getActorId())
                && ActorRegistryManager.getInstance().getActor(actor).isPresent()
                && ActorRegistryManager.getInstance().getActor(actor).map(a -> a.getType() == actorType
                    && a.getRigProfile() == profile && Objects.equals(customRig, a.getCustomRigId())).orElse(false)
                && payload.getClips().contains(source) && source.getTimelineClip() == wrapper
                && wrapper.getEntityClip() == source && wrapper.getParentTrack() == owner && owner.getClips().contains(wrapper)
                && Float.isFinite(common.start) && Float.isFinite(common.end) && common.start >= 0 && common.end >= common.start;
    }
    public void cancel() { finished = true; }
    public boolean commit() {
        if (!isCurrent(model, source)) { cancel(); return false; }
        State after = capture(draft);
        if (before.equals(after)) { cancel(); return true; }
        if (source instanceof EntityTransformClip && !common.keys.isEmpty())
            throw new IllegalArgumentException("此变换片段使用关键帧，请在播放头处编辑关键帧");
        after.validate();
        validateBehavior(after, before, true);
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Edit selected actor clip",
                () -> apply(after, before, false), () -> apply(before, after, true)));
        finished = true; return true;
    }
    private void apply(State expected, State next, boolean forward) {
        float expectedStart = expected instanceof Action a ? a.start : expected instanceof Spawn s ? s.start : common.start;
        float expectedEnd = expected instanceof Action a ? a.end : expected instanceof Spawn s ? s.end : common.end;
        Common expectedCommon = new Common(common.name,expectedStart,expectedEnd,common.muted,common.weight,common.keys);
        if (!validBinding() || !expectedCommon.equals(Common.capture(source)) || !expected.equals(capture(source)))
            throw new IllegalArgumentException("片段目标或源数据已变化，无法应用属性修改");
        validateBehavior(next, expected, forward);
        // Undo may restore an older, malformed value; validate new user input before constructing the command.
        next.apply(source);
        if(expected instanceof Spawn beforeSpawn && next instanceof Spawn afterSpawn
                && !Objects.equals(beforeSpawn.preset,afterSpawn.preset)) model.invalidateActorEditContext();
    }
    private static State capture(EntityClip clip) {
        if (clip instanceof EntityTransformClip c) return new Transform(new Vector3f(c.getFromPos()), new Vector3f(c.getToPos()),
                new Vector3f(c.getFromRot()), new Vector3f(c.getToRot()), new Vector3f(c.getFromScale()),
                new Vector3f(c.getToScale()), c.getInterpolation(), c.getPathId(), c.isFollowPath());
        if (clip instanceof EntityLookAtClip c) return new Look(c.getMode(), new Vector3f(c.getWorldPoint()), c.getTargetEntityUuid(), c.getStrength());
        if (clip instanceof ActorActionClip c) return new Action(c.getActionType(), c.getIntensity(), c.isLoop(), c.getStartTime(), c.getEndTime());
        if (clip instanceof ActorRelationClip c) return new Relation(c.getTargetActorId(), c.getRelationType(), c.getDistance(), c.getAngle(), c.getHeightOffset(), c.isMaintainLookAt());
        if (clip instanceof EntityActionClip c) return new Event(c.getActionType(),c.isTriggerOnce(),PropEventParameters.copy(c.getParams(),true));
        if (clip instanceof EntitySpawnClip c) return new Spawn(new Vector3f(c.getPosition()),c.getYawDeg(),c.getPitchDeg(),c.getRollDeg(),new Vector3f(c.getScale()),c.isVisibleOnSpawn(),c.getStartTime(),c.getEndTime(),c.getBoneAnimationId(),c.getBlendSettings(),c.getPreset());
        if (clip instanceof ActorPoseClip c) return new PoseState(List.of(c.isOverrideBody(),c.isOverrideHead(),c.isOverrideArms(),c.isOverrideLegs()),
                List.of(c.getBodyPitch(),c.getBodyYaw(),c.getBodyRoll(),c.getHeadPitch(),c.getHeadYaw(),c.getLeftArmPitch(),c.getLeftArmYaw(),c.getLeftArmRoll(),c.getRightArmPitch(),c.getRightArmYaw(),c.getRightArmRoll(),c.getLeftLegPitch(),c.getRightLegPitch()));
        if (clip instanceof EntityStateClip c) return new Status(c.isSneaking(),c.isSprinting(),c.isGlowing(),c.isInvulnerable(),c.getVisibility());
        if (clip instanceof EntityAnimationClip c) return new Animation(c.getSource(),c.getAnimationId(),c.isLoop(),c.getSpeed(),c.getBlendSettings());
        if (clip instanceof EntityDespawnClip c) return new Despawn(c.getMode());
        throw new IllegalArgumentException("不支持此片段类型");
    }
    private void validateBehavior(State next, State expected, boolean forward) {
        if (next instanceof PoseState) {
            if (forward && !ActorBehaviorCapabilities.supportsLegacyPose(ActorRegistryManager.getInstance().getActor(actor).orElse(null)))
                throw new IllegalArgumentException("此姿态片段需要标准人形骨骼绑定");
        } else if (next instanceof Action a) {
            if (forward && !ActorBehaviorCapabilities.supportsAction(ActorRegistryManager.getInstance().getActor(actor).orElse(null), a.type))
                throw new IllegalArgumentException("当前演员不支持此动作，请选择可用动作");
            var old = (Action) expected;
            if (a.start != old.start || a.end != old.end)
                for (var other : payload.getClips()) if (other != source && other instanceof ActorActionClip && !other.isMuted()
                        && other.getEndTime() > a.start && other.getStartTime() < a.end)
                    throw new IllegalArgumentException("动作时间范围与已有主动作重叠");
        } else if (next instanceof Event e) {
            if (forward) PropEventParameters.validateActor(ActorRegistryManager.getInstance().getActor(actor).orElse(null),e.type,e.params);
        } else if (next instanceof Spawn s) {
            var old = (Spawn) expected;
            if(forward && s.preset!=null && !Objects.equals(s.preset,old.preset)) {
                var currentActor=ActorRegistryManager.getInstance().getActor(actor).orElse(null);
                var asset=com.director.actor.animation.asset.AnimationAssetLibrary.getInstance().find(s.preset.animationId()).orElse(null);
                boolean compatible=asset!=null?com.director.actor.rig.ActorRigCompatibility.accepts(currentActor,asset.rigId())
                        && com.director.actor.rig.ActorRigCompatibility.resolveRig(currentActor)
                            .map(r->com.director.actor.rig.skeleton.RigPoseComposer.sameLayout(r,asset.rig())).orElse(false):
                        com.director.actor.animation.AnimationLibrary.getInstance().get(s.preset.animationId()).isPresent()
                        && com.director.actor.rig.ActorRigCompatibility.resolveRig(currentActor)
                            .map(com.director.actor.rig.skeleton.HumanoidTopology::matches).orElse(false);
                if(!compatible) throw new IllegalArgumentException("预设不存在或与演员骨架不兼容");
                if(Objects.equals(s.animationId,s.preset.animationId())) throw new IllegalArgumentException("预设不能引用本片段的手工动画");
            }
            if (s.start != old.start || s.end != old.end)
                for (var other : payload.getClips()) if (other != source && other instanceof EntitySpawnClip && !other.isMuted()
                        && other.getEndTime() > s.start && other.getStartTime() < s.end)
                    throw new IllegalArgumentException("出生片段时间范围与已有出生片段重叠");
        } else if (next instanceof Relation r) {
            if (forward && !ActorBehaviorCapabilities.supportsRelation(r.type))
                throw new IllegalArgumentException("此关系尚未实现，请选择可用关系类型");
            if (ActorBehaviorCapabilities.supportsRelation(r.type))
                ActorRelationPropertyValidation.requireValid(model, actor, source, r.target, common.start, common.end);
        }
    }
    private static boolean validScale(Vector3f value) {
        return value.isFinite() && value.x >= .01f && value.y >= .01f && value.z >= .01f
                && value.x <= 100 && value.y <= 100 && value.z <= 100;
    }
}
