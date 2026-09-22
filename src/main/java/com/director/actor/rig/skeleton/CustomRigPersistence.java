package com.director.actor.rig.skeleton;

import com.director.persistence.AtomicFileWriter;
import com.director.util.WorldIdentifier;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Versioned JSON persistence for project custom rig skeletons. */
public final class CustomRigPersistence {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomRigPersistence.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int FILE_SCHEMA_VERSION = 1;
    private static final String FILE_NAME = "custom_rigs.json";

    private CustomRigPersistence() {}

    /** Portable single-rig format for importing/exporting a custom skeleton file. */
    public static void exportRig(Path file, RigDefinition rig) throws IOException {
        if (Files.exists(file)) throw new IOException("目标文件已存在，请选择新的文件名");
        JsonObject root = new JsonObject();
        root.addProperty("format", "director:rig");
        root.addProperty("schemaVersion", FILE_SCHEMA_VERSION);
        root.add("rig", RigDefinitionPersistence.write(rig));
        RigDefinitionPersistence.read(root.getAsJsonObject("rig"));
        AtomicFileWriter.writeString(file, GSON.toJson(root));
    }

    public static RigDefinition importRig(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            int limit = 4 * 1024 * 1024;
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit) throw new IOException("骨架文件超过 4 MiB 限制");
            JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!"director:rig".equals(required(root, "format").getAsString())
                    || required(root, "schemaVersion").getAsInt() != FILE_SCHEMA_VERSION)
                throw new IllegalArgumentException("不支持的骨架文件格式或版本");
            return RigDefinitionPersistence.read(required(root, "rig").getAsJsonObject());
        } catch (RuntimeException e) {
            throw new IOException("骨架文件校验失败：" + e.getMessage(), e);
        }
    }

    public static boolean saveToWorld(CustomRigLibrary library) {
        Path file = worldFile();
        if (file == null) return false;
        try {
            save(file, library);
            return true;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[CustomRigPersistence] 保存自定义骨架失败: {}", file, e);
            return false;
        }
    }

    public static boolean loadFromWorld(CustomRigLibrary library) {
        Path file = worldFile();
        if (file == null || !Files.exists(file)) return false;
        try {
            load(file, library);
            return true;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[CustomRigPersistence] 加载自定义骨架失败: {}", file, e);
            return false;
        }
    }

    public static void save(Path file, CustomRigLibrary library) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", FILE_SCHEMA_VERSION);
        JsonArray rigs = new JsonArray();
        for (RigDefinition rig : library.all()) rigs.add(RigDefinitionPersistence.write(rig));
        root.add("rigs", rigs);
        AtomicFileWriter.writeString(file, GSON.toJson(root));
    }

    public static void load(Path file, CustomRigLibrary library) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        int version = required(root, "schemaVersion").getAsInt();
        if (version != FILE_SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported custom rig schema: " + version);
        JsonArray array = required(root, "rigs").getAsJsonArray();
        library.clear();
        for (JsonElement element : array) library.register(RigDefinitionPersistence.read(element.getAsJsonObject()));
    }

    private static JsonElement required(JsonObject object, String name) {
        if (object == null || !object.has(name) || object.get(name).isJsonNull())
            throw new IllegalArgumentException("Missing custom rig field: " + name);
        return object.get(name);
    }

    private static Path worldFile() {
        try {
            Path save = WorldIdentifier.getWorldSavePath();
            return save == null ? null : save.resolve("director").resolve(FILE_NAME);
        } catch (RuntimeException e) {
            LOGGER.warn("[CustomRigPersistence] 无法解析世界目录", e);
            return null;
        }
    }
}
