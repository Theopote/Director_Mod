package com.director.ui.properties.actor;

import com.director.core.DirectorServices;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.timeline.model.entity.EntityClip;
import com.director.timeline.model.entity.EntitySpawnClip;
import com.director.ui.actor.edit.ActorOwnedIntervalEditing;
import imgui.ImGui;
import imgui.type.ImFloat;
import java.util.IdentityHashMap;
import java.util.Map;

public final class ActorOwnedIntervalsPropertyUI {
    private final Map<EntityClip, RangeDraft> drafts = new IdentityHashMap<>();

    private static final class RangeDraft {
        final float originalStart;
        final float originalEnd;
        final ImFloat start;
        final ImFloat end;
        boolean invalid;

        RangeDraft(EntityClip target) {
            originalStart = target.getStartTime();
            originalEnd = target.getEndTime();
            start = new ImFloat(originalStart);
            end = new ImFloat(originalEnd);
        }

        boolean isCurrent(EntityClip target) {
            return originalStart == target.getStartTime() && originalEnd == target.getEndTime();
        }
    }

    public void retain(EntitySpawnClip spawn) {
        drafts.keySet().removeIf(target -> !ActorOwnedIntervalEditing.contains(spawn, target));
    }

    public void renderLookIntervals(EntitySpawnClip spawn) {
        var targets = spawn.getLookTargets();
        if (targets.isEmpty() || !ImGui.collapsingHeader(tr("title") + "##spawnLookIntervals")) return;
        for (var target : targets) {
            ImGui.pushID(target.getId().toString());
            String mode = switch (target.getMode()) {
                case CAMERA -> "mode_camera";
                case WORLD_POINT -> "mode_world_point";
                case ENTITY_UUID -> "mode_entity";
            };
            ImGui.text(ActorPropertyAdapterI18n.tr("gui.director.properties.adapter.actor.look_at." + mode));
            if (ImGui.button(tr("edit_target")))
                com.director.ui.properties.PropertyContextService.get().inspectActorInterval(spawn, target);
            renderRange(spawn, target);
            ImGui.popID();
        }
    }

    public void renderRange(EntitySpawnClip spawn, EntityClip target) {
        var model = DirectorServices.get().containerTimeline().getTimelineModel();
        var draft = drafts.compute(target, (clip, previous) ->
                previous != null && previous.isCurrent(clip) ? previous : new RangeDraft(clip));
        float visibleStart = Math.max(spawn.getStartTime(), target.getStartTime());
        float visibleEnd = Math.min(spawn.getEndTime(), target.getEndTime());
        if (target.getStartTime() < spawn.getStartTime() || target.getEndTime() > spawn.getEndTime()) {
            if (visibleEnd > visibleStart)
                ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.look_interval.partially_trimmed",
                        visibleStart, visibleEnd));
            else ImGui.textWrapped(tr("fully_trimmed"));
        }
        ImGui.beginDisabled(!ActorOwnedIntervalEditing.editable(model, spawn, target));
        if (ImGui.inputFloat(tr("start"), draft.start)) draft.invalid = false;
        if (ImGui.inputFloat(tr("end"), draft.end)) draft.invalid = false;
        if (ImGui.button(tr("apply"))) {
            draft.invalid = !ActorOwnedIntervalEditing.resize(model, spawn, target, draft.start.get(), draft.end.get());
        }
        ImGui.sameLine();
        if (ImGui.button(ActorPropertyAdapterI18n.tr("gui.director.common.delete"))
                && ActorOwnedIntervalEditing.remove(model, spawn, target)) drafts.remove(target);
        ImGui.endDisabled();
        if (draft.invalid) ImGui.textWrapped(target instanceof com.director.timeline.model.entity.ActorActionClip
                ? ActorPropertyAdapterI18n.tr("gui.director.actor.behavior.owned_range_invalid") : tr("invalid"));
    }

    private static String tr(String key) {
        return ActorPropertyAdapterI18n.tr("gui.director.actor.look_interval." + key);
    }
}
