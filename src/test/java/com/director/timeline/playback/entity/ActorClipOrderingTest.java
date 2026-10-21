package com.director.timeline.playback.entity;

import com.director.timeline.model.entity.*;
import com.director.timeline.model.*;
import com.director.timeline.persistence.TimelineModelPersistence;
import com.director.ui.timeline.commands.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorClipOrderingTest {
    @Test void crossingOtherClipsReordersAndRemovalUsesStableId() {
        var track=new ActorTrack();var a=new EntitySpawnClip("A",0,10);var b=new EntitySpawnClip("B",3,9);
        var c=new EntitySpawnClip("C",6,8);track.addClip(a);track.addClip(b);track.addClip(c);
        assertEquals(List.of(a,b,c),track.getClips());
        a.setTimeRange(7,17);assertEquals(List.of(b,c,a),track.getClips());
        c.offsetTime(-5);assertEquals(List.of(c,b,a),track.getClips());
        assertSame(a,track.findClip(a.getId()).orElseThrow());
        assertTrue(track.removeClip(a.getId()));assertFalse(track.removeClip(a.getId()));
        assertEquals(List.of(c,b),track.getClips());
    }
    @Test void cachedListsAreImmutableMembershipSnapshotsAndDetachedEditsDoNotInvalidateThem() {
        var track=new ActorTrack();var a=new EntitySpawnClip("A",0,10);var b=new EntitySpawnClip("B",3,9);
        track.addClip(a);var before=track.getClips();assertSame(before,track.getClips());
        assertThrows(UnsupportedOperationException.class,()->before.clear());
        track.addClip(b);assertEquals(List.of(a),before);assertEquals(List.of(a,b),track.getClips());
        track.addClip(a);assertEquals(2,track.getClipCount());
        track.removeClip(a.getId());var after=track.getClips();a.offsetTime(100);assertSame(after,track.getClips());
        track.clearClips();assertFalse(track.hasClips());assertEquals(0,track.getClipCount());
        var empty=track.getClips();b.offsetTime(50);assertSame(empty,track.getClips());
    }
    @Test void intervalQueriesKeepLongEarlierClipsAndIncludeAllStartTimeTies() {
        var track=new ActorTrack();var longClip=new EntitySpawnClip("Long",0,100);
        var a=new EntityStateClip("A",4,6);var b=new EntityStateClip("B",4,8);var future=new EntityStateClip("Future",20,30);
        for(var clip:List.of(longClip,a,b,future)) track.addClip(clip);
        var expected=new ArrayList<EntityClip>(List.of(longClip,a,b));expected.sort(Comparator.naturalOrder());
        assertEquals(expected,track.findClipsAt(4));assertEquals(expected,track.findClipsAt(6));
        assertEquals(List.of(longClip),track.findClipsAt(9));
        future.setTimeRange(2,5);assertTrue(track.findClipsAt(4).contains(future));
        assertEquals(4,track.findClipsAt(4).size());assertTrue(track.findClipsAt(-1).isEmpty());
    }
    @Test void wrapperMovesAndUndoRedoKeepSamplingPrecedenceAndKeyTimes() {
        var track=new ActorTrack();track.addClip(new EntitySpawnClip("Spawn",0,20));
        var early=new EntityStateClip("Early",3,14);early.setVisibility(.2f);track.addClip(early);
        var moving=new EntityStateClip("Moving",4,15);moving.setVisibility(.8f);track.addClip(moving);
        var key=new EntityClipKeyframe(5);moving.addKeyframe(key);
        var wrapper=new EntityTimelineClip(moving,0);var sampler=new EntityClipSampler(track);
        assertEquals(.8f,sampler.sampleSnapshot(6).visibility,1e-6f);
        var model=new TimelineModel(new TimelineViewportState());var history=new TimelineCommandHistory();
        history.executeCommand(new StateChangeCommand(model,"Move actor clip",()->wrapper.setTimeRange(4,15),()->wrapper.setTimeRange(2,13)));
        assertEquals(3,key.getTime());assertEquals(.2f,sampler.sampleSnapshot(6).visibility,1e-6f);
        assertTrue(history.undo());assertEquals(5,key.getTime());assertEquals(.8f,sampler.sampleSnapshot(6).visibility,1e-6f);
        assertTrue(history.redo());assertEquals(3,key.getTime());assertEquals(.2f,sampler.sampleSnapshot(6).visibility,1e-6f);
        assertEquals(3,track.getClipCount());
    }
    @Test void retimingNotifiesEveryPayloadAndActionReplacementDetachesOldClips() {
        var a=new ActorTrack();var b=new ActorTrack();var moved=new ActorActionClip("Moved",0,10);
        var other=new ActorActionClip("Other",3,8);a.addClip(moved);a.addClip(other);b.addClip(moved);b.addClip(other);
        a.getClips();b.getClips();moved.setTimeRange(4,14);
        assertEquals(List.of(other,moved),a.getActionClips());assertEquals(List.of(other,moved),b.getActionClips());
        var replacement=new ActorActionClip("New",1,5);a.replaceActionClips(List.of(replacement));
        var snapshot=a.getClips();moved.setTimeRange(2,12);assertSame(snapshot,a.getClips());
        assertEquals(List.of(moved,other),b.getActionClips());assertEquals(List.of(replacement),a.getActionClips());
    }
    @Test void persistedOrderMatchesCurrentEditedTimes() {
        var track=new ActorTrack();var a=new EntitySpawnClip("A",0,5);var b=new EntitySpawnClip("B",6,9);
        track.addClip(a);track.addClip(b);track.getClips();a.setTimeRange(10,15);
        var loaded=new ActorTrack();for(var clip:track.getClips()) loaded.addClip(TimelineModelPersistence.entityClipFromDTO(TimelineModelPersistence.entityClipToDTO(clip)));
        assertEquals(List.of("B","A"),loaded.getClips().stream().map(EntityClip::getName).toList());
        assertEquals(loaded.getClips().getLast().getId(),loaded.findClipsAt(12).getFirst().getId());
    }
}
