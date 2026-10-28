package com.director.ui.actor.pose;

import com.director.actor.*;
import com.director.actor.animation.*;
import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorAnimationCatalogTest {
    @Test void builtinRegistrationInvalidatesCachedRowsAndProjectShadowing() {
        var builtins=AnimationLibrary.getInstance();String id="test:catalog_builtin_revision";
        var cache=new ActorAnimationCatalog.Cache();
        var before=cache.entries(library,builtins,player);
        var animation=new AnimationData(id,"Cached builtin",1,List.of(),AnimationData.AnimationSource.CUSTOM);
        try {
            builtins.register(animation);
            var rows=cache.entries(library,builtins,player);
            assertNotSame(before,rows);assertTrue(rows.stream().anyMatch(e->e.id().equals(id)));
            add(id,"Project shadow",HumanoidRig.DEFINITION,AnimationData.Purpose.PERFORMANCE);
            var shadow=cache.entries(library,builtins,player).stream().filter(e->e.id().equals(id)).findFirst().orElseThrow();
            assertEquals(ActorAnimationCatalog.Source.PROJECT,shadow.source());assertFalse(shadow.compatible());
            library.remove(id);builtins.remove(id);
            assertTrue(cache.entries(library,builtins,player).stream().noneMatch(e->e.id().equals(id)));
        } finally { builtins.remove(id); }
    }
    @Test void cachedRowsAndFiltersAreReusedUntilMetadataOrCompatibilityChanges() {
        var asset=add("project:cached","First",ArticulatedPlayerRig.DEFINITION,AnimationData.Purpose.PERFORMANCE);
        var cache=new ActorAnimationCatalog.Cache();
        var rows=cache.entries(library,null,player);
        assertSame(rows,cache.entries(library,null,player));
        var visible=cache.filter(rows,"First",null,null,true,null);
        assertSame(visible,cache.filter(rows,"First",null,null,true,null));
        asset.setName("Second");
        var renamed=cache.entries(library,null,player);
        assertNotSame(rows,renamed);
        assertTrue(cache.filter(renamed,"First",null,null,true,null).isEmpty());
        assertEquals("Second",renamed.getFirst().name());
        asset.setName("Second");
        assertSame(renamed,cache.entries(library,null,player));
        asset.setPurpose(AnimationData.Purpose.LOCOMOTION);
        var changed=cache.entries(library,null,player);
        assertEquals(AnimationData.Purpose.LOCOMOTION,changed.getFirst().purpose());
        assertFalse(cache.entries(library,null,new Actor("Cow",ActorType.COW)).getFirst().compatible());
        assertTrue(cache.entries(library,null,player).getFirst().compatible());
        library.remove(asset.id());
        assertTrue(cache.entries(library,null,player).isEmpty());
        library.register(asset);
        assertEquals(1,cache.entries(library,null,player).size());
    }

    @Test void replacedRemovedAndSharedAssetsNotifyOnlyTheirCurrentLibraries() {
        var asset=add("project:versioned","A",ArticulatedPlayerRig.DEFINITION,AnimationData.Purpose.PERFORMANCE);
        var other=new AnimationAssetLibrary();other.register(asset);
        long first=library.revision(),second=other.revision();
        asset.setName("B");
        assertTrue(library.revision()>first);assertTrue(other.revision()>second);
        library.remove(asset.id());first=library.revision();second=other.revision();
        asset.setPurpose(AnimationData.Purpose.STATIC_POSE);
        assertEquals(first,library.revision());assertTrue(other.revision()>second);
        var replacement=add(asset.id(),"C",ArticulatedPlayerRig.DEFINITION,AnimationData.Purpose.PERFORMANCE);
        first=library.revision();asset.setName("Detached");assertEquals(first,library.revision());
        replacement.setName("Current");assertTrue(library.revision()>first);
        library.clear();first=library.revision();replacement.setName("Cleared");assertEquals(first,library.revision());
        other.clear();
    }
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
