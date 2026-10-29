package com.director.timeline.playback;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;

class TimelineSamplingPolicyTest {
    @Test void exportFailurePreservesCauseAndIdentifiesSubsystemAndTime() {
        var cause = new IllegalArgumentException("missing animation");
        var error = assertThrows(IllegalStateException.class, () ->
                TimelineSamplingPolicy.EXPORT.run("actor", 2.5f, () -> { throw cause; }));
        assertSame(cause, error.getCause());
        assertTrue(error.getMessage().contains("actor"));
        assertTrue(error.getMessage().contains("2.5"));
        assertTrue(error.getMessage().contains("missing animation"));
    }

    @Test void failedExportDoesNotAdvanceToLaterSubsystems() {
        List<String> samples = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> {
            TimelineSamplingPolicy.EXPORT.run("weather", 0, () -> { throw new IllegalStateException(); });
            TimelineSamplingPolicy.EXPORT.run("actor", 0, () -> samples.add("actor"));
        });
        assertTrue(samples.isEmpty());
    }

    @Test void previewCanContinueAfterAnOptionalSubsystemFails() {
        List<String> samples = new ArrayList<>();
        TimelineSamplingPolicy.PREVIEW.run("weather", 0, () -> { throw new IllegalStateException(); });
        TimelineSamplingPolicy.PREVIEW.run("actor", 0, () -> samples.add("actor"));
        assertEquals(List.of("actor"), samples);
    }

    @Test void neitherModeSwallowsCancellation() {
        for (var policy : TimelineSamplingPolicy.values()) {
            var cancellation = new CancellationException();
            assertSame(cancellation, assertThrows(CancellationException.class,
                    () -> policy.run("actor", 0, () -> { throw cancellation; })));
        }
    }
}
