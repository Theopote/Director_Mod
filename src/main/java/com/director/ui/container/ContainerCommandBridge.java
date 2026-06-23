package com.director.ui.container;

import com.director.core.container.commands.ContainerCommand;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 容器命令桥接器：为非 UI 调用方提供统一命令历史入口。
 */
public final class ContainerCommandBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerCommandBridge.class);

    private static volatile TimelineCommandHistory history;

    private ContainerCommandBridge() {
    }

    public static void bindHistory(TimelineCommandHistory commandHistory) {
        history = commandHistory;
        LOGGER.debug("[ContainerCommandBridge] Bound command history: {}", commandHistory != null);
    }

    public static void execute(ContainerCommand command) {
        if (command == null) {
            return;
        }
        TimelineCommandHistory h = history;
        if (h != null) {
            h.executeCommand(new ContainerTimelineCommandAdapter(command));
        } else {
            LOGGER.warn("[ContainerCommandBridge] No history bound, execute without undo: {}", command.getDescription());
            command.execute();
        }
    }
}
