package com.director.core.container.persistence;

import com.director.core.container.AnimationMode;
import com.director.core.container.BlockStore;
import com.director.core.container.Container;
import com.director.core.container.ContainerAnimationPreset;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerState;
import com.director.core.container.ContainerType;
import com.director.persistence.AtomicFileWriter;
import com.director.persistence.PersistenceSaveStatus;
import com.director.timeline.container.ContainerTimelineService;
import com.director.util.WorldIdentifier;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 容器持久化管理：按存档（世界）保存/加载容器与方块数据。
 */
public final class ContainerPersistence {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerPersistence.class);
    private static final Path CONFIG_DIR = Paths.get("config", "director", "containers");
    private static final long AUTO_SAVE_DELAY_MS = 1200L;
    private static volatile boolean loading = false;
    private static volatile boolean pendingAutoSave = false;
    private static volatile long lastAutoSaveRequestMs = 0L;
    /** 恢复映射所属世界；同一世界重复加载时保留尚未被时间线消费的映射。 */
    private static Path recoveryMappingWorldPath = null;

    private ContainerPersistence() {}

    private static Path getContainerFile() {
        Path worldDir = getWorldDirectorDir();
        if (worldDir != null) {
            return worldDir.resolve("containers.dat");
        }
        return null;
    }

    private static Path getLegacyContainerFile() {
        String worldId = WorldIdentifier.getCurrentWorldId();
        if (worldId == null || worldId.isEmpty() || "default".equals(worldId)) {
            return null;
        }
        return CONFIG_DIR.resolve(worldId + "_containers.dat");
    }

    private static Path getWorldDirectorDir() {
        try {
            Path savePath = WorldIdentifier.getWorldSavePath();
            if (savePath != null) {
                return savePath.resolve("director");
            }
        } catch (Exception e) {
            LOGGER.debug("[ContainerPersistence] 无法解析当前世界存档目录", e);
        }
        return null;
    }

    /**
     * @return 是否成功写入当前存档目录；跳过保存时视为无失败
     */
    public static boolean saveForCurrentWorld(ContainerManager manager) {
        if (manager == null || loading) {
            return true;
        }
        try {
            Path file = getContainerFile();
            if (file == null) {
                LOGGER.debug("[ContainerPersistence] 当前无活动存档目录，跳过保存");
                PersistenceSaveStatus.reportContainerResult(false);
                return false;
            }
            if (!Files.exists(file.getParent())) {
                Files.createDirectories(file.getParent());
            }
            NbtCompound root = new NbtCompound();
            root.putLong("timestamp", System.currentTimeMillis());
            NbtList list = new NbtList();
            for (Container c : collectContainersInHierarchyOrder(manager)) {
                list.add(encodeContainer(c));
            }
            root.put("containers", list);
            ContainerTimelineService.initialize();
            Map<UUID, Map<UUID, String>> recoveryTargets =
                ContainerTimelineService.requireInitialized().getContainerRecoveryMapping();
            if (!recoveryTargets.isEmpty()) {
                root.put("uuidRecovery", encodeRecoveryTargets(recoveryTargets));
            }
            AtomicFileWriter.write(file, tempFile -> NbtIo.writeCompressed(root, tempFile));
            LOGGER.debug("[ContainerPersistence] 已保存容器数据: {}", file);
            pendingAutoSave = false;
            PersistenceSaveStatus.reportContainerResult(true);
            return true;
        } catch (Exception e) {
            LOGGER.warn("[ContainerPersistence] 保存容器数据失败: {}", e.getMessage(), e);
            PersistenceSaveStatus.reportContainerResult(false);
            return false;
        }
    }

    public static void loadForCurrentWorld(ContainerManager manager) {
        if (manager == null) {
            return;
        }
        Path file = getContainerFile();
        Path legacy = getLegacyContainerFile();
        if (file == null) {
            ContainerTimelineService.initialize();
            ContainerTimelineService.requireInitialized().clearContainerRecoveryMapping();
            recoveryMappingWorldPath = null;
            LOGGER.debug("[ContainerPersistence] 当前无活动存档目录，跳过加载");
            return;
        }

        // 加载期间置位，避免 clear() 触发时间线片段删除并回写覆盖存档。
        loading = true;
        boolean saveAfterLoad = false;
        try {
            ContainerTimelineService.initialize();
            boolean sameWorld = file.equals(recoveryMappingWorldPath);
            if (!sameWorld) {
                ContainerTimelineService.requireInitialized().setContainerRecoveryMapping(Map.of());
                recoveryMappingWorldPath = file;
            }
            // 关键：先清空内存状态，再按当前存档加载。
            // 即使目标存档没有文件，也不能保留上一个存档的容器数据。
            manager.clear();

            // 自动迁移：世界目录优先；若世界目录没有但旧 config 存在，迁移一次
            if (!Files.exists(file) && legacy != null && Files.exists(legacy) && !legacy.equals(file)) {
                try {
                    if (!Files.exists(file.getParent())) {
                        Files.createDirectories(file.getParent());
                    }
                    Files.copy(legacy, file);
                    LOGGER.info("[ContainerPersistence] 已迁移容器存档到世界目录: {} -> {}", legacy, file);
                } catch (Exception e) {
                    LOGGER.warn("[ContainerPersistence] 迁移容器存档失败: {}", e.getMessage(), e);
                }
            }
            if (!Files.exists(file)) {
                ContainerTimelineService.requireInitialized().clearContainerRecoveryMapping();
                recoveryMappingWorldPath = null;
                LOGGER.info("[ContainerPersistence] 未找到容器存档，使用默认容器 (世界: {})", WorldIdentifier.getCurrentWorldId());
                return;
            }

            NbtCompound root = NbtIo.readCompressed(file, net.minecraft.nbt.NbtSizeTracker.ofUnlimitedBytes());
            NbtList list = root.getList("containers").orElseGet(NbtList::new);
            DecodedContainers decoded = decodeContainers(list);
            Map<UUID, Map<UUID, String>> recoveryTargets = mergeRecoveryTargets(
                decodeRecoveryTargets(root.getCompound("uuidRecovery").orElseGet(NbtCompound::new)),
                decoded.recoveryTargets());
            manager.restoreHierarchyBulk(decoded.roots());

            // 容器存档先于时间线加载。映射保留在服务中，时间线模型绑定或重载时再统一应用。
            // 修复后的容器存档可能在同一世界初始化流程中再次加载；此时不能丢掉仍待时间线消费的映射。
            if (!sameWorld || !recoveryTargets.isEmpty()) {
                ContainerTimelineService.requireInitialized().setContainerRecoveryMapping(recoveryTargets);
            }

            if (decoded.repairedDuplicateIds() > 0) {
                LOGGER.warn("[ContainerPersistence] 加载时修复了 {} 个重复容器 ID", decoded.repairedDuplicateIds());
                saveAfterLoad = true;
            }
            if (decoded.isolatedParentReferences() > 0) {
                LOGGER.warn("[ContainerPersistence] {} 个父引用指向重复 ID，因目标不唯一已隔离为根容器",
                    decoded.isolatedParentReferences());
            }
            LOGGER.info("[ContainerPersistence] 已加载 {} 个容器 (世界: {})", decoded.orderedContainers().size(), WorldIdentifier.getCurrentWorldId());
        } catch (Exception e) {
            ContainerTimelineService.requireInitialized().clearContainerRecoveryMapping();
            recoveryMappingWorldPath = null;
            LOGGER.warn("[ContainerPersistence] 加载容器数据失败: {}", e.getMessage(), e);
        } finally {
            loading = false;
        }
        if (saveAfterLoad) {
            saveForCurrentWorld(manager);
        }
    }

    public static boolean isLoading() {
        return loading;
    }

    public static void requestAutoSave() {
        if (loading) {
            return;
        }
        // 如果ContainerManager正在初始化，跳过自动保存以避免无限递归
        if (ContainerManager.isInitializing()) {
            return;
        }
        pendingAutoSave = true;
        lastAutoSaveRequestMs = System.currentTimeMillis();
    }

    public static void tickAutoSave(ContainerManager manager) {
        if (!pendingAutoSave || loading || manager == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastAutoSaveRequestMs < AUTO_SAVE_DELAY_MS) {
            return;
        }
        saveForCurrentWorld(manager);
    }

    public static void flushPendingAutoSave(ContainerManager manager) {
        if (!pendingAutoSave || loading || manager == null) {
            return;
        }
        saveForCurrentWorld(manager);
    }

    record DecodedContainers(
            List<Container> orderedContainers,
            List<Container> roots,
            Map<UUID, Map<UUID, String>> recoveryTargets,
            int repairedDuplicateIds,
            int isolatedParentReferences) {}

    private record PersistedTimestamps(long created, long modified) {}

    private static NbtCompound encodeRecoveryTargets(Map<UUID, Map<UUID, String>> recoveryTargets) {
        NbtCompound root = new NbtCompound();
        recoveryTargets.forEach((originalId, targets) -> {
            if (originalId == null || targets == null || targets.size() < 2) {
                return;
            }
            NbtCompound encodedTargets = new NbtCompound();
            targets.forEach((recoveredId, name) -> {
                if (recoveredId != null) {
                    encodedTargets.putString(recoveredId.toString(), Objects.requireNonNullElse(name, ""));
                }
            });
            if (encodedTargets.getKeys().size() >= 2) {
                root.put(originalId.toString(), encodedTargets);
            }
        });
        return root;
    }

    private static Map<UUID, Map<UUID, String>> decodeRecoveryTargets(NbtCompound root) {
        Map<UUID, Map<UUID, String>> recoveryTargets = new LinkedHashMap<>();
        for (String originalKey : root.getKeys()) {
            try {
                UUID originalId = UUID.fromString(originalKey);
                NbtCompound encodedTargets = root.getCompound(originalKey).orElse(null);
                if (encodedTargets == null) {
                    continue;
                }
                Map<UUID, String> targets = new LinkedHashMap<>();
                for (String recoveredKey : encodedTargets.getKeys()) {
                    try {
                        targets.put(UUID.fromString(recoveredKey),
                            encodedTargets.getString(recoveredKey, ""));
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("[ContainerPersistence] 忽略无效的恢复目标 UUID: {}", recoveredKey);
                    }
                }
                if (targets.size() >= 2) {
                    recoveryTargets.put(originalId, targets);
                }
            } catch (IllegalArgumentException e) {
                LOGGER.warn("[ContainerPersistence] 忽略无效的原始重复 UUID: {}", originalKey);
            }
        }
        return recoveryTargets;
    }

    private static Map<UUID, Map<UUID, String>> mergeRecoveryTargets(
            Map<UUID, Map<UUID, String>> persisted,
            Map<UUID, Map<UUID, String>> decoded) {
        Map<UUID, Map<UUID, String>> merged = new LinkedHashMap<>();
        persisted.forEach((id, targets) -> merged.put(id, new LinkedHashMap<>(targets)));
        decoded.forEach((id, targets) -> merged.put(id, new LinkedHashMap<>(targets)));
        return merged;
    }

    /**
     * 解码整份容器列表并显式记录重复 ID 的恢复目标。
     * <p>父引用只有 UUID，没有 occurrence 信息；若父 ID 重复，则无法唯一确定目标，
     * 因此将子节点隔离为根节点，而不是随机挂到某个同 ID 容器下。</p>
     */
    static DecodedContainers decodeContainers(NbtList list) {
        Map<UUID, Container> containersById = new LinkedHashMap<>();
        Map<UUID, List<Container>> occurrencesById = new LinkedHashMap<>();
        Map<UUID, UUID> parentById = new HashMap<>();
        Map<UUID, PersistedTimestamps> timestampsById = new HashMap<>();
        List<Container> orderedContainers = new ArrayList<>(list.size());
        int repairedDuplicateIds = 0;

        for (int i = 0; i < list.size(); i++) {
            NbtCompound tag = list.getCompound(i).orElse(null);
            if (tag == null) {
                continue;
            }
            Container container = decodeContainer(tag);
            UUID originalId = container.getId();
            occurrencesById.computeIfAbsent(originalId, ignored -> new ArrayList<>()).add(container);
            if (containersById.containsKey(originalId)) {
                UUID recoveredId;
                do {
                    recoveredId = UUID.randomUUID();
                } while (containersById.containsKey(recoveredId));
                container.replaceIdForRecovery(recoveredId);
                repairedDuplicateIds++;
                LOGGER.warn("[ContainerPersistence] 容器存档存在重复 ID，已修复: {} -> {} ({})",
                    originalId, recoveredId, container.getName());
            }
            containersById.put(container.getId(), container);
            orderedContainers.add(container);
            timestampsById.put(container.getId(), new PersistedTimestamps(
                tag.getLong("created", container.getCreatedTime()),
                tag.getLong("modified", container.getModifiedTime())));
            tag.getIntArray("parent").ifPresent(parent -> parentById.put(container.getId(), Uuids.toUuid(parent)));
        }

        Map<UUID, Map<UUID, String>> recoveryTargets = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<Container>> entry : occurrencesById.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            Map<UUID, String> targets = new LinkedHashMap<>();
            for (Container container : entry.getValue()) {
                targets.put(container.getId(), container.getName());
            }
            recoveryTargets.put(entry.getKey(), Collections.unmodifiableMap(targets));
        }

        // 严格按文件顺序回放层级，避免 HashMap 遍历导致顺序漂移。
        List<Container> roots = new ArrayList<>();
        int isolatedParentReferences = 0;
        for (Container container : orderedContainers) {
            UUID parentId = parentById.get(container.getId());
            if (parentId == null) {
                roots.add(container);
                continue;
            }
            if (recoveryTargets.containsKey(parentId)) {
                roots.add(container);
                isolatedParentReferences++;
                LOGGER.warn("[ContainerPersistence] 容器 {} 的父引用 {} 指向重复 ID，已隔离为根容器",
                    container.getName(), parentId);
                continue;
            }
            Container parent = containersById.get(parentId);
            if (parent != null && parent != container && !parent.isDescendantOf(container)) {
                parent.addChild(container);
            } else {
                roots.add(container);
            }
        }
        // addChild/removeFromParent 会刷新父容器的 modifiedTime；层级回放完成后再恢复审计时间。
        for (Container container : orderedContainers) {
            PersistedTimestamps timestamps = timestampsById.get(container.getId());
            container.restoreTimestampsForRecovery(timestamps.created(), timestamps.modified());
        }

        return new DecodedContainers(
            Collections.unmodifiableList(orderedContainers),
            Collections.unmodifiableList(roots),
            Collections.unmodifiableMap(recoveryTargets),
            repairedDuplicateIds,
            isolatedParentReferences);
    }

    private static List<Container> collectContainersInHierarchyOrder(ContainerManager manager) {
        List<Container> ordered = new ArrayList<>();
        for (Container root : manager.getRootContainersView()) {
            collectPreOrder(root, ordered);
        }
        return ordered;
    }

    private static void collectPreOrder(Container container, List<Container> out) {
        out.add(container);
        for (Container child : container.getChildren()) {
            collectPreOrder(child, out);
        }
    }

    static NbtCompound encodeContainer(Container c) {
        NbtCompound tag = getNbtCompound(c);

        // 坐标集合
        NbtList blocks = new NbtList();
        for (BlockPos pos : c.getBlocksView()) {
            blocks.add(NbtLong.of(pos.asLong()));
        }
        tag.put("blocks", blocks);

        // blockStore（快照）
        NbtList snapshots = new NbtList();
        for (Map.Entry<BlockPos, BlockStore.Entry> e : c.getBlockStore().raw().entrySet()) {
            NbtCompound snap = new NbtCompound();
            snap.putLong("pos", e.getKey().asLong());
            snap.put("state", encodeBlockState(e.getValue().state));
            if (e.getValue().beNbt != null) {
                snap.put("be", e.getValue().beNbt);
            }
            snapshots.add(snap);
        }
        tag.put("snapshots", snapshots);

        return tag;
    }

    private static @NotNull NbtCompound getNbtCompound(Container c) {
        NbtCompound tag = new NbtCompound();
        tag.putIntArray("id", Uuids.toIntArray(c.getId()));
        tag.putString("name", c.getName());
        tag.putString("type", c.getType().name());
        if (c.getParent() != null) {
            tag.putIntArray("parent", Uuids.toIntArray(c.getParent().getId()));
        }
        tag.putInt("color", c.getColor());
        tag.putFloat("opacity", c.getOpacity());
        tag.putBoolean("locked", c.isLocked());
        tag.putString("state", c.getState().name());
        tag.putBoolean("intake", c.isIntakeEnabled());
        tag.putLong("created", c.getCreatedTime());
        tag.putLong("modified", c.getModifiedTime());
        if (c.getLevelKey() != null) {
            tag.putString("level", c.getLevelKey().getValue().toString());
        }
        tag.put("metadata", encodeValues(c.getMetadataView()));
        tag.put("defaultAnimationPreset", encodeAnimationPreset(c.getDefaultAnimationPreset()));
        return tag;
    }

    private static NbtCompound encodeAnimationPreset(ContainerAnimationPreset preset) {
        NbtCompound tag = new NbtCompound();
        ContainerAnimationPreset value = preset != null ? preset : ContainerAnimationPreset.defaultPreset();
        tag.putString("mode", value.getMode().name());
        tag.putFloat("suggestedDuration", value.getSuggestedDuration());
        tag.putFloat("delay", value.getDelay());
        tag.putString("easing", value.getEasing());
        tag.putString("ordering", value.getOrdering());
        tag.put("params", encodeValues(value.getParams()));
        return tag;
    }

    private static ContainerAnimationPreset decodeAnimationPreset(NbtCompound tag) {
        AnimationMode mode;
        try {
            mode = AnimationMode.valueOf(tag.getString("mode", AnimationMode.INSTANT.name()));
        } catch (IllegalArgumentException e) {
            LOGGER.warn("[ContainerPersistence] 无效的容器默认动画模式 '{}'，使用默认值",
                tag.getString("mode", AnimationMode.INSTANT.name()));
            mode = AnimationMode.INSTANT;
        }
        ContainerAnimationPreset preset = new ContainerAnimationPreset(
            mode,
            tag.getFloat("suggestedDuration", ContainerAnimationPreset.DEFAULT_SUGGESTED_DURATION),
            tag.getFloat("delay", 0.0f),
            tag.getString("easing", com.director.core.animation.easing.EasingFunction.LINEAR.name()),
            tag.getString("ordering", "Sequential"));
        decodeValues(tag.getCompound("params").orElseGet(NbtCompound::new)).forEach(preset::setParam);
        return preset;
    }

    private static NbtCompound encodeValues(Map<String, Object> values) {
        NbtCompound tag = new NbtCompound();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isEmpty()) {
                continue;
            }
            NbtCompound encoded = encodeValue(entry.getValue());
            if (encoded != null) {
                tag.put(entry.getKey(), encoded);
            }
        }
        return tag;
    }

    /**
     * 使用显式 type 字段保留包装类型，避免 NBT 的 byte 无法区分 Byte 与 Boolean。
     */
    private static NbtCompound encodeValue(Object value) {
        NbtCompound tag = new NbtCompound();
        if (value == null) {
            tag.putString("type", "null");
        } else if (value instanceof String text) {
            tag.putString("type", "string");
            tag.putString("value", text);
        } else if (value instanceof Boolean bool) {
            tag.putString("type", "boolean");
            tag.putByte("value", (byte) (bool ? 1 : 0));
        } else if (value instanceof Byte number) {
            tag.putString("type", "byte");
            tag.putByte("value", number);
        } else if (value instanceof Short number) {
            tag.putString("type", "short");
            tag.putShort("value", number);
        } else if (value instanceof Integer number) {
            tag.putString("type", "int");
            tag.putInt("value", number);
        } else if (value instanceof Long number) {
            tag.putString("type", "long");
            tag.putLong("value", number);
        } else if (value instanceof Float number) {
            tag.putString("type", "float");
            tag.putFloat("value", number);
        } else if (value instanceof Double number) {
            tag.putString("type", "double");
            tag.putDouble("value", number);
        } else if (value instanceof UUID uuid) {
            tag.putString("type", "uuid");
            tag.putIntArray("value", Uuids.toIntArray(uuid));
        } else {
            LOGGER.warn("[ContainerPersistence] 跳过不支持类型的 metadata/preset 参数: {}", value.getClass().getName());
            return null;
        }
        return tag;
    }

    private static Map<String, Object> decodeValues(NbtCompound tag) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : tag.getKeys()) {
            NbtCompound entry = tag.getCompound(key).orElse(null);
            if (entry == null) {
                continue;
            }
            Object value = decodeValue(entry);
            if (value != null || "null".equals(entry.getString("type", ""))) {
                values.put(key, value);
            }
        }
        return values;
    }

    private static Object decodeValue(NbtCompound tag) {
        String type = tag.getString("type", "");
        return switch (type) {
            case "null" -> null;
            case "string" -> tag.getString("value", "");
            case "boolean" -> tag.getByte("value", (byte) 0) != 0;
            case "byte" -> tag.getByte("value", (byte) 0);
            case "short" -> tag.getShort("value", (short) 0);
            case "int" -> tag.getInt("value", 0);
            case "long" -> tag.getLong("value", 0L);
            case "float" -> tag.getFloat("value", 0.0f);
            case "double" -> tag.getDouble("value", 0.0);
            case "uuid" -> tag.getIntArray("value").map(Uuids::toUuid).orElse(null);
            default -> {
                LOGGER.warn("[ContainerPersistence] 跳过未知类型的 metadata/preset 参数: {}", type);
                yield null;
            }
        };
    }

    static Container decodeContainer(NbtCompound tag) {
        UUID id = tag.getIntArray("id").map(Uuids::toUuid).orElseGet(UUID::randomUUID);
        String name = tag.getString("name", "Container");
        String typeStr = tag.getString("type", ContainerType.GENERAL.name());
        ContainerType type;
        try {
            type = ContainerType.valueOf(typeStr);
        } catch (Exception e) {
            type = ContainerType.GENERAL;
        }
        Container c = new Container(id, name, type);
        c.setColor(tag.getInt("color", c.getColor()));
        c.setOpacity(tag.getFloat("opacity", c.getOpacity()));
        String stateStr = tag.getString("state", c.getState().name());
        ContainerState decodedState = c.getState();
        try {
            decodedState = ContainerState.valueOf(stateStr);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("[ContainerPersistence] 无效的容器状态 '{}'，使用默认值", stateStr);
        }
        String levelStr = tag.getString("level", "");
        if (!levelStr.isEmpty()) {
            try {
                Identifier levelId = Identifier.of(levelStr);
                c.setLevelKey(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, levelId));
            } catch (Exception e) {
                LOGGER.warn("[ContainerPersistence] 无效的容器维度 '{}'，忽略该字段", levelStr, e);
            }
        }

        // blocks
        NbtList blocks = tag.getList("blocks").orElseGet(NbtList::new);
        List<BlockPos> positions = new ArrayList<>(blocks.size());
        for (NbtElement block : blocks) {
            if (block instanceof NbtLong nl) {
                positions.add(BlockPos.fromLong(nl.longValue()));
            }
        }
        c.addBlocks(positions);

        // blockStore snapshots
        NbtList snaps = tag.getList("snapshots").orElseGet(NbtList::new);
        Map<BlockPos, BlockStore.Entry> store = c.getBlockStore().raw();
        for (int i = 0; i < snaps.size(); i++) {
            NbtCompound snap = snaps.getCompound(i).orElse(null);
            if (snap == null) continue;
            BlockPos pos = BlockPos.fromLong(snap.getLong("pos", 0L));
            BlockState state = decodeBlockState(snap.getCompound("state").orElseGet(NbtCompound::new));
            NbtCompound be = snap.getCompound("be").orElse(null);
            store.put(pos, new BlockStore.Entry(state, be));
        }
        if (decodedState != c.getState()) {
            c.setState(decodedState);
        }
        c.setLocked(tag.getBoolean("locked", c.isLocked()));
        c.setIntakeEnabled(tag.getBoolean("intake", c.isIntakeEnabled()));
        decodeValues(tag.getCompound("metadata").orElseGet(NbtCompound::new)).forEach(c::setMetadata);
        c.setDefaultAnimationPreset(decodeAnimationPreset(
            tag.getCompound("defaultAnimationPreset").orElseGet(NbtCompound::new)));
        // 所有业务 setter 都会刷新 modifiedTime，因此最后按存档值恢复审计时间。
        c.restoreTimestampsForRecovery(
            tag.getLong("created", c.getCreatedTime()),
            tag.getLong("modified", c.getModifiedTime()));
        return c;
    }

    private static NbtCompound encodeBlockState(BlockState state) {
        NbtCompound tag = new NbtCompound();
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        tag.putString("name", id.toString());
        NbtCompound props = new NbtCompound();
        for (Property<?> prop : state.getProperties()) {
            props.putString(prop.getName(), state.get(prop).toString());
        }
        tag.put("properties", props);
        return tag;
    }

    private static BlockState decodeBlockState(NbtCompound tag) {
        Identifier id = Identifier.tryParse(tag.getString("name", "minecraft:air"));
        Block block = id != null ? Registries.BLOCK.get(id) : net.minecraft.block.Blocks.AIR;
        BlockState state = block.getDefaultState();
        NbtCompound props = tag.getCompound("properties").orElseGet(NbtCompound::new);
        for (String key : props.getKeys()) {
            Property<?> property = block.getStateManager().getProperty(key);
            if (property != null) {
                state = applyProperty(state, property, props.getString(key, ""));
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.parse(value);
        return parsed.map(v -> state.with(property, v)).orElse(state);
    }
}
