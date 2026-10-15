package com.director.ui.actor.pose;

import com.director.actor.ActorRef;
import com.director.actor.animation.asset.AnimationAsset;
import com.director.actor.animation.asset.AnimationAssetLibrary;
import com.director.actor.animation.asset.AnimationLoopMode;
import com.director.actor.animation.asset.BoneTrack;
import com.director.actor.animation.asset.ChannelKeyframe;
import com.director.actor.preview.ActorPreviewManager;
import com.director.actor.preview.ActorPosePreview;
import com.director.actor.rig.Pose;
import com.director.actor.rig.skeleton.BoneTransform;
import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.actor.rig.skeleton.RigPose;
import com.director.actor.rig.skeleton.RigWorldPose;
import com.director.client.gizmo.Axis;
import com.director.client.gizmo.GizmoManager;
import com.director.client.gizmo.GizmoMode;
import com.director.client.gizmo.GizmoType;
import com.director.core.DirectorServices;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.entity.EntityAnimationClip;
import com.director.timeline.model.entity.ActorPoseClip;
import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.model.entity.EntityClip;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.ui.timeline.commands.StateChangeCommand;
import com.director.ui.timeline.commands.TimelineCommandBridge;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.joml.Quaternionf;

import java.util.EnumMap;
import java.util.Objects;
import java.util.UUID;

/** Owns a detached bone gesture draft and its single undo transaction. */
public final class ActorBoneGizmoController {
    private static final ActorBoneGizmoController INSTANCE = new ActorBoneGizmoController();
    private static final float DEFAULT_CLIP_DURATION = 1f;
    private static final double PIVOT_EPSILON_SQ = 1.0e-8;

    private boolean ownsGizmo;
    private boolean dragging;
    private boolean pending;
    private boolean changed;
    private TimelineModel gestureModel;
    private com.director.ui.actor.edit.ActorGestureContext gestureContext;
    private float gestureTime;
    private boolean gestureAutoKey;
    private ActorAnimationAuthoringSettings.EditTarget gestureTarget;
    private com.director.actor.animation.asset.InterpolationMode gestureInterpolation;
    private ActorPoseClip originalPoseClip;
    private RigPose previewBefore;
    private Pose legacyPreviewBefore;
    private Vec3d previewPositionBefore;
    private boolean hadBoneTrack;
    private GizmoMode requestedGestureMode;
    private float clipStart, clipSpeed, assetDuration;
    private boolean clipLoop;
    private UUID actorId;
    private String boneId;
    private Vec3d lastPivot;
    private ActorTrack actorTrack;
    private TimelineTrack timelineTrack;
    private ActorPoseClip editingClip;
    private PoseClipState beforeState;
    private boolean createdClip;
    private final EnumMap<Axis, Float> baseAngles = new EnumMap<>(Axis.class);
    private boolean animationGesture;
    private AnimationAsset editingAsset;
    private BoneTrack editingBoneTrack;
    private EntityAnimationClip editingAnimationClip;
    private ChannelKeyframe<Quaternionf> beforeRotationKey;
    private ChannelKeyframe<Vector3f> beforeTranslationKey;
    private ChannelKeyframe<Vector3f> beforeScaleKey;
    private Quaternionf animationBaseRotation;
    private Vector3f animationBaseTranslation;
    private Vec3d animationActorBasePosition;
    private Vector3f animationBaseScale;
    private float animationKeyTime;
    private boolean createdAsset;
    private boolean createdAnimationClip;
    private GizmoMode activeBoneMode = GizmoMode.ROTATE;

    private ActorBoneGizmoController() {}
    public static ActorBoneGizmoController getInstance() { return INSTANCE; }
    public boolean isDragging() { return dragging || pending; }
    public UUID getActorId() { return actorId; }

    public void update() {
        if ((dragging || pending) && (!contextValid()
                || org.lwjgl.glfw.GLFW.glfwGetKey(net.minecraft.client.MinecraftClient.getInstance()
                    .getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) == org.lwjgl.glfw.GLFW.GLFW_PRESS)) cancelDraft();
        if (pending) refreshDraftPreview();
        ActorBoneSelectionController selection = ActorBoneSelectionController.getInstance();
        if (!selection.isActive() || selection.getActorId() == null || selection.getSelectedBoneId() == null) {
            deactivateOwnedGizmo();
            resetTarget();
            return;
        }
        UUID selectedActor = selection.getActorId();
        String selectedBone = selection.getSelectedBoneId();
        if (!Objects.equals(actorId, selectedActor)) {
            actorId = selectedActor;
            resolveTrack();
        }
        boneId = selectedBone;
        RigWorldPose worldPose = ActorBoneWorldQuery.resolve(actorId);
        if (worldPose == null) {
            deactivateOwnedGizmo();
            return;
        }
        GizmoMode desiredMode = desiredMode(worldPose);
        activeBoneMode = desiredMode;
        if ("root".equals(boneId) && desiredMode == GizmoMode.ROTATE) {
            deactivateOwnedGizmo();
            return;
        }
        Vector3f p = worldPose.position(boneId);
        Vec3d pivot = new Vec3d(p.x, p.y, p.z);
        configureGizmo(pivot, desiredMode);
        if (!dragging && GizmoManager.get().getContext()!=null) {
            int index=worldPose.rig().requireBoneIndex(boneId);
            int frame=desiredMode==GizmoMode.TRANSLATE ? worldPose.rig().bone(index).parentIndex() : index;
            var basis=frame>=0 ? worldPose.transform(frame) : worldPose.transform(index);
            GizmoManager.get().getContext().setOrientation(basis.getUnnormalizedRotation(new org.joml.Quaternionf()));
        }
    }

    private GizmoMode desiredMode(RigWorldPose worldPose) {
        GizmoMode requested = ActorAnimationAuthoringSettings.getInstance().getBoneGizmoMode();
        if ((requested != GizmoMode.TRANSLATE && requested != GizmoMode.SCALE)
                || ActorAnimationAuthoringSettings.getInstance().getEditTarget() == ActorAnimationAuthoringSettings.EditTarget.QUICK_POSE) {
            return GizmoMode.ROTATE;
        }
        try {
            var capabilities = worldPose.rig().bone(worldPose.rig().requireBoneIndex(boneId)).capabilities();
            return requested == GizmoMode.TRANSLATE && capabilities.translation()
                    || requested == GizmoMode.SCALE && capabilities.scale() ? requested : GizmoMode.ROTATE;
        } catch (RuntimeException ignored) {
            return GizmoMode.ROTATE;
        }
    }

    private void configureGizmo(Vec3d pivot, GizmoMode mode) {
        if (dragging) return;
        GizmoManager gm = GizmoManager.get();
        boolean ours = gm.isActive() && gm.getType() == GizmoType.TRANSFORM
                && gm.getController() != null
                && gm.getController().getTransformApplier() instanceof ActorBoneTransformApplier;
        if (!ours) {
            if (gm.isActive()) gm.deactivate();
            Box box = new Box(pivot.x - 0.06, pivot.y - 0.06, pivot.z - 0.06,
                    pivot.x + 0.06, pivot.y + 0.06, pivot.z + 0.06);
            gm.activate(GizmoType.TRANSFORM, box, pivot);
            ownsGizmo = true;
            gm.getContext().setMode(mode);
            gm.getController().setTransformApplier(
                    new ActorBoneTransformApplier(DirectorServices.get().selection(), this));
            if (mode == GizmoMode.ROTATE) gm.ensureRotationHandlesForTransform();
            lastPivot = pivot;
            return;
        }
        ownsGizmo = true;
        gm.getContext().setMode(mode);
        if (lastPivot == null || lastPivot.squaredDistanceTo(pivot) > PIVOT_EPSILON_SQ) {
            gm.setPivot(pivot);
            gm.setTargetBox(new Box(pivot.x - 0.06, pivot.y - 0.06, pivot.z - 0.06,
                    pivot.x + 0.06, pivot.y + 0.06, pivot.z + 0.06));
            lastPivot = pivot;
        }
    }

    void beginRotationGesture() {
        if (pending) cancelDraft();
        if (actorId == null || boneId == null
                || ("root".equals(boneId) && activeBoneMode == GizmoMode.ROTATE)) return;
        if (actorTrack == null) resolveTrack();
        TimelineModel model = resolveModel();
        if (actorTrack == null || model == null || timelineTrack == null || timelineTrack.isLocked() || model.isPlaying()) return;

        com.director.ui.timeline.render.ActorBoneTimelineRows.cancelAllKeyInteractions();
        ActorAnimationDopeSheet.cancelKeyInteraction();
        ActorKeyframeTimePanel.cancelAll();
        ActorBonePropertiesPanel.cancelNumericDraft();
        float time = model.getCurrentTime();
        gestureModel = model;
        gestureTime = time;
        var settings = ActorAnimationAuthoringSettings.getInstance();
        gestureContext=new com.director.ui.actor.edit.ActorGestureContext(model,
                ActorBoneSelectionController.getInstance()::getRevision,settings::getRevision);
        gestureAutoKey = settings.isAutoKeyframe();
        requestedGestureMode = settings.getBoneGizmoMode();
        gestureTarget = settings.getEditTarget();
        gestureInterpolation = settings.getInterpolation();
        changed = false;
        if (ActorPreviewManager.getInstance().getPreview(actorId) instanceof ActorPosePreview preview) {
            previewBefore = preview.getFinalRigPose();
            legacyPreviewBefore = preview.getFinalPose() == null ? null : preview.getFinalPose().copy();
            previewPositionBefore = new Vec3d(preview.getX(), preview.getY(), preview.getZ());
        }
        if (gestureTarget == ActorAnimationAuthoringSettings.EditTarget.ANIMATION) {
            beginAnimationGesture(model, time);
            return;
        }
        editingClip = findActivePoseClip(actorTrack, time);
        originalPoseClip = editingClip;
        createdClip = editingClip == null;
        if (createdClip) {
            editingClip = new ActorPoseClip("Pose", time, time + DEFAULT_CLIP_DURATION);
            seedFromPreview(editingClip);
            new EntityTimelineClip(editingClip, actorTrack.getColorRgba());
            beforeState = null;
        } else {
            beforeState = PoseClipState.capture(editingClip);
            editingClip = new ActorPoseClip("Pose draft", editingClip.getStartTime(), editingClip.getEndTime());
            beforeState.apply(editingClip);
        }
        BonePoseChannelMapper.enableOverride(editingClip, boneId);
        baseAngles.clear();
        for (Axis axis : Axis.values()) baseAngles.put(axis, BonePoseChannelMapper.read(editingClip, boneId, axis));
        dragging = true;

    }

    void beginTransformGesture() { beginRotationGesture(); }

    void updateTranslationGesture(Vec3d delta) {
        if(!dragging || !contextValid()) { if(dragging) cancelDraft(); return; }
        if (!animationGesture || editingBoneTrack == null || animationBaseTranslation == null) return;
        Vector3f value = translate(animationBaseTranslation, delta);
        editingBoneTrack.translation().put(animationKeyTime, value,
                gestureInterpolation);
        applyLiveRigTranslation(value);
        changed = !value.equals(animationBaseTranslation);
    }

    void updateScaleGesture(Vec3d delta) {
        if(!dragging || !contextValid()) { if(dragging) cancelDraft(); return; }
        if (!animationGesture || editingBoneTrack == null || animationBaseScale == null) return;
        Vector3f value = scale(animationBaseScale, delta);
        editingBoneTrack.scale().put(animationKeyTime, value,
                gestureInterpolation);
        applyLiveRigScale(value);
        changed = !value.equals(animationBaseScale);
    }

    void updateRotationGesture(Axis axis, float angleRadians) {
        if(!dragging || !contextValid()) { if(dragging) cancelDraft(); return; }
        if (animationGesture) {
            updateAnimationGesture(axis, angleRadians);
            return;
        }
        if (!dragging || editingClip == null || !BonePoseChannelMapper.isEditable(boneId, axis)) return;
        float value = baseAngles.getOrDefault(axis, 0f) + angleRadians;
        BonePoseChannelMapper.write(editingClip, boneId, axis, value);
        applyLivePreview(axis, value);
        changed = createdClip ? angleRadians != 0f : !beforeState.equals(PoseClipState.capture(editingClip));
    }

    void endRotationGesture() {
        if (!dragging) return;
        if (!contextValid() || !changed) { cancelDraft(); return; }
        dragging = false;
        pending = true;
        if (gestureAutoKey) applyDraft();
    }

    public boolean hasPendingDraft() { return pending; }

    public String draftDescription() {
        return boneId + " · " + gestureTime + "s · " + activeBoneMode
                + (animationGesture ? " · " + editingAsset.id() + " (" + net.minecraft.client.resource.language.I18n.translate("gui.director.actor.bone_draft.shared") + ")" : " / " + net.minecraft.client.resource.language.I18n.translate("gui.director.actor.bone_draft.pose"));
    }

    private boolean contextValid() {
        var selection = ActorBoneSelectionController.getInstance();
        var settings = ActorAnimationAuthoringSettings.getInstance();
        return gestureContext != null && gestureContext.isCurrent(resolveModel()) && gestureModel != null && gestureModel == resolveModel()
                && !gestureModel.isPlaying() && Float.compare(gestureTime, gestureModel.getCurrentTime()) == 0
                && gestureModel.getAllTracks().contains(timelineTrack) && timelineTrack.getType()==TrackType.ACTOR
                && com.director.actor.ActorRegistryManager.getInstance().getActor(actorId).isPresent() && !timelineTrack.isLocked() && !timelineTrack.isMuted()
                && actorTrack.getActorRef()!=null && Objects.equals(actorId,actorTrack.getActorRef().getActorId())
                && timelineTrack.getActorTrackPayload() == actorTrack
                && selection.isActive() && Objects.equals(actorId, selection.getActorId())
                && Objects.equals(boneId, selection.getSelectedBoneId())
                && gestureTarget == settings.getEditTarget()
                && (gestureTarget == ActorAnimationAuthoringSettings.EditTarget.QUICK_POSE
                    || requestedGestureMode == settings.getBoneGizmoMode())
                && (createdAnimationClip || editingAnimationClip == null
                    || ActorEmbeddedAnimation.bound(actorTrack, editingAnimationClip) && !editingAnimationClip.isMuted())
                && (editingAsset == null || (createdAsset
                    ? !AnimationAssetLibrary.getInstance().contains(editingAsset.id())
                    : AnimationAssetLibrary.getInstance().find(editingAsset.id()).orElse(null) == editingAsset))
                && animationSourceUnchanged()
                && (originalPoseClip == null || actorTrack.getClips().contains(originalPoseClip)
                    && !originalPoseClip.isMuted() && beforeState.equals(PoseClipState.capture(originalPoseClip)));
    }

    private boolean animationSourceUnchanged() {
        if (!animationGesture) return true;
        if (Float.compare(clipStart, editingAnimationClip.getStartTime()) != 0
                || Float.compare(clipSpeed, editingAnimationClip.getSpeed()) != 0
                || clipLoop != editingAnimationClip.isLoop()
                || Float.compare(assetDuration, editingAsset.duration()) != 0
                || !editingAsset.id().equals(editingAnimationClip.getAnimationId())) return false;
        BoneTrack original = editingAsset.findBoneTrack(boneId).orElse(null);
        if (hadBoneTrack == (original == null)) return false;
        if (original == null) return true;
        return animationBaseTranslation != null
                ? Objects.equals(beforeTranslationKey, original.translation().findAt(animationKeyTime).orElse(null))
                : animationBaseScale != null
                ? Objects.equals(beforeScaleKey, original.scale().findAt(animationKeyTime).orElse(null))
                : Objects.equals(beforeRotationKey, original.rotation().findAt(animationKeyTime).orElse(null));
    }

    public void applyDraft() {
        if (!pending) return;
        if (!contextValid()) { cancelDraft(); return; }
        if (animationGesture) registerAnimationUndo(gestureModel);
        else registerUndo(gestureModel, editingClip);
        clearDraft();
    }

    public void cancelDraft() {
        if ((dragging || pending) && gestureModel==resolveModel()
                && Float.compare(gestureTime,gestureModel.getCurrentTime())==0) {
            if (ActorPreviewManager.getInstance().getPreview(actorId) instanceof ActorPosePreview preview) {
                if (legacyPreviewBefore != null) preview.setFinalPose(legacyPreviewBefore);
                preview.setFinalRigPose(previewBefore);
                if (previewPositionBefore != null) preview.refreshPositionAndAngles(
                        previewPositionBefore.x, previewPositionBefore.y, previewPositionBefore.z,
                        preview.getYaw(), preview.getPitch());
            }
        }
        clearDraft();
    }

    private void clearDraft() {
        pending = false;
        resetAnimationGesture();
        editingClip = null;
        originalPoseClip = null;
        beforeState = null;
        createdClip = false;
        changed = false;
        gestureModel = null;
        gestureContext = null;
        previewBefore = null;
        legacyPreviewBefore = null;
        previewPositionBefore = null;
        baseAngles.clear();
        lastPivot = null;
    }

    private void refreshDraftPreview() {
        if (animationGesture && editingBoneTrack != null) {
            if (animationBaseTranslation != null) editingBoneTrack.translation().findAt(animationKeyTime)
                    .ifPresent(key -> applyLiveRigTranslation(key.value()));
            else if (animationBaseScale != null) editingBoneTrack.scale().findAt(animationKeyTime)
                    .ifPresent(key -> applyLiveRigScale(key.value()));
            else editingBoneTrack.rotation().findAt(animationKeyTime)
                    .ifPresent(key -> applyLiveRigPreview(key.value()));
        } else if (editingClip != null) {
            for (Axis axis : Axis.values()) if (BonePoseChannelMapper.isEditable(boneId, axis))
                applyLivePreview(axis, BonePoseChannelMapper.read(editingClip, boneId, axis));
        }
    }

    private void beginAnimationGesture(TimelineModel model, float timelineTime) {
        ActorAnimationAuthoringSettings settings = ActorAnimationAuthoringSettings.getInstance();
        AnimationAssetLibrary library = AnimationAssetLibrary.getInstance();
        editingAnimationClip = findActiveAssetClip(actorTrack, timelineTime, library);
        editingAsset = editingAnimationClip == null ? null : library.find(editingAnimationClip.getAnimationId()).orElse(null);

        if (editingAsset == null) {
            String assetId = settings.getAssetId();
            editingAsset = library.find(assetId).orElse(null);
            createdAsset = editingAsset == null;
            if (createdAsset) {
                var rig = com.director.actor.ActorRegistryManager.getInstance().getActor(actorId)
                        .flatMap(com.director.actor.rig.ActorRigCompatibility::resolveRig)
                        .orElse(HumanoidRig.DEFINITION);
                editingAsset = new AnimationAsset(assetId, assetId, rig,
                        settings.getNewAssetDuration(), AnimationLoopMode.ONCE);

            }
            editingAnimationClip = new EntityAnimationClip("Keyframe animation", timelineTime,
                    timelineTime + Math.max(0.1f, editingAsset.duration()));
            editingAnimationClip.setAnimationId(editingAsset.id());
            editingAnimationClip.setSource(EntityAnimationClip.AnimationSource.CUSTOM);
            editingAnimationClip.setLoop(editingAsset.loopMode() != AnimationLoopMode.ONCE);
            new EntityTimelineClip(editingAnimationClip, actorTrack.getColorRgba());
            createdAnimationClip = true;
        }

        float speed = Math.max(0.01f, editingAnimationClip.getSpeed());
        var actorRig = com.director.actor.ActorRegistryManager.getInstance().getActor(actorId)
                .flatMap(com.director.actor.rig.ActorRigCompatibility::resolveRig).orElse(null);
        if (actorRig == null || !actorRig.id().equals(editingAsset.rigId())) { clearDraft(); return; }
        animationKeyTime = editingAsset.normalizeTime((timelineTime - editingAnimationClip.getStartTime()) * speed,
                editingAnimationClip.isLoop() ? editingAsset.loopMode() : AnimationLoopMode.ONCE);
        clipStart = editingAnimationClip.getStartTime();
        clipSpeed = editingAnimationClip.getSpeed();
        clipLoop = editingAnimationClip.isLoop();
        assetDuration = editingAsset.duration();
        hadBoneTrack = editingAsset.findBoneTrack(boneId).isPresent();
        BoneTrack original = editingAsset.findBoneTrack(boneId).orElseGet(BoneTrack::new);
        editingBoneTrack = new BoneTrack();
        if (activeBoneMode == GizmoMode.TRANSLATE) {
            beforeTranslationKey = original.translation().findAt(animationKeyTime).orElse(null);
            animationBaseTranslation = resolveAnimationBaseTranslation(editingAsset, animationKeyTime);
            if (ActorPreviewManager.getInstance().getPreview(actorId) instanceof ActorPosePreview preview) {
                animationActorBasePosition = new Vec3d(preview.getX(), preview.getY(), preview.getZ()).subtract(
                        animationBaseTranslation.x, animationBaseTranslation.y, animationBaseTranslation.z);
            }
        } else if (activeBoneMode == GizmoMode.SCALE) {
            beforeScaleKey = original.scale().findAt(animationKeyTime).orElse(null);
            animationBaseScale = editingAsset.sample(animationKeyTime, AnimationLoopMode.ONCE)
                    .localTransform(boneId).scale();
        } else {
            beforeRotationKey = original.rotation().findAt(animationKeyTime).orElse(null);
            animationBaseRotation = resolveAnimationBaseRotation(editingAsset, animationKeyTime);
        }
        // An already authored channel is being edited on the timeline, not auditioned.
        // Save its next key on release even when creation of new channels is manual.
        gestureAutoKey |= switch (activeBoneMode) {
            case TRANSLATE -> !original.translation().isEmpty();
            case SCALE -> !original.scale().isEmpty();
            default -> !original.rotation().isEmpty();
        };
        animationGesture = true;
        dragging = true;

    }

    private Quaternionf resolveAnimationBaseRotation(AnimationAsset asset, float keyTime) {
        if (asset.findBoneTrack(boneId).filter(track -> !track.rotation().isEmpty()).isPresent()) {
            return asset.sample(keyTime, AnimationLoopMode.ONCE).localTransform(boneId).rotation();
        }
        if (ActorPreviewManager.getInstance().getPreview(actorId) instanceof ActorPosePreview preview) {
            RigPose pose = preview.getFinalRigPose();
            if (pose != null && pose.rig().id().equals(asset.rigId())) return pose.localTransform(boneId).rotation();
        }
        return asset.rig().bone(asset.rig().requireBoneIndex(boneId)).restLocal().rotation();
    }

    private Vector3f resolveAnimationBaseTranslation(AnimationAsset asset, float keyTime) {
        if (asset.findBoneTrack(boneId).filter(track -> !track.translation().isEmpty()).isPresent()) {
            return asset.sample(keyTime, AnimationLoopMode.ONCE).localTransform(boneId).translation();
        }
        if (ActorPreviewManager.getInstance().getPreview(actorId) instanceof ActorPosePreview preview) {
            RigPose pose = preview.getFinalRigPose();
            if (pose != null && pose.rig().id().equals(asset.rigId())) return pose.localTransform(boneId).translation();
        }
        return asset.rig().bone(asset.rig().requireBoneIndex(boneId)).restLocal().translation();
    }

    private void updateAnimationGesture(Axis axis, float angleRadians) {
        if (!dragging || editingBoneTrack == null || animationBaseRotation == null) return;
        Quaternionf value = rotate(new Quaternionf(animationBaseRotation), axis, angleRadians);
        editingBoneTrack.rotation().put(animationKeyTime, value,
                gestureInterpolation);
        applyLiveRigPreview(value);
        changed = !value.equals(animationBaseRotation, 1.0e-6f);
    }

    static Quaternionf rotate(Quaternionf base, Axis axis, float angleRadians) {
        return switch (axis) {
            case X -> base.rotateX(angleRadians);
            case Y -> base.rotateY(angleRadians);
            case Z -> base.rotateZ(angleRadians);
        };
    }

    static Vector3f translate(Vector3f base, Vec3d delta) {
        return new Vector3f(base).add((float) delta.x, (float) delta.y, (float) delta.z);
    }

    static Vector3f scale(Vector3f base, Vec3d delta) {
        return new Vector3f(
                Math.max(0.01f, base.x * (1f + (float) delta.x)),
                Math.max(0.01f, base.y * (1f + (float) delta.y)),
                Math.max(0.01f, base.z * (1f + (float) delta.z)));
    }

    private void applyLiveRigPreview(Quaternionf rotation) {
        if (!(ActorPreviewManager.getInstance().getPreview(actorId) instanceof ActorPosePreview preview)) return;
        RigPose pose = preview.getFinalRigPose();
        if (pose == null || !pose.rig().id().equals(editingAsset.rigId())) pose = editingAsset.sample(animationKeyTime, AnimationLoopMode.ONCE);
        BoneTransform current = pose.localTransform(boneId);
        pose.setLocalTransform(boneId, current.withRotation(rotation));
        preview.setFinalRigPose(pose);
    }

    private void applyLiveRigTranslation(Vector3f translation) {
        if (!(ActorPreviewManager.getInstance().getPreview(actorId) instanceof ActorPosePreview preview)) return;
        RigPose pose = preview.getFinalRigPose();
        if (pose == null || !pose.rig().id().equals(editingAsset.rigId())) pose = editingAsset.sample(animationKeyTime, AnimationLoopMode.ONCE);
        BoneTransform current = pose.localTransform(boneId);
        pose.setLocalTransform(boneId, new BoneTransform(translation, current.rotation(), current.scale()));
        preview.setFinalRigPose(pose);
        if ("root".equals(boneId) && animationActorBasePosition != null) {
            Vec3d target = animationActorBasePosition.add(translation.x, translation.y, translation.z);
            preview.refreshPositionAndAngles(target.x, target.y, target.z, preview.getYaw(), preview.getPitch());
            GizmoManager.get().setPivot(target);
        }
    }

    private void applyLiveRigScale(Vector3f scale) {
        if (!(ActorPreviewManager.getInstance().getPreview(actorId) instanceof ActorPosePreview preview)) return;
        RigPose pose = preview.getFinalRigPose();
        if (pose == null || !pose.rig().id().equals(editingAsset.rigId())) pose = editingAsset.sample(animationKeyTime, AnimationLoopMode.ONCE);
        BoneTransform current = pose.localTransform(boneId);
        pose.setLocalTransform(boneId, new BoneTransform(current.translation(), current.rotation(), scale));
        preview.setFinalRigPose(pose);
    }

    private void registerAnimationUndo(TimelineModel model) {
        AnimationAsset asset = editingAsset;
        BoneTrack draftTrack = editingBoneTrack;
        BoneTrack boneTrack = asset.findBoneTrack(boneId).orElseGet(BoneTrack::new);
        String targetBone = boneId;
        boolean addBoneTrack = !hadBoneTrack;
        EntityAnimationClip clip = editingAnimationClip;
        EntityTimelineClip wrapper = clip == null ? null : clip.getTimelineClip();
        ChannelKeyframe<Quaternionf> before = beforeRotationKey;
        ChannelKeyframe<Quaternionf> after = draftTrack.rotation().findAt(animationKeyTime).orElse(null);
        ChannelKeyframe<Vector3f> beforeTranslation = beforeTranslationKey;
        ChannelKeyframe<Vector3f> afterTranslation = draftTrack.translation().findAt(animationKeyTime).orElse(null);
        boolean translating = animationBaseTranslation != null;
        ChannelKeyframe<Vector3f> beforeScale = beforeScaleKey;
        ChannelKeyframe<Vector3f> afterScale = draftTrack.scale().findAt(animationKeyTime).orElse(null);
        boolean scaling = animationBaseScale != null;
        float keyTime = animationKeyTime;
        boolean addAsset = createdAsset;
        boolean addClip = createdAnimationClip;
        ActorTrack targetActorTrack = actorTrack;
        TimelineTrack targetTimelineTrack = timelineTrack;
        if ((!translating && !scaling && after == null) || (translating && afterTranslation == null)
                || (scaling && afterScale == null)) {
            return;
        }
        TimelineCommandBridge.execute(new StateChangeCommand(model, "Keyframe actor bone",
                () -> {
                    if (scaling) {
                        if (beforeScale == null) boneTrack.scale().removeAt(keyTime);
                        else boneTrack.scale().put(beforeScale.time(), beforeScale.value(), beforeScale.interpolation());
                    } else if (translating) {
                        if (beforeTranslation == null) boneTrack.translation().removeAt(keyTime);
                        else boneTrack.translation().put(beforeTranslation.time(), beforeTranslation.value(), beforeTranslation.interpolation());
                    } else if (before == null) boneTrack.rotation().removeAt(keyTime);
                    else boneTrack.rotation().put(before.time(), before.value(), before.interpolation());
                    if (addBoneTrack && boneTrack.isEmpty()) asset.removeBoneTrack(targetBone);
                    if (addClip && clip != null) {
                        targetActorTrack.removeClip(clip.getId());
                        if (targetTimelineTrack != null && wrapper != null) targetTimelineTrack.removeClip(wrapper);
                    }
                    if (addAsset) AnimationAssetLibrary.getInstance().remove(asset.id());
                },
                () -> {
                    if (addAsset) AnimationAssetLibrary.getInstance().register(asset);
                    if (addBoneTrack) asset.attachBoneTrack(targetBone, boneTrack);
                    if (addClip && clip != null && !targetActorTrack.getClips().contains(clip)) {
                        targetActorTrack.addClip(clip);
                        if (targetTimelineTrack != null && wrapper != null && !targetTimelineTrack.getClips().contains(wrapper)) targetTimelineTrack.addClip(wrapper);
                    }
                    if (scaling) boneTrack.scale().put(afterScale.time(), afterScale.value(), afterScale.interpolation());
                    else if (translating) boneTrack.translation().put(afterTranslation.time(), afterTranslation.value(), afterTranslation.interpolation());
                    else boneTrack.rotation().put(after.time(), after.value(), after.interpolation());
                }));
    }

    private void resetAnimationGesture() {
        dragging = false;
        animationGesture = false;
        editingAsset = null;
        editingBoneTrack = null;
        editingAnimationClip = null;
        beforeRotationKey = null;
        beforeTranslationKey = null;
        beforeScaleKey = null;
        animationBaseRotation = null;
        animationBaseTranslation = null;
        animationActorBasePosition = null;
        animationBaseScale = null;
        createdAsset = false;
        createdAnimationClip = false;
        animationKeyTime = 0f;
    }

    public void deactivate() {
        cancelDraft();
        deactivateOwnedGizmo();
        resetTarget();
    }

    private void seedFromPreview(ActorPoseClip clip) {
        if (!(ActorPreviewManager.getInstance().getPreview(actorId) instanceof ActorPosePreview preview)) return;
        Pose pose = preview.getFinalPose();
        if (pose == null) return;
        PoseClipState.fromPose(pose).applyValues(clip);
    }

    private void applyLivePreview(Axis axis, float value) {
        if (!(ActorPreviewManager.getInstance().getPreview(actorId) instanceof ActorPosePreview preview)) return;
        Pose current = preview.getFinalPose();
        if (current == null) current = new Pose(); else current = current.copy();
        BonePoseChannelMapper.write(current, boneId, axis, value);
        preview.setFinalPose(current);
    }

    private void registerUndo(TimelineModel model, ActorPoseClip clip) {
        PoseClipState after = PoseClipState.capture(clip);
        if (createdClip) {
            ActorTrack targetActorTrack = actorTrack;
            TimelineTrack targetTimelineTrack = timelineTrack;
            EntityTimelineClip wrapper = clip.getTimelineClip();
            TimelineCommandBridge.execute(new StateChangeCommand(model, "Pose actor bone",
                    () -> { targetActorTrack.removeClip(clip.getId()); if (targetTimelineTrack != null && wrapper != null) targetTimelineTrack.removeClip(wrapper); },
                    () -> { if (!targetActorTrack.getClips().contains(clip)) targetActorTrack.addClip(clip); if (targetTimelineTrack != null && wrapper != null && !targetTimelineTrack.getClips().contains(wrapper)) targetTimelineTrack.addClip(wrapper); after.apply(clip); }));
        } else if (beforeState != null && !beforeState.equals(after)) {

            PoseClipState before = beforeState;
            ActorPoseClip target = originalPoseClip;
            TimelineCommandBridge.execute(new StateChangeCommand(model, "Pose actor bone",
                    () -> before.apply(target), () -> after.apply(target)));
        } else {
            model.markDirty();
        }
    }

    private static ActorPoseClip findActivePoseClip(ActorTrack track, float time) {
        ActorPoseClip result = null;
        for (EntityClip clip : track.getClips()) {
            if (clip instanceof ActorPoseClip pose && !pose.isMuted()
                    && time >= pose.getStartTime() && time <= pose.getEndTime()
                    && (result == null || pose.getStartTime() >= result.getStartTime())) result = pose;
        }
        return result;
    }

    private static EntityAnimationClip findActiveAssetClip(ActorTrack track, float time,
                                                            AnimationAssetLibrary library) {
        EntityAnimationClip result = null;
        for (EntityClip clip : ActorEmbeddedAnimation.clips(track)) {
            if (clip instanceof EntityAnimationClip animation && !animation.isMuted()
                    && time >= animation.getStartTime() && time <= animation.getEndTime()
                    && library.contains(animation.getAnimationId())
                    && (result == null || animation.getStartTime() >= result.getStartTime())) result = animation;
        }
        return result;
    }

    private void resolveTrack() {
        actorTrack = null;
        timelineTrack = null;
        TimelineModel model = resolveModel();
        if (model == null || actorId == null) return;
        for (TimelineTrack track : model.getTracks()) {
            if (track == null || track.getType() != TrackType.ACTOR) continue;
            ActorTrack candidate = track.getActorTrackPayload();
            ActorRef ref = candidate == null ? null : candidate.getActorRef();
            if (ref != null && Objects.equals(actorId, ref.getActorId())) {
                actorTrack = candidate;
                timelineTrack = track;
                return;
            }
        }
    }

    private static TimelineModel resolveModel() {
        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        return model != null ? model : com.director.ui.timeline.playback.PlaybackOrchestrator.get().getBoundModel();
    }

    private void deactivateOwnedGizmo() {
        GizmoManager gm = GizmoManager.get();
        if (ownsGizmo && gm.isActive() && gm.getController() != null
                && gm.getController().getTransformApplier() instanceof ActorBoneTransformApplier) gm.deactivate();
        ownsGizmo = false;
        lastPivot = null;
    }

    private void resetTarget() { actorId = null; boneId = null; actorTrack = null; timelineTrack = null; }

    private record PoseClipState(boolean body, boolean head, boolean arms, boolean legs,
            float bp, float by, float br, float hp, float hy,
            float lap, float lay, float lar, float rap, float ray, float rar, float llp, float rlp) {
        static PoseClipState capture(ActorPoseClip c) {
            return new PoseClipState(c.isOverrideBody(), c.isOverrideHead(), c.isOverrideArms(), c.isOverrideLegs(),
                    c.getBodyPitch(), c.getBodyYaw(), c.getBodyRoll(), c.getHeadPitch(), c.getHeadYaw(),
                    c.getLeftArmPitch(), c.getLeftArmYaw(), c.getLeftArmRoll(), c.getRightArmPitch(),
                    c.getRightArmYaw(), c.getRightArmRoll(), c.getLeftLegPitch(), c.getRightLegPitch());
        }
        static PoseClipState fromPose(Pose p) {
            return new PoseClipState(false, false, false, false, p.bodyPitch, p.bodyYaw, p.bodyRoll,
                    p.headPitch, p.headYaw, p.leftArmPitch, p.leftArmYaw, p.leftArmRoll,
                    p.rightArmPitch, p.rightArmYaw, p.rightArmRoll, p.leftLegPitch, p.rightLegPitch);
        }
        void apply(ActorPoseClip c) { c.setOverrideBody(body); c.setOverrideHead(head); c.setOverrideArms(arms); c.setOverrideLegs(legs); applyValues(c); }
        void applyValues(ActorPoseClip c) {
            c.setBodyPitch(bp); c.setBodyYaw(by); c.setBodyRoll(br); c.setHeadPitch(hp); c.setHeadYaw(hy);
            c.setLeftArmPitch(lap); c.setLeftArmYaw(lay); c.setLeftArmRoll(lar);
            c.setRightArmPitch(rap); c.setRightArmYaw(ray); c.setRightArmRoll(rar);
            c.setLeftLegPitch(llp); c.setRightLegPitch(rlp);
        }
    }
}
