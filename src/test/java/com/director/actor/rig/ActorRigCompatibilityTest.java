package com.director.actor.rig;

import com.director.actor.ActorType;
import com.director.actor.rig.skeleton.HumanoidRig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorRigCompatibilityTest {
    @Test void everyTypeHasExplicitCompatibility() {
        for (var type : ActorType.values()) {
            boolean humanoid = type == ActorType.PLAYER || type == ActorType.ZOMBIE || type == ActorType.SKELETON;
            assertEquals(humanoid, ActorRigCompatibility.accepts(type, HumanoidRig.DEFINITION.id()), type.name());
            assertFalse(ActorRigCompatibility.accepts(type, "unknown:rig"));
            assertFalse(ActorRigCompatibility.explanation(type).isBlank());
        }
        assertFalse(ActorRigCompatibility.accepts((ActorType) null, HumanoidRig.DEFINITION.id()));
    }
    @Test void distinctFamiliesDoNotDependOnProceduralProfileFallback() {
        assertEquals(ActorRigCompatibility.Family.VILLAGER, ActorRigCompatibility.family(ActorType.VILLAGER));
        assertEquals(ActorRigCompatibility.Family.QUADRUPED, ActorRigCompatibility.family(ActorType.COW));
        assertEquals(ActorRigCompatibility.Family.CUSTOM, ActorRigCompatibility.family(ActorType.CUSTOM));
    }
    @Test void typeChangeChecksSpawnOwnedManualAndPresetEvenWhenMuted() {
        var library=com.director.actor.animation.asset.AnimationAssetLibrary.getInstance();
        String id="test:compatibility-embedded";
        library.register(new com.director.actor.animation.asset.AnimationAsset(id,"Test",
                com.director.actor.rig.skeleton.ArticulatedPlayerRig.DEFINITION,2,com.director.actor.animation.asset.AnimationLoopMode.ONCE));
        try {
            var model=new com.director.timeline.model.TimelineModel(new com.director.timeline.model.TimelineViewportState());
            var actorId=java.util.UUID.randomUUID();var track=new com.director.timeline.model.TimelineTrack("Actor",com.director.timeline.model.TrackType.ACTOR);
            var payload=new com.director.timeline.model.entity.ActorTrack();payload.setActorRef(new com.director.actor.ActorRef(actorId));
            track.setActorTrackPayload(payload);model.addTrack(track);
            var spawn=new com.director.timeline.model.entity.EntitySpawnClip("Spawn",10,20);spawn.setMuted(true);payload.addClip(spawn);
            spawn.setBoneAnimationId(id);
            assertTrue(ActorRigCompatibility.canChangeType(model,actorId,ActorType.PLAYER));
            assertFalse(ActorRigCompatibility.canChangeType(model,actorId,ActorType.ZOMBIE));
            assertFalse(ActorRigCompatibility.canChangeType(model,actorId,ActorType.COW));
            spawn.setBoneAnimationId(null);spawn.setPreset(new com.director.timeline.model.entity.ActorPresetBinding(id,false,1,com.director.actor.rig.skeleton.PoseBlendSettings.DEFAULT));
            assertFalse(ActorRigCompatibility.canChangeType(model,actorId,ActorType.ZOMBIE));
            spawn.setPreset(null);assertTrue(ActorRigCompatibility.canChangeType(model,actorId,ActorType.COW));
            spawn.setPreset(new com.director.timeline.model.entity.ActorPresetBinding("wave",false,1,com.director.actor.rig.skeleton.PoseBlendSettings.DEFAULT));
            assertFalse(ActorRigCompatibility.canChangeType(model,actorId,ActorType.COW));
            assertTrue(ActorRigCompatibility.canChangeType(model,actorId,ActorType.ZOMBIE));
        } finally { library.remove(id); }
    }
}
