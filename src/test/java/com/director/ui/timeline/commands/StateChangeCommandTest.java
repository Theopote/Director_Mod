package com.director.ui.timeline.commands;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StateChangeCommandTest {
    @Test
    void completedGestureCanBeUndoneAndRedoneAsOneCommand() {
        AtomicInteger value = new AtomicInteger(7);
        TimelineCommandHistory history = new TimelineCommandHistory();
        history.executeCommand(new StateChangeCommand(null, "gesture",
            () -> value.set(2), () -> value.set(7)));

        assertEquals(1, history.getUndoSize());
        history.undo();
        assertEquals(2, value.get());
        history.redo();
        assertEquals(7, value.get());
    }
}
