package com.director.actor.skin;

import com.director.util.WorldIdentifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ActorSkinStorageTest {

    @AfterEach
    void cleanup() {
        WorldIdentifier.overrideWorldSavePathForTesting(null);
        WorldIdentifier.clearCache();
    }

    @Test
    void relativeFileNameUsesActorId() {
        UUID id = UUID.randomUUID();
        String name = ActorSkinStorage.getRelativeSkinFileName(id);
        assertTrue(name.startsWith("skins/"));
        assertTrue(name.endsWith(".png"));
        assertTrue(name.contains(id.toString()));
    }

    @Test
    void importSkinWritesIntoSkinsDirectory() throws Exception {
        UUID id = UUID.randomUUID();
        Path tmpRoot = Files.createTempDirectory("director-skin-test");
        WorldIdentifier.overrideWorldSavePathForTesting(tmpRoot);

        Path src = tmpRoot.resolve("source.png");
        Files.write(src, new byte[]{0, 1, 2});

        String rel = ActorSkinStorage.importSkin(src, id);
        assertNotNull(rel);
        assertEquals(ActorSkinStorage.getRelativeSkinFileName(id), rel);

        Path dest = ActorSkinStorage.getSkinFile(id);
        assertNotNull(dest);
        assertTrue(dest.normalize().toString().contains("skins"));
        assertTrue(Files.exists(dest));
    }
}
