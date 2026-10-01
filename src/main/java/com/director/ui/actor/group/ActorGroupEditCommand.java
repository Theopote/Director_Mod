package com.director.ui.actor.group;

import com.director.timeline.model.TimelineModel;
import com.director.ui.timeline.commands.BaseTimelineCommand;
import com.director.ui.timeline.commands.TimelineCommandBridge;
import java.util.List;

/** One group operation; a failed member restores every member already attempted. */
final class ActorGroupEditCommand extends BaseTimelineCommand {
    record Change(Runnable before, Runnable after) {}
    private final List<Change> changes;
    private final String description;
    private ActorGroupEditCommand(TimelineModel model, String description, List<Change> changes) {
        super(model, "actor_group_edit");
        this.description = description;
        this.changes = List.copyOf(changes);
    }
    static void execute(TimelineModel model, String description, List<Change> changes) {
        if (!changes.isEmpty()) TimelineCommandBridge.execute(new ActorGroupEditCommand(model, description, changes));
    }
    @Override protected void doExecute() {
        int attempted = 0;
        try {
            for (var change : changes) { attempted++; change.after().run(); }
        } catch (RuntimeException failure) {
            for (int i = attempted - 1; i >= 0; i--) {
                try { changes.get(i).before().run(); }
                catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        }
    }
    @Override protected void doUndo() {
        for (int i = changes.size() - 1; i >= 0; i--) changes.get(i).before().run();
    }
    @Override public String getDescription() { return description; }
}
