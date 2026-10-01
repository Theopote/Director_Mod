package com.director.ui.properties.actor;

import com.director.core.DirectorServices;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.entity.EntityLookAtClip;
import com.director.ui.actor.edit.ActorTransformEditService;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import org.joml.Vector3f;

import java.util.Objects;
import java.util.UUID;

/**
 * 选中演员时，在属性面板展示播放头时刻的变换与朝向数值，并写回时间线片段。
 */
public final class ActorLiveTransformPropertyUI {

    private static final ImFloat posX = new ImFloat();
    private static final ImFloat posY = new ImFloat();
    private static final ImFloat posZ = new ImFloat();
    private static final ImFloat rotYaw = new ImFloat();
    private static final ImFloat rotPitch = new ImFloat();
    private static final ImFloat rotRoll = new ImFloat();
    private static final ImFloat scaleX = new ImFloat(1f);
    private static final ImFloat scaleY = new ImFloat(1f);
    private static final ImFloat scaleZ = new ImFloat(1f);
    private static final ImFloat lookAtX = new ImFloat();
    private static final ImFloat lookAtY = new ImFloat();
    private static final ImFloat lookAtZ = new ImFloat();
    private static final ImFloat lookAtStrength = new ImFloat(1f);
    private static final ImInt lookAtModeIndex = new ImInt();

    private static UUID cachedActorId;
    private static float cachedTime = Float.NaN;

    private ActorLiveTransformPropertyUI() {
    }

    public static void render(UUID actorId) {
        if (actorId == null) {
            return;
        }
        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model == null) {
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.TIMELINE_NOT_LOADED));
            return;
        }
        float time = model.getCurrentTime();
        syncFromSample(actorId, time);
        var target = ActorTransformEditService.resolveTrack(actorId);
        boolean readOnly = model.isPlaying() || target == null || target.timelineTrack().isLocked()
                || target.timelineTrack().isMuted()
                || com.director.ui.actor.gizmo.ActorGizmoController.getInstance().isDragging()
                || com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().isDragging();
        ImGui.beginDisabled(readOnly);

        if (ImGui.collapsingHeader(Icons.TRANSFORM + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.SECTION_TRANSFORM),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.TIME_HINT_FMT, time));
            ImGui.spacing();

            boolean changed = false;
            UITheme.beginGroup(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.POSITION));
            changed |= inputFloat("X##actorLivePosX", posX);
            changed |= inputFloat("Y##actorLivePosY", posY);
            changed |= inputFloat("Z##actorLivePosZ", posZ);
            UITheme.endGroup();

            UITheme.beginGroup(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.ROTATION));
            changed |= inputFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.YAW) + "##actorLiveRotY", rotYaw);
            changed |= inputFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.PITCH) + "##actorLiveRotP", rotPitch);
            changed |= inputFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.ROLL) + "##actorLiveRotR", rotRoll);
            UITheme.endGroup();

            UITheme.beginGroup(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.SCALE));
            changed |= inputFloat("X##actorLiveScaleX", scaleX);
            changed |= inputFloat("Y##actorLiveScaleY", scaleY);
            changed |= inputFloat("Z##actorLiveScaleZ", scaleZ);
            UITheme.endGroup();

            if (changed) {
                ActorTransformEditService.applyTransform(
                        actorId,
                        time,
                        new Vector3f(posX.get(), posY.get(), posZ.get()),
                        new Vector3f(rotYaw.get(), rotPitch.get(), rotRoll.get()),
                        new Vector3f(scaleX.get(), scaleY.get(), scaleZ.get()));
            }
        }

        if (ImGui.collapsingHeader(Icons.TARGET + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.SECTION_LOOK_AT),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            EntityLookAtClip.TargetMode[] modes = {
                    EntityLookAtClip.TargetMode.WORLD_POINT,
                    EntityLookAtClip.TargetMode.CAMERA
            };
            if (ImGui.combo(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.TARGET_MODE) + "##actorLiveLookAtMode",
                    lookAtModeIndex, ActorPropertyAdapterI18n.liveLookAtModeLabels())) {
                applyCurrentLookAt(actorId, time);
            }

            float[] strength = {lookAtStrength.get()};
            if (ImGui.sliderFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.STRENGTH) + "##actorLiveLookAtStrength",
                    strength, 0f, 1f, "%.2f")) {
                lookAtStrength.set(strength[0]);
                applyCurrentLookAt(actorId, time);
            }

            if (modes[lookAtModeIndex.get()] == EntityLookAtClip.TargetMode.WORLD_POINT) {
                UITheme.beginGroup(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LookAt.WORLD_COORDS));
                boolean pointChanged = false;
                pointChanged |= inputFloat("X##actorLiveLookX", lookAtX);
                pointChanged |= inputFloat("Y##actorLiveLookY", lookAtY);
                pointChanged |= inputFloat("Z##actorLiveLookZ", lookAtZ);
                UITheme.endGroup();
                if (pointChanged) {
                    applyCurrentLookAt(actorId, time);
                }
            } else if (ImGui.button(Icons.TARGET + " "
                    + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.APPLY_LOOK_AT_CAMERA)
                    + "##actorLiveLookCam")) {
                lookAtModeIndex.set(1);
                applyCurrentLookAt(actorId, time);
            }
        }

        ImGui.spacing();
        ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.GIZMO_HINT));
        ImGui.endDisabled();
    }

    private static void applyCurrentLookAt(UUID actorId, float time) {
        if (lookAtModeIndex.get() == 0) {
            ActorTransformEditService.applyLookAtWorldPoint(
                    actorId, time, lookAtX.get(), lookAtY.get(), lookAtZ.get(), lookAtStrength.get());
        } else {
            ActorTransformEditService.applyLookAtCamera(actorId, time, lookAtStrength.get());
        }
    }

    private static void syncFromSample(UUID actorId, float time) {
        if (Objects.equals(actorId, cachedActorId) && Math.abs(time - cachedTime) < 1e-4f && ImGui.isAnyItemActive()) {
            return;
        }
        ActorTransformEditService.TransformSnapshot transform =
                ActorTransformEditService.sampleTransform(actorId, time);
        posX.set(transform.position().x);
        posY.set(transform.position().y);
        posZ.set(transform.position().z);
        rotYaw.set(transform.rotation().x);
        rotPitch.set(transform.rotation().y);
        rotRoll.set(transform.rotation().z);
        scaleX.set(transform.scale().x);
        scaleY.set(transform.scale().y);
        scaleZ.set(transform.scale().z);

        ActorTransformEditService.LookAtSnapshot lookAt =
                ActorTransformEditService.sampleLookAt(actorId, time);
        lookAtX.set(lookAt.worldPoint().x);
        lookAtY.set(lookAt.worldPoint().y);
        lookAtZ.set(lookAt.worldPoint().z);
        lookAtStrength.set(lookAt.strength());
        lookAtModeIndex.set(lookAt.mode() == EntityLookAtClip.TargetMode.WORLD_POINT ? 0 : 1);

        cachedActorId = actorId;
        cachedTime = time;
    }

    private static boolean inputFloat(String label, ImFloat value) {
        return ImGui.inputFloat(label, value);
    }
}
