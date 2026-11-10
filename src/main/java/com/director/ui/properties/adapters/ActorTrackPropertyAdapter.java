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
        var model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model == null) return false;
        UITheme.title(ActorPropertyAdapterI18n.tr("gui.director.actor.track.title"));
        var name = new ImString(track.getName(), 256);
        ImGui.beginDisabled(track.isLocked());
        if (ImGui.inputText(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.TRACK_NAME),
                name, ImGuiInputTextFlags.EnterReturnsTrue) && !name.get().isBlank() && !name.get().equals(track.getName())) {
            TimelineCommandBridge.execute(new RenameTrackCommand(model, track, name.get().strip()));
        }
        ImGui.endDisabled();
        if (ImGui.checkbox(ActorPropertyAdapterI18n.tr("gui.director.actor.track.mute"), track.isMuted())) {
            TimelineCommandBridge.execute(new ToggleTrackMuteCommand(model, track));
        }
        if (ImGui.checkbox(ActorPropertyAdapterI18n.tr("gui.director.actor.track.lock"), track.isLocked())) {
            TimelineCommandBridge.execute(new ToggleTrackLockCommand(model, track));
        }
        var payload = track.getActorTrackPayload();
        var actor = payload == null || payload.getActorRef() == null ? null
                : com.director.actor.ActorRegistryManager.getInstance().getActor(payload.getActorRef().getActorId()).orElse(null);
        if (actor == null) return false;
        if (ImGui.button(ActorPropertyAdapterI18n.tr("gui.director.actor.track.inspect"))) {
            com.director.ui.actor.selection.ActorSelectionHub.selectWholeActor(actor.getActorId());
        }
        ImGui.separator();
        ImGui.textWrapped(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ACTOR_CLIP_HINT));
        ImGui.beginDisabled(track.isLocked() || track.isMuted() || model.isPlaying() || model.isScrubbing());
        for (var kind : ActorTrackClipKind.values()) renderActorClipButton(track, kind);
        ImGui.endDisabled();
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

        if (ImGui.button(label + "##actorTrackCreate" + kind, 92, UITheme.Spacing.BUTTON_HEIGHT)) {
            var action = ActionBrushState.get();
            var clip = ActorTrackClipAuthoringService.getInstance().createClip(track, kind, action);
            return clip != null;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip);
        }
        return false;
    }
}
