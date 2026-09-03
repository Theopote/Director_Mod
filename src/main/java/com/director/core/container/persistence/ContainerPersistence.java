package com.director.core.container.persistence;

import com.director.core.container.BlockStore;
import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerState;
import com.director.core.container.ContainerType;
import com.director.persistence.PersistenceSaveStatus;
import com.director.persistence.AtomicFileWriter;
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
            LOGGER.debug("[ContainerPersistence] 当前无活动存档目录，跳过加载");
            return;
        }

        // 加载期间置位，避免 clear() 触发时间线片段删除并回写覆盖存档。
        loading = true;
        boolean saveAfterLoad = false;
        try {
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
                LOGGER.info("[ContainerPersistence] 未找到容器存档，使用默认容器 (世界: {})", WorldIdentifier.getCurrentWorldId());
                return;
            }

            NbtCompound root = NbtIo.readCompressed(file, net.minecraft.nbt.NbtSizeTracker.ofUnlimitedBytes());
            NbtList list = root.getList("containers").orElseGet(NbtList::new);
            Map<UUID, Container> map = new LinkedHashMap<>();
            Map<UUID, UUID> parentMap = new HashMap<>();
            List<Container> orderedContainers = new ArrayList<>(list.size());
            int repairedDuplicateIds = 0;

            for (int i = 0; i < list.size(); i++) {
                NbtCompound tag = list.getCompound(i).orElse(null);
                if (tag == null) continue;
                Container c = decodeContainer(tag);
                UUID originalId = c.getId();
                if (map.containsKey(originalId)) {
                    UUID recoveredId;
                    do {
                        recoveredId = UUID.randomUUID();
                    } while (map.containsKey(recoveredId));
                    c.replaceIdForRecovery(recoveredId);
                    repairedDuplicateIds++;
                    LOGGER.warn("[ContainerPersistence] 容器存档存在重复 ID，已修复: {} -> {} ({})",
                        originalId, recoveredId, c.getName());
                }
                map.put(c.getId(), c);
                orderedContainers.add(c);
                tag.getIntArray("parent").ifPresent(arr -> parentMap.put(c.getId(), Uuids.toUuid(arr)));
            }

            // 严格按文件顺序回放层级，避免 HashMap 遍历导致顺序漂移。
            List<Container> roots = new ArrayList<>();
            for (Container c : orderedContainers) {
                UUID pid = parentMap.get(c.getId());
                if (pid != null) {
                    Container parent = map.get(pid);
                    if (parent != null && parent != c && !parent.isDescendantOf(c)) {
                        parent.addChild(c);
                    } else {
                        roots.add(c);
                    }
                } else {
                    roots.add(c);
                }
            }

            manager.restoreHierarchyBulk(roots);

            if (repairedDuplicateIds > 0) {
                LOGGER.warn("[ContainerPersistence] 加载时修复了 {} 个重复容器 ID", repairedDuplicateIds);
                saveAfterLoad = true;
            }
            LOGGER.info("[ContainerPersistence] 已加载 {} 个容器 (世界: {})", orderedContainers.size(), WorldIdentifier.getCurrentWorldId());
        } catch (Exception e) {
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

    private static NbtCompound encodeContainer(Container c) {
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
        return tag;
    }

    private static Container decodeContainer(NbtCompound tag) {
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
