package com.director.ui.properties.adapters;

import com.director.core.DirectorServices;
import com.director.actor.Actor;
import com.director.actor.ActorRegistryManager;
import com.director.actor.action.ActorActionType;
import com.director.actor.rig.ActorRigProfile;
import com.director.ui.actor.selection.ActorSelectionHub;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.PropertyContext;
import com.director.ui.properties.actor.ActorLiveTransformPropertyUI;
import com.director.ui.properties.actor.RigProfileStylePropertyUI;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.entity.ActorActionClip;
import com.director.timeline.model.entity.ActorPoseClip;
import com.director.timeline.model.entity.ActorRouteClip;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.timeline.model.entity.ActorTrack;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.actor.path.ActorPathController;
import com.director.ui.tools.EditorTool;
import com.director.ui.tools.ToolRegistry;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.Objects;
import java.util.UUID;

public final class EditorToolPropertyAdapter implements IPropertyAdapter<EditorTool> {
    private final ImInt actionIndex = new ImInt(0);
    private final ImFloat actionIntensity = new ImFloat(1.0f);
    private final ImFloat actionDuration = new ImFloat(1.0f);
    private final ImBoolean actionLoop = new ImBoolean(true);
    private final ImFloat poseDuration = new ImFloat(1.0f);
    private final ImBoolean poseBody = new ImBoolean(false);
    private final ImBoolean poseHead = new ImBoolean(true);
    private final ImBoolean poseArms = new ImBoolean(false);
    private final ImBoolean poseLegs = new ImBoolean(false);
    private final ImFloat poseHeadPitchDeg = new ImFloat(0.0f);
    private final ImFloat poseHeadYawDeg = new ImFloat(0.0f);
    private final ImFloat poseBodyPitchDeg = new ImFloat(0.0f);
    private final ImFloat poseBodyYawDeg = new ImFloat(0.0f);
    private final ImFloat poseBodyRollDeg = new ImFloat(0.0f);
    private final ImFloat poseLeftArmDeg = new ImFloat(0.0f);
    private final ImFloat poseRightArmDeg = new ImFloat(0.0f);
    private final ImFloat poseLeftLegDeg = new ImFloat(0.0f);
    private final ImFloat poseRightLegDeg = new ImFloat(0.0f);
    private final ImBoolean animationAutoKeyframe = new ImBoolean(false);
    private final ImString animationAssetId = new ImString("project:actor_animation", 128);
    private final ImInt animationInterpolation = new ImInt(2);
    private final ImFloat animationAssetDuration = new ImFloat(5f);
    private final ImInt animationBoneGizmoMode = new ImInt(0);

    @Override
    public Class<EditorTool> getTargetType() {
        return EditorTool.class;
    }

    @Override
    public boolean supports(PropertyContext context, Object target) {
        return context == PropertyContext.TOOL && target instanceof EditorTool;
    }

    @Override
    public String getAdapterName() {
        return Icons.TOOLBOX + " " + tr(DirectorLang.Properties.AdapterEditorTool.ADAPTER_NAME);
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean renderProperties(EditorTool tool) {
        if (tool == null) {
            ImGui.textDisabled(tr(DirectorLang.Properties.AdapterEditorTool.NO_TOOL_SELECTED));
            return false;
        }

        renderToolHeader(tool);
        if (ToolRegistry.isActorToolId(tool.getId())) {
            renderActorToolProperties(tool);
        } else {
            renderGenericToolProperties(tool);
        }
        return false;
    }

    private void renderToolHeader(EditorTool tool) {
        UITheme.label(tool.getIcon() + " " + tool.getDisplayName(), UITheme.DarkTheme.TEXT_SELECTED);
        if (tool.getDescription() != null && !tool.getDescription().isBlank()) {
            ImGui.textWrapped(tool.getDescription());
        }
        ImGui.spacing();
        ImGui.separator();
    }

    private void renderGenericToolProperties(EditorTool tool) {
        if (ImGui.collapsingHeader(Icons.INFO + " " + tr(DirectorLang.Properties.AdapterEditorTool.SECTION_STATUS),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.text(tr(DirectorLang.Properties.AdapterEditorTool.ID_FMT, tool.getId()));
            ImGui.text(tr(DirectorLang.Properties.AdapterEditorTool.GROUP_FMT, tool.getGroup()));
            ImGui.text(tool.isEnabled()
                    ? tr(DirectorLang.Properties.AdapterEditorTool.STATUS_AVAILABLE)
                    : tr(DirectorLang.Properties.AdapterEditorTool.STATUS_UNAVAILABLE));
        }
    }

    private void renderActorToolProperties(EditorTool tool) {
        Actor actor = resolveSelectedActor();
        if (ImGui.collapsingHeader(Icons.SMILE + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.CURRENT_ACTOR),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            if (actor == null) {
                ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.NO_ACTOR_SELECTED));
                ImGui.textWrapped(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.SELECT_ACTOR_HINT));
            } else {
                ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.NAME_FMT, actor.getName()));
                ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.TYPE_FMT,
                        ActorPropertyAdapterI18n.actorTypeLabel(actor.getType())));
                ActorRigProfile rig = actor.getRigProfile();
                ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.RIG_FMT,
                        rig != null ? rig.getClass().getSimpleName()
                                : ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.RIG_NONE)));
            }
        }

        if (actor != null && actor.getRigProfile() != null) {
            RigProfileStylePropertyUI.render(actor.getRigProfile().getStyle());
        }

        if (Objects.equals(tool.getId(), ToolRegistry.ACTOR_POSE_TOOL_ID)) {
            renderAnimationAuthoring();
            renderPoseAuthoring(actor);
            renderLocalPoseAuthoring(actor);
        } else if (Objects.equals(tool.getId(), ToolRegistry.ACTOR_PATH_TOOL_ID)) {
            renderActorPathToolProperties(actor);
        } else if (ToolRegistry.isActorTransformGizmoToolId(tool.getId())) {
            if (actor != null) {
                ActorLiveTransformPropertyUI.render(actor.getActorId());
            } else {
                renderTransformHint();
            }
        }
    }

    private void renderAnimationAuthoring() {
        if (!ImGui.collapsingHeader(Icons.ANIMATION + " 关键帧动画", ImGuiTreeNodeFlags.DefaultOpen)) return;
        var settings = com.director.ui.actor.pose.ActorAnimationAuthoringSettings.getInstance();
        animationAutoKeyframe.set(settings.isAutoKeyframe());
        if (ImGui.checkbox("自动关键帧##actorAnimationAutoKey", animationAutoKeyframe)) {
            settings.setAutoKeyframe(animationAutoKeyframe.get());
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("开启后，骨骼 Gizmo 写入 AnimationAsset；关闭时写入快速姿态片段");

        animationBoneGizmoMode.set(settings.getBoneGizmoMode() == com.director.client.gizmo.GizmoMode.TRANSLATE ? 1
                : settings.getBoneGizmoMode() == com.director.client.gizmo.GizmoMode.SCALE ? 2 : 0);
        if (ImGui.combo("骨骼 Gizmo##actorBoneGizmoMode", animationBoneGizmoMode,
                new String[]{"旋转", "位移（支持的骨骼）", "缩放（支持的骨骼）"})) {
            settings.setBoneGizmoMode(switch (animationBoneGizmoMode.get()) {
                case 1 -> com.director.client.gizmo.GizmoMode.TRANSLATE;
                case 2 -> com.director.client.gizmo.GizmoMode.SCALE;
                default -> com.director.client.gizmo.GizmoMode.ROTATE;
            });
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("位移关键帧目前用于支持该通道的骨骼；标准人形首先开放 Root");

        animationAssetId.set(settings.getAssetId());
        if (ImGui.inputText("动画资产 ID##actorAnimationAssetId", animationAssetId)) {
            settings.setAssetId(animationAssetId.get());
        }
        float[] duration = { settings.getNewAssetDuration() };
        if (ImGui.sliderFloat("新资产时长##actorAnimationDuration", duration, 0.1f, 60f, "%.2fs")) {
            settings.setNewAssetDuration(duration[0]);
        }
        var modes = com.director.actor.animation.asset.InterpolationMode.values();
        animationInterpolation.set(settings.getInterpolation().ordinal());
        String[] labels = {"阶梯", "线性", "平滑"};
        if (ImGui.combo("关键帧插值##actorAnimationInterpolation", animationInterpolation, labels)) {
            settings.setInterpolation(modes[Math.max(0, Math.min(animationInterpolation.get(), modes.length - 1))]);
        }
        ImGui.separator();
        UUID actorId = ActorSelectionHub.getSelectedActorId();
        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        com.director.ui.actor.pose.ActorAnimationAssetPanel.render(model, actorId);
        com.director.ui.actor.pose.ActorAnimationDopeSheet.render(model, actorId);
    }

    private void renderLocalPoseAuthoring(Actor actor) {
        if (!ImGui.collapsingHeader(Icons.TRANSFORM + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.LOCAL_POSE_OVERRIDE),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            return;
        }
        if (actor == null) {
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.SELECT_ACTOR_FIRST));
            return;
        }

        float[] duration = { poseDuration.get() };
        if (ImGui.sliderFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.DURATION) + "##actorLocalPoseDuration",
                duration, 0.05f, 10.0f, "%.2fs")) {
            poseDuration.set(Math.max(0.05f, duration[0]));
        }

        ImGui.checkbox(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.HEAD) + "##actorLocalPoseHead", poseHead);
        ImGui.sameLine();
        ImGui.checkbox(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.BODY) + "##actorLocalPoseBody", poseBody);
        ImGui.checkbox(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.ARMS) + "##actorLocalPoseArms", poseArms);
        ImGui.sameLine();
        ImGui.checkbox(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.LEGS) + "##actorLocalPoseLegs", poseLegs);

        if (poseHead.get()) {
            degreeSlider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.HEAD_PITCH) + "##actorLocalHeadPitch", poseHeadPitchDeg);
            degreeSlider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.HEAD_YAW) + "##actorLocalHeadYaw", poseHeadYawDeg);
        }
        if (poseBody.get()) {
            degreeSlider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.BODY_PITCH) + "##actorLocalBodyPitch", poseBodyPitchDeg);
            degreeSlider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.BODY_YAW) + "##actorLocalBodyYaw", poseBodyYawDeg);
            degreeSlider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.BODY_ROLL) + "##actorLocalBodyRoll", poseBodyRollDeg);
        }
        if (poseArms.get()) {
            degreeSlider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.LEFT_ARM) + "##actorLocalLeftArm", poseLeftArmDeg);
            degreeSlider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.RIGHT_ARM) + "##actorLocalRightArm", poseRightArmDeg);
        }
        if (poseLegs.get()) {
            degreeSlider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.LEFT_LEG) + "##actorLocalLeftLeg", poseLeftLegDeg);
            degreeSlider(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Pose.RIGHT_LEG) + "##actorLocalRightLeg", poseRightLegDeg);
        }

        if (ImGui.button(Icons.ADD + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.CREATE_LOCAL_POSE_CLIP)
                + "##createActorLocalPoseClip")) {
            createActorPoseClip(actor);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.CREATE_LOCAL_POSE_TOOLTIP));
        }
    }

    private void degreeSlider(String label, ImFloat value) {
        float[] arr = { value.get() };
        if (ImGui.sliderFloat(label, arr, -90.0f, 90.0f,
                ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.DEGREE_FMT))) {
            value.set(arr[0]);
        }
    }

    private void renderPoseAuthoring(Actor actor) {
        if (!ImGui.collapsingHeader(Icons.ANIMATION + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.POSE_CLIP),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            return;
        }

        if (actor == null) {
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.SELECT_ACTOR_FIRST));
            return;
        }

        ActorActionType[] actions = ActorActionType.values();
        int current = Math.max(0, Math.min(actionIndex.get(), actions.length - 1));
        ActorRigProfile rig = actor.getRigProfile();
        String currentLabel = ActorPropertyAdapterI18n.actionSemanticLabel(actions[current]);
        if (rig != null && !rig.supports(actions[current])) {
            currentLabel += ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.RIG_NOT_SUPPORTED_SUFFIX);
        }

        if (ImGui.beginCombo(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.ACTION_SEMANTIC) + "##actorPoseAction", currentLabel)) {
            for (int i = 0; i < actions.length; i++) {
                ActorActionType action = actions[i];
                boolean supported = rig == null || rig.supports(action);
                boolean selected = i == current;
                if (!supported) {
                    ImGui.beginDisabled();
                }
                if (ImGui.selectable(ActorPropertyAdapterI18n.actionSemanticLabel(action), selected)) {
                    actionIndex.set(i);
                }
                if (!supported) {
                    ImGui.endDisabled();
                }
                if (selected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }

        float[] intensity = { actionIntensity.get() };
        if (ImGui.sliderFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.INTENSITY) + "##actorPoseIntensity",
                intensity, 0.0f, 1.0f, "%.2f")) {
            actionIntensity.set(intensity[0]);
        }

        float[] duration = { actionDuration.get() };
        if (ImGui.sliderFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.DURATION) + "##actorPoseDuration",
                duration, 0.05f, 10.0f, "%.2fs")) {
            actionDuration.set(Math.max(0.05f, duration[0]));
        }

        if (ImGui.checkbox(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.LOOP) + "##actorPoseLoop", actionLoop)) {
            // ImBoolean is updated by ImGui.
        }

        if (ImGui.button(Icons.ADD + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.CREATE_POSE_CLIP)
                + "##createActorPoseClip")) {
            createActorActionClip(actor, actions[Math.max(0, Math.min(actionIndex.get(), actions.length - 1))]);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.CREATE_POSE_TOOLTIP));
        }
    }

    private void renderActorPathToolProperties(Actor actor) {
        if (!ImGui.collapsingHeader(Icons.PATH + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.PATH_DRAWING),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            return;
        }

        ActorPathController controller = ActorPathController.getInstance();
        if (actor == null) {
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.SELECT_ACTOR_FIRST));
            return;
        }

        ImGui.textWrapped(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.PATH_DRAW_HINT));

        ActorRouteClip.InterpolationMode[] modes = ActorRouteClip.InterpolationMode.values();
        ImInt interpolation = new ImInt(controller.getInterpolationMode().ordinal());
        if (ImGui.combo(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.CURVE_TYPE) + "##actorPathInterpolation",
                interpolation, ActorPropertyAdapterI18n.routeInterpolationLabels())) {
            controller.setInterpolationMode(modes[Math.max(0, Math.min(interpolation.get(), modes.length - 1))]);
        }

        ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.ANCHOR_COUNT_FMT, controller.getAnchorCount()));
        if (controller.isDrawing()) {
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.DRAWING_IN_PROGRESS));
        }

        if (ImGui.button(Icons.ADD + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.APPLY_TO_ACTOR)
                + "##actorPathApplyActor")) {
            controller.applyPathToSelectedActor();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.APPLY_PATH_TOOLTIP));
        }
        ImGui.sameLine();
        if (ImGui.button(Icons.DELETE + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.DELETE_SELECTED_ANCHOR)
                + "##actorPathDeleteAnchor")) {
            controller.removeSelectedAnchor();
        }
    }

    private void renderTransformHint() {
        if (ImGui.collapsingHeader(Icons.TRANSFORM + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.TRANSFORM_AND_LOOK_AT),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.textWrapped(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.TRANSFORM_HINT_1));
            ImGui.textWrapped(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.TRANSFORM_HINT_2));
            ImGui.textWrapped(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.TRANSFORM_HINT_3));
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.ToolEditor.TRANSFORM_HINT_4));
        }
    }

    private Actor resolveSelectedActor() {
        UUID actorId = ActorSelectionHub.getSelectedActorId();
        if (actorId == null) {
            return null;
        }
        return ActorRegistryManager.getInstance().getActor(actorId).orElse(null);
    }

    private void createActorActionClip(Actor actor, ActorActionType action) {
        if (actor == null || action == null) {
            return;
        }

        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        TrackContext trackContext = resolveActorTrack(model, actor.getActorId());
        if (model == null || trackContext == null) {
            return;
        }

        float start = model.getCurrentTime();
        float end = start + Math.max(0.05f, actionDuration.get());
        ActorActionClip clip = new ActorActionClip(action.name(), start, end);
        clip.setActionType(action);
        clip.setIntensity(actionIntensity.get());
        clip.setLoop(actionLoop.get());

        trackContext.actorTrack.addClip(clip);
        EntityTimelineClip timelineClip = new EntityTimelineClip(clip, trackContext.actorTrack.getColorRgba());
        trackContext.timelineTrack.addClip(timelineClip);
        model.selectItem(timelineClip, false);
        model.markDirty();
    }

    private void createActorPoseClip(Actor actor) {
        if (actor == null) {
            return;
        }
        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        TrackContext trackContext = resolveActorTrack(model, actor.getActorId());
        if (model == null || trackContext == null) {
            return;
        }

        float start = model.getCurrentTime();
        float end = start + Math.max(0.05f, poseDuration.get());
        ActorPoseClip clip = new ActorPoseClip("Pose", start, end);
        clip.setOverrideBody(poseBody.get());
        clip.setOverrideHead(poseHead.get());
        clip.setOverrideArms(poseArms.get());
        clip.setOverrideLegs(poseLegs.get());
        clip.setHeadPitch(toRadians(poseHeadPitchDeg));
        clip.setHeadYaw(toRadians(poseHeadYawDeg));
        clip.setBodyPitch(toRadians(poseBodyPitchDeg));
        clip.setBodyYaw(toRadians(poseBodyYawDeg));
        clip.setBodyRoll(toRadians(poseBodyRollDeg));
        clip.setLeftArmPitch(toRadians(poseLeftArmDeg));
        clip.setRightArmPitch(toRadians(poseRightArmDeg));
        clip.setLeftLegPitch(toRadians(poseLeftLegDeg));
        clip.setRightLegPitch(toRadians(poseRightLegDeg));

        trackContext.actorTrack.addClip(clip);
        EntityTimelineClip timelineClip = new EntityTimelineClip(clip, trackContext.actorTrack.getColorRgba());
        trackContext.timelineTrack.addClip(timelineClip);
        model.selectItem(timelineClip, false);
        model.markDirty();
    }

    private float toRadians(ImFloat degrees) {
        return (float) Math.toRadians(degrees.get());
    }

    private TrackContext resolveActorTrack(TimelineModel model, UUID actorId) {
        if (model == null || actorId == null) {
            return null;
        }
        for (TimelineTrack track : model.getTracks()) {
            if (track == null || track.getType() != TrackType.ACTOR) {
                continue;
            }
            ActorTrack actorTrack = track.getActorTrackPayload();
            if (actorTrack == null || actorTrack.getActorRef() == null) {
                continue;
            }
            if (Objects.equals(actorTrack.getActorRef().getActorId(), actorId)) {
                return new TrackContext(track, actorTrack);
            }
        }
        return null;
    }

    private record TrackContext(TimelineTrack timelineTrack, ActorTrack actorTrack) {
    }

    private static String tr(String key) {
        return DirectorI18n.translate(key);
    }

    private static String tr(String key, Object... args) {
        return DirectorI18n.translate(key, args);
    }
}
