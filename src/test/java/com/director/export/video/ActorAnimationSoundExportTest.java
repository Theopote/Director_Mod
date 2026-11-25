package com.director.export.video;

import com.director.actor.animation.asset.AnimationAsset;
import com.director.actor.animation.asset.AnimationAssetLibrary;
import com.director.actor.animation.asset.AnimationLoopMode;
import com.director.actor.rig.skeleton.ArticulatedPlayerRig;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TimelineViewportState;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.model.entity.AnimationEvent;
import com.director.timeline.model.entity.EntityActionClip;
import com.director.timeline.model.entity.EntityAnimationClip;
import com.director.timeline.model.entity.EntitySpawnClip;
import com.director.timeline.playback.entity.EntityActionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActorAnimationSoundExportTest {
    private static final String ASSET_ID = "test:export-play-sound";

    @BeforeEach
    void setup() {
        AnimationAssetLibrary.getInstance().register(
                new AnimationAsset(ASSET_ID, "Cycle", ArticulatedPlayerRig.DEFINITION, 1, AnimationLoopMode.ONCE));
    }

    @AfterEach
    void cleanup() {
        AnimationAssetLibrary.getInstance().remove(ASSET_ID);
    }

    @Test
    void collectsPlaySoundFromAnimationClip() {
        var model = new TimelineModel(new TimelineViewportState());
        model.setSuppressPersistenceWrites(true);
        var track = new TimelineTrack("Actor", TrackType.ACTOR);
        var payload = new ActorTrack();
        var clip = new EntityAnimationClip("Anim", 0, 2);
        clip.setAnimationId(ASSET_ID);
        clip.setLoop(false);
        clip.addEvent(new AnimationEvent(.5f, new EntityActionEvent(EntityActionClip.ActionType.PLAY_SOUND,
                Map.of("soundId", "minecraft:entity.player.attack.sweep", "volume", .6f, "pitch", 1.2f)),
                AnimationEvent.Timing.CLIP));
        payload.addClip(clip);
        track.setActorTrackPayload(payload);
        model.addTrack(track);

        var cues = ActorAnimationSoundExport.collect(model, 0, 2);
        assertEquals(1, cues.size());
        assertEquals(1f, cues.getFirst().timeSec(), 1e-4);
        assertEquals("minecraft:entity.player.attack.sweep", cues.getFirst().soundId());
        assertEquals(.6f, cues.getFirst().volume(), 1e-4);
        assertEquals(1.2f, cues.getFirst().pitch(), 1e-4);
    }

    @Test
    void collectsPlaySoundFromSpawnBoneAnimation() {
        var model = new TimelineModel(new TimelineViewportState());
        model.setSuppressPersistenceWrites(true);
        var track = new TimelineTrack("Actor", TrackType.ACTOR);
        var payload = new ActorTrack();
        var spawn = new EntitySpawnClip("Spawn", 0, 4);
        spawn.setBoneAnimationId(ASSET_ID);
        var bone = spawn.getBoneAnimation();
        assertTrue(bone != null);
        bone.addEvent(new AnimationEvent(.25f, new EntityActionEvent(EntityActionClip.ActionType.PLAY_SOUND,
                Map.of("soundId", "minecraft:block.note_block.hat", "volume", 1f, "pitch", 1f)),
                AnimationEvent.Timing.CLIP));
        payload.addClip(spawn);
        track.setActorTrackPayload(payload);
        model.addTrack(track);

        var cues = ActorAnimationSoundExport.collect(model, 0, 4);
        assertEquals(1, cues.size());
        assertEquals(spawn.getStartTime() + .25f * spawn.getLength(), cues.getFirst().timeSec(), 1e-3);
        assertEquals("minecraft:block.note_block.hat", cues.getFirst().soundId());
    }

    @Test
    void ignoresNonSoundEventsAndMutedTracks() {
        var model = new TimelineModel(new TimelineViewportState());
        model.setSuppressPersistenceWrites(true);
        var track = new TimelineTrack("Actor", TrackType.ACTOR);
        track.setMuted(true);
        var payload = new ActorTrack();
        var clip = new EntityAnimationClip("Anim", 0, 2);
        clip.setAnimationId(ASSET_ID);
        clip.addEvent(new AnimationEvent(.5f, new EntityActionEvent(EntityActionClip.ActionType.PLAY_SOUND,
                Map.of("soundId", "minecraft:entity.player.attack.sweep")), AnimationEvent.Timing.CLIP));
        payload.addClip(clip);
        track.setActorTrackPayload(payload);
        model.addTrack(track);

        assertTrue(ActorAnimationSoundExport.collect(model, 0, 2).isEmpty());
    }
}
