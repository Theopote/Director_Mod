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
    private Runnable rollbackAudio = () -> {};
    private Runnable pendingUndoAudio = () -> {};
    private boolean added;
    private boolean executeMayMutate;
    private boolean entityAdded;
    private boolean lightChanged;
    private com.director.lighting.PointLight previousLight;
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
        executeMayMutate = false;
        if (track.getClips().contains(clip) || clip.getParentTrack() != null) {
            throw new IllegalStateException("Clip is already attached to a track");
        }
        added = false;
        entityAdded = false;
        lightChanged = false;
        previousLight = null;
        if(clip instanceof com.director.lighting.LightTimelineClip light) {
            var current=model.getPointLight(light.lightId());
            previousLight = current;
            if(current!=null) lightAsset=current;
            else if(lightAsset==null) return;
        }
        var audio = TimelineAudioService.getInstance();
        executeMayMutate = true;
        rollbackAudio = audio.captureRemovalState(clip);
        boolean previous = audio.isSuppressAutoCreate();
        audio.setSuppressAutoCreate(true);
        try { added = track.addClip(clip); } finally { audio.setSuppressAutoCreate(previous); }
        if (!added) {
            if (restoreAudio != null) {
                throw new IllegalStateException("Track rejected clip redo: " + clip.getLabel());
            }
            return;
        }
        if(clip instanceof com.director.lighting.LightTimelineClip light && model.getPointLight(light.lightId())==null) {
            lightChanged = true;
            model.replacePointLight(light.lightId(),lightAsset);
        }
        if (clip instanceof EntityTimelineClip entity) {
            track.getOrCreateActorTrackPayload().addClip(entity.getEntityClip());
            entityAdded = true;
        }
        if (restoreAudio != null) restoreAudio.run();
        else initializeAudio.accept(clip);
    }
    @Override protected void doUndo() {
        if (!added) return;
        var audio = TimelineAudioService.getInstance();
        pendingUndoAudio = audio.captureRemovalState(clip);
        audio.onClipRemoved(clip);
        track.removeClip(clip);
        if(clip instanceof com.director.lighting.LightTimelineClip light) {
            var removed=com.director.lighting.LightLifecycle.removeUnreferenced(model,List.of(light.lightId()));
            if(!removed.isEmpty()) lightAsset=removed.getFirst();
        }
        if (clip instanceof EntityTimelineClip entity) track.getOrCreateActorTrackPayload().removeClip(entity.getEntityClip().getId());
        restoreAudio = pendingUndoAudio;
        rollbackAudio = pendingUndoAudio;
        added = false;
        executeMayMutate = false;
        entityAdded = false;
        lightChanged = false;
    }

    @Override protected void compensateExecute() {
        if (!executeMayMutate) {
            return;
        }
        var audio = TimelineAudioService.getInstance();
        if (track.getClips().contains(clip)) {
            audio.onClipRemoved(clip);
            track.removeClip(clip);
        } else {
            audio.onClipRemoved(clip);
        }
        if (entityAdded && clip instanceof EntityTimelineClip entity && track.getActorTrackPayload() != null) {
            track.getActorTrackPayload().removeClip(entity.getEntityClip().getId());
        }
        if (lightChanged && clip instanceof com.director.lighting.LightTimelineClip light) {
            model.replacePointLight(light.lightId(), previousLight);
        }
        rollbackAudio.run();
        added = false;
        executeMayMutate = false;
        entityAdded = false;
        lightChanged = false;
    }

    @Override protected void compensateUndo() {
        var audio = TimelineAudioService.getInstance();
        boolean previous = audio.isSuppressAutoCreate();
        audio.setSuppressAutoCreate(true);
        try {
            if (!track.getClips().contains(clip) && !track.addClip(clip)) {
                throw new IllegalStateException("Track rejected clip restoration");
            }
        } finally {
            audio.setSuppressAutoCreate(previous);
        }
        if (clip instanceof EntityTimelineClip entity) {
            track.getOrCreateActorTrackPayload().addClip(entity.getEntityClip());
        }
        if(clip instanceof com.director.lighting.LightTimelineClip light) {
            com.director.lighting.LightLifecycle.restore(model,
                    lightAsset != null ? List.of(lightAsset) : List.of());
        }
        pendingUndoAudio.run();
        added = true;
        entityAdded = clip instanceof EntityTimelineClip;
    }
    @Override public boolean canUndo() { return added; }
    @Override public String getDescription() { return "添加片段: " + clip.getLabel(); }
}
