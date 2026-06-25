package com.director.actor.persistence;

import com.director.actor.Actor;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ActorRegistryPersistenceTest {

    @Test
    void skinFileNameIsPersistedAndRestored() throws Exception {
        ActorRegistry registry = new ActorRegistry();
        Actor actor = registry.createActor("A", ActorType.PLAYER);
        UUID id = actor.getActorId();
        actor.setSkinFileName("skins/" + id + ".png");

        // Use a temporary world director directory by overriding WorldIdentifier.getWorldSavePath via reflection.
        Path tempWorld = Files.createTempDirectory("director-test-world");
        overrideWorldSavePath(tempWorld);

        assertTrue(ActorRegistryPersistence.saveToFile(registry));

        ActorRegistry loaded = new ActorRegistry();
        ActorRegistryPersistence.loadFromFile(loaded);
        assertEquals(1, loaded.size());
        Actor loadedActor = loaded.getActor(id).orElseThrow();
        assertEquals(actor.getSkinFileName(), loadedActor.getSkinFileName());
    }

    private static void overrideWorldSavePath(Path worldPath) throws Exception {
        Class<?> cls = Class.forName("com.director.util.WorldIdentifier");
        Field field = cls.getDeclaredField("cachedWorldId");
        field.setAccessible(true);
        field.set(null, null);

        Field pathField = cls.getDeclaredField("cachedWorldKey");
        pathField.setAccessible(true);
        pathField.set(null, null);

        // WorldIdentifier.getWorldSavePath() uses MinecraftClient; in tests we rely on the path
        // based fallback under current directory. Link our temp dir to ./saves/<world>.
        Path saves = Path.of("saves");
        Files.createDirectories(saves);
        Path link = saves.resolve(worldPath.getFileName());
        if (!Files.exists(link)) {
            Files.createDirectories(link);
        }
    }
}

