package com.director.actor.path.persistence;

import com.director.actor.path.ActorPathDefinition;
import com.director.actor.path.ActorPathLibrary;
import com.director.timeline.model.entity.ActorRouteClip;
import com.director.util.WorldIdentifier;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ActorPathPersistence {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorPathPersistence.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String VERSION = "1.0";

    private ActorPathPersistence() {
    }

    public static boolean hasSavedData() {
        Path file = getActorPathsFile();
        return file != null && Files.exists(file);
    }

    public static boolean saveToFile(ActorPathLibrary library) {
        if (library == null) {
            return false;
        }
        try {
            Path file = getActorPathsFile();
            if (file == null) {
                return false;
            }
            Path parent = file.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            JsonObject root = new JsonObject();
            root.addProperty("version", VERSION);
            JsonArray paths = new JsonArray();
            for (ActorPathDefinition path : library.getAll()) {
                paths.add(serializePath(path));
            }
            root.add("paths", paths);

            Path tempFile = file.resolveSibling(file.getFileName().toString() + ".tmp");
            Files.writeString(tempFile, GSON.toJson(root));
            try {
                Files.move(tempFile, file,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.FileSystemException e) {
                Files.move(tempFile, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            LOGGER.info("[ActorPathPersistence] 保存 {} 条演员路径到 {}", library.size(), file);
            return true;
        } catch (IOException e) {
            LOGGER.error("[ActorPathPersistence] 保存失败", e);
            return false;
        }
    }

    public static void loadFromFile(ActorPathLibrary library) {
        if (library == null) {
            return;
        }
        library.clear();
        try {
            Path file = getActorPathsFile();
            if (file == null || !Files.exists(file)) {
                return;
            }
            JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
            if (root == null || !root.has("paths")) {
                return;
            }
            List<ActorPathDefinition> loaded = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("paths")) {
                ActorPathDefinition path = deserializePath(element.getAsJsonObject());
                if (path != null && path.getWaypoints().size() >= 2) {
                    loaded.add(path);
                }
            }
            library.replaceAll(loaded);
            LOGGER.info("[ActorPathPersistence] 从 {} 加载 {} 条演员路径", file, loaded.size());
        } catch (Exception e) {
            LOGGER.error("[ActorPathPersistence] 加载失败", e);
        }
    }

    private static JsonObject serializePath(ActorPathDefinition path) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", path.getId().toString());
        obj.addProperty("name", path.getName());
        obj.addProperty("interpolation", path.getInterpolationMode().name());
        JsonArray waypoints = new JsonArray();
        for (Vector3f point : path.getWaypoints()) {
            JsonObject wp = new JsonObject();
            wp.addProperty("x", point.x);
            wp.addProperty("y", point.y);
            wp.addProperty("z", point.z);
            waypoints.add(wp);
        }
        obj.add("waypoints", waypoints);
        return obj;
    }

    private static ActorPathDefinition deserializePath(JsonObject obj) {
        if (obj == null || !obj.has("id") || !obj.has("name")) {
            return null;
        }
        UUID id = UUID.fromString(obj.get("id").getAsString());
        ActorPathDefinition path = new ActorPathDefinition(id, obj.get("name").getAsString());
        if (obj.has("interpolation")) {
            try {
                path.setInterpolationMode(ActorRouteClip.InterpolationMode.valueOf(obj.get("interpolation").getAsString()));
            } catch (IllegalArgumentException ignored) {
                // keep default
            }
        }
        List<Vector3f> waypoints = new ArrayList<>();
        if (obj.has("waypoints")) {
            for (JsonElement element : obj.getAsJsonArray("waypoints")) {
                JsonObject wp = element.getAsJsonObject();
                waypoints.add(new Vector3f(
                        wp.get("x").getAsFloat(),
                        wp.get("y").getAsFloat(),
                        wp.get("z").getAsFloat()
                ));
            }
        }
        path.setWaypoints(waypoints);
        return path;
    }

    private static Path getActorPathsFile() {
        try {
            Path savePath = WorldIdentifier.getWorldSavePath();
            if (savePath != null) {
                Path directorDir = savePath.resolve("director");
                if (!Files.exists(directorDir)) {
                    Files.createDirectories(directorDir);
                }
                return directorDir.resolve("actor_paths.json");
            }
        } catch (Exception e) {
            LOGGER.warn("[ActorPathPersistence] 无法获取世界目录", e);
        }
        return null;
    }
}
