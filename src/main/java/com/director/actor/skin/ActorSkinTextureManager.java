package com.director.actor.skin;

import com.director.actor.Actor;
import com.director.actor.ActorRegistry;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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

    private ActorSkinTextureManager() {
    }

    /**
     * 获取或加载指定演员的皮肤纹理 Identifier。
     *
     * @return Identifier，如果没有皮肤或加载失败则返回 null
     */
    public static Identifier getOrLoad(UUID actorId) {
        Objects.requireNonNull(actorId, "actorId");

        // 如果缓存中已有，直接返回
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

        Path skinPath = resolveSkinPath(actorId, skinFileName);
        if (skinPath == null || !Files.exists(skinPath)) {
            return null;
        }

        TextureManager textureManager = client.getTextureManager();
        Identifier id = Identifier.of(NAMESPACE, PATH_PREFIX + actorId);

        try {
            NativeImage image = NativeImage.read(Files.newInputStream(skinPath));
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            textureManager.registerTexture(id, texture);
            CACHE.put(actorId, id);
            LOGGER.debug("[ActorSkinTextureManager] 注册皮肤纹理: {} -> {}", skinPath, id);
            return id;
        } catch (IOException e) {
            LOGGER.error("[ActorSkinTextureManager] 读取皮肤纹理失败: {}", skinPath, e);
            return null;
        }
    }

    /**
     * 失效指定演员的纹理缓存，下次调用 {@link #getOrLoad(UUID)} 时会重新加载。
     */
    public static void invalidate(UUID actorId) {
        if (actorId == null) {
            return;
        }
        Identifier id = CACHE.remove(actorId);
        if (id != null) {
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.getTextureManager() != null) {
                    client.getTextureManager().destroyTexture(id);
                }
            } catch (Exception e) {
                LOGGER.debug("[ActorSkinTextureManager] 销毁纹理失败: {}", id, e);
            }
        }
    }

    private static Path resolveSkinPath(UUID actorId, String skinFileName) {
        // 优先使用 Actor.skinFileName 提供的相对路径；若为空则退回到默认路径。
        if (skinFileName != null && !skinFileName.isBlank()) {
            // 使用 ActorSkinStorage 中的世界目录解析，保证与存档绑定。
            Path root = com.director.util.WorldIdentifier.getWorldSavePath();
            if (root != null) {
                Path dir = root.resolve("director");
                return dir.resolve(skinFileName).normalize();
            }
        }
        // 兜底：根据 actorId 计算默认路径
        return ActorSkinStorage.getSkinFile(actorId);
    }
}

