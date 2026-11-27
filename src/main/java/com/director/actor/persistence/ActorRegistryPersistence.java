package com.director.actor.persistence;

import com.director.actor.Actor;
import com.director.actor.ActorGroup;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorType;
import com.director.actor.rig.RigProfileStyle;
import com.director.persistence.ActorLibrarySaveGuard;
import com.director.persistence.AtomicFileWriter;
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

    private static final ActorLibrarySaveGuard SAVE_GUARD = new ActorLibrarySaveGuard();
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
     * @return 是否成功写入当前存档目录
     */
    public static boolean saveToFile(ActorRegistry registry) {
        return saveToFile(registry, getActorRegistryFile());
    }

    /** Saves to an explicit registry file (used when flushing the previous world after a switch). */
    public static boolean saveToFile(ActorRegistry registry, Path file) {
        if (registry == null || file == null) return false;
        try {
            return writeSnapshot(file, snapshot(registry));
        } catch (RuntimeException e) {
            LOGGER.error("[ActorRegistryPersistence] Unable to capture actor registry", e);
            return false;
        }
    }

    public static String snapshot(ActorRegistry registry) {
        return GSON.toJson(getJsonObject(registry));
    }

    public static boolean writeSnapshot(Path file, String snapshot) {
        try {
            SAVE_GUARD.requireWritable(file);
            AtomicFileWriter.writeString(file, snapshot);
            return true;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[ActorRegistryPersistence] Unable to save {}", file, e);
            return false;
        }
    }

    public static Path currentRegistryFile() {
        return getActorRegistryFile();
    }

    static @NotNull JsonObject getJsonObject(ActorRegistry registry) {
        JsonObject root = new JsonObject();
        root.addProperty("version", "1.0");

        JsonArray actorsArray = new JsonArray();
        for (Actor actor : registry.getAllActors()) {
            JsonObject actorObj = new JsonObject();
            actorObj.addProperty("actorId", actor.getActorId().toString());
            actorObj.addProperty("name", actor.getName());
            actorObj.addProperty("type", actor.getType().name());
            actorObj.addProperty("playerModel", actor.getPlayerModel().name());
            actorObj.addProperty("limbBendMode", actor.getLimbBendMode().name());
            if (actor.getSkinFileName() != null) {
                actorObj.addProperty("skinFileName", actor.getSkinFileName());
            }
            if (actor.getCustomRigId() != null) {
                actorObj.addProperty("customRigId", actor.getCustomRigId());
            }
            if (actor.getCustomModelId() != null) {
                actorObj.addProperty("customModelId", actor.getCustomModelId());
            }
            actorObj.add("style", writeStyle(actor.getRigProfile().getStyle()));
            actorsArray.add(actorObj);
        }
        root.add("actors", actorsArray);

        JsonArray groupsArray = new JsonArray();
        for (ActorGroup group : registry.getAllGroups()) {
            JsonObject groupObj = new JsonObject();
            groupObj.addProperty("groupId", group.getGroupId().toString());
            groupObj.addProperty("name", group.getName());
            groupObj.addProperty("formationType", group.getFormationType().name());
            groupObj.addProperty("formationSpacing", group.getFormationSpacing());
            groupObj.addProperty("delayPolicy", group.getDelayPolicy().name());
            groupObj.addProperty("delayStepSeconds", group.getDelayStepSeconds());
            groupObj.addProperty("randomSeed", group.getRandomSeed());

            JsonArray memberArray = new JsonArray();
            for (UUID actorId : group.getActorIds()) {
                memberArray.add(actorId.toString());
            }
            groupObj.add("actorIds", memberArray);
            groupsArray.add(groupObj);
        }
        root.add("groups", groupsArray);
        root.addProperty("actorCount", actorsArray.size());
        root.addProperty("groupCount", groupsArray.size());
        return root;
    }

    /**
     * 从文件加载 ActorRegistry
     *
     * @param registry ActorRegistry（将加载的数据填充到此实例）
     */
    public static void loadFromFile(ActorRegistry registry) {
        loadFromFile(registry, getActorRegistryFile());
    }

    public static void loadFromFile(ActorRegistry registry, Path file) {
        if (registry == null) {
            LOGGER.warn("[ActorRegistryPersistence] 尝试加载到 null ActorRegistry");
            return;
        }

        if (file == null || Files.notExists(file)) {
            return;
        }
        try {
            String json = Files.readString(file);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null || !root.isJsonObject()) {
                throw new IllegalArgumentException("Actor registry root must be a JSON object");
            }
            JsonArray actorsArray = root.has("actors") && root.get("actors").isJsonArray()
                    ? root.getAsJsonArray("actors") : new JsonArray();
            JsonArray groupsArray = root.has("groups") && root.get("groups").isJsonArray()
                    ? root.getAsJsonArray("groups") : new JsonArray();
            int actorCount = actorsArray.size();
            int groupCount = groupsArray.size();
            ActorRegistry loaded = new ActorRegistry();
            loadFromJson(loaded, root);
            if (loaded.size() != actorCount || loaded.groupSize() != groupCount) {
                throw new IllegalArgumentException("Actor registry contains invalid or duplicate entries");
            }
            registry.clear();
            loaded.getAllActors().forEach(registry::registerActor);
            loaded.getAllGroups().forEach(registry::registerGroup);
            SAVE_GUARD.loaded(file);
        } catch (IOException e) {
            SAVE_GUARD.loadFailed(file);
            LOGGER.error("[ActorRegistryPersistence] 加载 ActorRegistry 失败", e);
        } catch (RuntimeException e) {
            SAVE_GUARD.loadFailed(file);
            LOGGER.error("[ActorRegistryPersistence] 解析 ActorRegistry 文件失败", e);
        }
    }

    static void loadFromJson(ActorRegistry registry, JsonObject root) {
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
                    if (actorObj.has("limbBendMode")) {
                        try {
                            actor.setLimbBendMode(com.director.actor.rig.skeleton.LimbBendMode.valueOf(
                                    actorObj.get("limbBendMode").getAsString()));
                        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException invalid) {
                            LOGGER.warn("[ActorRegistryPersistence] 演员 {} 的肢体弯曲模式无效，使用默认值", actorId);
                        }
                    }
                    if (actorObj.has("playerModel") && !actorObj.get("playerModel").isJsonNull()) {
                        try {
                            actor.setPlayerModel(Actor.PlayerModel.valueOf(actorObj.get("playerModel").getAsString()));
                        } catch (IllegalArgumentException | IllegalStateException ignored) {
                            // Unknown model values retain the legacy default appearance.
                        }
                    }
                    if (actorObj.has("skinFileName")) {
                        try {
                            String skinFileName = actorObj.get("skinFileName").getAsString();
                            actor.setSkinFileName(skinFileName);
                        } catch (Exception ignored) {
                            // 旧版本或异常字段时忽略皮肤信息，保证兼容性
                        }
                    }
                    if (actorObj.has("customRigId")) {
                        try {
                            actor.setCustomRigId(actorObj.get("customRigId").getAsString());
                        } catch (Exception ignored) {
                            // 旧版本或异常字段时忽略自定义骨架绑定
                        }
                    }
                    if (actorObj.has("customModelId")) {
                        try {
                            actor.setCustomModelId(actorObj.get("customModelId").getAsString());
                        } catch (Exception ignored) {
                            // 旧版本或异常字段时忽略自定义模型绑定
                        }
                    }
                    if (actorObj.has("style") && actorObj.get("style").isJsonObject()) {
                        readStyle(actor.getRigProfile().getStyle(), actorObj.getAsJsonObject("style"));
                    }
                    registry.registerActor(actor);
                    loadedCount++;

                } catch (Exception e) {
                    LOGGER.warn("[ActorRegistryPersistence] 加载 Actor 失败，跳过", e);
                }
            }

            LOGGER.info("[ActorRegistryPersistence] 加载 ActorRegistry: {} 个 Actor", loadedCount);
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
                    if (groupObj.has("randomSeed")) group.setRandomSeed(groupObj.get("randomSeed").getAsLong());

                    if (groupObj.has("formationType")) {
                        try {
                            group.setFormationType(ActorGroup.FormationType.valueOf(groupObj.get("formationType").getAsString()));
                        } catch (Exception ignored) {
                        }
                    }
                    if (groupObj.has("formationSpacing")) {
                        group.setFormationSpacing(groupObj.get("formationSpacing").getAsFloat());
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

    }

    /**
     * 检查是否有保存的 ActorRegistry 文件
     *
     * @return 如果文件存在则返回 true
     */
    public static boolean hasSavedData() {
        Path file = getActorRegistryFile();
        return file != null && !Files.notExists(file);
    }

    private static JsonObject writeStyle(RigProfileStyle style) {
        JsonObject out = new JsonObject();
        out.addProperty("motionScale", style.motionScale);
        out.addProperty("tempo", style.tempo);
        out.addProperty("leanBias", style.leanBias);
        out.addProperty("armExpressiveness", style.armExpressiveness);
        out.addProperty("legExpressiveness", style.legExpressiveness);
        return out;
    }

    private static void readStyle(RigProfileStyle style, JsonObject in) {
        style.motionScale = readStyleFloat(in, "motionScale", style.motionScale, 0.5f, 1.5f);
        style.tempo = readStyleFloat(in, "tempo", style.tempo, 0.7f, 1.3f);
        style.leanBias = readStyleFloat(in, "leanBias", style.leanBias, 0.5f, 1.5f);
        style.armExpressiveness = readStyleFloat(in, "armExpressiveness", style.armExpressiveness, 0.3f, 1.5f);
        style.legExpressiveness = readStyleFloat(in, "legExpressiveness", style.legExpressiveness, 0.3f, 1.5f);
    }

    private static float readStyleFloat(JsonObject in, String key, float fallback, float min, float max) {
        if (!in.has(key) || !in.get(key).isJsonPrimitive()) return fallback;
        try {
            float value = in.get(key).getAsFloat();
            if (!Float.isFinite(value)) return fallback;
            return Math.clamp(value, min, max);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}

