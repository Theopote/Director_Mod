package com.director.ui.properties.adapters;

import com.director.actor.ActorRef;
import com.director.actor.Actor;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorRegistryManager;
import com.director.core.DirectorServices;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.model.entity.ActorRelationClip;
import com.director.timeline.model.entity.EntityClip;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ActorRelationClipPropertyAdapter implements IPropertyAdapter<ActorRelationClip> {
    @Override
    public Class<ActorRelationClip> getTargetType() {
        return ActorRelationClip.class;
    }

    @Override
    public String getAdapterName() {
        return Icons.TARGET + " " + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Relation.ADAPTER_NAME);
    }

    @Override
    public boolean renderProperties(ActorRelationClip clip) {
        if (clip == null) {
            ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Relation.NO_CLIP));
            return false;
        }

        boolean changed = false;
        UITheme.title(getAdapterName());

        if (ImGui.collapsingHeader(Icons.TIME + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.SECTION_BASIC),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.START_FMT, clip.getStartTime()));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.END_FMT, clip.getEndTime()));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.DURATION_FMT, clip.getLength()));
        }

        if (ImGui.collapsingHeader(Icons.SETTINGS + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Relation.SECTION_RELATION),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ActorRelationClip.RelationType[] types = ActorRelationClip.RelationType.values();
            ImInt current = new ImInt(clip.getRelationType().ordinal());
            if (ImGui.combo(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Relation.RELATION_TYPE) + "##relationType",
                    current, ActorPropertyAdapterI18n.relationTypeLabels())) {
                int idx = Math.max(0, Math.min(current.get(), types.length - 1));
                clip.setRelationType(types[idx]);
                changed = true;
            }

            ActorRelationClip.RelationType relationType = clip.getRelationType();
            boolean showDistance = relationType == ActorRelationClip.RelationType.FACE_TO_FACE
                    || relationType == ActorRelationClip.RelationType.FOLLOW
                    || relationType == ActorRelationClip.RelationType.CIRCLE_AROUND
                    || relationType == ActorRelationClip.RelationType.RELATIVE_POSITION;
            boolean showAngle = relationType == ActorRelationClip.RelationType.CIRCLE_AROUND
                    || relationType == ActorRelationClip.RelationType.RELATIVE_POSITION;
            boolean showHeight = relationType != ActorRelationClip.RelationType.PASS_OBJECT;
            boolean showLookAt = relationType == ActorRelationClip.RelationType.FACE_TO_FACE
                    || relationType == ActorRelationClip.RelationType.FOLLOW
                    || relationType == ActorRelationClip.RelationType.CIRCLE_AROUND;

            if (showDistance) {
                float[] distance = {clip.getDistance()};
                if (ImGui.sliderFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Relation.DISTANCE) + "##relationDistance",
                        distance, 0.1f, 20.0f, "%.2f")) {
                    clip.setDistance(distance[0]);
                    changed = true;
                }
            }

            if (showAngle) {
                float[] angle = {clip.getAngle()};
                if (ImGui.sliderFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Relation.ANGLE) + "##relationAngle",
                        angle, -180.0f, 180.0f, "%.1f deg")) {
                    clip.setAngle(angle[0]);
                    changed = true;
                }
            }

            if (showHeight) {
                float[] height = {clip.getHeightOffset()};
                if (ImGui.sliderFloat(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Relation.HEIGHT_OFFSET) + "##relationHeight",
                        height, -5.0f, 5.0f, "%.2f")) {
                    clip.setHeightOffset(height[0]);
                    changed = true;
                }
            }

            if (showLookAt) {
                boolean maintainLookAt = clip.isMaintainLookAt();
                if (ImGui.checkbox(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Relation.MAINTAIN_LOOK_AT) + "##relationLookAt",
                        maintainLookAt)) {
                    clip.setMaintainLookAt(!maintainLookAt);
                    changed = true;
                }
            }
        }

        if (ImGui.collapsingHeader(Icons.USER + " "
                + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Relation.SECTION_TARGET),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ActorRegistry registry = ActorRegistryManager.getInstance();
            UUID selfActorId = resolveSelfActorId(clip);
            List<Actor> actors = new ArrayList<>();
            for (Actor actor : registry.getAllActors()) {
                if (selfActorId == null || !selfActorId.equals(actor.getActorId())) {
                    actors.add(actor);
                }
            }
            String[] labels = new String[actors.size() + 1];
            labels[0] = ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Relation.TARGET_ACTOR_NONE);

            int selectedIndex = 0;
            UUID currentTarget = clip.getTargetActorId();
            for (int i = 0; i < actors.size(); i++) {
                Actor actor = actors.get(i);
                labels[i + 1] = actor.getName();
                if (currentTarget != null && currentTarget.equals(actor.getActorId())) {
                    selectedIndex = i + 1;
                }
            }

            ImInt targetSelection = new ImInt(selectedIndex);
            if (ImGui.combo(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Relation.TARGET_ACTOR) + "##relationTargetActor",
                    targetSelection, labels)) {
                int index = targetSelection.get();
                if (index <= 0) {
                    clip.setTargetActorId(null);
                } else {
                    clip.setTargetActorId(actors.get(index - 1).getActorId());
                }
                changed = true;
            }

            if (clip.getTargetActorId() != null) {
                ImGui.textDisabled(ActorPropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterActor.Relation.TARGET_ACTOR_UUID_FMT,
                        clip.getTargetActorId().toString()));
            }
        }

        return changed;
    }

    private UUID resolveSelfActorId(ActorRelationClip clip) {
        TimelineTrack selectedTrack = DirectorServices.get().uiSelection().getSelectedTrack();
        UUID actorIdFromSelectedTrack = resolveActorIdFromTrack(selectedTrack);
        if (actorIdFromSelectedTrack != null) {
            return actorIdFromSelectedTrack;
        }

        Object payload = DirectorServices.get().uiSelection().getSelectionPayload();
        if (payload instanceof TimelineTrack payloadTrack) {
            UUID actorIdFromPayloadTrack = resolveActorIdFromTrack(payloadTrack);
            if (actorIdFromPayloadTrack != null) {
                return actorIdFromPayloadTrack;
            }
        }

        if (!(payload instanceof EntityClip) && clip == null) {
            return null;
        }

        EntityClip targetClip = payload instanceof EntityClip entityClip ? entityClip : clip;
        if (targetClip == null) {
            return null;
        }

        var model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model == null) {
            return null;
        }

        for (TimelineTrack track : model.getTracks()) {
            UUID actorId = resolveActorIdFromTrack(track);
            if (actorId == null) {
                continue;
            }
            ActorTrack actorTrack = track.getActorTrackPayload();
            if (actorTrack == null) {
                continue;
            }
            for (EntityClip entityClip : actorTrack.getClips()) {
                if (entityClip == targetClip || entityClip.getId().equals(targetClip.getId())) {
                    return actorId;
                }
            }
        }

        return null;
    }

    private UUID resolveActorIdFromTrack(TimelineTrack selectedTrack) {
        if (selectedTrack == null || selectedTrack.getType() != TrackType.ACTOR) {
            return null;
        }
        ActorTrack actorTrack = selectedTrack.getActorTrackPayload();
        if (actorTrack == null) {
            return null;
        }
        ActorRef actorRef = actorTrack.getActorRef();
        return actorRef != null ? actorRef.getActorId() : null;
    }
}
