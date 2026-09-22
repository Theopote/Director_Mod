package com.director.actor.model;

import com.director.actor.model.texture.CuboidFaceUvs;
import com.director.persistence.AtomicFileWriter;
import com.director.util.WorldIdentifier;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Versioned JSON persistence for project custom block-cuboid models. */
public final class CustomModelPersistence {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomModelPersistence.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int FILE_SCHEMA_VERSION = 1;
    private static final String FILE_NAME = "custom_models.json";

    private CustomModelPersistence() {}

    public static void exportModel(Path file, CustomModelDefinition model) throws IOException {
        if (Files.exists(file)) throw new IOException("目标文件已存在，请选择新的文件名");
        JsonObject root = new JsonObject();
        root.addProperty("format", "director:model");
        root.addProperty("schemaVersion", FILE_SCHEMA_VERSION);
        root.add("model", writeModel(model));
        readModel(root.getAsJsonObject("model"));
        AtomicFileWriter.writeString(file, GSON.toJson(root));
    }

    public static CustomModelDefinition importModel(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            int limit = 4 * 1024 * 1024;
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit) throw new IOException("模型文件超过 4 MiB 限制");
            JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!"director:model".equals(required(root, "format").getAsString())
                    || required(root, "schemaVersion").getAsInt() != FILE_SCHEMA_VERSION)
                throw new IllegalArgumentException("不支持的模型文件格式或版本");
            return readModel(required(root, "model").getAsJsonObject());
        } catch (RuntimeException e) {
            throw new IOException("模型文件校验失败：" + e.getMessage(), e);
        }
    }

    public static boolean saveToWorld(CustomModelLibrary library) {
        Path file = worldFile();
        if (file == null) return false;
        try {
            save(file, library);
            return true;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[CustomModelPersistence] 保存自定义模型失败: {}", file, e);
            return false;
        }
    }

    public static boolean loadFromWorld(CustomModelLibrary library) {
        Path file = worldFile();
        if (file == null || !Files.exists(file)) return false;
        try {
            load(file, library);
            return true;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[CustomModelPersistence] 加载自定义模型失败: {}", file, e);
            return false;
        }
    }

    public static void save(Path file, CustomModelLibrary library) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", FILE_SCHEMA_VERSION);
        JsonArray models = new JsonArray();
        for (CustomModelDefinition model : library.all()) models.add(writeModel(model));
        root.add("models", models);
        AtomicFileWriter.writeString(file, GSON.toJson(root));
    }

    public static void load(Path file, CustomModelLibrary library) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        int version = required(root, "schemaVersion").getAsInt();
        if (version != FILE_SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported custom model schema: " + version);
        JsonArray array = required(root, "models").getAsJsonArray();
        library.clear();
        for (JsonElement element : array) library.register(readModel(element.getAsJsonObject()));
    }

    private static JsonObject writeModel(CustomModelDefinition model) {
        JsonObject out = new JsonObject();
        out.addProperty("id", model.id());
        out.addProperty("displayName", model.displayName());
        out.addProperty("rigId", model.rigId());
        JsonArray cuboids = new JsonArray();
        for (BoneCuboid cuboid : model.cuboids()) {
            JsonObject c = new JsonObject();
            c.addProperty("boneId", cuboid.boneId());
            c.add("offset", writeVector(cuboid.offset()));
            c.add("size", writeVector(cuboid.size()));
            c.addProperty("blockStateId", cuboid.blockStateId());
            if (cuboid.itemModelId() != null) {
                c.addProperty("itemModelId", cuboid.itemModelId());
            }
            if (cuboid.textureId() != null) {
                c.addProperty("textureId", cuboid.textureId());
            }
            if (cuboid.faceUvs() != null) {
                c.add("faceUvs", writeFaceUvs(cuboid.faceUvs()));
            }
            cuboids.add(c);
        }
        out.add("cuboids", cuboids);
        return out;
    }

    private static CustomModelDefinition readModel(JsonObject in) {
        String id = required(in, "id").getAsString();
        String displayName = in.has("displayName") ? in.get("displayName").getAsString() : id;
        String rigId = required(in, "rigId").getAsString();
        List<BoneCuboid> cuboids = new ArrayList<>();
        for (JsonElement element : required(in, "cuboids").getAsJsonArray()) {
            JsonObject c = element.getAsJsonObject();
            cuboids.add(new BoneCuboid(
                    required(c, "boneId").getAsString(),
                    readVector(required(c, "offset").getAsJsonObject()),
                    readVector(required(c, "size").getAsJsonObject()),
                    required(c, "blockStateId").getAsString(),
                    optionalString(c, "itemModelId"),
                    optionalString(c, "textureId"),
                    c.has("faceUvs") ? readFaceUvs(c.getAsJsonObject("faceUvs")) : null));
        }
        return new CustomModelDefinition(id, displayName, rigId, cuboids);
    }

    private static JsonObject writeVector(Vector3f v) {
        JsonObject out = new JsonObject();
        out.addProperty("x", v.x); out.addProperty("y", v.y); out.addProperty("z", v.z);
        return out;
    }

    private static Vector3f readVector(JsonObject in) {
        return new Vector3f(required(in, "x").getAsFloat(), required(in, "y").getAsFloat(), required(in, "z").getAsFloat());
    }

    private static JsonObject writeFaceUvs(CuboidFaceUvs faceUvs) {
        JsonObject out = new JsonObject();
        writeFace(out, "north", faceUvs.north());
        writeFace(out, "south", faceUvs.south());
        writeFace(out, "east", faceUvs.east());
        writeFace(out, "west", faceUvs.west());
        writeFace(out, "up", faceUvs.up());
        writeFace(out, "down", faceUvs.down());
        return out;
    }

    private static void writeFace(JsonObject out, String name, float[] uv) {
        if (uv == null || uv.length < 4) {
            return;
        }
        JsonArray array = new JsonArray();
        array.add(uv[0]);
        array.add(uv[1]);
        array.add(uv[2]);
        array.add(uv[3]);
        out.add(name, array);
    }

    private static CuboidFaceUvs readFaceUvs(JsonObject in) {
        return new CuboidFaceUvs(
                readFace(in, "north"),
                readFace(in, "south"),
                readFace(in, "east"),
                readFace(in, "west"),
                readFace(in, "up"),
                readFace(in, "down"));
    }

    private static float[] readFace(JsonObject in, String name) {
        if (!in.has(name) || in.get(name).isJsonNull()) {
            return null;
        }
        JsonArray array = in.getAsJsonArray(name);
        if (array.size() < 4) {
            return null;
        }
        return new float[] {
                array.get(0).getAsFloat(),
                array.get(1).getAsFloat(),
                array.get(2).getAsFloat(),
                array.get(3).getAsFloat()
        };
    }

    private static String optionalString(JsonObject object, String name) {
        if (!object.has(name) || object.get(name).isJsonNull()) {
            return null;
        }
        return object.get(name).getAsString();
    }

    private static JsonElement required(JsonObject object, String name) {
        if (object == null || !object.has(name) || object.get(name).isJsonNull())
            throw new IllegalArgumentException("Missing custom model field: " + name);
        return object.get(name);
    }

    private static Path worldFile() {
        try {
            Path save = WorldIdentifier.getWorldSavePath();
            return save == null ? null : save.resolve("director").resolve(FILE_NAME);
        } catch (RuntimeException e) {
            LOGGER.warn("[CustomModelPersistence] 无法解析世界目录", e);
            return null;
        }
    }
}
