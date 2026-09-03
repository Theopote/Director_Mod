package com.director.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared entry point for editor actions that participate in the timeline undo stack. */
public final class TimelineCommandBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineCommandBridge.class);
    private static volatile TimelineCommandHistory history;

    private TimelineCommandBridge() {}

    public static void bind(TimelineCommandHistory commandHistory) {
        history = commandHistory;
    }

    public static void execute(TimelineCommand command) {
        if (command == null) return;
        TimelineCommandHistory current = history;
        if (current != null) current.executeCommand(command);
        else {
            LOGGER.warn("[TimelineCommandBridge] No history bound; execute without undo: {}", command.getDescription());
            command.execute();
        }
    }
}
