package com.director.timeline.playback;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineViewportState;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TimelineSamplingContextTest {
    @Test void defaultsToPreviewAndNestedScopesRestoreTheirParent() {
        assertSame(TimelineSamplingContext.PREVIEW, TimelineSamplingContext.current());
        try (var export = TimelineSamplingContext.open(TimelineSamplingContext.EXPORT)) {
            assertSame(TimelineSamplingContext.EXPORT, TimelineSamplingContext.current());
            try (var headless = TimelineSamplingContext.open(TimelineSamplingContext.HEADLESS)) {
                assertSame(TimelineSamplingContext.HEADLESS, TimelineSamplingContext.current());
            }
            assertSame(TimelineSamplingContext.EXPORT, TimelineSamplingContext.current());
            export.close();
            export.close();
            assertSame(TimelineSamplingContext.PREVIEW, TimelineSamplingContext.current());
        }
        assertSame(TimelineSamplingContext.PREVIEW, TimelineSamplingContext.current());
    }

    @Test void exceptionalExitRestoresThePreviousContext() {
        assertThrows(IllegalStateException.class, () -> TimelineSamplingContext.run(
                TimelineSamplingContext.EXPORT, () -> {
                    assertSame(TimelineSamplingContext.EXPORT, TimelineSamplingContext.current());
                    throw new IllegalStateException("expected");
                }));
        assertSame(TimelineSamplingContext.PREVIEW, TimelineSamplingContext.current());
    }

    @Test void headlessSamplingSuspendsAndRestoresIndependentPlaybackState() {
        var model = new TimelineModel(new TimelineViewportState());
        model.setSuppressPersistenceWrites(true);
        model.setDuration(30f);
        model.setCurrentTime(6.5f);
        model.setPlaying(true);

        var saved = TimelinePlaybackSampler.HeadlessPlaybackState.captureAndSuspend(model);
        assertFalse(model.isPlaying());
        assertFalse(model.isScrubbing());
        model.setCurrentTime(24f);
        saved.restore(model);

        assertEquals(6.5f, model.getCurrentTime(), 0.0001f);
        assertTrue(model.isPlaying());
        assertFalse(model.isScrubbing());
    }

    @Test void exportAndHeadlessPoliciesFailFastWhilePreviewRecovers() {
        var previewAttempts = new AtomicInteger();
        TimelineSamplingPolicy.PREVIEW.run("test", 1f, () -> {
            previewAttempts.incrementAndGet();
            throw new IllegalArgumentException("expected");
        });
        assertEquals(1, previewAttempts.get());

        for (var policy : new TimelineSamplingPolicy[]{
                TimelineSamplingPolicy.EXPORT, TimelineSamplingPolicy.HEADLESS}) {
            var failure = assertThrows(IllegalStateException.class,
                    () -> policy.run("test", 1f, () -> {
                        throw new IllegalArgumentException("expected");
                    }));
            assertInstanceOf(IllegalArgumentException.class, failure.getCause());
            assertThrows(CancellationException.class,
                    () -> policy.run("test", 1f, () -> {
                        throw new CancellationException("expected");
                    }));
        }
    }
}
