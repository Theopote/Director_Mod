package com.director.ui.actor.pose;

import com.director.actor.*;
import com.director.actor.animation.*;
import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorAnimationCatalogTest {
    final AnimationAssetLibrary library=new AnimationAssetLibrary();
    final Actor player=new Actor("Player",ActorType.PLAYER);
    AnimationAsset add(String id,String name,RigDefinition rig,AnimationData.Purpose purpose) {
        var asset=new AnimationAsset(id,name,rig,2,AnimationLoopMode.ONCE);asset.setPurpose(purpose);library.register(asset);return asset;
    }
    List<ActorAnimationCatalog.Entry> entries(Actor actor) {
        return ActorAnimationCatalog.entries(library,AnimationLibrary.getInstance().getAllAnimations(),actor);
    }
    @Test void searchSourcePurposeAndCompatibilityIntersectWithoutChangingAssets() {
        var wave=add("project:wave","挥手 Wave",ArticulatedPlayerRig.DEFINITION,AnimationData.Purpose.PERFORMANCE);
        add("project:walk","Walk",ArticulatedPlayerRig.DEFINITION,AnimationData.Purpose.LOCOMOTION);
        add("project:other","Other",HumanoidRig.DEFINITION,AnimationData.Purpose.PERFORMANCE);
        var catalog=entries(player);
        var results=ActorAnimationCatalog.filter(catalog,"  WAVE  ",ActorAnimationCatalog.Source.PROJECT,AnimationData.Purpose.PERFORMANCE,true,null);
        assertEquals(List.of("project:wave"),results.stream().map(ActorAnimationCatalog.Entry::id).toList());
        assertEquals(1,ActorAnimationCatalog.filter(catalog,"挥手",ActorAnimationCatalog.Source.PROJECT,null,true,null).size());
        assertTrue(ActorAnimationCatalog.filter(catalog,"project:other",null,null,true,null).isEmpty());
        assertEquals(1,ActorAnimationCatalog.filter(catalog,"project:other",null,null,false,null).size());
        assertTrue(ActorAnimationCatalog.filter(catalog,"project:wave",null,null,true,wave.id()).isEmpty());
        assertEquals(3,library.size());assertTrue(wave.boneTracks().isEmpty());assertEquals("挥手 Wave",wave.name());
    }
    @Test void projectIdShadowsBuiltinEvenWhenProjectRigIsIncompatible() {
        add("walk","Shadow",HumanoidRig.DEFINITION,AnimationData.Purpose.PERFORMANCE);
        var rows=entries(player).stream().filter(e->e.id().equals("walk")).toList();
        assertEquals(1,rows.size());assertEquals(ActorAnimationCatalog.Source.PROJECT,rows.getFirst().source());
        assertFalse(rows.getFirst().compatible());
        assertTrue(ActorAnimationCatalog.filter(entries(player),"walk",ActorAnimationCatalog.Source.BUILTIN,null,true,null).stream().noneMatch(e->e.id().equals("walk")));
    }
    @Test void missingRigCanBrowseButCannotOfferCompatibleBindingsAndRenamesRefresh() {
        var asset=add("project:a","First",ArticulatedPlayerRig.DEFINITION,AnimationData.Purpose.PERFORMANCE);
        assertTrue(ActorAnimationCatalog.filter(entries(null),"",null,null,true,null).isEmpty());
        assertFalse(ActorAnimationCatalog.filter(entries(null),"First",null,null,false,null).isEmpty());
        asset.setName("Second");
        assertTrue(ActorAnimationCatalog.filter(entries(player),"First",null,null,true,null).isEmpty());
        assertEquals(1,ActorAnimationCatalog.filter(entries(player),"Second",null,null,true,null).size());
        var cow=new Actor("Cow",ActorType.COW);
        assertTrue(ActorAnimationCatalog.filter(entries(cow),"",null,null,true,null).isEmpty());
    }
}
