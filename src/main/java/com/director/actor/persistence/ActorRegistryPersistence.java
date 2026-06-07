package com.director.actor.persistence;

import com.director.actor.Actor;
import com.director.actor.ActorGroup;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorType;
import com.director.util.WorldIdentifier;
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
import java.util.UUID;

/**
 * ActorRegistry 持久化管理器
 * 负责保存和加载 ActorRegistry 数据
 *
 * @author Director Team
 * @version 1.0
 */
public class ActorRegistryPersistence {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorRegistryPersistence.class);
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    /**
     * 获取当前世界的 ActorRegistry 文件路径
     */
    private static Path getActorRegistryFile() {
        Path worldDir = getWorldDirectorDir();
        if (worldDir != null) {
            // ✅ 最佳实践：把工程文件写进世界存档目录，天然"仅绑定当前存档"
            return worldDir.resolve("actor_registry.json");
        }
        // 不再回退到全局 config，避免跨存档串状态
        return null;
    }

    /**
     * 获取当前世界的 Director 工作目录（saves/<world>/director），不存在则返回 null。
     */
    private static Path getWorldDirectorDir() {
        try {
            Path savePath = WorldIdentifier.getWorldSavePath();
            if (savePath != null) {
                Path chronoDir = savePath.resolve("director");
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
     * ⭐ 性能优化：添加异常保护，防止保存失败导致崩溃
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
            if (file == null) {
                LOGGER.debug("[ActorRegistryPersistence] 当前无活动存档目录，跳过保存");
                return;
            }
            
            Path parent = file.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            JsonObject root = getJsonObject(registry);
            String json = GSON.toJson(root);
            
            // ⭐ 使用临时文件写入，然后原子性重命名，避免写入过程中崩溃导致文件损坏
            Path tempFile = file.resolveSibling(file.getFileName().toString() + ".tmp");
            Files.writeString(tempFile, json);
            try {
                // 尝试原子性移动（某些文件系统可能不支持）
                Files.move(tempFile, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.FileSystemException e) {
                // 如果不支持原子移动，使用普通替换
                Files.move(tempFile, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            LOGGER.info("[ActorRegistryPersistence] 保存 ActorRegistry 到文件: {} ({} 个 Actor)", file, registry.size());

        } catch (IOException e) {
            LOGGER.error("[ActorRegistryPersistence] 保存 ActorRegistry 失败", e);
        } catch (Exception e) {
            // 捕获所有异常，防止保存操作导致崩溃
            LOGGER.error("[ActorRegistryPersistence] 保存 ActorRegistry 时发生未预期的异常", e);
        }
    }

    private static @NotNull JsonObject getJsonObject(ActorRegistry registry) {
        JsonObject root = new JsonObject();
        root.addProperty("version", "1.0");
        root.addProperty("actorCount", registry.size());
        root.addProperty("groupCount", registry.groupSize());

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

        JsonArray groupsArray = new JsonArray();
        for (ActorGroup group : registry.getAllGroups()) {
            JsonObject groupObj = new JsonObject();
            groupObj.addProperty("groupId", group.getGroupId().toString());
            groupObj.addProperty("name", group.getName());
            groupObj.addProperty("formationType", group.getFormationType().name());
            groupObj.addProperty("delayPolicy", group.getDelayPolicy().name());
            groupObj.addProperty("delayStepSeconds", group.getDelayStepSeconds());

            JsonArray memberArray = new JsonArray();
            for (UUID actorId : group.getActorIds()) {
                memberArray.add(actorId.toString());
            }
            groupObj.add("actorIds", memberArray);
            groupsArray.add(groupObj);
        }
        root.add("groups", groupsArray);
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
            if (file == null) {
                LOGGER.debug("[ActorRegistryPersistence] 当前无活动存档目录，跳过加载");
                return;
            }
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

            if (root.has("groups")) {
                JsonArray groupsArray = root.getAsJsonArray("groups");
                int loadedGroupCount = 0;
                for (JsonElement element : groupsArray) {
                    try {
                        JsonObject groupObj = element.getAsJsonObject();
                        UUID groupId = UUID.fromString(groupObj.get("groupId").getAsString());
                        String name = groupObj.has("name") ? groupObj.get("name").getAsString() : "Actor Group";
                        ActorGroup group = new ActorGroup(groupId, name);

                        if (groupObj.has("formationType")) {
                            try {
                                group.setFormationType(ActorGroup.FormationType.valueOf(groupObj.get("formationType").getAsString()));
                            } catch (Exception ignored) {
                            }
                        }
                        if (groupObj.has("delayPolicy")) {
                            try {
                                group.setDelayPolicy(ActorGroup.DelayPolicy.valueOf(groupObj.get("delayPolicy").getAsString()));
                            } catch (Exception ignored) {
                            }
                        }
                        if (groupObj.has("delayStepSeconds")) {
                            group.setDelayStepSeconds(groupObj.get("delayStepSeconds").getAsFloat());
                        }
                        if (groupObj.has("actorIds")) {
                            for (JsonElement actorIdElement : groupObj.getAsJsonArray("actorIds")) {
                                try {
                                    UUID actorId = UUID.fromString(actorIdElement.getAsString());
                                    if (registry.contains(actorId)) {
                                        group.addActor(actorId);
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                        }

                        registry.registerGroup(group);
                        loadedGroupCount++;
                    } catch (Exception e) {
                        LOGGER.warn("[ActorRegistryPersistence] 加载 ActorGroup 失败，跳过", e);
                    }
                }
                LOGGER.info("[ActorRegistryPersistence] 从文件加载 ActorGroup: {} 个", loadedGroupCount);
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
        return file != null && Files.exists(file);
    }
}

