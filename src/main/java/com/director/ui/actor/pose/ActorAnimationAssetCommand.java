package com.director.ui.actor.pose;

import com.director.timeline.model.TimelineModel;
import com.director.ui.timeline.commands.BaseTimelineCommand;
import java.util.List;

/** Asset and clip edits roll back all attempted steps if a mutation fails. */
final class ActorAnimationAssetCommand extends BaseTimelineCommand {
    record Change(Runnable before, Runnable after) {}
    private final String description;
    private final Runnable preflight;
    private final List<Change> changes;

    ActorAnimationAssetCommand(TimelineModel model, String description, Runnable preflight, List<Change> changes) {
        super(model, "actor_animation_asset");
        this.description = description;
        this.preflight = preflight;
        this.changes = List.copyOf(changes);
    }
    @Override protected void doExecute() {
        preflight.run();
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
        int index = changes.size() - 1;
        try {
            for (; index >= 0; index--) changes.get(index).before().run();
        } catch (RuntimeException failure) {
            for (int i = index; i < changes.size(); i++) {
                try { changes.get(i).after().run(); }
                catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        }
    }
    @Override public String getDescription() { return description; }
}
