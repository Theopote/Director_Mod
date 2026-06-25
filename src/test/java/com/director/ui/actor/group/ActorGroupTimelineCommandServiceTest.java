package com.director.ui.actor.group;

import com.director.actor.ActorGroup;
import com.director.actor.ActorRef;
import com.director.actor.action.ActorActionType;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TimelineViewportState;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.entity.ActorActionClip;
import com.director.timeline.model.entity.ActorRouteClip;
import com.director.timeline.model.entity.ActorTrack;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ActorGroupTimelineCommandServiceTest {

    private static final float EPS = 0.0001f;

    @Test
    void createActionClipsHonorsSequentialDelay() {
        TimelineModel model = new TimelineModel(new TimelineViewportState());
        model.setCurrentTime(10.0f);

        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        ActorTrack atA = createActorTrackPayload(a);
        ActorTrack atB = createActorTrackPayload(b);
        ActorTrack atC = createActorTrackPayload(c);

        TimelineTrack ta = createActorTimelineTrack("A", atA);
        TimelineTrack tb = createActorTimelineTrack("B", atB);
        TimelineTrack tc = createActorTimelineTrack("C", atC);

        model.addTrack(ta);
        model.addTrack(tb);
        model.addTrack(tc);

        ActorGroup group = new ActorGroup("g");
        group.addActor(a);
        group.addActor(b);
        group.addActor(c);
        group.setDelayPolicy(ActorGroup.DelayPolicy.SEQUENTIAL);
        group.setDelayStepSeconds(0.25f);

        ActorGroupTimelineCommandService svc = ActorGroupTimelineCommandService.getInstance();
        int created = svc.createActionClips(model, group, ActorActionType.WALK, 2.0f, 1.0f, true);
        assertEquals(3, created);

        assertSingleActionClip(atA, 10.0f, 12.0f, ActorActionType.WALK);
        assertSingleActionClip(atB, 10.25f, 12.25f, ActorActionType.WALK);
        assertSingleActionClip(atC, 10.50f, 12.50f, ActorActionType.WALK);
    }

    @Test
    void copyLatestRouteAppliesLineFormationOffset() {
        TimelineModel model = new TimelineModel(new TimelineViewportState());
        model.setCurrentTime(1.0f);

        UUID sourceId = UUID.randomUUID();
        UUID t1 = UUID.randomUUID();
        UUID t2 = UUID.randomUUID();
        UUID t3 = UUID.randomUUID();

        ActorTrack srcTrack = createActorTrackPayload(sourceId);
        ActorRouteClip srcRoute = new ActorRouteClip("route", 0.0f, 5.0f);
        srcRoute.setWaypoints(List.of(
                new Vector3f(0, 64, 0),
                new Vector3f(0, 64, 10)
        ));
        srcTrack.addClip(srcRoute);

        TimelineTrack srcTimeline = createActorTimelineTrack("src", srcTrack);
        TimelineTrack tt1 = createActorTimelineTrack("t1", createActorTrackPayload(t1));
        TimelineTrack tt2 = createActorTimelineTrack("t2", createActorTrackPayload(t2));
        TimelineTrack tt3 = createActorTimelineTrack("t3", createActorTrackPayload(t3));

        model.addTrack(srcTimeline);
        model.addTrack(tt1);
        model.addTrack(tt2);
        model.addTrack(tt3);

        ActorGroup group = new ActorGroup("g");
        group.addActor(t1);
        group.addActor(t2);
        group.addActor(t3);
        group.setFormationType(ActorGroup.FormationType.LINE);
        group.setFormationSpacing(2.0f);
        group.setDelayPolicy(ActorGroup.DelayPolicy.NONE);

        ActorGroupTimelineCommandService svc = ActorGroupTimelineCommandService.getInstance();
        int created = svc.copyLatestRouteFromActor(model, group, sourceId);
        assertEquals(3, created);

        ActorRouteClip r1 = findLatestRouteClip(tt1.getActorTrackPayload());
        ActorRouteClip r2 = findLatestRouteClip(tt2.getActorTrackPayload());
        ActorRouteClip r3 = findLatestRouteClip(tt3.getActorTrackPayload());
        assertNotNull(r1);
        assertNotNull(r2);
        assertNotNull(r3);

        // LINE formation at yaw=0 spreads on X axis: [-2, 0, +2]
        assertEquals(-2.0f, r1.getWaypoints().getFirst().x, EPS);
        assertEquals(0.0f, r2.getWaypoints().getFirst().x, EPS);
        assertEquals(2.0f, r3.getWaypoints().getFirst().x, EPS);
    }

    private static ActorTrack createActorTrackPayload(UUID actorId) {
        ActorTrack actorTrack = new ActorTrack("actor");
        actorTrack.setActorRef(new ActorRef(actorId));
        return actorTrack;
    }

    private static TimelineTrack createActorTimelineTrack(String name, ActorTrack payload) {
        TimelineTrack track = new TimelineTrack(name, TrackType.ACTOR);
        track.setActorTrackPayload(payload);
        return track;
    }

    private static void assertSingleActionClip(ActorTrack track, float start, float end, ActorActionType type) {
        List<ActorActionClip> clips = track.getActionClips();
        assertEquals(1, clips.size());
        ActorActionClip clip = clips.getFirst();
        assertEquals(type, clip.getActionType());
        assertEquals(start, clip.getStartTime(), EPS);
        assertEquals(end, clip.getEndTime(), EPS);
    }

    private static ActorRouteClip findLatestRouteClip(ActorTrack track) {
        ActorRouteClip latest = null;
        for (var clip : track.getClips()) {
            if (!(clip instanceof ActorRouteClip route)) {
                continue;
            }
            if (latest == null || route.getStartTime() > latest.getStartTime()) {
                latest = route;
            }
        }
        return latest;
    }
}

