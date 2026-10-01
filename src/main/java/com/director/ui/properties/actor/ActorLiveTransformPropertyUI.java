package com.director.ui.properties.actor;

import com.director.core.DirectorServices;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.entity.EntityLookAtClip;
import com.director.ui.actor.edit.ActorTransformEditService;
import com.director.ui.actor.edit.ActorNumericEditSession;
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

    private static ActorNumericEditSession gesture;
    private static UUID editingActor;
    private static TimelineModel editingModel;
    private static com.director.timeline.model.TimelineTrack editingTrack;
    private static String gestureWidget;
    private static boolean widgetRendered, cancelledUntilRelease;
    private static int lastFrame = -1;
    private static String error = "";
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
        var target = ActorTransformEditService.resolveTrack(actorId);
        boolean readOnly = model.isPlaying() || target == null || target.timelineTrack().isLocked()
                || target.timelineTrack().isMuted()
                || com.director.ui.actor.gizmo.ActorGizmoController.getInstance().isDragging()
                || com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().isDragging();
        int frame = ImGui.getFrameCount();
        if (gesture != null && (frame != lastFrame + 1 || readOnly || target == null
                || !gesture.isCurrent(model, target.timelineTrack(), actorId))) cancelGesture();
        lastFrame = frame; widgetRendered = false;
        if (ImGui.isKeyPressed(imgui.flag.ImGuiKey.Escape)) cancelGesture();
        if (cancelledUntilRelease && !ImGui.isMouseDown(0) && !ImGui.isAnyItemActive()) cancelledUntilRelease = false;
        syncFromSample(actorId, time);
        editingActor = actorId; editingModel = model; editingTrack = target == null ? null : target.timelineTrack();
        ImGui.beginDisabled(readOnly || cancelledUntilRelease);

        if (ImGui.collapsingHeader(Icons.TRANSFORM + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.SECTION_TRANSFORM),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.TIME_HINT_FMT, time));
            ImGui.spacing();

            UITheme.beginGroup(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.POSITION));
            inputFloat("X##actorLivePosX", posX);
            inputFloat("Y##actorLivePosY", posY);
            inputFloat("Z##actorLivePosZ", posZ);
            UITheme.endGroup();

            UITheme.beginGroup(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.ROTATION));
            inputFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.YAW) + "##actorLiveRotY", rotYaw);
            inputFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.PITCH) + "##actorLiveRotP", rotPitch);
            inputFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.ROLL) + "##actorLiveRotR", rotRoll);
            UITheme.endGroup();

            UITheme.beginGroup(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.SCALE));
            inputFloat("X##actorLiveScaleX", scaleX);
            inputFloat("Y##actorLiveScaleY", scaleY);
            inputFloat("Z##actorLiveScaleZ", scaleZ);
            UITheme.endGroup();
        }

        if (ImGui.collapsingHeader(Icons.TARGET + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.SECTION_LOOK_AT),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            EntityLookAtClip.TargetMode[] modes = {
                    EntityLookAtClip.TargetMode.WORLD_POINT,
                    EntityLookAtClip.TargetMode.CAMERA, EntityLookAtClip.TargetMode.ENTITY_UUID
            };
            if (ImGui.combo(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.TARGET_MODE) + "##actorLiveLookAtMode",
                    lookAtModeIndex, new String[]{ActorPropertyAdapterI18n.liveLookAtModeLabels()[0],
                            ActorPropertyAdapterI18n.liveLookAtModeLabels()[1],
                            ActorPropertyAdapterI18n.lookAtModeLabel(EntityLookAtClip.TargetMode.ENTITY_UUID)})) {
                applyCurrentLookAt();
            }

            float[] strength = {lookAtStrength.get()};
            boolean strengthChanged = ImGui.sliderFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.STRENGTH) + "##actorLiveLookAtStrength",
                    strength, 0f, 1f, "%.2f");
            if (strengthChanged) {
                lookAtStrength.set(strength[0]);
            }
            observe("strength", ActorNumericEditSession.Kind.LOOK_AT, strengthChanged);

            if (modes[lookAtModeIndex.get()] == EntityLookAtClip.TargetMode.WORLD_POINT) {
                UITheme.beginGroup(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LookAt.WORLD_COORDS));
                inputFloat("X##actorLiveLookX", lookAtX);
                inputFloat("Y##actorLiveLookY", lookAtY);
                inputFloat("Z##actorLiveLookZ", lookAtZ);
                UITheme.endGroup();

            } else if (modes[lookAtModeIndex.get()] == EntityLookAtClip.TargetMode.CAMERA && ImGui.button(Icons.TARGET + " "
                    + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.APPLY_LOOK_AT_CAMERA)
                    + "##actorLiveLookCam")) {
                lookAtModeIndex.set(1);
                applyCurrentLookAt();
            }
        }

        if (gesture != null && !widgetRendered) cancelGesture();
        if (!error.isEmpty()) ImGui.textWrapped(error);
        ImGui.spacing();
        ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.LiveTransform.GIZMO_HINT));
        ImGui.endDisabled();
    }

    private static void applyCurrentLookAt() {
        try {
            finishGesture();
            beginGesture("target", ActorNumericEditSession.Kind.LOOK_AT);
            gesture.updateLookTarget(lookAtModeIndex.get() == 0 ? EntityLookAtClip.TargetMode.WORLD_POINT
                    : lookAtModeIndex.get() == 1 ? EntityLookAtClip.TargetMode.CAMERA : EntityLookAtClip.TargetMode.ENTITY_UUID,
                    new Vector3f(lookAtX.get(), lookAtY.get(), lookAtZ.get()));
            finishGesture();
        } catch (RuntimeException failure) { failGesture(failure); }
    }

    private static void beginGesture(String widget, ActorNumericEditSession.Kind kind) {
        if (gesture != null && !widget.equals(gestureWidget)) finishGesture();
        if (gesture == null) {
            gesture = new ActorNumericEditSession(editingModel, editingTrack, kind,
                    ActorTransformEditService.sampleTransform(editingActor, editingModel.getCurrentTime()));
            gestureWidget = widget;
            cachedActorId = editingActor; cachedTime = editingModel.getCurrentTime();
        }
        widgetRendered = true;
    }
    private static void observe(String widget, ActorNumericEditSession.Kind kind, boolean changed) {
        if (cancelledUntilRelease) return;
        try {
            if (ImGui.isItemActivated() || changed) beginGesture(widget, kind);
            if (gesture != null && widget.equals(gestureWidget)) {
                widgetRendered = true;
                if (changed) {
                    if (kind == ActorNumericEditSession.Kind.TRANSFORM)
                        gesture.updateTransform(new Vector3f(posX.get(), posY.get(), posZ.get()),
                                new Vector3f(rotYaw.get(), rotPitch.get(), rotRoll.get()),
                                new Vector3f(scaleX.get(), scaleY.get(), scaleZ.get()));
                    else if (widget.equals("strength")) gesture.updateStrength(lookAtStrength.get());
                    else gesture.updateLookTarget(EntityLookAtClip.TargetMode.WORLD_POINT,
                                new Vector3f(lookAtX.get(), lookAtY.get(), lookAtZ.get()));
                }
                if (ImGui.isItemDeactivated() || changed && !ImGui.isItemActive()) finishGesture();
            }
        } catch (RuntimeException failure) { failGesture(failure); }
    }
    private static void finishGesture() {
        if (gesture == null) return;
        var current = gesture; gesture = null; gestureWidget = null;
        if (current.isCurrent(editingModel, editingTrack, editingActor)) current.commit();
        else current.cancel();
        cachedActorId = null; error = "";
    }
    private static void cancelGesture() {
        if (gesture != null) gesture.cancel();
        gesture = null; gestureWidget = null; cachedActorId = null; cancelledUntilRelease = true;
    }
    private static void failGesture(RuntimeException failure) {
        cancelGesture(); error = failure.getMessage() == null ? "编辑已取消" : failure.getMessage();
    }

    private static void syncFromSample(UUID actorId, float time) {
        if (Objects.equals(actorId, cachedActorId) && Math.abs(time - cachedTime) < 1e-4f && gesture != null) {
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
        lookAtModeIndex.set(lookAt.mode() == EntityLookAtClip.TargetMode.WORLD_POINT ? 0 : lookAt.mode() == EntityLookAtClip.TargetMode.CAMERA ? 1 : 2);

        cachedActorId = actorId;
        cachedTime = time;
    }

    private static void inputFloat(String label, ImFloat value) {
        boolean changed = ImGui.inputFloat(label, value);
        observe(label, label.contains("actorLiveLook") ? ActorNumericEditSession.Kind.LOOK_AT
                : ActorNumericEditSession.Kind.TRANSFORM, changed);
    }
}
