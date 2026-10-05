package com.director.ui.timeline.render;

import com.director.actor.*;
import com.director.actor.Actor;
import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.actor.pose.*;
import com.director.ui.timeline.commands.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ActorKeyframeShortcutTest {
    private TimelineModel model;
    private TimelineTrack track;
    private EntityAnimationClip clip;
    private AnimationAsset asset;
    private ActorBoneTimelineRows rows;
    private TimelineCommandHistory history;
    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(null); ActorRegistryManager.setRegistry(new ActorRegistry());
        var actor=new Actor("Player",ActorType.PLAYER); ActorRegistryManager.getInstance().registerActor(actor);
        model=new TimelineModel(new TimelineViewportState()); model.setCurrentTime(2);
        track=new TimelineTrack("Actor",TrackType.ACTOR); var payload=new ActorTrack();
        payload.setActorRef(new ActorRef(actor.getActorId())); track.setActorTrackPayload(payload); model.addTrack(track);
        asset=new AnimationAsset("test:shortcut","Keys",HumanoidRig.DEFINITION,5,AnimationLoopMode.ONCE);
        AnimationAssetLibrary.getInstance().register(asset);
        clip=new EntityAnimationClip("Animation",0,10); clip.setAnimationId(asset.id()); payload.addClip(clip);
        track.addClip(new EntityTimelineClip(clip,payload.getColorRgba())); model.setSelectedClip(clip.getTimelineClip());
        rows=new ActorBoneTimelineRows(); history=new TimelineCommandHistory(); TimelineCommandBridge.bind(history);
        asset.getOrCreateBoneTrack("root").translation().put(1,new Vector3f(2),InterpolationMode.LINEAR);
        ActorKeyframeClipboard.getInstance().copy(asset,List.of(new ActorAnimationAuthoringService.Key("root",ActorAnimationAuthoringService.Channel.TRANSLATION,1)));
    }
    @AfterEach void cleanup() {
        TimelineCommandBridge.bind(null); ActorRegistryManager.setRegistry(null); AnimationAssetLibrary.getInstance().remove(asset.id());
    }
    @Test void focusedPasteThenDeleteTargetsKeysAndLeavesClipIntact() {
        rows.focusReference(track,clip,asset);
        assertTrue(rows.handleKeyCommand(model,"paste")); assertEquals(1,history.getUndoSize());
        assertTrue(asset.findBoneTrack("root").orElseThrow().translation().findAt(2).isPresent());
        assertTrue(rows.handleKeyCommand(model,"delete")); assertEquals(2,history.getUndoSize());
        assertTrue(track.getClips().contains(clip.getTimelineClip())); assertTrue(track.getActorTrackPayload().getClips().contains(clip));
        assertTrue(history.undo()); assertTrue(asset.findBoneTrack("root").orElseThrow().translation().findAt(2).isPresent());
    }
    @Test void rejectedAndEmptyKeyCommandsConsumeWithoutClipFallback() {
        rows.focusReference(track,clip,asset); track.setLocked(true);
        assertTrue(rows.handleKeyCommand(model,"delete")); assertTrue(rows.handleKeyCommand(model,"paste"));
        track.setLocked(false); assertTrue(rows.handleKeyCommand(model,"delete"));
        assertEquals(0,history.getUndoSize()); assertTrue(track.getClips().contains(clip.getTimelineClip()));
    }
    @Test void noKeyFocusOrChangedClipFallsThroughToNormalRouting() {
        assertFalse(rows.handleKeyCommand(model,"delete")); rows.focusReference(track,clip,asset);
        model.selectItem(track,false); assertFalse(rows.handleKeyCommand(model,"delete"));
    }
    @Test void cutCopiesKeysAndDeletesThemWithOneUndo() {
        rows.focusReference(track,clip,asset); assertTrue(rows.handleKeyCommand(model,"paste"));
        int before=history.getUndoSize(); assertTrue(rows.handleKeyCommand(model,"cut"));
        assertEquals(before+1,history.getUndoSize());
        assertEquals(new Vector3f(2),ActorKeyframeClipboard.getInstance().at(asset,4,5).getFirst().value());
        assertTrue(asset.findBoneTrack("root").orElseThrow().translation().findAt(2).isEmpty());
        assertTrue(history.undo()); assertTrue(asset.findBoneTrack("root").orElseThrow().translation().findAt(2).isPresent());
        assertTrue(track.getClips().contains(clip.getTimelineClip()));
    }

}
