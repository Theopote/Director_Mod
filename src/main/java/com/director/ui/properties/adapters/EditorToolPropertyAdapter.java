package com.director.ui.properties.adapters;

import com.director.actor.Actor;
import com.director.actor.ActorRegistryManager;
import com.director.actor.action.ActorActionType;
import com.director.actor.rig.ActorRigProfile;
import com.director.ui.actor.selection.ActorSelectionHub;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.PropertyContext;
import com.director.ui.properties.actor.RigProfileStylePropertyUI;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.ui.timeline.container.ContainerTimelineService;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.TrackType;
import com.director.ui.timeline.model.entity.ActorActionClip;
import com.director.ui.timeline.model.entity.EntityTimelineClip;
import com.director.ui.timeline.model.entity.EntityTrack;
import com.director.ui.tools.EditorTool;
import com.director.ui.tools.ToolRegistry;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImInt;

import java.util.Objects;
import java.util.UUID;

public final class EditorToolPropertyAdapter implements IPropertyAdapter<EditorTool> {
    private final ImInt actionIndex = new ImInt(0);
    private final ImFloat actionIntensity = new ImFloat(1.0f);
    private final ImFloat actionDuration = new ImFloat(1.0f);
    private final ImBoolean actionLoop = new ImBoolean(true);

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
        return Icons.TOOLBOX + " 工具属性";
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean renderProperties(EditorTool tool) {
        if (tool == null) {
            ImGui.textDisabled("未选择工具");
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
        if (ImGui.collapsingHeader(Icons.INFO + " 工具状态", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.text("ID: " + tool.getId());
            ImGui.text("Group: " + tool.getGroup());
            ImGui.text(tool.isEnabled() ? "状态: 可用" : "状态: 不可用");
        }
    }

    private void renderActorToolProperties(EditorTool tool) {
        Actor actor = resolveSelectedActor();
        if (ImGui.collapsingHeader(Icons.SMILE + " 当前演员", ImGuiTreeNodeFlags.DefaultOpen)) {
            if (actor == null) {
                ImGui.textDisabled("未选择 Actor");
                ImGui.textWrapped("在当前 Actor 工具下点击世界中的演员，或在 Actor 轨道中选择一个演员。");
            } else {
                ImGui.text("Name: " + actor.getName());
                ImGui.text("Type: " + actor.getType());
                ActorRigProfile rig = actor.getRigProfile();
                ImGui.text("Rig: " + (rig != null ? rig.getClass().getSimpleName() : "None"));
            }
        }

        if (actor != null && actor.getRigProfile() != null) {
            RigProfileStylePropertyUI.render(actor.getRigProfile().getStyle());
        }

        if (Objects.equals(tool.getId(), ToolRegistry.ACTOR_POSE_TOOL_ID)) {
            renderPoseAuthoring(actor);
        } else if (Objects.equals(tool.getId(), ToolRegistry.ACTOR_LOOK_AT_TOOL_ID)) {
            renderLookAtHint();
        } else {
            renderTransformHint(tool);
        }
    }

    private void renderPoseAuthoring(Actor actor) {
        if (!ImGui.collapsingHeader(Icons.ANIMATION + " 姿态片段", ImGuiTreeNodeFlags.DefaultOpen)) {
            return;
        }

        if (actor == null) {
            ImGui.textDisabled("请先选择一个 Actor。");
            return;
        }

        ActorActionType[] actions = ActorActionType.values();
        int current = Math.max(0, Math.min(actionIndex.get(), actions.length - 1));
        ActorRigProfile rig = actor.getRigProfile();
        String currentLabel = actions[current].name();
        if (rig != null && !rig.supports(actions[current])) {
            currentLabel += " (当前 Rig 不支持)";
        }

        if (ImGui.beginCombo("动作语义##actorPoseAction", currentLabel)) {
            for (int i = 0; i < actions.length; i++) {
                ActorActionType action = actions[i];
                boolean supported = rig == null || rig.supports(action);
                boolean selected = i == current;
                if (!supported) {
                    ImGui.beginDisabled();
                }
                if (ImGui.selectable(action.name(), selected)) {
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
        if (ImGui.sliderFloat("强度##actorPoseIntensity", intensity, 0.0f, 1.0f, "%.2f")) {
            actionIntensity.set(intensity[0]);
        }

        float[] duration = { actionDuration.get() };
        if (ImGui.sliderFloat("时长##actorPoseDuration", duration, 0.05f, 10.0f, "%.2fs")) {
            actionDuration.set(Math.max(0.05f, duration[0]));
        }

        if (ImGui.checkbox("循环##actorPoseLoop", actionLoop)) {
            // ImBoolean is updated by ImGui.
        }

        if (ImGui.button(Icons.ADD + " 在当前时间创建姿态片段##createActorPoseClip")) {
            createActorActionClip(actor, actions[Math.max(0, Math.min(actionIndex.get(), actions.length - 1))]);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("创建 ActorActionClip，由 RigProfile 在播放时生成最终姿态。");
        }
    }

    private void renderLookAtHint() {
        if (ImGui.collapsingHeader(Icons.TARGET + " 视线工具", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.textWrapped("点击世界中的目标点，创建或更新当前时间覆盖的 LookAtClip。");
            ImGui.textWrapped("需要看向摄像机时，可在 Entity Track 属性中使用 Look At Camera。");
        }
    }

    private void renderTransformHint(EditorTool tool) {
        if (ImGui.collapsingHeader(Icons.TRANSFORM + " 变换工具", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.textWrapped("选择 Actor 后拖动 Gizmo，系统会在当前时间创建或更新 TransformClip。");
            ImGui.text("当前工具: " + tool.getDisplayName());
            ImGui.textDisabled("G/R/S 可在 Actor 工具组内切换移动、旋转、缩放。");
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

        TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
        TrackContext trackContext = resolveEntityTrack(model, actor.getActorId());
        if (model == null || trackContext == null) {
            return;
        }

        float start = model.getCurrentTime();
        float end = start + Math.max(0.05f, actionDuration.get());
        ActorActionClip clip = new ActorActionClip(action.name(), start, end);
        clip.setActionType(action);
        clip.setIntensity(actionIntensity.get());
        clip.setLoop(actionLoop.get());

        trackContext.entityTrack.addClip(clip);
        EntityTimelineClip timelineClip = new EntityTimelineClip(clip, trackContext.entityTrack.getColorRgba());
        trackContext.timelineTrack.addClip(timelineClip);
        model.selectItem(timelineClip, false);
        model.markDirty();
    }

    private TrackContext resolveEntityTrack(TimelineModel model, UUID actorId) {
        if (model == null || actorId == null) {
            return null;
        }
        for (TimelineTrack track : model.getTracks()) {
            if (track == null || track.getType() != TrackType.ENTITY) {
                continue;
            }
            EntityTrack entityTrack = track.getEntityTrackPayload();
            if (entityTrack == null || entityTrack.getActorRef() == null) {
                continue;
            }
            if (Objects.equals(entityTrack.getActorRef().getActorId(), actorId)) {
                return new TrackContext(track, entityTrack);
            }
        }
        return null;
    }

    private record TrackContext(TimelineTrack timelineTrack, EntityTrack entityTrack) {
    }
}
