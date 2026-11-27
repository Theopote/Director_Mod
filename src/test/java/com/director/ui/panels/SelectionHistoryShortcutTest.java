package com.director.ui.panels;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionHistoryShortcutTest {
    @Test
    void handlesUndoRedoOnlyForFocusedNonTextWindow() {
        assertTrue(SelectionHistoryPanel.shouldHandleKeyboardShortcuts(true, false));
    }

    @Test
    void ignoresShortcutsWhenAnotherWindowHasFocus() {
        assertFalse(SelectionHistoryPanel.shouldHandleKeyboardShortcuts(false, false));
    }

    @Test
    void leavesTextInputUndoRedoToTheActiveControl() {
        assertFalse(SelectionHistoryPanel.shouldHandleKeyboardShortcuts(true, true));
    }
}
