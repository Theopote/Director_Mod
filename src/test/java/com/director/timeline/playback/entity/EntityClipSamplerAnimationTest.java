package com.director.timeline.playback.entity;

import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.model.entity.EntityAnimationClip;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityClipSamplerAnimationTest {
    @Test
    void exposesAnimationTimeRelativeToClipStart() {
        ActorTrack track = new ActorTrack("actor");
        EntityAnimationClip clip = new EntityAnimationClip("walk", 2f, 6f);
        clip.setAnimationId("walk");
        track.addClip(clip);

        EntitySample sample = new EntityClipSampler(track).sampleAt(3.25f);

        assertEquals("walk", sample.animationId);
        assertEquals(1.25f, sample.animationLocalTime, 1.0e-5f);
    }
}
