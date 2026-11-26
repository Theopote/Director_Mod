package com.director.actor.skin;

import com.director.actor.Actor;
import com.director.actor.ActorRegistryManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * ActorSkinTextureManager
 *
 * <p>负责从磁盘加载演员皮肤 PNG，并将其注册为 Minecraft 纹理。
 */
public final class ActorSkinTextureManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorSkinTextureManager.class);
    private static final String NAMESPACE = "director";
    private static final String PATH_PREFIX = "actor_skins/";

    private static final Map<UUID, Identifier> CACHE = new HashMap<>();
    /** Failed (actorId, skinFileName) pairs — skip disk I/O until invalidate/clear. */
    private static final Set<String> FAILED = new HashSet<>();

    private ActorSkinTextureManager() {
    }

    /**
     * 获取或加载指定演员的皮肤纹理 Identifier。
     *
     * @return Identifier，如果没有皮肤或加载失败则返回 null
     */
    public static Identifier getOrLoad(UUID actorId) {
        Objects.requireNonNull(actorId, "actorId");

        Identifier cached = CACHE.get(actorId);
        if (cached != null) {
            return cached;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getTextureManager() == null) {
            return null;
        }

        Optional<Actor> actorOpt = ActorRegistryManager.getInstance().getActor(actorId);
        if (actorOpt.isEmpty()) {
            return null;
        }
        Actor actor = actorOpt.get();
        String skinFileName = actor.getSkinFileName();
        if (skinFileName == null || skinFileName.isBlank()) {
            return null;
        }
        String failKey = failKey(actorId, skinFileName);
        if (FAILED.contains(failKey)) {
            return null;
        }

        Path skinPath = resolveSkinPath(actorId, skinFileName);
        if (skinPath == null || !Files.exists(skinPath)) {
            markFailed(failKey, skinPath == null ? "path rejected or missing" : "file missing: " + skinPath);
            return null;
        }

        TextureManager textureManager = client.getTextureManager();
        Identifier id = Identifier.of(NAMESPACE, PATH_PREFIX + actorId);

        NativeImage image = null;
        NativeImageBackedTexture texture = null;
        try {
            try (var in = Files.newInputStream(skinPath)) {
                image = NativeImage.read(in);
            }

            Supplier<String> nameSupplier = () -> NAMESPACE + ":" + PATH_PREFIX + actorId;
            texture = new NativeImageBackedTexture(nameSupplier, image);
            textureManager.registerTexture(id, texture);
            CACHE.put(actorId, id);
            FAILED.remove(failKey);
            LOGGER.debug("[ActorSkinTextureManager] 注册皮肤纹理: {} -> {}", skinPath, id);
            return id;
        } catch (IOException e) {
            markFailed(failKey, "read failed: " + skinPath);
            LOGGER.error("[ActorSkinTextureManager] 读取皮肤纹理失败: {}", skinPath, e);
            return null;
        } catch (RuntimeException e) {
            markFailed(failKey, "register failed: " + skinPath);
            LOGGER.error("[ActorSkinTextureManager] 注册皮肤纹理失败: {}", skinPath, e);
            return null;
        } finally {
            if (!CACHE.containsKey(actorId)) {
                if (texture != null) {
                    try {
                        texture.close();
                    } catch (Exception ignored) {
                        // best-effort cleanup
                    }
                } else if (image != null) {
                    try {
                        image.close();
                    } catch (Exception ignored) {
                        // best-effort cleanup
                    }
                }
            }
        }
    }

    /**
     * 失效指定演员的纹理缓存，下次调用 {@link #getOrLoad(UUID)} 时会重新加载。
     */
    public static void invalidate(UUID actorId) {
        if (actorId == null) {
            return;
        }
        FAILED.removeIf(key -> key.startsWith(actorId + "\0"));
        Identifier id = CACHE.remove(actorId);
        destroyTexture(id);
    }

    /** Destroy every dynamic skin texture and clear positive/negative caches (world switch). */
    public static void clearAll() {
        for (Identifier id : new HashMap<>(CACHE).values()) {
            destroyTexture(id);
        }
        CACHE.clear();
        FAILED.clear();
    }

    private static void destroyTexture(Identifier id) {
        if (id == null) return;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getTextureManager() != null) {
                client.getTextureManager().destroyTexture(id);
            }
        } catch (Exception e) {
            LOGGER.debug("[ActorSkinTextureManager] 销毁纹理失败: {}", id, e);
        }
    }

    private static void markFailed(String failKey, String detail) {
        if (FAILED.add(failKey)) {
            LOGGER.warn("[ActorSkinTextureManager] 皮肤加载失败，已负缓存: {}", detail);
        }
    }

    private static String failKey(UUID actorId, String skinFileName) {
        return actorId + "\0" + skinFileName;
    }

    static Path resolveSkinPath(UUID actorId, String skinFileName) {
        if (skinFileName != null && !skinFileName.isBlank()) {
            Path root = com.director.util.WorldIdentifier.getWorldSavePath();
            if (root != null) {
                Path dir = root.resolve("director").normalize();
                Path resolved = dir.resolve(skinFileName).normalize();
                if (!resolved.startsWith(dir)) {
                    LOGGER.warn("[ActorSkinTextureManager] 拒绝越界皮肤路径: {}", skinFileName);
                    return null;
                }
                return resolved;
            }
        }
        return ActorSkinStorage.getSkinFile(actorId);
    }
}
