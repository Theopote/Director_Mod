package com.director.timeline.model.entity;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class EntitySpawnClipViewTest {
    @Test
    void defensiveGettersCopyWhileViewsShareStorage() {
        var spawn = new EntitySpawnClip("Spawn", 0, 5);
        spawn.setPosition(1, 2, 3);
        spawn.setScale(2, 3, 4);
        assertNotSame(spawn.getPosition(), spawn.getPosition());
        assertNotSame(spawn.getScale(), spawn.getScale());
        assertSame(spawn.positionView(), spawn.positionView());
        assertSame(spawn.scaleView(), spawn.scaleView());
        assertEquals(new Vector3f(1, 2, 3), spawn.positionView());
        assertEquals(new Vector3f(2, 3, 4), spawn.scaleView());
    }
}
