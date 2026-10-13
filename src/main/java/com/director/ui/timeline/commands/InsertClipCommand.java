package com.director.ui.timeline.commands;

import com.director.timeline.model.*;
import com.director.timeline.model.audio.AudioEvent;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.ui.timeline.audio.TimelineAudioService;
import java.util.List;

public final class InsertClipCommand extends BaseTimelineCommand {
    private final TimelineTrack track;
    private final TimelineClip clip;
    private final java.util.function.Consumer<TimelineClip> initializeAudio;
    private Runnable restoreAudio;
    private boolean added;
    private com.director.lighting.PointLight lightAsset;
    public InsertClipCommand(TimelineModel model, TimelineTrack track, TimelineClip clip,
            java.util.function.Consumer<TimelineClip> initializeAudio, com.director.lighting.PointLight lightAsset) {
        this(model,track,clip,initializeAudio);
        if(clip instanceof com.director.lighting.LightTimelineClip light && lightAsset!=null && !light.lightId().equals(lightAsset.id()))
            throw new IllegalArgumentException("Clipboard light ID mismatch");
        this.lightAsset=lightAsset;
    }
    public InsertClipCommand(TimelineModel model, TimelineTrack track, TimelineClip clip, List<AudioEvent> events, float delta) {
        this(model, track, clip, destination -> TimelineAudioService.getInstance().insertClipEvents(destination, track, events, delta));
    }
    public InsertClipCommand(TimelineModel model, TimelineTrack track, TimelineClip clip, java.util.function.Consumer<TimelineClip> initializeAudio) {
        super(model, "clip_added"); this.track = track; this.clip = clip; this.initializeAudio = initializeAudio;
    }
    @Override protected void doExecute() {
        if(clip instanceof com.director.lighting.LightTimelineClip light) {
            var current=model.getPointLight(light.lightId());
            if(current!=null) lightAsset=current;
            else if(lightAsset==null) {added=false;return;}
        }
        var audio = TimelineAudioService.getInstance();
        boolean previous = audio.isSuppressAutoCreate();
        audio.setSuppressAutoCreate(true);
        try { added = track.addClip(clip); } finally { audio.setSuppressAutoCreate(previous); }
        if (!added) return;
        if(clip instanceof com.director.lighting.LightTimelineClip light && model.getPointLight(light.lightId())==null)
            model.replacePointLight(light.lightId(),lightAsset);
        if (clip instanceof EntityTimelineClip entity) track.getOrCreateActorTrackPayload().addClip(entity.getEntityClip());
        if (restoreAudio != null) restoreAudio.run();
        else initializeAudio.accept(clip);
    }
    @Override protected void doUndo() {
        if (!added) return;
        restoreAudio = TimelineAudioService.getInstance().captureRemovalState(clip);
        TimelineAudioService.getInstance().onClipRemoved(clip);
        track.removeClip(clip);
        if(clip instanceof com.director.lighting.LightTimelineClip light) {
            var removed=com.director.lighting.LightLifecycle.removeUnreferenced(model,List.of(light.lightId()));
            if(!removed.isEmpty()) lightAsset=removed.getFirst();
        }
        if (clip instanceof EntityTimelineClip entity) track.getOrCreateActorTrackPayload().removeClip(entity.getEntityClip().getId());
    }
    @Override public boolean canUndo() { return added; }
    @Override public String getDescription() { return "添加片段: " + clip.getLabel(); }
}
