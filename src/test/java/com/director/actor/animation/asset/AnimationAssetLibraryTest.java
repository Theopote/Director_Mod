package com.director.actor.animation.asset;

import com.director.actor.rig.skeleton.HumanoidRig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationAssetLibraryTest {
    private final AnimationAssetLibrary library = AnimationAssetLibrary.getInstance();

    @AfterEach void cleanUp() { library.clear(); }

    @Test
    void registersAndReportsReplacedAsset() {
        AnimationAsset first = asset("project:walk", "Walk A");
        AnimationAsset replacement = asset("project:walk", "Walk B");

        assertTrue(library.register(first).isEmpty());
        assertEquals(first, library.register(replacement).orElseThrow());
        assertEquals(replacement, library.find("project:walk").orElseThrow());
        assertEquals(1, library.size());
    }

    private static AnimationAsset asset(String id, String name) {
        return new AnimationAsset(id, name, HumanoidRig.DEFINITION, 1f, AnimationLoopMode.ONCE);
    }
}
