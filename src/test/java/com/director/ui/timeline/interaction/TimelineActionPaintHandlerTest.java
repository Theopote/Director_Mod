package com.director.ui.timeline.interaction;

import com.director.actor.Actor;
import com.director.actor.ActorRef;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorRegistryManager;
import com.director.actor.ActorType;
import com.director.actor.action.ActorActionType;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TimelineViewportState;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.entity.ActorActionClip;
import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.model.entity.EntitySpawnClip;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.timeline.playback.entity.EntityClipSampler;
import com.director.ui.timeline.commands.TimelineCommandBridge;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimelineActionPaintHandlerTest {
    @Test void reverseStrokeSplitsOnlyOwnedActionsAndUndoRestoresOriginal() {
        var actor = new Actor("Player", ActorType.PLAYER);
        ActorRegistryManager.setRegistry(new ActorRegistry());
        ActorRegistryManager.getInstance().registerActor(actor);
        var history = new TimelineCommandHistory();
        TimelineCommandBridge.bind(history);
        try {
            var model = new TimelineModel(new TimelineViewportState());
            var track = new TimelineTrack("Actor", TrackType.ACTOR);
            var payload = new ActorTrack();
            payload.setActorRef(new ActorRef(actor.getActorId()));
            track.setActorTrackPayload(payload);
            model.addTrack(track);
            var spawn = new EntitySpawnClip("Performance", 1, 8);
            payload.addClip(spawn);
            track.addClip(new EntityTimelineClip(spawn, 0));

            var walk = new ActorActionClip("Walk", 2, 7);
            walk.setActionType(ActorActionType.WALK);
            walk.setIntensity(.4f);
            spawn.addActionInterval(walk);
            var muted = new ActorActionClip("Muted", 3, 6);
            muted.setMuted(true);
            spawn.addActionInterval(muted);

            assertTrue(TimelineActionPaintHandler.paint(model, track, spawn, ActorActionType.RUN, 5, 4));
            assertEquals(1, history.getUndoSize());
            assertEquals(1, track.getClips().size());
            var sampler = new EntityClipSampler(payload);
            assertEquals(ActorActionType.WALK, sampler.sampleSnapshot(3).action);
            assertEquals(ActorActionType.RUN, sampler.sampleSnapshot(4.5f).action);
            assertEquals(.4f, sampler.sampleSnapshot(6).actionIntensity);
            assertTrue(spawn.getActionIntervals().contains(muted));
            assertTrue(history.undo());
            assertEquals(java.util.List.of(walk, muted), spawn.getActionIntervals());
            assertTrue(history.redo());
            assertEquals(ActorActionType.RUN, sampler.sampleSnapshot(4.5f).action);
            assertFalse(TimelineActionPaintHandler.paint(model, track, spawn, ActorActionType.RUN, 9, 7));

            var legacy = new ActorActionClip("Legacy", 6, 8);
            payload.addClip(legacy);
            assertFalse(TimelineActionPaintHandler.paint(model, track, spawn, ActorActionType.RUN, 7, 10));
            assertTrue(payload.getClips().contains(legacy));
            assertEquals(1, history.getUndoSize());
        } finally {
            TimelineCommandBridge.bind(null);
            ActorRegistryManager.setRegistry(null);
        }
    }
}
