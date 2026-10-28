package com.director.ui.actor.pose;

import com.director.timeline.model.entity.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorInspectorPlaybackTest {
    @Test void resamplingSeesSameTimeEditsAndNeverMutatesOlderSnapshots() {
        var track=new ActorTrack("A");var spawn=new EntitySpawnClip("A",0,5);track.addClip(spawn);
        var query=new ActorInspectorPlayback();
        spawn.setPosition(1,2,3);var before=query.sample(track,1);
        spawn.setPosition(4,5,6);var after=query.sample(track,1);
        assertEquals(1,before.position.x);assertEquals(4,after.position.x);
        spawn.setMuted(true);assertFalse(query.sample(track,1).active);
        spawn.setMuted(false);assertTrue(query.sample(track,1).active);
        track.removeClip(spawn.getId());assertFalse(query.sample(track,1).enabled);
    }
    @Test void switchingOrClearingOwnerCannotRetainOldClassifiedClips() {
        var a=new ActorTrack("A");var spawn=new EntitySpawnClip("A",0,5);a.addClip(spawn);
        var b=new ActorTrack("B");var query=new ActorInspectorPlayback();
        assertTrue(query.sample(a,1).active);assertFalse(query.sample(b,1).enabled);
        assertNull(query.sample(null,1));assertTrue(query.sample(a,1).active);
        query.clear();assertTrue(query.sample(a,1).active);
    }
}
