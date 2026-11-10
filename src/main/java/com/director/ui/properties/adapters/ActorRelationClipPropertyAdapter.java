package com.director.ui.properties.adapters;

import com.director.actor.Actor;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorRegistryManager;
import com.director.i18n.ActorPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.timeline.model.entity.ActorRelationClip;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.PropertyRow;
import com.director.ui.properties.Section;
import com.director.ui.properties.actor.ActorClipInspectorGesture;
import com.director.actor.ActorBehaviorCapabilities;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import imgui.ImGui;
import imgui.type.ImInt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

public final class ActorRelationClipPropertyAdapter implements IPropertyAdapter<ActorRelationClip> {
    private final ActorClipInspectorGesture gesture = new ActorClipInspectorGesture();
    private static final int MAX_RECENT_TARGETS = 5;
    private static final Deque<UUID> RECENT_TARGETS = new ArrayDeque<>();

    @Override
    public Class<ActorRelationClip> getTargetType() {
        return ActorRelationClip.class;
    }

    @Override
    public String getAdapterName() {
        return Icons.TARGET + " " + ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Relation.ADAPTER_NAME);
    }

    @Override
    public boolean renderProperties(ActorRelationClip source) {
        var draft = gesture.begin(source);
        if (!(draft instanceof ActorRelationClip clip)) { gesture.end(); return false; }

        boolean changed = false;
        UITheme.title(getAdapterName());

        if (Section.header("actor_relation_clip.section_basic", ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.SECTION_BASIC))) {
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.START_FMT, clip.getStartTime()));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.END_FMT, clip.getEndTime()));
            ImGui.text(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Common.DURATION_FMT, clip.getLength()));
        }

        if (Section.header("actor_relation_clip.section_relation", ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Relation.SECTION_RELATION))) {
            PropertyRow.field(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Relation.RELATION_TYPE));
            if (ImGui.beginCombo("##relationType", ActorPropertyAdapterI18n.relationTypeLabels()[clip.getRelationType().ordinal()])) {
                for (var type : ActorRelationClip.RelationType.values()) {
                    boolean supported = ActorBehaviorCapabilities.supportsRelation(type);
                    ImGui.beginDisabled(!supported);
                    if (ImGui.selectable(ActorPropertyAdapterI18n.relationTypeLabels()[type.ordinal()], type == clip.getRelationType())) {
                        clip.setRelationType(type); gesture.requestCommit();
                    }
                    ImGui.endDisabled();
                }
                ImGui.endCombo();
            }
            ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.behavior.relation_runtime_hint"));
            ImGui.textWrapped(ActorPropertyAdapterI18n.tr("gui.director.actor.behavior.relation_ground_hint"));

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

            ImGui.beginDisabled(!ActorBehaviorCapabilities.supportsRelation(relationType));
            if (showDistance) {
                float[] distance = {clip.getDistance()};
                PropertyRow.field(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Relation.DISTANCE));
                if (ImGui.sliderFloat("##relationDistance",
                        distance, 0.1f, 20.0f, "%.2f")) {
                    clip.setDistance(distance[0]);
                    changed = true;
                }
                gesture.observe("distance", changed);
                changed = false;
            }

            if (showAngle) {
                float[] angle = {clip.getAngle()};
                PropertyRow.field(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Relation.ANGLE));
                if (ImGui.sliderFloat("##relationAngle",
                        angle, -180.0f, 180.0f,
                        "%.1f deg")) {
                    clip.setAngle(angle[0]);
                    changed = true;
                }
                gesture.observe("angle", changed);
                changed = false;
            }

            if (showHeight) {
                float[] height = {clip.getHeightOffset()};
                PropertyRow.field(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Relation.HEIGHT_OFFSET));
                if (ImGui.sliderFloat("##relationHeight",
                        height, -5.0f, 5.0f, "%.2f")) {
                    clip.setHeightOffset(height[0]);
                    changed = true;
                }
                gesture.observe("height", changed);
                changed = false;
            }

            if (showLookAt) {
                boolean maintainLookAt = clip.isMaintainLookAt();
                PropertyRow.label(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Relation.MAINTAIN_LOOK_AT));
                if (ImGui.checkbox("##relationLookAt",
                        maintainLookAt)) {
                    clip.setMaintainLookAt(!maintainLookAt);
                    changed = true;
                }
                gesture.observe("look", changed);
                changed = false;
            }
            ImGui.endDisabled();
        }

        if (Section.header("actor_relation_clip.section_target", ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Relation.SECTION_TARGET))) {
            ActorRegistry registry = ActorRegistryManager.getInstance();
            UUID selfActorId = source.getTimelineClip().getParentTrack().getActorTrackPayload().getActorRef().getActorId();
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
                labels[i + 1] = buildActorLabel(actor);
                if (currentTarget != null && currentTarget.equals(actor.getActorId())) {
                    selectedIndex = i + 1;
                }
            }

            if (renderRecentTargets(clip, actors)) gesture.requestCommit();

            ImInt targetSelection = new ImInt(selectedIndex);
            PropertyRow.field(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Relation.TARGET_ACTOR));
            if (ImGui.combo("##relationTargetActor",
                    targetSelection, labels)) {
                int index = targetSelection.get();
                if (index <= 0) {
                    clip.setTargetActorId(null);
                } else {
                    UUID targetActorId = actors.get(index - 1).getActorId();
                    clip.setTargetActorId(targetActorId);
                    pushRecentTarget(targetActorId);
                }
                gesture.requestCommit();
            }

            if (clip.getTargetActorId() != null) {
                ImGui.textDisabled(ActorPropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterActor.Relation.TARGET_ACTOR_UUID_FMT,
                        clip.getTargetActorId().toString()));
            }
        }

        gesture.end();
        return false;
    }

    private static String buildActorLabel(Actor actor) {
        if (actor == null) {
            return "";
        }
        String type = ActorPropertyAdapterI18n.actorTypeLabel(actor.getType());
        return actor.getName() + " (" + type + ")";
    }

    private boolean renderRecentTargets(ActorRelationClip clip, List<Actor> availableActors) {
        if (availableActors == null || availableActors.isEmpty() || RECENT_TARGETS.isEmpty()) {
            return false;
        }

        boolean changed = false;
        ImGui.textDisabled(ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Relation.RECENT_TARGETS));
        String recentPrefix = ActorPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterActor.Relation.TARGET_RECENT_PREFIX);
        int index = 0;
        for (UUID actorId : RECENT_TARGETS) {
            Actor actor = findActorById(availableActors, actorId);
            if (actor == null) {
                continue;
            }
            if (ImGui.button(recentPrefix + ": " + buildActorLabel(actor) + "##recentRelationTarget" + actorId)) {
                clip.setTargetActorId(actorId);
                pushRecentTarget(actorId);
                changed = true;
            }
            index++;
            if (index >= 3) {
                break;
            }
        }
        return changed;
    }

    private static Actor findActorById(List<Actor> actors, UUID actorId) {
        for (Actor actor : actors) {
            if (actor.getActorId().equals(actorId)) {
                return actor;
            }
        }
        return null;
    }

    private static void pushRecentTarget(UUID actorId) {
        if (actorId == null) {
            return;
        }
        RECENT_TARGETS.remove(actorId);
        RECENT_TARGETS.addFirst(actorId);
        while (RECENT_TARGETS.size() > MAX_RECENT_TARGETS) {
            RECENT_TARGETS.removeLast();
        }
    }
}
