package com.director.actor.query;
import com.director.actor.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.model.entity.EntityAnimationClip;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ActorProjectDiagnosticsTest {
    @Test void embeddedAnimationIssuesNavigateToVisibleSpawnOwner() {
        var model = new TimelineModel(new TimelineViewportState()); var registry = new ActorRegistry();
        var actor = registry.createActor("A", ActorType.PLAYER);
        var payload = new ActorTrack(); payload.setActorRef(new ActorRef(actor.getActorId()));
        var spawn = new com.director.timeline.model.entity.EntitySpawnClip("Spawn", 0, 10);
        spawn.setBoneAnimationId("audit:missing_manual");
        spawn.setPreset(new com.director.timeline.model.entity.ActorPresetBinding("audit:missing_preset", false, 1, com.director.actor.rig.skeleton.PoseBlendSettings.DEFAULT));
        payload.addClip(spawn);
        var track = new TimelineTrack("A", TrackType.ACTOR); track.setActorTrackPayload(payload); model.addTrack(track);
        var wrapper = new com.director.timeline.model.entity.EntityTimelineClip(spawn, payload.getColorRgba()); track.addClip(wrapper);
        var issues = ActorProjectDiagnostics.scan(model, registry);
        assertEquals(2, issues.size());
        for (var issue : issues) { assertSame(spawn, issue.clip()); assertSame(wrapper, issue.currentWrapper(model)); }
        assertEquals(2, ActorAnimationReferences.ids(payload).size());
    }
    @Test void diagnosticNavigationRejectsDetachedClip() {
        var model = new TimelineModel(new TimelineViewportState());
        var track = new TimelineTrack("Actor", TrackType.ACTOR); var payload = new ActorTrack(); track.setActorTrackPayload(payload); model.addTrack(track);
        var clip = new com.director.timeline.model.entity.ActorRelationClip("Follow", 0, 10); payload.addClip(clip);
        var wrapper = new com.director.timeline.model.entity.EntityTimelineClip(clip, payload.getColorRgba()); track.addClip(wrapper);
        var issue = new ActorProjectDiagnostics.Issue(track, ActorProjectDiagnostics.Reason.MISSING_RELATION_TARGET, "Follow", clip);
        assertSame(wrapper, issue.currentWrapper(model));
        payload.removeClip(clip.getId()); assertNull(issue.currentWrapper(model));
        payload.addClip(clip); track.removeClip(wrapper); assertNull(issue.currentWrapper(model));
    }
    @Test void reportsMissingPathAndUnsupportedStoredBehaviors() {
        var model = new TimelineModel(new TimelineViewportState()); var registry = new ActorRegistry();
        var actor = registry.createActor("Cow", ActorType.COW);
        var payload = new ActorTrack(); payload.setActorRef(new ActorRef(actor.getActorId()));
        var route = new com.director.timeline.model.entity.ActorRouteClip("Route", 0, 10);
        route.setSourcePathId(java.util.UUID.randomUUID()); payload.addClip(route);
        var action = new com.director.timeline.model.entity.ActorActionClip("Dance", 0, 10);
        action.setActionType(com.director.actor.action.ActorActionType.DANCE); payload.addClip(action);
        var relation = new com.director.timeline.model.entity.ActorRelationClip("Mirror", 0, 10);
        relation.setRelationType(com.director.timeline.model.entity.ActorRelationClip.RelationType.MIRROR); payload.addClip(relation);
        var track = new TimelineTrack("Cow", TrackType.ACTOR); track.setActorTrackPayload(payload); model.addTrack(track);
        var reasons = ActorProjectDiagnostics.scan(model, registry).stream().map(ActorProjectDiagnostics.Issue::reason).toList();
        assertTrue(reasons.contains(ActorProjectDiagnostics.Reason.MISSING_PATH));
        assertTrue(reasons.contains(ActorProjectDiagnostics.Reason.UNSUPPORTED_ACTION));
        assertTrue(reasons.contains(ActorProjectDiagnostics.Reason.UNSUPPORTED_RELATION));
        assertNotNull(route.getSourcePathId());
    }
    @Test void checksCustomDependenciesAndMissingRelationTargets() {
        var model = new TimelineModel(new TimelineViewportState()); var registry = new ActorRegistry();
        var actor = registry.createActor("Custom", ActorType.CUSTOM);
        actor.setCustomRigId("audit:missing_rig"); actor.setCustomModelId("audit:missing_model");
        var payload = new ActorTrack(); payload.setActorRef(new ActorRef(actor.getActorId()));
        var relation = new com.director.timeline.model.entity.ActorRelationClip("Follow", 50, 60);
        relation.setTargetActorId(java.util.UUID.randomUUID()); payload.addClip(relation);
        var track = new TimelineTrack("Custom", TrackType.ACTOR); track.setActorTrackPayload(payload); model.addTrack(track);
        var reasons = ActorProjectDiagnostics.scan(model, registry).stream().map(ActorProjectDiagnostics.Issue::reason).toList();
        assertTrue(reasons.contains(ActorProjectDiagnostics.Reason.MISSING_CUSTOM_RIG));
        assertTrue(reasons.contains(ActorProjectDiagnostics.Reason.MISSING_CUSTOM_MODEL));
        assertTrue(reasons.contains(ActorProjectDiagnostics.Reason.MISSING_RELATION_TARGET));
        relation.setTargetActorId(actor.getActorId());
        assertTrue(ActorProjectDiagnostics.scan(model, registry).stream().anyMatch(i -> i.reason() == ActorProjectDiagnostics.Reason.SELF_RELATION));
    }
    @Test void scansMutedFutureClipsWithoutModifyingThem() {
        var model = new TimelineModel(new TimelineViewportState()); var registry = new ActorRegistry();
        var actor = registry.createActor("A", ActorType.PLAYER);
        var payload = new ActorTrack(); payload.setActorRef(new ActorRef(actor.getActorId()));
        var clip = new EntityAnimationClip("Future", 100, 110); clip.setAnimationId("audit:missing_animation"); clip.setMuted(true); payload.addClip(clip);
        var track = new TimelineTrack("Muted", TrackType.ACTOR); track.setActorTrackPayload(payload); track.setMuted(true); model.addTrack(track);
        var issues = ActorProjectDiagnostics.scan(model, registry);
        assertEquals(1, issues.size()); assertEquals(ActorProjectDiagnostics.Reason.MISSING_ANIMATION, issues.getFirst().reason());
        assertTrue(clip.isMuted()); assertEquals(100, clip.getStartTime());
        registry.removeActor(actor.getActorId());
        assertTrue(ActorProjectDiagnostics.scan(model, registry).stream().anyMatch(i -> i.reason() == ActorProjectDiagnostics.Reason.MISSING_ACTOR));
    }
}
