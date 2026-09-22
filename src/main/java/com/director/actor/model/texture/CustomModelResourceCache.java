package com.director.actor.model.texture;

import com.director.actor.model.BoneCuboid;
import com.director.persistence.AtomicFileWriter;
import com.director.util.WorldIdentifier;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Content-hash cache for generated custom model item resources. */
public final class CustomModelResourceCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomModelResourceCache.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int FILE_SCHEMA_VERSION = 1;
    private static final String FILE_NAME = "index.json";
    private static final CustomModelResourceCache INSTANCE = new CustomModelResourceCache();

    private final Map<String, CacheEntry> entries = new LinkedHashMap<>();
    private volatile String loadedWorldKey;

    private CustomModelResourceCache() {}

    public static CustomModelResourceCache getInstance() { return INSTANCE; }

    public record CacheEntry(
            String pieceKey,
            String itemModelId,
            String modelId,
            String textureId,
            String textureContentHash,
            int atlasWidth,
            int atlasHeight
    ) {}

    public synchronized void loadForCurrentWorld() {
        String worldKey = WorldIdentifier.getCurrentWorldId();
        if (worldKey.equals(loadedWorldKey)) {
            return;
        }
        entries.clear();
        loadedWorldKey = worldKey;
        Path file = worldFile();
        if (file == null || !Files.exists(file)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (required(root, "schemaVersion").getAsInt() != FILE_SCHEMA_VERSION) {
                LOGGER.warn("[CustomModelResourceCache] 不支持的缓存版本: {}", file);
                return;
            }
            JsonObject stored = required(root, "entries").getAsJsonObject();
            for (String contentKey : stored.keySet()) {
                JsonObject entry = stored.getAsJsonObject(contentKey);
                entries.put(contentKey, new CacheEntry(
                        required(entry, "pieceKey").getAsString(),
                        required(entry, "itemModelId").getAsString(),
                        required(entry, "modelId").getAsString(),
                        required(entry, "textureId").getAsString(),
                        required(entry, "textureContentHash").getAsString(),
                        required(entry, "atlasWidth").getAsInt(),
                        required(entry, "atlasHeight").getAsInt()));
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("[CustomModelResourceCache] 加载缓存失败: {}", file, e);
        }
    }

    public synchronized Optional<CacheEntry> find(String contentKey) {
        return Optional.ofNullable(entries.get(contentKey));
    }

    public synchronized Optional<int[]> findAtlasForModel(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return Optional.empty();
        }
        for (CacheEntry entry : entries.values()) {
            if (modelId.equals(entry.modelId())) {
                return Optional.of(new int[] {entry.atlasWidth(), entry.atlasHeight()});
            }
        }
        return Optional.empty();
    }

    public synchronized CacheEntry resolve(
            BoneCuboid cuboid,
            String modelId,
            String textureId,
            String textureContentHash,
            int atlasWidth,
            int atlasHeight) {
        String contentKey = CustomModelResourceBuilder.contentKey(
                cuboid, atlasWidth, atlasHeight, textureContentHash);
        CacheEntry existing = entries.get(contentKey);
        if (existing != null) {
            return existing;
        }
        String pieceKey = CustomModelResourceBuilder.pieceKeyFromContentKey(contentKey);
        CacheEntry created = new CacheEntry(
                pieceKey,
                itemModelId(pieceKey),
                modelId,
                textureId,
                textureContentHash,
                atlasWidth,
                atlasHeight);
        entries.put(contentKey, created);
        save();
        return created;
    }

    public static String hashFile(Path file) throws IOException {
        return sha256(Files.readAllBytes(file));
    }

    public static String itemModelId(String pieceKey) {
        return "director:custom_model_piece/" + pieceKey;
    }

    public static boolean hasRuntimeArtifacts(CacheEntry entry) {
        if (entry == null) {
            return false;
        }
        Path modelFile = DirectorRuntimeResourcePack.modelPath(entry.pieceKey());
        return Files.isRegularFile(modelFile);
    }

    private synchronized void save() {
        Path file = worldFile();
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("schemaVersion", FILE_SCHEMA_VERSION);
            JsonObject stored = new JsonObject();
            for (Map.Entry<String, CacheEntry> entry : entries.entrySet()) {
                CacheEntry value = entry.getValue();
                JsonObject object = new JsonObject();
                object.addProperty("pieceKey", value.pieceKey());
                object.addProperty("itemModelId", value.itemModelId());
                object.addProperty("modelId", value.modelId());
                object.addProperty("textureId", value.textureId());
                object.addProperty("textureContentHash", value.textureContentHash());
                object.addProperty("atlasWidth", value.atlasWidth());
                object.addProperty("atlasHeight", value.atlasHeight());
                stored.add(entry.getKey(), object);
            }
            root.add("entries", stored);
            AtomicFileWriter.writeString(file, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.warn("[CustomModelResourceCache] 保存缓存失败: {}", file, e);
        }
    }

    private static Path worldFile() {
        Path save = WorldIdentifier.getWorldSavePath();
        return save == null ? null : save.resolve("director").resolve("custom_model_cache").resolve(FILE_NAME);
    }

    private static JsonElement required(JsonObject object, String name) {
        if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
            throw new IllegalArgumentException("Missing custom model cache field: " + name);
        }
        return object.get(name);
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
