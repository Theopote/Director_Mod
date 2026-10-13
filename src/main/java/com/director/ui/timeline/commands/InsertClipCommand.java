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
    public InsertClipCommand(TimelineModel model, TimelineTrack track, TimelineClip clip, List<AudioEvent> events, float delta) {
        this(model, track, clip, destination -> TimelineAudioService.getInstance().insertClipEvents(destination, track, events, delta));
    }
    public InsertClipCommand(TimelineModel model, TimelineTrack track, TimelineClip clip, java.util.function.Consumer<TimelineClip> initializeAudio) {
        super(model, "clip_added"); this.track = track; this.clip = clip; this.initializeAudio = initializeAudio;
    }
    @Override protected void doExecute() {
        var audio = TimelineAudioService.getInstance();
        boolean previous = audio.isSuppressAutoCreate();
        audio.setSuppressAutoCreate(true);
        try { added = track.addClip(clip); } finally { audio.setSuppressAutoCreate(previous); }
        if (!added) return;
        if (clip instanceof EntityTimelineClip entity) track.getOrCreateActorTrackPayload().addClip(entity.getEntityClip());
        if (restoreAudio != null) restoreAudio.run();
        else initializeAudio.accept(clip);
    }
    @Override protected void doUndo() {
        if (!added) return;
        restoreAudio = TimelineAudioService.getInstance().captureRemovalState(clip);
        TimelineAudioService.getInstance().onClipRemoved(clip);
        track.removeClip(clip);
        if (clip instanceof EntityTimelineClip entity) track.getOrCreateActorTrackPayload().removeClip(entity.getEntityClip().getId());
    }
    @Override public boolean canUndo() { return added; }
    @Override public String getDescription() { return "添加片段: " + clip.getLabel(); }
}
