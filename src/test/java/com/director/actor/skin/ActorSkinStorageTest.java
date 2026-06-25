package com.director.actor.skin;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ActorSkinStorageTest {

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
        Path director = tmpRoot.resolve("director");
        Files.createDirectories(director);
        Path saves = Path.of("saves");
        Files.createDirectories(saves);
        Path link = saves.resolve(tmpRoot.getFileName());
        if (!Files.exists(link)) {
            Files.createDirectories(link);
        }

        Path src = tmpRoot.resolve("source.png");
        Files.write(src, new byte[]{0, 1, 2});

        String rel = ActorSkinStorage.importSkin(src, id);
        assertNotNull(rel);

        Path dest = ActorSkinStorage.getSkinFile(id);
        assertNotNull(dest);
        assertTrue(dest.normalize().toString().contains("skins"));
        assertTrue(Files.exists(dest));
    }
}

