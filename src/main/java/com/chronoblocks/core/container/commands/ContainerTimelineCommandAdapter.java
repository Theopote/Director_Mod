package com.chronoblocks.core.container.commands;

import com.chronoblocks.ui.timeline.commands.TimelineCommand;

/**
 * 将 ContainerCommand 适配为 TimelineCommand，以复用统一的命令历史系统。
 */
public class ContainerTimelineCommandAdapter implements TimelineCommand {

    private final ContainerCommand delegate;

    public ContainerTimelineCommandAdapter(ContainerCommand delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute() {
        delegate.execute();
    }

    @Override
    public void undo() {
        delegate.undo();
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }
}

