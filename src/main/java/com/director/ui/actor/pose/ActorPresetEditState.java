package com.director.ui.actor.pose;

import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.entity.EntitySpawnClip;
import java.util.*;

/** Revisions retain switch-away-and-back changes without affecting committed undo targets. */
public record ActorPresetEditState(List<Entry> entries) {
    public record Entry(EntitySpawnClip owner,long revision) {}
    public ActorPresetEditState { entries=List.copyOf(entries); }
    public static ActorPresetEditState capture(TimelineTrack track) {
        var entries=new ArrayList<Entry>();
        if(track!=null && track.getActorTrackPayload()!=null)
            for(var clip:track.getActorTrackPayload().getClips()) if(clip instanceof EntitySpawnClip spawn)
                entries.add(new Entry(spawn,spawn.getPresetRevision()));
        return new ActorPresetEditState(entries);
    }
}
