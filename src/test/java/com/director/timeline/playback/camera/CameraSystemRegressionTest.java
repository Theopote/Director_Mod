package com.director.timeline.playback.camera;
import com.director.timeline.model.*;
import com.director.timeline.model.camera.*;
import com.director.timeline.model.camera.path.*;
import com.director.timeline.playback.camera.*;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class CameraSystemRegressionTest {
    private CameraKeyframe key(float t, float x) {
        var k = new CameraKeyframe("k", t);
        k.setPosition(new Vector3f(x, 0, 0));
        k.setSegmentType(CameraKeyframe.SegmentPathType.LINEAR);
        return k;
    }
    @Test void stepReachesNextPositionAtExactKey() {
        var a=key(0,0); a.setSegmentType(CameraKeyframe.SegmentPathType.STEP);
        var b=key(1,100);
        var manager=new TimelineCameraPathManager(); manager.setKeyframes(List.of(a,b));
        assertEquals(100,manager.sample(1).orElseThrow().position().x);
        assertEquals(100,manager.sample(1.001f).orElseThrow().position().x);
    }
    @Test void rotationStepReachesNextRotationAtExactKey() {
        var a=key(0,0); a.setRotationInterpolation(CameraKeyframe.RotationInterpolation.STEP);
        var b=key(1,100); b.setRotation(new Vector3f(0,90,0));
        var manager=new TimelineCameraPathManager(); manager.setKeyframes(List.of(a,b));
        assertEquals(90,manager.sample(1).orElseThrow().yaw());
        assertEquals(90,manager.sample(1.001f).orElseThrow().yaw());
    }
    @Test void adjoiningClipsChooseNextAtCut() {
        var track=new TimelineTrack("camera",TrackType.CAMERA);
        var a=new CustomPathClip("a",0,1); a.addKeyframe(key(0,0));
        var b=new CustomPathClip("b",1,2); b.addKeyframe(key(1,100));
        track.addCameraPathClip(a); track.addCameraPathClip(b);
        var sampler=new CameraTrackSampler(); sampler.setCameraTrack(track);
        assertEquals(100,sampler.sample(1).orElseThrow().position().x);
        assertEquals(100,sampler.sample(1.001f).orElseThrow().position().x);
    }
    private CameraTrackSampler dollyChain() {
        var track=new TimelineTrack("camera",TrackType.CAMERA);
        for(int i=0;i<3;i++) {
            var clip=new DollyPathClip("d"+i,i,i+1);
            clip.setDistance(10); clip.setStartPosition(Vec3d.ZERO); clip.setDollyDirection(new Vec3d(0,0,1));
            track.addCameraPathClip(clip);
        }
        var sampler=new CameraTrackSampler(); sampler.setCameraTrack(track); sampler.setKeyframes(List.of(key(0,0)));
        return sampler;
    }
    @Test void dollyChainIsIndependentOfSamplingHistory() {
        var sequential=dollyChain(); sequential.sample(.5f); sequential.sample(1.5f);
        double played=sequential.sample(2.5f).orElseThrow().position().z;
        double jumped=dollyChain().sample(2.5f).orElseThrow().position().z;
        assertEquals(25, played, 1e-5);
        assertEquals(played,jumped,1e-5);
    }
    @Test void disabledAndOutOfRangeClipsDoNotSample() {
        var track=new TimelineTrack("camera",TrackType.CAMERA);
        var clip=new CustomPathClip("a",1,2); clip.addKeyframe(key(1,100));
        track.addCameraPathClip(clip);
        var sampler=new CameraTrackSampler(); sampler.setCameraTrack(track);
        assertTrue(track.hasCameraPathClips()); assertTrue(sampler.sample(0).isEmpty());
        clip.setEnabled(false);
        assertTrue(track.hasCameraPathClips()); assertTrue(sampler.sample(1.5f).isEmpty());
    }

    private TimelineModel model(TimelineTrack... tracks) {
        var model=new TimelineModel(new TimelineViewportState()); model.setSuppressPersistenceWrites(true);
        for(var track:tracks) model.addTrack(track);
        return model;
    }
    private TimelineTrack shot(String name, float x) {
        var track=new TimelineTrack(name,TrackType.CAMERA);
        var clip=new CustomPathClip(name,0,5); clip.addKeyframe(key(0,x));
        track.addCameraPathClip(clip); return track;
    }
    @org.junit.jupiter.api.AfterEach void clearControllerFixtures() throws Exception {
        CameraRuntime.getInstance().reset();
        CameraRuntime.getInstance().updateCurrentSample(CameraSample.EMPTY);
        TimelineCameraController.getInstance().init(null);
        com.director.client.camera.CameraRenderOverride.clear();
    }
    @Test void switchingTracksResamplesStaticPreview() throws Exception {
        var a=shot("A",0); var b=shot("B",100);
        var controller=TimelineCameraController.getInstance(); controller.init(model(a,b));
        var first=a.getCameraPathClips().getFirst();
        var old=((CustomPathClip)first).sampleCamera(1);
        controller.previewAt(1);
        controller.setActiveCameraTrack(b); controller.updateOwnerAndSample(.016f);
        assertEquals(b,controller.getActiveCameraTrack());
        assertEquals(100,controller.getCurrentSample().position().x);
    }
    @Test void gapAndDisabledClipReleaseTimelineOwner() throws Exception {
        var track=shot("A",100); var clip=track.getCameraPathClips().getFirst();
        clip.setStartTime(1); clip.setEndTime(2);
        var model=model(track); model.setCurrentTime(0); model.setPlaying(true);
        var controller=TimelineCameraController.getInstance(); controller.init(model);
        CameraRuntime.getInstance().setOwner(CameraRuntime.Owner.TIMELINE);
        var old=((CustomPathClip)clip).sampleCamera(1);
        CameraRuntime.getInstance().applyTimelineSample(old);
        controller.updateOwnerAndSample(.016f);
        assertTrue(CameraRuntime.getInstance().isPlayerOwner());
        assertSame(old,controller.getCurrentSample());
        clip.setEnabled(false); model.setCurrentTime(1.5f);
        controller.updateOwnerAndSample(.016f);
        assertTrue(CameraRuntime.getInstance().isPlayerOwner());
        assertSame(old,controller.getCurrentSample());
    }
    @Test void directPropertyPreviewAppliesEditedKey() throws Exception {
        var controller=TimelineCameraController.getInstance();
        var runtime=CameraRuntime.getInstance();
        controller.init(model(shot("A",0)));
        controller.previewKeyframeDirect(key(0,200));
        assertEquals(200,runtime.getCurrentSample().position().x);
        assertTrue(runtime.isTimelineOwner());
    }
    @Test void detachingModelReleasesTimelineOwner() throws Exception {
        var controller=TimelineCameraController.getInstance(); controller.init(model(shot("A",100)));
        CameraRuntime.getInstance().setOwner(CameraRuntime.Owner.TIMELINE);
        controller.init(null); controller.updateOwnerAndSample(.016f);
        assertTrue(CameraRuntime.getInstance().isPlayerOwner());
    }

    @Test void emptyExportTrackReleasesOwnerInsteadOfExportingOldShot() {
        var controller=TimelineCameraController.getInstance(); controller.init(model(shot("A",100)));
        controller.previewAt(1);
        assertTrue(CameraRuntime.getInstance().isTimelineOwner());
        controller.applySampleForExport(6);
        assertTrue(CameraRuntime.getInstance().isPlayerOwner());
    }
    @Test void playerExportReleasesStaticPreviewWithoutMovingPlayer() {
        var controller=TimelineCameraController.getInstance(); controller.init(model(shot("A",100)));
        controller.previewAt(1);
        controller.preparePlayerCameraForExport();
        assertTrue(CameraRuntime.getInstance().isPlayerOwner());
        controller.updateOwnerAndSample(.016f);
        assertTrue(CameraRuntime.getInstance().isPlayerOwner());
    }
    @Test void playbackStartsInGapWithoutTakingOwner() {
        var track=shot("A",100); track.getCameraPathClips().getFirst().setStartTime(1);
        var model=model(track); model.setCurrentTime(0); model.setPlaying(true);
        var controller=TimelineCameraController.getInstance(); controller.init(model);
        controller.startPlayback();
        assertTrue(CameraRuntime.getInstance().isPlayerOwner());
    }
    @Test void zeroDurationClipNeverTakesCamera() {
        var clip=new CustomPathClip("empty",1,1);
        assertFalse(clip.isActiveAt(1));
    }
    @Test void backwardSeekAndRepeatedSamplingAreStable() {
        var sampler=dollyChain();
        var target=sampler.sample(2.5f).orElseThrow();
        sampler.sample(.5f); sampler.sample(1.5f);
        assertEquals(target,sampler.sample(2.5f).orElseThrow());
        assertEquals(target,sampler.sample(2.5f).orElseThrow());
    }
    @Test void disablingActiveTrackBindingClearsStalePathReference() {
        var controller=TimelineCameraController.getInstance(); controller.init(model(shot("A",100)));
        controller.previewAt(1); controller.setActiveCameraTrack(null);
        controller.updateOwnerAndSample(.016f);
        assertTrue(CameraRuntime.getInstance().isPlayerOwner());
    }

    @Test void exportRestoresOriginalTrackTimeAndPreview() {
        var a=shot("A",10); var b=shot("B",100);
        var model=model(a,b); model.setCurrentTime(1);
        var controller=TimelineCameraController.getInstance(); controller.init(model); controller.previewAt(1);
        var saved=controller.saveStateForExport();
        controller.setActiveCameraTrack(b); model.setCurrentTime(3); controller.applySampleForExport(3);
        assertEquals(100,controller.getCurrentSample().position().x);
        controller.restoreStateAfterExport(saved);
        assertSame(a,controller.getActiveCameraTrack()); assertEquals(1,model.getCurrentTime());
        assertEquals(10,controller.getCurrentSample().position().x);
        assertTrue(CameraRuntime.getInstance().isTimelineOwner());
    }
    @Test void exportRestoresPlayingState() {
        var model=model(shot("A",10)); model.setCurrentTime(1); model.setPlaying(true);
        var controller=TimelineCameraController.getInstance(); controller.init(model);
        var saved=controller.saveStateForExport(); assertFalse(model.isPlaying());
        model.setCurrentTime(3); controller.applySampleForExport(3);
        controller.restoreStateAfterExport(saved);
        assertTrue(model.isPlaying()); assertEquals(1,model.getCurrentTime());
    }
    @Test void exportCleanupDoesNotRestoreIntoDifferentProject() {
        var controller=TimelineCameraController.getInstance(); controller.init(model(shot("A",10)));
        var saved=controller.saveStateForExport();
        var next=model(shot("B",100)); next.setCurrentTime(2); controller.init(next);
        controller.restoreStateAfterExport(saved);
        assertEquals(2,next.getCurrentTime());
        assertEquals("B",controller.getActiveCameraTrack().getName());
    }
    @Test void repeatingModelAndTrackBindingDoesNotDiscardEditedPreview() {
        var track=shot("A",10); var model=model(track);
        var controller=TimelineCameraController.getInstance(); controller.init(model);
        controller.previewKeyframeDirect(key(0,200));
        controller.init(model); controller.setActiveCameraTrack(track); controller.updateOwnerAndSample(.016f);
        assertEquals(200,controller.getCurrentSample().position().x);
    }
    @Test void relativeCraneAfterDollyResolvesSameEntryOnSeek() {
        var track=new TimelineTrack("mixed",TrackType.CAMERA);
        var dolly=new DollyPathClip("d",0,1); dolly.setDistance(10);
        var crane=new CranePathClip("c",1,2); crane.setHeightDelta(10);
        track.addCameraPathClip(dolly); track.addCameraPathClip(crane);
        var direct=new CameraTrackSampler(); direct.setCameraTrack(track); direct.setKeyframes(List.of(key(0,0)));
        var jumped=direct.sample(1.5f).orElseThrow();
        var sequential=new CameraTrackSampler(); sequential.setCameraTrack(track); sequential.setKeyframes(List.of(key(0,0)));
        sequential.sample(.5f);
        assertEquals(jumped,sequential.sample(1.5f).orElseThrow());
        assertEquals(10,jumped.position().z,1e-5); assertEquals(5,jumped.position().y,1e-5);
    }

    @Test void closingPlayingTimelineDoesNotReacquirePreviewOnReopen() {
        var first=model(shot("A",10));
        var controller=TimelineCameraController.getInstance(); controller.init(first); first.setPlaying(true);
        controller.onTimelineUiClosed(); controller.updateOwnerAndSample(.016f);
        assertTrue(CameraRuntime.getInstance().isPlayerOwner());
        assertFalse(controller.hasOverride());
        var next=model(shot("B",100)); controller.init(next); controller.updateOwnerAndSample(.016f);
        assertTrue(CameraRuntime.getInstance().isPlayerOwner());
    }
    @Test void historicalSnapshotDoesNotEnterOriginalPathsOrChangeCameraOwner() {
        var track=new TimelineTrack("query",TrackType.CAMERA);
        var dolly=new DollyPathClip("Dolly",0,2);dolly.setDistance(10);track.addCameraPathClip(dolly);
        var sampler=new CameraTrackSampler();sampler.setCameraTrack(track);
        var frames=List.of(key(0,0));sampler.setKeyframes(frames);
        var owner=CameraRuntime.getInstance().isTimelineOwner();var current=CameraRuntime.getInstance().getCurrentSample();
        var before=dolly.getStartPosition();
        var a=sampler.sampleSnapshot(track,frames,1).orElseThrow();
        assertEquals(5,a.position().z,1e-5);assertEquals(before,dolly.getStartPosition());
        sampler.sampleSnapshot(track,frames,.2f);assertEquals(a,sampler.sampleSnapshot(track,frames,1).orElseThrow());
        assertEquals(owner,CameraRuntime.getInstance().isTimelineOwner());assertEquals(current,CameraRuntime.getInstance().getCurrentSample());
        assertEquals(a,sampler.sample(1).orElseThrow());
    }
    @Test void controllerSnapshotLeavesPlayheadPreviewAndBindingUnchanged() {
        var track=shot("A",10);var model=model(track);model.setCurrentTime(1);
        var controller=TimelineCameraController.getInstance();controller.init(model);controller.previewAt(1);
        var current=controller.getCurrentSample();var owner=CameraRuntime.getInstance().isTimelineOwner();
        assertEquals(10,controller.sampleSnapshot(3).orElseThrow().position().x);
        assertEquals(1,model.getCurrentTime());assertSame(track,controller.getActiveCameraTrack());
        assertEquals(current,controller.getCurrentSample());assertEquals(owner,CameraRuntime.getInstance().isTimelineOwner());
        track.setMuted(true);assertTrue(controller.sampleSnapshot(3).isEmpty());
    }
    @Test void historyWithoutRelativeEntryAnchorNeverReadsCurrentCamera() {
        var track=new TimelineTrack("relative",TrackType.CAMERA);track.addCameraPathClip(new DollyPathClip("Dolly",0,2));
        var sampler=new CameraTrackSampler();sampler.setCameraTrack(track);
        assertTrue(sampler.sampleSnapshot(track,List.of(),1).isEmpty());
        assertTrue(sampler.sampleSnapshot(track,List.of(),Float.NaN).isEmpty());
    }
    @Test void explicitlyAnchoredCameraPathCanBeQueriedWithoutKeyframes() {
        var track=new TimelineTrack("fixed",TrackType.CAMERA);var dolly=new DollyPathClip("Dolly",0,2);
        dolly.setStartPosition(new Vec3d(10,20,30));dolly.setDollyDirection(new Vec3d(0,0,1));
        dolly.setAlignStartToCurrentPosition(false);dolly.setDistance(8);track.addCameraPathClip(dolly);
        var sampler=new CameraTrackSampler();
        var at=sampler.sampleSnapshot(track,List.of(),1).orElseThrow();
        assertEquals(new Vec3d(10,20,34),at.position());
        assertEquals(new Vec3d(10,20,30),dolly.getStartPosition());
    }
}
