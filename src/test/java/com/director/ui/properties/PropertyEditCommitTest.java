package com.director.ui.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyEditCommitTest {
    private final PropertyEditCommit edit = new PropertyEditCommit();

    @Test
    void releasingAnUnchangedControlDoesNotCommit() {
        edit.beginFrame();
        edit.observe(false, true);
        assertFalse(edit.endFrame());
        edit.beginFrame();
        edit.observe(false, false);
        assertFalse(edit.endFrame());
    }

    @Test
    void pendingChangeCommitsWhenItsControlStopsRendering() {
        edit.beginFrame();
        edit.observe(true, true);
        edit.observe(false, false);
        assertFalse(edit.endFrame());
        edit.beginFrame();
        assertTrue(edit.endFrame());
        assertFalse(edit.endFrame());
    }

    @Test
    void switchingTargetsFlushesPendingChangesExactlyOnce() {
        edit.beginFrame();
        edit.observe(true, true);
        assertFalse(edit.endFrame());
        assertTrue(edit.finish());
        assertFalse(edit.finish());
    }

    @Test
    void inactiveEditsCommitInTheSameFrame() {
        edit.beginFrame();
        edit.observe(true, false);
        assertTrue(edit.endFrame());
    }
}
