package com.director.ui.properties.actor;

import com.director.core.DirectorServices;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.timeline.model.entity.EntityLookAtClip;
import com.director.timeline.model.entity.EntitySpawnClip;
import com.director.ui.actor.lookat.ActorLookIntervalEditing;
import imgui.ImGui;
import imgui.type.ImFloat;
import java.util.IdentityHashMap;
import java.util.Map;

public final class ActorLookIntervalsPropertyUI {
    private final Map<EntityLookAtClip, RangeDraft> drafts = new IdentityHashMap<>();

    private static final class RangeDraft {
        final float originalStart;
        final float originalEnd;
        final ImFloat start;
        final ImFloat end;
        boolean invalid;

        RangeDraft(EntityLookAtClip target) {
            originalStart = target.getStartTime();
            originalEnd = target.getEndTime();
            start = new ImFloat(originalStart);
            end = new ImFloat(originalEnd);
        }

        boolean isCurrent(EntityLookAtClip target) {
            return originalStart == target.getStartTime() && originalEnd == target.getEndTime();
        }
    }

    public void render(EntitySpawnClip spawn) {
        var targets = spawn.getLookTargets();
        drafts.keySet().retainAll(targets);
        if (targets.isEmpty() || !ImGui.collapsingHeader(tr("title") + "##spawnLookIntervals")) return;

        var model = DirectorServices.get().containerTimeline().getTimelineModel();
        for (var target : targets) {
            var draft = drafts.compute(target, (clip, previous) ->
                    previous != null && previous.isCurrent(clip) ? previous : new RangeDraft(clip));
            ImGui.pushID(target.getId().toString());
            String mode = switch (target.getMode()) {
                case CAMERA -> "mode_camera";
                case WORLD_POINT -> "mode_world_point";
                case ENTITY_UUID -> "mode_entity";
            };
            ImGui.text(ActorPropertyAdapterI18n.tr("gui.director.properties.adapter.actor.look_at." + mode));
            ImGui.beginDisabled(!ActorLookIntervalEditing.editable(model, spawn, target));
            if (ImGui.inputFloat(tr("start"), draft.start)) draft.invalid = false;
            if (ImGui.inputFloat(tr("end"), draft.end)) draft.invalid = false;
            if (ImGui.button(tr("apply"))) {
                draft.invalid = !ActorLookIntervalEditing.resize(model, spawn, target, draft.start.get(), draft.end.get());
            }
            ImGui.sameLine();
            if (ImGui.button(ActorPropertyAdapterI18n.tr("gui.director.common.delete"))
                    && ActorLookIntervalEditing.remove(model, spawn, target)) drafts.remove(target);
            ImGui.endDisabled();
            if (draft.invalid) ImGui.textWrapped(tr("invalid"));
            ImGui.popID();
        }
    }

    private static String tr(String key) {
        return ActorPropertyAdapterI18n.tr("gui.director.actor.look_interval." + key);
    }
}
