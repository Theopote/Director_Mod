package com.director.actor.animation.asset;

import com.director.actor.rig.skeleton.RigDefinition;
import com.director.actor.rig.skeleton.RigDefinitions;
import com.director.persistence.AtomicFileWriter;
import com.director.util.WorldIdentifier;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Versioned JSON persistence for project animation assets. */
public final class AnimationAssetPersistence {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnimationAssetPersistence.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int FILE_SCHEMA_VERSION = 1;
    private static final String FILE_NAME = "animation_assets.json";

    private AnimationAssetPersistence() {}

    /** Portable single-asset format, separate from the project library file. */
    public static void exportAsset(Path file, AnimationAsset asset) throws IOException {
        if (Files.exists(file)) throw new IOException("目标文件已存在，请选择新的文件名");
        JsonObject root = new JsonObject();
        root.addProperty("format", "director:animation");
        root.addProperty("schemaVersion", FILE_SCHEMA_VERSION);
        root.add("asset", writeAsset(asset));
        // Validate with the same decoder before writing a portable asset.
        readAsset(root.getAsJsonObject("asset"));
        AtomicFileWriter.writeString(file, GSON.toJson(root));
    }

    /** Parse completely before the editor modifies its project library. */
    public static AnimationAsset importAsset(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            int limit = 16 * 1024 * 1024;
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit) throw new IOException("动画文件超过 16 MiB 限制");
            JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!"director:animation".equals(required(root, "format").getAsString())
                    || required(root, "schemaVersion").getAsInt() != FILE_SCHEMA_VERSION)
                throw new IllegalArgumentException("不支持的动画文件格式或版本");
            return readAsset(required(root, "asset").getAsJsonObject());
        } catch (RuntimeException e) {
            throw new IOException("动画文件校验失败：" + e.getMessage(), e);
        }
    }

    public static boolean saveToWorld(AnimationAssetLibrary library) {
        Path file = worldFile();
        if (file == null) return false;
        try {
            save(file, library);
            return true;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[AnimationAssetPersistence] 保存动画资产失败: {}", file, e);
            return false;
        }
    }

    public static boolean loadFromWorld(AnimationAssetLibrary library) {
        Path file = worldFile();
        if (file == null || !Files.exists(file)) return false;
        try {
            load(file, library);
            return true;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[AnimationAssetPersistence] 加载动画资产失败: {}", file, e);
            return false;
        }
    }

    public static void save(Path file, AnimationAssetLibrary library) throws IOException {
        if (file == null) throw new IllegalArgumentException("file");
        if (library == null) throw new IllegalArgumentException("library");
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", FILE_SCHEMA_VERSION);
        JsonArray assets = new JsonArray();
        for (AnimationAsset asset : library.all()) assets.add(writeAsset(asset));
        root.add("assets", assets);
        AtomicFileWriter.writeString(file, GSON.toJson(root));
    }

    public static void load(Path file, AnimationAssetLibrary library) throws IOException {
        if (file == null) throw new IllegalArgumentException("file");
        if (library == null) throw new IllegalArgumentException("library");
        try {
            replaceFromJson(Files.readString(file, StandardCharsets.UTF_8), library);
        } catch (RuntimeException | IOException primary) {
            Path backup = file.resolveSibling(file.getFileName() + ".bak");
            if (!Files.exists(backup)) {
                if (primary instanceof IOException io) throw io;
                throw primary;
            }
            LOGGER.warn("[AnimationAssetPersistence] 主文件损坏，尝试备份: {}", backup, primary);
            replaceFromJson(Files.readString(backup, StandardCharsets.UTF_8), library);
        }
    }

    private static void replaceFromJson(String json, AnimationAssetLibrary library) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int version = required(root, "schemaVersion").getAsInt();
        if (version != FILE_SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported animation asset schema: " + version);
        JsonArray array = required(root, "assets").getAsJsonArray();
        List<AnimationAsset> parsed = new ArrayList<>();
        for (JsonElement element : array) parsed.add(readAsset(element.getAsJsonObject()));
        library.clear();
        for (AnimationAsset asset : parsed) library.register(asset);
    }

    private static JsonObject writeAsset(AnimationAsset asset) {
        JsonObject out = new JsonObject();
        out.addProperty("schemaVersion", asset.schemaVersion());
        out.addProperty("id", asset.id());
        out.addProperty("name", asset.name());
        out.addProperty("rigId", asset.rigId());
        out.addProperty("duration", asset.duration());
        out.addProperty("loopMode", asset.loopMode().name());
        JsonObject tracks = new JsonObject();
        for (var entry : asset.boneTracks().entrySet()) {
            JsonObject track = new JsonObject();
            track.add("translation", writeVectors(entry.getValue().translation()));
            track.add("rotation", writeQuaternions(entry.getValue().rotation()));
            track.add("scale", writeVectors(entry.getValue().scale()));
            tracks.add(entry.getKey(), track);
        }
        out.add("boneTracks", tracks);
        JsonArray markers = new JsonArray();
        for (AnimationMarker marker : asset.markers()) {
            JsonObject value = new JsonObject();
            value.addProperty("time", marker.time());
            value.addProperty("name", marker.name());
            markers.add(value);
        }
        out.add("markers", markers);
        return out;
    }

    private static AnimationAsset readAsset(JsonObject in) {
        int version = required(in, "schemaVersion").getAsInt();
        if (version != AnimationAsset.CURRENT_SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported asset schema: " + version);
        String rigId = required(in, "rigId").getAsString();
        RigDefinition rig = RigDefinitions.find(rigId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown rig: " + rigId));
        AnimationAsset asset = new AnimationAsset(required(in, "id").getAsString(), required(in, "name").getAsString(),
                rig, required(in, "duration").getAsFloat(),
                AnimationLoopMode.valueOf(required(in, "loopMode").getAsString()));
        JsonObject tracks = required(in, "boneTracks").getAsJsonObject();
        for (var entry : tracks.entrySet()) {
            BoneTrack track = asset.getOrCreateBoneTrack(entry.getKey());
            JsonObject value = entry.getValue().getAsJsonObject();
            readVectors(required(value, "translation").getAsJsonArray(), track.translation());
            readQuaternions(required(value, "rotation").getAsJsonArray(), track.rotation());
            readVectors(required(value, "scale").getAsJsonArray(), track.scale());
            for (var frame : track.translation().keyframes()) {
                if (frame.time() > asset.duration() || !frame.value().isFinite())
                    throw new IllegalArgumentException("Invalid translation keyframe");
            }
            for (var frame : track.rotation().keyframes()) {
                if (frame.time() > asset.duration() || !frame.value().isFinite())
                    throw new IllegalArgumentException("Invalid rotation keyframe");
            }
            for (var frame : track.scale().keyframes()) {
                var scale = frame.value();
                if (frame.time() > asset.duration() || !scale.isFinite()
                        || scale.x <= 0 || scale.y <= 0 || scale.z <= 0)
                    throw new IllegalArgumentException("Invalid scale keyframe");
            }
        }
        if (in.has("markers")) {
            for (JsonElement markerElement : in.getAsJsonArray("markers")) {
                JsonObject marker = markerElement.getAsJsonObject();
                asset.addMarker(new AnimationMarker(required(marker, "time").getAsFloat(), required(marker, "name").getAsString()));
            }
        }
        return asset;
    }

    private static JsonArray writeVectors(KeyframeTrack<Vector3f> track) {
        JsonArray out = new JsonArray();
        for (ChannelKeyframe<Vector3f> frame : track.keyframes()) {
            Vector3f v = frame.value();
            JsonObject value = frame(frame.time(), frame.interpolation());
            value.addProperty("x", v.x); value.addProperty("y", v.y); value.addProperty("z", v.z);
            out.add(value);
        }
        return out;
    }

    private static JsonArray writeQuaternions(KeyframeTrack<Quaternionf> track) {
        JsonArray out = new JsonArray();
        for (ChannelKeyframe<Quaternionf> frame : track.keyframes()) {
            Quaternionf q = frame.value();
            JsonObject value = frame(frame.time(), frame.interpolation());
            value.addProperty("x", q.x); value.addProperty("y", q.y); value.addProperty("z", q.z); value.addProperty("w", q.w);
            out.add(value);
        }
        return out;
    }

    private static JsonObject frame(float time, InterpolationMode interpolation) {
        JsonObject out = new JsonObject();
        out.addProperty("time", time);
        out.addProperty("interpolation", interpolation.name());
        return out;
    }

    private static void readVectors(JsonArray values, KeyframeTrack<Vector3f> track) {
        for (JsonElement element : values) {
            JsonObject value = element.getAsJsonObject();
            track.put(required(value, "time").getAsFloat(),
                    new Vector3f(required(value, "x").getAsFloat(), required(value, "y").getAsFloat(), required(value, "z").getAsFloat()),
                    InterpolationMode.valueOf(required(value, "interpolation").getAsString()));
        }
    }

    private static void readQuaternions(JsonArray values, KeyframeTrack<Quaternionf> track) {
        for (JsonElement element : values) {
            JsonObject value = element.getAsJsonObject();
            track.put(required(value, "time").getAsFloat(),
                    new Quaternionf(required(value, "x").getAsFloat(), required(value, "y").getAsFloat(),
                            required(value, "z").getAsFloat(), required(value, "w").getAsFloat()),
                    InterpolationMode.valueOf(required(value, "interpolation").getAsString()));
        }
    }

    private static JsonElement required(JsonObject object, String name) {
        if (object == null || !object.has(name) || object.get(name).isJsonNull())
            throw new IllegalArgumentException("Missing animation asset field: " + name);
        return object.get(name);
    }

    private static Path worldFile() {
        try {
            Path save = WorldIdentifier.getWorldSavePath();
            return save == null ? null : save.resolve("director").resolve(FILE_NAME);
        } catch (RuntimeException e) {
            LOGGER.warn("[AnimationAssetPersistence] 无法解析世界目录", e);
            return null;
        }
    }
}
