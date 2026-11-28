package com.director.ui.timeline.commands;

/** Shared entry point for editor actions that participate in the timeline undo stack. */
public final class TimelineCommandBridge {
    private static volatile TimelineCommandHistory history;

    private TimelineCommandBridge() {}

    public static void bind(TimelineCommandHistory commandHistory) {
        history = commandHistory;
    }

    public static void execute(TimelineCommand command) {
        if (command == null) return;
        TimelineCommandHistory current = history;
        if (current == null) {
            throw new IllegalStateException(
                    "Timeline command history is not bound: " + command.getDescription());
        }
        current.executeCommand(command);
    }
}
