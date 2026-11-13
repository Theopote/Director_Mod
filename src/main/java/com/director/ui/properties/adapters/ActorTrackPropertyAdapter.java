package com.director.ui.properties.adapters;

import com.director.core.DirectorServices;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.i18n.TimelinePropertyAdapterI18n;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.ui.actor.track.ActorTrackClipAuthoringService;
import com.director.ui.actor.track.ActorTrackClipKind;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.PropertyRow;
import com.director.ui.properties.actor.ActorPathMovementPropertyUI;
import com.director.ui.theme.UITheme;
import com.director.ui.timeline.commands.RenameTrackCommand;
import com.director.ui.timeline.commands.TimelineCommandBridge;
import com.director.ui.timeline.commands.ToggleTrackLockCommand;
import com.director.ui.timeline.commands.ToggleTrackMuteCommand;
import com.director.ui.timeline.interaction.ActionBrushState;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImString;

public final class ActorTrackPropertyAdapter implements IPropertyAdapter<TimelineTrack> {
    private String createError = "";
    private TimelineTrack createErrorTrack;
    @Override
    public Class<TimelineTrack> getTargetType() {
        return TimelineTrack.class;
    }

    @Override
    public boolean supports(Object target) {
        return target instanceof TimelineTrack track && track.getType() == TrackType.ACTOR;
    }

    @Override
    public boolean renderProperties(TimelineTrack track) {
        if (createErrorTrack != track) {
            createError = "";
            createErrorTrack = track;
        }
        var model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model == null) return false;
        UITheme.title(ActorPropertyAdapterI18n.tr("gui.director.actor.track.title"));
        var name = new ImString(track.getName(), 256);
        ImGui.beginDisabled(track.isLocked());
        PropertyRow.field(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.TRACK_NAME));
        if (ImGui.inputText("##actor_track_track_name",
                name, ImGuiInputTextFlags.EnterReturnsTrue) && !name.get().isBlank() && !name.get().equals(track.getName())) {
            TimelineCommandBridge.execute(new RenameTrackCommand(model, track, name.get().strip()));
        }
        ImGui.endDisabled();
        PropertyRow.label(ActorPropertyAdapterI18n.tr("gui.director.actor.track.mute"));
        if (ImGui.checkbox("##actor_track_mute", track.isMuted())) {
            TimelineCommandBridge.execute(new ToggleTrackMuteCommand(model, track));
        }
        PropertyRow.label(ActorPropertyAdapterI18n.tr("gui.director.actor.track.lock"));
        if (ImGui.checkbox("##actor_track_lock", track.isLocked())) {
            TimelineCommandBridge.execute(new ToggleTrackLockCommand(model, track));
        }
        var payload = track.getActorTrackPayload();
        var actor = payload == null || payload.getActorRef() == null ? null
                : com.director.actor.ActorRegistryManager.getInstance().getActor(payload.getActorRef().getActorId()).orElse(null);
        if (actor == null) {
            ImGui.textWrapped(ActorPropertyAdapterI18n.tr(payload == null || payload.getActorRef() == null
                    ? "gui.director.actor.track.unbound" : "gui.director.actor.track.missing_actor"));
            return false;
        }
        ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.track.bound_actor", actor.getName()));
        if (ImGui.button(ActorPropertyAdapterI18n.tr("gui.director.actor.track.inspect"))) {
            com.director.ui.actor.selection.ActorSelectionHub.selectActor(actor.getActorId());
            com.director.ui.properties.PropertyContextService.get().inspectActorObject(actor);
        }
        ImGui.separator();
        ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.track.create_at_playhead"));
        ImGui.textDisabled(ActorPropertyAdapterI18n.tr("gui.director.actor.track.current_brush",
                ActorPropertyAdapterI18n.actionSemanticLabel(ActionBrushState.get())));
        ImGui.beginDisabled(track.isLocked() || track.isMuted() || model.isPlaying() || model.isScrubbing());
        for (var kind : ActorTrackClipKind.values()) renderActorClipButton(track, kind);
        ImGui.endDisabled();
        if (!createError.isEmpty()) ImGui.textWrapped(createError);
        ActorPathMovementPropertyUI.render(actor.getActorId());
        return false;
    }

    private boolean renderActorClipButton(TimelineTrack track, ActorTrackClipKind kind) {
        String label;
        String tooltip;
        switch (kind) {
            case TRANSFORM:
                label = TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ACTOR_CLIP_TRANSFORM);
                tooltip = TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ACTOR_CLIP_TRANSFORM_TOOLTIP);
                break;
            case LOOK_AT_CAMERA:
                label = TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ACTOR_CLIP_LOOK_AT);
                tooltip = TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ACTOR_CLIP_LOOK_AT_TOOLTIP);
                break;
            case ACTION:
                label = TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ACTOR_CLIP_ACTION);
                tooltip = TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ACTOR_CLIP_ACTION_TOOLTIP);
                break;
            case POSE:
                label = TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ACTOR_CLIP_POSE);
                tooltip = TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ACTOR_CLIP_POSE_TOOLTIP);
                break;
            case STATE:
                label = TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ACTOR_CLIP_STATE);
                tooltip = TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ACTOR_CLIP_STATE_TOOLTIP);
                break;
            default:
                label = kind.name();
                tooltip = kind.name();
                break;
        }

        if (ImGui.button(label + "##actorTrackCreate" + kind, 0, UITheme.Spacing.BUTTON_HEIGHT)) {
            var service = ActorTrackClipAuthoringService.getInstance();
            var clip = service.createClip(track, kind, ActionBrushState.get());
            createError = clip == null ? refusalText(service.lastRefusal()) : "";
            return clip != null;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip);
        }
        return false;
    }

    private static String refusalText(String key) {
        if (key == null || key.isBlank()) return ActorPropertyAdapterI18n.tr("gui.director.actor.track.create_rejected");
        String text = ActorPropertyAdapterI18n.tr(key);
        return text == null || text.isBlank() || text.equals(key)
                ? ActorPropertyAdapterI18n.tr("gui.director.actor.track.create_rejected") : text;
    }
}
