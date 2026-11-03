package com.director.actor.query;
import com.director.actor.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.model.entity.EntityAnimationClip;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ActorProjectDiagnosticsTest {
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
