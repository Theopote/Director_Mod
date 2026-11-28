package com.director.export.video;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineViewportState;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.*;

class VideoExportPlaybackStateTest {
    private TimelineModel model() {
        var model = new TimelineModel(new TimelineViewportState());
        model.setSuppressPersistenceWrites(true);
        model.setDuration(30f);
        model.setCurrentTime(4.25f);
        return model;
    }

    @Test void successfulExportRestoresOriginalPlayingStateAndPlayhead() {
        var model = model();
        model.setPlaying(true);
        var saved = VideoExportPlaybackState.capture(model);
        saved.suspend();
        assertFalse(model.isPlaying());
        assertFalse(model.isScrubbing());

        model.setCurrentTime(27f);
        saved.restore();
        saved.restore();
        assertEquals(4.25f, model.getCurrentTime(), 0.0001f);
        assertTrue(model.isPlaying());
        assertFalse(model.isScrubbing());
    }

    @Test void canceledExportRestoresScrubbingStateInFinally() {
        var model = model();
        model.setScrubbing(true);
        var saved = VideoExportPlaybackState.capture(model);
        saved.suspend();
        try {
            model.setCurrentTime(18f);
            throw new CancellationException("expected");
        } catch (CancellationException expected) {
            // Mirrors VideoExportManager's common finally restoration path.
        } finally {
            saved.restore();
        }
        assertEquals(4.25f, model.getCurrentTime(), 0.0001f);
        assertFalse(model.isPlaying());
        assertTrue(model.isScrubbing());
    }

    @Test void failedExportRestoresPlaybackEvenWhenSamplingThrows() {
        var model = model();
        model.setPlaying(true);
        var saved = VideoExportPlaybackState.capture(model);
        saved.suspend();
        try {
            model.setCurrentTime(22f);
            throw new IllegalStateException("expected");
        } catch (IllegalStateException expected) {
            // Mirrors VideoExportManager's common finally restoration path.
        } finally {
            saved.restore();
        }
        assertEquals(4.25f, model.getCurrentTime(), 0.0001f);
        assertTrue(model.isPlaying());
        assertFalse(model.isScrubbing());
    }

    @Test void missingModelHasNoPlaybackStateToRestore() {
        assertNull(VideoExportPlaybackState.capture(null));
    }
}
