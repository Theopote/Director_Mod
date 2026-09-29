package com.director.ui.actor.group;

import com.director.actor.ActorGroup;
import com.director.actor.ActorRef;
import com.director.actor.action.ActorActionType;
import com.director.actor.group.ActorGroupController;
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
    private static final ActorGroupController GROUP_CONTROLLER = ActorGroupController.getInstance();

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
    void createActionClipsHonorsWaveDelay() {
        TimelineModel model = new TimelineModel(new TimelineViewportState());
        model.setCurrentTime(3.0f);

        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        UUID e = UUID.randomUUID();

        ActorTrack atA = createActorTrackPayload(a);
        ActorTrack atB = createActorTrackPayload(b);
        ActorTrack atC = createActorTrackPayload(c);
        ActorTrack atD = createActorTrackPayload(d);
        ActorTrack atE = createActorTrackPayload(e);

        model.addTrack(createActorTimelineTrack("A", atA));
        model.addTrack(createActorTimelineTrack("B", atB));
        model.addTrack(createActorTimelineTrack("C", atC));
        model.addTrack(createActorTimelineTrack("D", atD));
        model.addTrack(createActorTimelineTrack("E", atE));

        ActorGroup group = new ActorGroup("g");
        group.addActor(a);
        group.addActor(b);
        group.addActor(c);
        group.addActor(d);
        group.addActor(e);
        group.setDelayPolicy(ActorGroup.DelayPolicy.WAVE);
        group.setDelayStepSeconds(0.1f);

        ActorGroupTimelineCommandService svc = ActorGroupTimelineCommandService.getInstance();
        int created = svc.createActionClips(model, group, ActorActionType.IDLE, 1.0f, 1.0f, true);
        assertEquals(5, created);

        // Wave mapping in ActorGroupController:
        // i=0->0, i=1->4, i=2->1, i=3->3, i=4->2
        assertSingleActionClip(atA, 3.0f, 4.0f, ActorActionType.IDLE);
        assertSingleActionClip(atB, 3.4f, 4.4f, ActorActionType.IDLE);
        assertSingleActionClip(atC, 3.1f, 4.1f, ActorActionType.IDLE);
        assertSingleActionClip(atD, 3.3f, 4.3f, ActorActionType.IDLE);
        assertSingleActionClip(atE, 3.2f, 4.2f, ActorActionType.IDLE);
    }

    @Test
    void createActionClipsRandomDelayWithinBounds() {
        TimelineModel model = new TimelineModel(new TimelineViewportState());
        model.setCurrentTime(8.0f);

        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();

        ActorTrack atA = createActorTrackPayload(a);
        ActorTrack atB = createActorTrackPayload(b);
        ActorTrack atC = createActorTrackPayload(c);
        ActorTrack atD = createActorTrackPayload(d);

        model.addTrack(createActorTimelineTrack("A", atA));
        model.addTrack(createActorTimelineTrack("B", atB));
        model.addTrack(createActorTimelineTrack("C", atC));
        model.addTrack(createActorTimelineTrack("D", atD));

        ActorGroup group = new ActorGroup("g");
        group.addActor(a);
        group.addActor(b);
        group.addActor(c);
        group.addActor(d);
        group.setDelayPolicy(ActorGroup.DelayPolicy.RANDOM);
        group.setDelayStepSeconds(0.2f);

        float duration = 2.0f;
        ActorGroupTimelineCommandService svc = ActorGroupTimelineCommandService.getInstance();
        int created = svc.createActionClips(model, group, ActorActionType.RUN, duration, 1.0f, true);
        assertEquals(4, created);

        assertRandomDelayClipBounds(atA, 8.0f, 8.0f + 0.2f * 4, duration, ActorActionType.RUN);
        assertRandomDelayClipBounds(atB, 8.0f, 8.0f + 0.2f * 4, duration, ActorActionType.RUN);
        assertRandomDelayClipBounds(atC, 8.0f, 8.0f + 0.2f * 4, duration, ActorActionType.RUN);
        assertRandomDelayClipBounds(atD, 8.0f, 8.0f + 0.2f * 4, duration, ActorActionType.RUN);
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
        srcRoute.setJumpStart(1f);
        srcRoute.setJumpDuration(0.7f);
        srcRoute.setJumpHeight(0.8f);
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
        var copied = (ActorRouteClip) tt1.getActorTrackPayload().getClips().getFirst();
        assertEquals(1f, copied.getJumpStart());
        assertEquals(0.7f, copied.getJumpDuration());
        assertEquals(0.8f, copied.getJumpHeight());

        // Compare against controller formation (single source of truth).
        assertRouteWaypointMatchesFormationOffset(group, srcRoute, tt1.getActorTrackPayload(), t1);
        assertRouteWaypointMatchesFormationOffset(group, srcRoute, tt2.getActorTrackPayload(), t2);
        assertRouteWaypointMatchesFormationOffset(group, srcRoute, tt3.getActorTrackPayload(), t3);
    }

    @Test
    void copyLatestRouteAppliesGridFormationOffset() {
        TimelineModel model = new TimelineModel(new TimelineViewportState());
        model.setCurrentTime(1.0f);

        UUID sourceId = UUID.randomUUID();
        UUID t1 = UUID.randomUUID();
        UUID t2 = UUID.randomUUID();
        UUID t3 = UUID.randomUUID();
        UUID t4 = UUID.randomUUID();

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
        TimelineTrack tt4 = createActorTimelineTrack("t4", createActorTrackPayload(t4));

        model.addTrack(srcTimeline);
        model.addTrack(tt1);
        model.addTrack(tt2);
        model.addTrack(tt3);
        model.addTrack(tt4);

        ActorGroup group = new ActorGroup("g");
        group.addActor(t1);
        group.addActor(t2);
        group.addActor(t3);
        group.addActor(t4);
        group.setFormationType(ActorGroup.FormationType.GRID);
        group.setFormationSpacing(2.0f);
        group.setDelayPolicy(ActorGroup.DelayPolicy.NONE);

        ActorGroupTimelineCommandService svc = ActorGroupTimelineCommandService.getInstance();
        int created = svc.copyLatestRouteFromActor(model, group, sourceId);
        assertEquals(4, created);

        assertRouteWaypointMatchesFormationOffset(group, srcRoute, tt1.getActorTrackPayload(), t1);
        assertRouteWaypointMatchesFormationOffset(group, srcRoute, tt2.getActorTrackPayload(), t2);
        assertRouteWaypointMatchesFormationOffset(group, srcRoute, tt3.getActorTrackPayload(), t3);
        assertRouteWaypointMatchesFormationOffset(group, srcRoute, tt4.getActorTrackPayload(), t4);
    }

    @Test
    void copyLatestRouteAppliesFollowLeaderFormationOffset() {
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
        group.setFormationType(ActorGroup.FormationType.FOLLOW_LEADER);
        group.setFormationSpacing(1.5f);
        group.setDelayPolicy(ActorGroup.DelayPolicy.NONE);

        ActorGroupTimelineCommandService svc = ActorGroupTimelineCommandService.getInstance();
        int created = svc.copyLatestRouteFromActor(model, group, sourceId);
        assertEquals(3, created);

        assertRouteWaypointMatchesFormationOffset(group, srcRoute, tt1.getActorTrackPayload(), t1);
        assertRouteWaypointMatchesFormationOffset(group, srcRoute, tt2.getActorTrackPayload(), t2);
        assertRouteWaypointMatchesFormationOffset(group, srcRoute, tt3.getActorTrackPayload(), t3);
    }

    @Test
    void copyLatestRouteAppliesCircleFormationOffset() {
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
        group.setFormationType(ActorGroup.FormationType.CIRCLE);
        group.setFormationSpacing(2.0f);
        group.setDelayPolicy(ActorGroup.DelayPolicy.NONE);

        ActorGroupTimelineCommandService svc = ActorGroupTimelineCommandService.getInstance();
        int created = svc.copyLatestRouteFromActor(model, group, sourceId);
        assertEquals(3, created);

        assertRouteWaypointMatchesFormationOffset(group, srcRoute, tt1.getActorTrackPayload(), t1);
        assertRouteWaypointMatchesFormationOffset(group, srcRoute, tt2.getActorTrackPayload(), t2);
        assertRouteWaypointMatchesFormationOffset(group, srcRoute, tt3.getActorTrackPayload(), t3);
    }

    @Test
    void copyLatestRouteHonorsSequentialDelay() {
        TimelineModel model = new TimelineModel(new TimelineViewportState());
        model.setCurrentTime(1.0f);

        UUID sourceId = UUID.randomUUID();
        UUID t1 = UUID.randomUUID();
        UUID t2 = UUID.randomUUID();
        UUID t3 = UUID.randomUUID();

        ActorRouteClip source = new ActorRouteClip("route", 2.0f, 6.0f);
        source.setWaypoints(List.of(
                new Vector3f(0, 64, 0),
                new Vector3f(0, 64, 10)
        ));
        ActorTrack srcTrack = createActorTrackPayload(sourceId);
        srcTrack.addClip(source);

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
        group.setDelayPolicy(ActorGroup.DelayPolicy.SEQUENTIAL);
        group.setDelayStepSeconds(0.5f);
        group.setFormationType(ActorGroup.FormationType.FREE);

        ActorGroupTimelineCommandService svc = ActorGroupTimelineCommandService.getInstance();
        int created = svc.copyLatestRouteFromActor(model, group, sourceId);
        assertEquals(3, created);

        assertRouteClipStart(tt1.getActorTrackPayload(), 2.0f);
        assertRouteClipStart(tt2.getActorTrackPayload(), 2.5f);
        assertRouteClipStart(tt3.getActorTrackPayload(), 3.0f);
    }

    @Test
    void copyLatestRouteHonorsWaveDelay() {
        TimelineModel model = new TimelineModel(new TimelineViewportState());
        model.setCurrentTime(1.0f);

        UUID sourceId = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        UUID e = UUID.randomUUID();

        ActorRouteClip source = new ActorRouteClip("route", 1.0f, 3.0f);
        source.setWaypoints(List.of(
                new Vector3f(0, 64, 0),
                new Vector3f(0, 64, 10)
        ));
        ActorTrack srcTrack = createActorTrackPayload(sourceId);
        srcTrack.addClip(source);

        TimelineTrack srcTimeline = createActorTimelineTrack("src", srcTrack);
        TimelineTrack ta = createActorTimelineTrack("a", createActorTrackPayload(a));
        TimelineTrack tb = createActorTimelineTrack("b", createActorTrackPayload(b));
        TimelineTrack tc = createActorTimelineTrack("c", createActorTrackPayload(c));
        TimelineTrack td = createActorTimelineTrack("d", createActorTrackPayload(d));
        TimelineTrack te = createActorTimelineTrack("e", createActorTrackPayload(e));
        model.addTrack(srcTimeline);
        model.addTrack(ta);
        model.addTrack(tb);
        model.addTrack(tc);
        model.addTrack(td);
        model.addTrack(te);

        ActorGroup group = new ActorGroup("g");
        group.addActor(a);
        group.addActor(b);
        group.addActor(c);
        group.addActor(d);
        group.addActor(e);
        group.setDelayPolicy(ActorGroup.DelayPolicy.WAVE);
        group.setDelayStepSeconds(0.1f);
        group.setFormationType(ActorGroup.FormationType.FREE);

        // Reference delays from controller (ensures "same semantics" guarantee)
        var triggers = GROUP_CONTROLLER.applyDelayPolicy(group, ActorActionType.IDLE, 0.0f);
        float da = triggerTime(triggers, a);
        float db = triggerTime(triggers, b);
        float dc = triggerTime(triggers, c);
        float dd = triggerTime(triggers, d);
        float de = triggerTime(triggers, e);

        ActorGroupTimelineCommandService svc = ActorGroupTimelineCommandService.getInstance();
        int created = svc.copyLatestRouteFromActor(model, group, sourceId);
        assertEquals(5, created);

        assertRouteClipStart(ta.getActorTrackPayload(), 1.0f + da);
        assertRouteClipStart(tb.getActorTrackPayload(), 1.0f + db);
        assertRouteClipStart(tc.getActorTrackPayload(), 1.0f + dc);
        assertRouteClipStart(td.getActorTrackPayload(), 1.0f + dd);
        assertRouteClipStart(te.getActorTrackPayload(), 1.0f + de);
    }

    @Test
    void copyLatestRouteRandomDelayMatchesGroupController() {
        TimelineModel model = new TimelineModel(new TimelineViewportState());
        model.setCurrentTime(1.0f);

        UUID groupId = UUID.fromString("00000000-0000-0000-0000-000000000007");
        UUID sourceId = UUID.fromString("00000000-0000-0000-0000-000000000100");
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID c = UUID.fromString("00000000-0000-0000-0000-000000000003");

        ActorRouteClip source = new ActorRouteClip("route", 5.0f, 7.0f);
        source.setWaypoints(List.of(
                new Vector3f(0, 64, 0),
                new Vector3f(0, 64, 10)
        ));
        ActorTrack srcTrack = createActorTrackPayload(sourceId);
        srcTrack.addClip(source);

        TimelineTrack srcTimeline = createActorTimelineTrack("src", srcTrack);
        TimelineTrack ta = createActorTimelineTrack("a", createActorTrackPayload(a));
        TimelineTrack tb = createActorTimelineTrack("b", createActorTrackPayload(b));
        TimelineTrack tc = createActorTimelineTrack("c", createActorTrackPayload(c));
        model.addTrack(srcTimeline);
        model.addTrack(ta);
        model.addTrack(tb);
        model.addTrack(tc);

        ActorGroup group = new ActorGroup(groupId, "g");
        group.addActor(a);
        group.addActor(b);
        group.addActor(c);
        group.setDelayPolicy(ActorGroup.DelayPolicy.RANDOM);
        group.setDelayStepSeconds(0.2f);
        group.setFormationType(ActorGroup.FormationType.FREE);

        var triggers = GROUP_CONTROLLER.applyDelayPolicy(group, ActorActionType.IDLE, 0.0f);
        float da = triggerTime(triggers, a);
        float db = triggerTime(triggers, b);
        float dc = triggerTime(triggers, c);

        ActorGroupTimelineCommandService svc = ActorGroupTimelineCommandService.getInstance();
        int created = svc.copyLatestRouteFromActor(model, group, sourceId);
        assertEquals(3, created);

        assertRouteClipStart(ta.getActorTrackPayload(), 5.0f + da);
        assertRouteClipStart(tb.getActorTrackPayload(), 5.0f + db);
        assertRouteClipStart(tc.getActorTrackPayload(), 5.0f + dc);
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

    private static void assertRandomDelayClipBounds(ActorTrack track,
                                                   float minStart,
                                                   float maxStartInclusive,
                                                   float duration,
                                                   ActorActionType type) {
        List<ActorActionClip> clips = track.getActionClips();
        assertEquals(1, clips.size());
        ActorActionClip clip = clips.getFirst();
        assertEquals(type, clip.getActionType());
        assertTrue(clip.getStartTime() >= minStart - EPS, "startTime should be >= base");
        assertTrue(clip.getStartTime() <= maxStartInclusive + EPS, "startTime should be <= base + delayStep * count");
        assertEquals(clip.getStartTime() + duration, clip.getEndTime(), 0.001f);
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

    private static float triggerTime(List<ActorGroupController.ActionTrigger> triggers, UUID actorId) {
        for (var t : triggers) {
            if (t.actorId().equals(actorId)) {
                return t.triggerTime();
            }
        }
        fail("Missing trigger for actor " + actorId);
        return 0.0f;
    }

    private static void assertRouteClipStart(ActorTrack track, float expectedStart) {
        ActorRouteClip clip = findLatestRouteClip(track);
        assertNotNull(clip);
        assertEquals(expectedStart, clip.getStartTime(), 0.001f);
    }

    private static void assertRouteWaypointMatchesFormationOffset(ActorGroup group,
                                                                  ActorRouteClip source,
                                                                  ActorTrack targetTrack,
                                                                  UUID targetActorId) {
        ActorRouteClip copy = findLatestRouteClip(targetTrack);
        assertNotNull(copy);

        Vector3f srcFirst = source.getWaypoints().getFirst();
        float yaw = routeHeadingYaw(source);
        var formations = GROUP_CONTROLLER.calculateFormation(
                group,
                new net.minecraft.util.math.Vec3d(srcFirst.x, srcFirst.y, srcFirst.z),
                yaw
        );
        net.minecraft.util.math.Vec3d expected = formationPosition(formations, targetActorId);

        Vector3f actual = copy.getWaypoints().getFirst();
        assertEquals((float) expected.x, actual.x, 0.001f);
        assertEquals((float) expected.y, actual.y, 0.001f);
        assertEquals((float) expected.z, actual.z, 0.001f);
    }

    private static net.minecraft.util.math.Vec3d formationPosition(
            List<ActorGroupController.FormationPosition> positions,
            UUID actorId) {
        for (var p : positions) {
            if (p.actorId().equals(actorId)) {
                return p.position();
            }
        }
        fail("Missing formation position for actor " + actorId);
        return new net.minecraft.util.math.Vec3d(0, 0, 0);
    }

    private static float routeHeadingYaw(ActorRouteClip source) {
        Vector3f forward = new Vector3f(0.0f, 0.0f, 1.0f);
        List<Vector3f> points = source.getWaypoints();
        if (points.size() >= 2) {
            Vector3f first = points.getFirst();
            for (int i = 1; i < points.size(); i++) {
                Vector3f candidate = new Vector3f(points.get(i)).sub(first);
                candidate.y = 0.0f;
                if (candidate.lengthSquared() > 0.0001f) {
                    forward = candidate.normalize();
                    break;
                }
            }
        }
        return (float) Math.toDegrees(Math.atan2(forward.x, forward.z));
    }
}

