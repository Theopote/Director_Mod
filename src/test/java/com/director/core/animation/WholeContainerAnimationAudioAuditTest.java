package com.director.core.animation;

import com.director.timeline.model.*;
import com.director.timeline.model.audio.*;
import com.director.ui.timeline.audio.TimelineAudioService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WholeContainerAnimationAudioAuditTest {
    private TimelineAudioService service(TimelineClip clip, AudioEvent event) throws Exception {
        var ctor = TimelineAudioService.class.getDeclaredConstructor(); ctor.setAccessible(true);
        var service = ctor.newInstance(); var model = new TimelineModel(new TimelineViewportState());
        model.setSuppressPersistenceWrites(true); var blocks = new TimelineTrack("blocks", TrackType.CONTAINER);
        blocks.addClip(clip); model.addTrack(blocks); var audio = new AudioTrack("audio");
        audio.addEvent(event); model.addTrack(audio); service.setTimelineModel(model);
        var link = TimelineAudioService.class.getDeclaredMethod("linkEventToClip", TimelineClip.class, AudioEvent.class);
        link.setAccessible(true); link.invoke(service, clip, event); return service;
    }
    private TimelineClip clip() {
        var clip = new TimelineClip("assembly", 10, 20, 0);
        clip.getAnimationConfig().setScope(ClipAnimationConfig.AnimationScope.WHOLE_CONTAINER);
        clip.getAnimationConfig().setDuration(2); return clip;
    }
    @Test void resizingUsesHandoffInsteadOfScalingSoundWithTheClip() throws Exception {
        var clip = clip(); float time = AnimationSoundCollector.wholeContainerEventTime(clip);
        var event = new AudioEvent("assembly", "minecraft:block.stone.place", time);
        var service = service(clip, event);
        clip.setLength(5); service.onClipTimeRangeChanged(clip, 10, 20, 10, 15);
        assertEquals(time, event.getTime(), 0.00001f);
        service.onClipTimeRangeChanged(clip, 10, 15, 20, 25);
        assertEquals(time + 10, event.getTime(), 0.00001f);
    }
    @Test void resizePreviewIsPureAndUsesTheSameHandoffClock() throws Exception {
        var clip = clip(); float time = AnimationSoundCollector.wholeContainerEventTime(clip);
        var event = new AudioEvent("assembly", "minecraft:block.stone.place", time);
        var service = service(clip, event); service.beginClipTimeRangePreview(clip, 10, 20);
        service.updateClipTimeRangePreview(clip, 10, 15);
        assertEquals(time, service.getPreviewTimeForEvent(event), 0.00001f); assertEquals(time, event.getTime());
        service.updateClipTimeRangePreview(clip, 20, 25);
        assertEquals(time + 10, service.getPreviewTimeForEvent(event), 0.00001f);
        service.endClipTimeRangePreview(clip); assertEquals(-1, service.getPreviewTimeForEvent(event));
    }
    @Test void changingWholeEffectInvalidatesSoundSignature() throws Exception {
        var ctor = TimelineAudioService.class.getDeclaredConstructor(); ctor.setAccessible(true);
        var service = ctor.newInstance(); var signature = TimelineAudioService.class
                .getDeclaredMethod("computeAutoSoundSignature", TimelineClip.class); signature.setAccessible(true);
        var clip = clip(); var before = signature.invoke(service, clip);
        clip.getAnimationConfig().getWholeContainer().setEffect(WholeContainerAnimationConfig.Effect.INSTANT);
        assertNotEquals(before, signature.invoke(service, clip));
    }
}
