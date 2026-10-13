package com.director.lighting;

import com.director.timeline.model.*;
import com.director.timeline.persistence.TimelineModelPersistence;
import com.director.ui.lighting.PointLightParameterEdit;
import com.director.ui.timeline.audio.TimelineAudioService;
import com.director.ui.timeline.commands.*;
import com.director.ui.timeline.state.ClipboardManager;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LightLifecycleTest {
    TimelineModel model;
    TimelineCommandHistory history;
    @BeforeEach void setup() {
        model=new TimelineModel(new TimelineViewportState());model.setSuppressPersistenceWrites(true);
        history=new TimelineCommandHistory();history.setModel(model);TimelineCommandBridge.bind(history);
        TimelineAudioService.getInstance().setTimelineModel(model);
    }
    @AfterEach void cleanup() {
        ClipboardManager.getInstance().clear();TimelineCommandBridge.bind(null);
        TimelineAudioService.getInstance().setTimelineModel(null);history.setModel(null);
    }
    PointLight light() {return PointLight.createArea(2,3,4,57,-34).area(8,6,57,-34).settings(7,12,2,true);}
    LightTimelineClip clip(TimelineTrack track,PointLight light,float start) {
        model.replacePointLight(light.id(),light);
        var clip=new LightTimelineClip("L",start,start+5,light.id());track.addClip(clip);return clip;
    }
    TimelineTrack track() {var t=new TimelineTrack("Light",TrackType.LIGHT);model.addTrack(t);return t;}
    void delete(LightTimelineClip clip) {history.executeCommand(new DeleteClipCommand(model,clip,clip.getParentTrack().getClips()));}

    @Test void lastClipDeletionRemovesAssetAndUndoRestoresIdGeometryKeysAndLight() {
        var l=light();var t=track();var c=clip(t,l,0);var key=new LightKeyframe(2,l.at(10,20,30));c.addKeyframe(key);
        delete(c);assertNull(model.getPointLight(l.id()));assertTrue(model.getPointLights().isEmpty());assertTrue(t.getClips().isEmpty());
        assertTrue(LightTimelineSampler.sample(model,1).isEmpty());
        history.undo();assertEquals(l,model.getPointLight(l.id()));assertEquals(List.of(c),t.getClips());assertSame(key,c.getClipKeyframes().getFirst());
        assertEquals(key.value(),LightTimelineSampler.sample(model,2).getFirst());
        history.redo();assertNull(model.getPointLight(l.id()));history.undo();assertEquals(l,model.getPointLight(l.id()));
    }
    @Test void deletionKeepsAssetUntilLastClipAcrossDifferentTracksIsRemoved() {
        var l=light();var first=clip(track(),l,0);var second=clip(track(),l,8);
        delete(first);assertEquals(l,model.getPointLight(l.id()));assertEquals(1,LightTimelineSampler.sample(model,9).size());
        delete(second);assertNull(model.getPointLight(l.id()));history.undo();assertEquals(l,model.getPointLight(l.id()));
        history.undo();assertTrue(LightLifecycle.containsClip(model,first));assertTrue(LightLifecycle.containsClip(model,second));
    }
    @Test void deletingTrackRemovesOnlyLightsThatHaveNoReferencesElsewhere() {
        var a=light();var b=PointLight.create(20,30,40);var first=track();var second=track();
        clip(first,a,0);clip(first,b,0);clip(second,b,8);
        history.executeCommand(new DeleteTrackCommand(model,first,model.getTracks()));
        assertNull(model.getPointLight(a.id()));assertEquals(b,model.getPointLight(b.id()));assertFalse(model.getTracks().contains(first));
        history.undo();assertEquals(a,model.getPointLight(a.id()));assertEquals(List.of(first,second),model.getTracks());
        history.redo();assertNull(model.getPointLight(a.id()));
    }
    @Test void mutedHiddenDisabledAndOutOfIntervalReferencesStillOwnTheirLights() {
        var l=light().settings(0,8,1,false);var first=clip(track(),l,0);var retained=track();clip(retained,l,50);
        retained.setMuted(true);retained.setVisible(false);delete(first);
        assertEquals(l,model.getPointLight(l.id()));assertTrue(LightTimelineSampler.sample(model,1).isEmpty());
    }
    @Test void batchDeletionAndReverseUndoRestoreAllSharedReferences() {
        var l=light();var a=clip(track(),l,0);var b=clip(track(),l,8);
        history.executeCommand(new BatchDeleteSelectionCommand(model,List.of(a,b)));
        assertNull(model.getPointLight(l.id()));history.undo();assertEquals(l,model.getPointLight(l.id()));
        assertTrue(LightLifecycle.containsClip(model,a));assertTrue(LightLifecycle.containsClip(model,b));
        history.redo();assertNull(model.getPointLight(l.id()));
    }
    @Test void movingAcrossTracksDoesNotDeleteTheTemporarilyUnboundAsset() {
        var l=light();var from=track();var to=track();var c=clip(from,l,0);
        history.executeCommand(new MoveClipCommand(model,c,from,to,0,5,10,15));
        assertEquals(l,model.getPointLight(l.id()));assertSame(to,c.getParentTrack());assertEquals(List.of(l),LightTimelineSampler.sample(model,11));
        history.undo();assertSame(from,c.getParentTrack());assertEquals(l,model.getPointLight(l.id()));
    }
    @Test void cuttingLastClipAndPastingRestoresGeometryThenUndoRedoKeepsOwnership() {
        var l=light();var from=track();var to=track();var c=clip(from,l,0);var key=new LightKeyframe(2,l.at(9,8,7));c.addKeyframe(key);
        var clipboard=ClipboardManager.getInstance();clipboard.cut(c,from,model,history);assertNull(model.getPointLight(l.id()));
        model.setCurrentTime(10);clipboard.pasteToTrack(to,model);
        assertEquals(l,model.getPointLight(l.id()));assertEquals(1,to.getClips().size());
        var pasted=(LightTimelineClip)to.getClips().getFirst();assertEquals(12,pasted.getClipKeyframes().getFirst().getTime());
        assertEquals(key.value(),((LightKeyframe)pasted.getClipKeyframes().getFirst()).value());
        history.undo();assertNull(model.getPointLight(l.id()));history.undo();assertEquals(l,model.getPointLight(l.id()));assertEquals(List.of(c),from.getClips());
        history.redo();assertNull(model.getPointLight(l.id()));history.redo();assertEquals(l,model.getPointLight(l.id()));assertEquals(List.of(pasted),to.getClips());
    }
    @Test void copiedClipCanRestoreLightAfterOriginalWasDeleted() {
        var l=light();var t=track();var c=clip(t,l,0);var clipboard=ClipboardManager.getInstance();clipboard.copy(c,model);delete(c);
        assertNull(model.getPointLight(l.id()));model.setCurrentTime(10);clipboard.pasteToTrack(t,model);
        assertEquals(l,model.getPointLight(l.id()));history.undo();assertNull(model.getPointLight(l.id()));history.undo();assertEquals(l,model.getPointLight(l.id()));
    }
    @Test void undoPasteDoesNotDeleteAStillSharedAssetOrRestoreStaleParameters() {
        var l=light();var t=track();var c=clip(t,l,0);var clipboard=ClipboardManager.getInstance();clipboard.copy(c,model);
        var edited=l.settings(13,8,1,true);model.replacePointLight(l.id(),edited);model.setCurrentTime(10);clipboard.pasteToTrack(t,model);
        assertEquals(edited,model.getPointLight(l.id()));history.undo();assertEquals(edited,model.getPointLight(l.id()));assertEquals(List.of(c),t.getClips());
    }
    @Test void rejectedPasteCannotIntroduceOrphanGeometry() {
        var l=light();var invalid=new TimelineTrack("Container",TrackType.CONTAINER);model.addTrack(invalid);
        var c=new LightTimelineClip("L",0,5,l.id());var insert=new InsertClipCommand(model,invalid,c,ignored -> {},l);insert.execute();
        assertFalse(insert.canUndo());assertNull(model.getPointLight(l.id()));assertTrue(invalid.getClips().isEmpty());
    }
    @Test void missingGeometryWithoutClipboardSnapshotCannotCreateDanglingClip() {
        var l=light();var t=track();var c=new LightTimelineClip("L",0,5,l.id());var insert=new InsertClipCommand(model,t,c,ignored -> {});insert.execute();
        assertFalse(insert.canUndo());assertTrue(t.getClips().isEmpty());
    }
    @Test void detachedTrackInvalidatesPendingKeyEditEvenWhenLightIsShared() {
        var l=light();var t=track();var c=clip(t,l,0);clip(track(),l,8);var k=new LightKeyframe(0,l);c.addKeyframe(k);
        var edit=new PointLightParameterEdit(model,c,k);edit.preview(l.settings(3,8,1,true));
        history.executeCommand(new DeleteTrackCommand(model,t,model.getTracks()));
        assertFalse(LightLifecycle.containsClip(model,c));assertFalse(edit.commit((a,b)->fail("Detached key must not commit")));assertEquals(l,model.getPointLight(l.id()));
    }
    @Test void modernProjectLoadRemovesOrphansButKeepsReferencedDisabledLights() {
        var orphan=light();model.replacePointLight(orphan.id(),orphan);
        var kept=PointLight.create(0,0,0).settings(0,8,1,false);clip(track(),kept,0);
        var restored=new TimelineModel(new TimelineViewportState());restored.setSuppressPersistenceWrites(true);
        TimelineModelPersistence.fromDTO(TimelineModelPersistence.toDTO(model,null),restored,null);
        assertNull(restored.getPointLight(orphan.id()));assertEquals(kept,restored.getPointLight(kept.id()));
    }
    @Test void deletedClipDoesNotReturnAfterSaveReload() {
        var l=light();delete(clip(track(),l,0));var dto=TimelineModelPersistence.toDTO(model,null);
        TimelineModelPersistence.fromDTO(dto,model,null);assertTrue(model.getPointLights().isEmpty());assertTrue(model.getTracks().getFirst().getClips().isEmpty());
    }
}
