package com.director.ui.actor.placement;

import com.director.ui.actor.placement.ActorPlacementController.StartFailure;
import com.director.ui.actor.placement.ActorPlacementController.StartResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActorPlacementStartResultTest {
    @Test
    void successHasNoFailureOrCause() {
        StartResult result = StartResult.succeeded();

        assertTrue(result.success());
        assertNull(result.failure());
        assertNull(result.cause());
    }

    @Test
    void everyFailureHasAStableDistinctTranslationKey() {
        for (StartFailure failure : StartFailure.values()) {
            assertFalse(failure.translationKey().isBlank());
            StartResult result = StartResult.failure(failure);
            assertFalse(result.success());
            assertSame(failure, result.failure());
            assertNull(result.cause());
        }
        assertEquals(StartFailure.values().length,
                Arrays.stream(StartFailure.values()).map(StartFailure::translationKey).distinct().count());
    }

    @Test
    void internalFailureRetainsCauseForDiagnostics() {
        RuntimeException cause = new IllegalStateException("preview failed");

        StartResult result = StartResult.failure(StartFailure.INTERNAL_ERROR, cause);

        assertFalse(result.success());
        assertSame(StartFailure.INTERNAL_ERROR, result.failure());
        assertSame(cause, result.cause());
    }

    @Test
    void inconsistentResultStatesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new StartResult(true, StartFailure.INTERNAL_ERROR, null));
        assertThrows(IllegalArgumentException.class,
                () -> new StartResult(false, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new StartResult(true, null, new IllegalStateException("unexpected")));
    }
}
