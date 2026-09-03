package com.director.ui.timeline.commands;

import com.director.timeline.model.TimelineModel;

/** A single already-completed editor gesture represented by its before/after state appliers. */
public final class StateChangeCommand extends BaseTimelineCommand {
    private final String description;
    private final Runnable applyBefore;
    private final Runnable applyAfter;

    public StateChangeCommand(TimelineModel model, String description, Runnable applyBefore, Runnable applyAfter) {
        super(model, "editor_transform");
        this.description = description;
        this.applyBefore = applyBefore;
        this.applyAfter = applyAfter;
    }

    @Override protected void doExecute() { applyAfter.run(); }
    @Override protected void doUndo() { applyBefore.run(); }
    @Override public String getDescription() { return description; }
}
