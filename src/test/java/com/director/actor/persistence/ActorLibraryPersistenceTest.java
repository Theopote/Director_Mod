package com.director.actor.persistence;

import com.director.actor.model.BuiltInCustomModels;
import com.director.actor.model.CustomModelLibrary;
import com.director.actor.model.CustomModelPersistence;
import com.director.actor.path.ActorPathDefinition;
import com.director.actor.path.ActorPathLibrary;
import com.director.actor.path.persistence.ActorPathPersistence;
import com.director.actor.rig.skeleton.BuiltInCustomRigs;
import com.director.actor.rig.skeleton.CustomRigLibrary;
import com.director.actor.rig.skeleton.CustomRigPersistence;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActorLibraryPersistenceTest {
    @Test
    void invalidPathKeepsTheLiveLibraryAndBlocksSavingUntilReload(@TempDir Path directory) throws IOException {
        var file = directory.resolve("actor_paths.json");
        var library = new ActorPathLibrary();
        var original = new ActorPathDefinition("Path");
        original.setWaypoints(List.of(new Vector3f(), new Vector3f(1f, 0f, 0f)));
        library.add(original);
        ActorPathPersistence.save(file, library);
        String valid = Files.readString(file);
        var invalidPath = new JsonObject();
        invalidPath.addProperty("id", "invalid-uuid");
        invalidPath.addProperty("name", "Invalid");
        String invalid = appendEntry(file, valid, "paths", invalidPath);

        assertThrows(IllegalArgumentException.class, () -> ActorPathPersistence.load(file, library));
        assertSame(original, library.get(original.getId()));
        assertThrows(IOException.class, () -> ActorPathPersistence.save(file, library));
        assertEquals(invalid, Files.readString(file));

        Files.writeString(file, valid);
        ActorPathPersistence.load(file, library);
        ActorPathPersistence.save(file, library);
        assertEquals(1, library.size());
    }

    @Test
    void invalidRigKeepsTheLiveLibraryAndBlocksSavingUntilReload(@TempDir Path directory) throws IOException {
        var file = directory.resolve("custom_rigs.json");
        var library = CustomRigLibrary.getInstance();
        var before = library.all();
        try {
            library.clear();
            var original = BuiltInCustomRigs.SIMPLE_ROBOT;
            library.register(original);
            CustomRigPersistence.save(file, library);
            String valid = Files.readString(file);
            String invalid = appendEntry(file, valid, "rigs", new JsonObject());

            assertThrows(RuntimeException.class, () -> CustomRigPersistence.load(file, library));
            assertSame(original, library.find(original.id()).orElseThrow());
            assertThrows(IOException.class, () -> CustomRigPersistence.save(file, library));
            assertEquals(invalid, Files.readString(file));

            Files.writeString(file, valid);
            CustomRigPersistence.load(file, library);
            CustomRigPersistence.save(file, library);
        } finally {
            library.clear();
            before.forEach(library::register);
        }
    }

    @Test
    void invalidModelKeepsTheLiveLibraryAndBlocksSavingUntilReload(@TempDir Path directory) throws IOException {
        var file = directory.resolve("custom_models.json");
        var library = CustomModelLibrary.getInstance();
        var before = library.all();
        try {
            library.clear();
            var original = BuiltInCustomModels.SIMPLE_ROBOT;
            library.register(original);
            CustomModelPersistence.save(file, library);
            String valid = Files.readString(file);
            String invalid = appendEntry(file, valid, "models", new JsonObject());

            assertThrows(IllegalArgumentException.class, () -> CustomModelPersistence.load(file, library));
            assertSame(original, library.find(original.id()).orElseThrow());
            assertThrows(IOException.class, () -> CustomModelPersistence.save(file, library));
            assertEquals(invalid, Files.readString(file));

            Files.writeString(file, valid);
            CustomModelPersistence.load(file, library);
            CustomModelPersistence.save(file, library);
        } finally {
            library.clear();
            before.forEach(library::register);
        }
    }

    private static String appendEntry(Path file, String valid, String arrayName, JsonObject entry) throws IOException {
        var json = JsonParser.parseString(valid).getAsJsonObject();
        json.getAsJsonArray(arrayName).add(entry);
        String invalid = json.toString();
        Files.writeString(file, invalid);
        return invalid;
    }
}
