package com.director.lighting;
import com.director.timeline.model.*;
import com.director.ui.timeline.commands.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class LightKeyframeAuthoringTest {
    TimelineModel model;
    TimelineTrack track;
    LightTimelineClip clip;
    PointLight light;
    TimelineCommandHistory history;
    @BeforeEach void setup() {
        model=new TimelineModel(new TimelineViewportState());model.setSuppressPersistenceWrites(true);
        light=PointLight.create(0,0,0);model.replacePointLight(light.id(),light);
        track=new TimelineTrack("Light",TrackType.LIGHT);clip=new LightTimelineClip("L",0,5,light.id());track.addClip(clip);model.addTrack(track);
        history=new TimelineCommandHistory();
    }
    AddKeyframeCommand add(float time) {var command=new AddKeyframeCommand(model,clip,time);history.executeCommand(command);return command;}
    @Test void lightTrackExposesKeyframeButtonCapability() {assertTrue(TrackType.LIGHT.supportsKeyframes());assertTrue(TrackType.LIGHT.supportsClips());}
    @Test void genericAddCreatesIndependentParameterKeysAndContinuousTransition() {
        var first=add(0).getLightKeyframe();first.setValue(light.settings(0,4,1,true));
        var second=add(4).getLightKeyframe();second.setValue(light.at(8,0,0).settings(12,12,3,true));
        assertNotSame(first,second);assertEquals(0,first.value().brightness());assertEquals(12,second.value().brightness());
        var middle=LightTimelineSampler.sample(model,2).getFirst();assertEquals(6,middle.brightness());assertEquals(4,middle.x());assertEquals(8,middle.range());assertEquals(2,middle.falloff());
        assertEquals(light,model.getPointLight(light.id()));assertEquals(2,history.getUndoSize());
    }
    @Test void insertedKeyCapturesTheInterpolatedValueAndKeepsTransition() {
        add(0).getLightKeyframe().setValue(light.settings(0,8,1,true));add(4).getLightKeyframe().setValue(light.settings(12,8,1,true));
        var key=add(2).getLightKeyframe();assertEquals(6,key.value().brightness());assertEquals(3,clip.sample(light,1).brightness());assertEquals(9,clip.sample(light,3).brightness());
    }
    @Test void undoRedoRetainsKeyIdentityParametersAndInterpolation() {
        var key=add(2).getLightKeyframe();key.setValue(light.settings(7,12,2,true));key.setInterpolation(TimelineInterpolation.SMOOTH);
        history.undo();assertTrue(clip.getClipKeyframes().isEmpty());history.redo();assertSame(key,clip.getClipKeyframes().getFirst());
        assertEquals(7,key.value().brightness());assertEquals(TimelineInterpolation.SMOOTH,key.getInterpolation());
    }
    @Test void repeatedAdditionSelectsExistingWithoutAddingHistoryOrReplacingValues() {
        var key=add(2).getLightKeyframe();key.setValue(light.settings(7,8,1,true));var duplicate=add(2.00001f);
        assertFalse(duplicate.canUndo());assertEquals(1,clip.getClipKeyframes().size());assertEquals(1,history.getUndoSize());assertEquals(7,key.value().brightness());
        history.undo();assertTrue(clip.getClipKeyframes().isEmpty());
    }
    @Test void lockedDetachedMissingAssetAndInvalidTimesCannotCreateKeys() {
        track.setLocked(true);assertFalse(add(1).canUndo());track.setLocked(false);
        for(float t:new float[]{-1,6,Float.NaN,Float.POSITIVE_INFINITY}) assertFalse(add(t).canUndo());
        model.removeTrack(track);assertFalse(add(1).canUndo());model.addTrack(track);
        model.replacePointLight(light.id(),null);assertFalse(add(1).canUndo());assertTrue(clip.getClipKeyframes().isEmpty());
    }
}
