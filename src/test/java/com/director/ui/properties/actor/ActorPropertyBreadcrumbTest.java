package com.director.ui.properties.actor;

import com.director.actor.Actor;
import com.director.actor.ActorRef;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorRegistryManager;
import com.director.actor.ActorType;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.entity.EntityAnimationClip;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActorPropertyBreadcrumbTest {
    @AfterEach
    void cleanup() { ActorRegistryManager.setRegistry(null); }

    @Test
    void buildsStableActorSelectionAncestry() {
        ActorRegistryManager.setRegistry(new ActorRegistry());
        var actor = new Actor("Hero", ActorType.PLAYER);
        ActorRegistryManager.getInstance().registerActor(actor);
        var track = new TimelineTrack("Hero track", TrackType.ACTOR);
        track.getOrCreateActorTrackPayload().setActorRef(new ActorRef(actor.getActorId()));
        var clip = new EntityAnimationClip("Wave", 0, 2);

        assertEquals(List.of("Hero", "Hero track", "Wave", "project:wave", "right_arm", "ROTATION", "0.250s"),
                ActorPropertyBreadcrumb.parts(track, clip, "project:wave",
                        List.of("right_arm", "ROTATION", "0.250s")));
    }
}
