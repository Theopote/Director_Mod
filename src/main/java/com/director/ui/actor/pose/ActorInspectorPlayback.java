package com.director.ui.actor.pose;

import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.playback.entity.EntityClipSampler;
import com.director.timeline.playback.entity.EntitySample;

/** Bounded inspector sampler; always resamples edits, never shares playback event state. */
public final class ActorInspectorPlayback {
    private ActorTrack owner;
    private EntityClipSampler sampler;
    public EntitySample sample(ActorTrack track,float time) {
        if(track==null) { clear(); return null; }
        if(owner!=track) { owner=track; sampler=new EntityClipSampler(track); }
        return sampler.sampleSnapshot(time);
    }
    public void clear() { owner=null; sampler=null; }
}
