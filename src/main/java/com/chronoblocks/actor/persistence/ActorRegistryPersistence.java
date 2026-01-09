package com.chronoblocks.actor.persistence;

import com.chronoblocks.actor.Actor;
import com.chronoblocks.actor.ActorRegistry;
import com.chronoblocks.actor.ActorType;
import com.chronoblocks.util.WorldIdentifier;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * ActorRegistry 持久化管理器
 * 负责保存和加载 ActorRegistry 数据
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class ActorRegistryPersistence {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorRegistryPersistence.class);
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    // 配置文件路径
    private static final Path CONFIG_DIR = Paths.get("config", "chronoblocks");

    /**
     * 获取当前世界的 ActorRegistry 文件路径
     */
    private static Path getActorRegistryFile() {
        Path worldDir = getWorldChronoBlocksDir();
        if (worldDir != null) {
            // ✅ 最佳实践：把工程文件写进世界存档目录，天然"仅绑定当前存档"
            return worldDir.resolve("actor_registry.json");
        }
        // 兜底：写入 config（用于无法获取世界目录的场景）
        String worldId = WorldIdentifier.getCurrentWorldId();
        return CONFIG_DIR.resolve("actors").resolve(worldId + "_actor_registry.json");
    }

    /**
     * 获取当前世界的 ChronoBlocks 工作目录（saves/<world>/chronoblocks），不存在则返回 null。
     */
    private static Path getWorldChronoBlocksDir() {
        try {
            Path savePath = WorldIdentifier.getWorldSavePath();
            if (savePath != null) {
                Path chronoDir = savePath.resolve("chronoblocks");
                if (!Files.exists(chronoDir)) {
                    Files.createDirectories(chronoDir);
                }
                return chronoDir;
            }
        } catch (Exception e) {
            LOGGER.warn("[ActorRegistryPersistence] 无法获取世界目录", e);
        }
        return null;
    }

    /**
     * 保存 ActorRegistry 到文件
     *
     * @param registry ActorRegistry
     */
    public static void saveToFile(ActorRegistry registry) {
        if (registry == null) {
            LOGGER.warn("[ActorRegistryPersistence] 尝试保存 null ActorRegistry");
            return;
        }

        try {
            Path file = getActorRegistryFile();
            Path parent = file.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            JsonObject root = getJsonObject(registry);

            String json = GSON.toJson(root);
            Files.writeString(file, json);

            LOGGER.info("[ActorRegistryPersistence] 保存 ActorRegistry 到文件: {} ({} 个 Actor)", file, registry.size());

        } catch (IOException e) {
            LOGGER.error("[ActorRegistryPersistence] 保存 ActorRegistry 失败", e);
        }
    }

    private static @NotNull JsonObject getJsonObject(ActorRegistry registry) {
        JsonObject root = new JsonObject();
        root.addProperty("version", "1.0");
        root.addProperty("actorCount", registry.size());

        JsonArray actorsArray = new JsonArray();
        for (Actor actor : registry.getAllActors()) {
            JsonObject actorObj = new JsonObject();
            actorObj.addProperty("actorId", actor.getActorId().toString());
            actorObj.addProperty("name", actor.getName());
            actorObj.addProperty("type", actor.getType().name());
            // 未来可以扩展更多字段（如 modelId）
            actorsArray.add(actorObj);
        }
        root.add("actors", actorsArray);
        return root;
    }

    /**
     * 从文件加载 ActorRegistry
     *
     * @param registry ActorRegistry（将加载的数据填充到此实例）
     */
    public static void loadFromFile(ActorRegistry registry) {
        if (registry == null) {
            LOGGER.warn("[ActorRegistryPersistence] 尝试加载到 null ActorRegistry");
            return;
        }

        try {
            Path file = getActorRegistryFile();
            if (!Files.exists(file)) {
                LOGGER.debug("[ActorRegistryPersistence] ActorRegistry 文件不存在: {}", file);
                return;
            }

            String json = Files.readString(file);
            JsonObject root = GSON.fromJson(json, JsonObject.class);

            String version = root.has("version") ? root.get("version").getAsString() : "1.0";
            LOGGER.debug("[ActorRegistryPersistence] 加载 ActorRegistry 版本: {}", version);

            if (root.has("actors")) {
                JsonArray actorsArray = root.getAsJsonArray("actors");
                int loadedCount = 0;

                for (JsonElement element : actorsArray) {
                    try {
                        JsonObject actorObj = element.getAsJsonObject();
                        UUID actorId = UUID.fromString(actorObj.get("actorId").getAsString());
                        String name = actorObj.get("name").getAsString();
                        String typeStr = actorObj.get("type").getAsString();
                        ActorType type = ActorType.valueOf(typeStr);

                        Actor actor = new Actor(actorId, name, type);
                        registry.registerActor(actor);
                        loadedCount++;

                    } catch (Exception e) {
                        LOGGER.warn("[ActorRegistryPersistence] 加载 Actor 失败，跳过", e);
                    }
                }

                LOGGER.info("[ActorRegistryPersistence] 从文件加载 ActorRegistry: {} ({} 个 Actor)", file, loadedCount);
            }

        } catch (IOException e) {
            LOGGER.error("[ActorRegistryPersistence] 加载 ActorRegistry 失败", e);
        } catch (Exception e) {
            LOGGER.error("[ActorRegistryPersistence] 解析 ActorRegistry 文件失败", e);
        }
    }

    /**
     * 检查是否有保存的 ActorRegistry 文件
     *
     * @return 如果文件存在则返回 true
     */
    public static boolean hasSavedData() {
        Path file = getActorRegistryFile();
        return Files.exists(file);
    }
}

