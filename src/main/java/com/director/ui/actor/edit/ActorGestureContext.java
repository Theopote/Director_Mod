package com.director.ui.actor.edit;
import com.director.timeline.model.TimelineModel;
import java.util.Objects;
import java.util.function.LongSupplier;
/** Immutable, transient gesture lifetime. Committed undo commands never retain this guard. */
public final class ActorGestureContext {
    private final TimelineModel model;
    private final long revision;
    private final LongSupplier[] details;
    private final long[] revisions;
    public ActorGestureContext(TimelineModel model,LongSupplier... details) {
        this.model=Objects.requireNonNull(model); revision=model.getActorEditContextRevision();
        this.details=details.clone(); revisions=new long[details.length];
        for(int i=0;i<details.length;i++) revisions[i]=details[i].getAsLong();
    }
    public boolean isCurrent(TimelineModel current) {
        if(current!=model || !Float.isFinite(model.getCurrentTime()) || model.getCurrentTime()<0 || model.isPlaying() || model.isScrubbing() || revision!=model.getActorEditContextRevision()) return false;
        for(int i=0;i<details.length;i++) if(revisions[i]!=details[i].getAsLong()) return false;
        return true;
    }
}
