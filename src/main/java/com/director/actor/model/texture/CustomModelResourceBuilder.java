package com.director.actor.model.texture;

import com.director.actor.model.BoneCuboid;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Builds Minecraft item model JSON for a single textured {@link BoneCuboid}. */
public final class CustomModelResourceBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomModelResourceBuilder.class);
    private static final float PIXELS_PER_BLOCK = 16f;
    private static final float MIN_PIXEL = -16f;
    private static final float MAX_PIXEL = 32f;

    private CustomModelResourceBuilder() {}

    public static GeneratedItemModel build(
            String modelId,
            String pieceKey,
            BoneCuboid cuboid,
            String textureKey,
            int atlasWidth,
            int atlasHeight) {
        if (!cuboid.usesCustomTexture()) {
            throw new IllegalArgumentException("Cuboid does not define a custom texture");
        }
        String safeModelId = sanitizePath(modelId);
        String safeTextureKey = sanitizePath(textureKey);
        String safePieceKey = sanitizePath(pieceKey);
        String texturePath = "custom_model/" + safeModelId + "/" + safeTextureKey;
        String modelPath = "custom_model_piece/" + safePieceKey;
        Identifier textureId = Identifier.of("director", texturePath);
        Identifier itemModelId = Identifier.of("director", modelPath);
        for (String warning : geometryWarnings(cuboid)) {
            LOGGER.warn("[CustomModelResourceBuilder] {}", warning);
        }
        String json = buildModelJson(cuboid, texturePath, atlasWidth, atlasHeight);
        return new GeneratedItemModel(itemModelId, textureId, json);
    }

    /** Returns warnings for cuboid geometry that must be clamped into Minecraft item model bounds. */
    public static List<String> geometryWarnings(BoneCuboid cuboid) {
        if (cuboid == null) {
            return List.of();
        }
        List<String> warnings = new ArrayList<>();
        Vector3f from = toPixelCorner(cuboid.offset(), cuboid.size(), false);
        Vector3f to = toPixelCorner(cuboid.offset(), cuboid.size(), true);
        recordCornerClamp(warnings, cuboid.boneId(), "from", from);
        recordCornerClamp(warnings, cuboid.boneId(), "to", to);
        return Collections.unmodifiableList(warnings);
    }

    public static String contentKey(
            BoneCuboid cuboid, int atlasWidth, int atlasHeight, String textureContentHash) {
        CuboidFaceUvs faces = cuboid.faceUvs();
        String payload = vector(cuboid.offset()) + "|" + vector(cuboid.size()) + "|"
                + face(faces == null ? null : faces.north()) + "|"
                + face(faces == null ? null : faces.south()) + "|"
                + face(faces == null ? null : faces.east()) + "|"
                + face(faces == null ? null : faces.west()) + "|"
                + face(faces == null ? null : faces.up()) + "|"
                + face(faces == null ? null : faces.down()) + "|"
                + atlasWidth + "|" + atlasHeight + "|" + textureContentHash;
        return sha256(payload);
    }

    public static String pieceKeyFromContentKey(String contentKey) {
        return contentKey.substring(0, 16);
    }

    /** @deprecated Prefer {@link #contentKey} + {@link #pieceKeyFromContentKey} for cache-aware dedup. */
    @Deprecated
    public static String pieceKey(BoneCuboid cuboid, String modelId, String textureKey) {
        CuboidFaceUvs faces = cuboid.faceUvs();
        String payload = modelId + "|" + textureKey + "|"
                + cuboid.boneId() + "|" + vector(cuboid.offset()) + "|" + vector(cuboid.size()) + "|"
                + face(faces == null ? null : faces.north()) + "|"
                + face(faces == null ? null : faces.south()) + "|"
                + face(faces == null ? null : faces.east()) + "|"
                + face(faces == null ? null : faces.west()) + "|"
                + face(faces == null ? null : faces.up()) + "|"
                + face(faces == null ? null : faces.down());
        return sha256(payload).substring(0, 16);
    }

    static String buildModelJson(BoneCuboid cuboid, String texturePath, int atlasWidth, int atlasHeight) {
        JsonObject root = new JsonObject();
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", "director:" + texturePath);
        textures.addProperty("0", "director:" + texturePath);
        root.add("textures", textures);

        JsonObject element = new JsonObject();
        JsonArray from = pixelArray(toPixelCorner(cuboid.offset(), cuboid.size(), false));
        JsonArray to = pixelArray(toPixelCorner(cuboid.offset(), cuboid.size(), true));
        element.add("from", from);
        element.add("to", to);

        JsonObject faces = new JsonObject();
        addFace(faces, "north", cuboid.faceUvs() == null ? null : cuboid.faceUvs().north(), atlasWidth, atlasHeight);
        addFace(faces, "south", cuboid.faceUvs() == null ? null : cuboid.faceUvs().south(), atlasWidth, atlasHeight);
        addFace(faces, "east", cuboid.faceUvs() == null ? null : cuboid.faceUvs().east(), atlasWidth, atlasHeight);
        addFace(faces, "west", cuboid.faceUvs() == null ? null : cuboid.faceUvs().west(), atlasWidth, atlasHeight);
        addFace(faces, "up", cuboid.faceUvs() == null ? null : cuboid.faceUvs().up(), atlasWidth, atlasHeight);
        addFace(faces, "down", cuboid.faceUvs() == null ? null : cuboid.faceUvs().down(), atlasWidth, atlasHeight);
        element.add("faces", faces);

        JsonArray elements = new JsonArray();
        elements.add(element);
        root.add("elements", elements);
        return root.toString();
    }

    private static void addFace(JsonObject faces, String name, float[] uv, int atlasWidth, int atlasHeight) {
        if (uv == null || uv.length < 4) {
            return;
        }
        JsonObject face = new JsonObject();
        face.add("uv", uvArray(normalizeUv(uv, atlasWidth, atlasHeight)));
        face.addProperty("texture", "#0");
        faces.add(name, face);
    }

    private static float[] normalizeUv(float[] uv, int atlasWidth, int atlasHeight) {
        if (atlasWidth <= 0 || atlasHeight <= 0) {
            return uv;
        }
        return new float[] {
                uv[0],
                atlasHeight - uv[3],
                uv[2],
                atlasHeight - uv[1]
        };
    }

    private static JsonArray pixelArray(Vector3f vector) {
        JsonArray array = new JsonArray();
        array.add(clampPixel(vector.x));
        array.add(clampPixel(vector.y));
        array.add(clampPixel(vector.z));
        return array;
    }

    private static JsonArray uvArray(float[] uv) {
        JsonArray array = new JsonArray();
        array.add(uv[0]);
        array.add(uv[1]);
        array.add(uv[2]);
        array.add(uv[3]);
        return array;
    }

    private static float clampPixel(float value) {
        return Math.max(MIN_PIXEL, Math.min(MAX_PIXEL, value));
    }

    private static void recordCornerClamp(List<String> warnings, String boneId, String corner, Vector3f vector) {
        recordAxisClamp(warnings, boneId, corner + ".x", vector.x);
        recordAxisClamp(warnings, boneId, corner + ".y", vector.y);
        recordAxisClamp(warnings, boneId, corner + ".z", vector.z);
    }

    private static void recordAxisClamp(List<String> warnings, String boneId, String axis, float value) {
        float clamped = clampPixel(value);
        if (Float.compare(clamped, value) != 0) {
            warnings.add(String.format(
                    Locale.ROOT,
                    "bone \"%s\" %s=%.3f 超出物品模型像素范围 [%.0f, %.0f]，已钳制为 %.3f",
                    boneId,
                    axis,
                    value,
                    MIN_PIXEL,
                    MAX_PIXEL,
                    clamped));
        }
    }

    private static Vector3f toPixelCorner(Vector3f offset, Vector3f size, boolean end) {
        if (!end) {
            return new Vector3f(
                    offset.x * PIXELS_PER_BLOCK,
                    offset.y * PIXELS_PER_BLOCK,
                    offset.z * PIXELS_PER_BLOCK);
        }
        return new Vector3f(
                (offset.x + size.x) * PIXELS_PER_BLOCK,
                (offset.y + size.y) * PIXELS_PER_BLOCK,
                (offset.z + size.z) * PIXELS_PER_BLOCK);
    }

    private static String vector(Vector3f vector) {
        return String.format(Locale.ROOT, "%.5f,%.5f,%.5f", vector.x, vector.y, vector.z);
    }

    private static String face(float[] uv) {
        if (uv == null || uv.length < 4) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.3f,%.3f,%.3f,%.3f", uv[0], uv[1], uv[2], uv[3]);
    }

    private static String sanitizePath(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-/]", "_");
    }

    private static String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
