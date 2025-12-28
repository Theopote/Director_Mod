package com.chronoblocks.core.container.persistence;

import com.chronoblocks.core.container.BlockStore;
import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.container.ContainerState;
import com.chronoblocks.core.container.ContainerType;
import com.chronoblocks.util.WorldIdentifier;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 容器持久化管理：按存档（世界）保存/加载容器与方块数据。
 */
public final class ContainerPersistence {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerPersistence.class);
    private static final Path CONFIG_DIR = Paths.get("config", "chronoblocks", "containers");
    private static volatile boolean loading = false;

    private ContainerPersistence() {}

    private static Path getContainerFile() {
        String worldId = WorldIdentifier.getCurrentWorldId();
        return CONFIG_DIR.resolve(worldId + "_containers.dat");
    }

    public static void saveForCurrentWorld(ContainerManager manager) {
        if (manager == null || loading) {
            return;
        }
        try {
            Path file = getContainerFile();
            if (!Files.exists(file.getParent())) {
                Files.createDirectories(file.getParent());
            }
            NbtCompound root = new NbtCompound();
            root.putLong("timestamp", System.currentTimeMillis());
            NbtList list = new NbtList();
            for (Container c : manager.getAllContainers()) {
                list.add(encodeContainer(c));
            }
            root.put("containers", list);
            NbtIo.writeCompressed(root, file);
            LOGGER.debug("[ContainerPersistence] 已保存容器数据: {}", file);
        } catch (Exception e) {
            LOGGER.warn("[ContainerPersistence] 保存容器数据失败: {}", e.getMessage(), e);
        }
    }

    public static void loadForCurrentWorld(ContainerManager manager) {
        if (manager == null) {
            return;
        }
        Path file = getContainerFile();
        if (!Files.exists(file)) {
            LOGGER.info("[ContainerPersistence] 未找到容器存档，使用默认容器 (世界: {})", WorldIdentifier.getCurrentWorldId());
            return;
        }
        loading = true;
        try {
            NbtCompound root = NbtIo.readCompressed(file, net.minecraft.nbt.NbtSizeTracker.ofUnlimitedBytes());
            NbtList list = root.getList("containers").orElseGet(NbtList::new);
            Map<UUID, Container> map = new HashMap<>();
            Map<UUID, UUID> parentMap = new HashMap<>();

            // 先清空现有容器
            manager.clear();

            for (int i = 0; i < list.size(); i++) {
                NbtCompound tag = list.getCompound(i).orElse(null);
                if (tag == null) continue;
                Container c = decodeContainer(tag);
                map.put(c.getId(), c);
                tag.getIntArray("parent").ifPresent(arr -> parentMap.put(c.getId(), Uuids.toUuid(arr)));
            }

            // 建立层级
            for (Container c : map.values()) {
                UUID pid = parentMap.get(c.getId());
                if (pid != null) {
                    Container parent = map.get(pid);
                    if (parent != null) {
                        parent.addChild(c);
                    } else {
                        manager.addContainer(c);
                    }
                } else {
                    manager.addContainer(c);
                }
            }

            LOGGER.info("[ContainerPersistence] 已加载 {} 个容器 (世界: {})", map.size(), WorldIdentifier.getCurrentWorldId());
        } catch (Exception e) {
            LOGGER.warn("[ContainerPersistence] 加载容器数据失败: {}", e.getMessage(), e);
        } finally {
            loading = false;
        }
    }

    public static void resetForCurrentWorld() {
        try {
            Path file = getContainerFile();
            if (Files.exists(file)) {
                Files.delete(file);
                LOGGER.info("[ContainerPersistence] 删除容器存档: {}", file);
            }
        } catch (IOException e) {
            LOGGER.warn("[ContainerPersistence] 删除容器存档失败: {}", e.getMessage(), e);
        }
    }

    public static void requestAutoSave() {
        if (loading) {
            return;
        }
        // 如果ContainerManager正在初始化，跳过自动保存以避免无限递归
        if (ContainerManager.isInitializing()) {
            return;
        }
        ContainerManager manager = ContainerManager.getInstance();
        if (manager != null) {
            saveForCurrentWorld(manager);
        }
    }

    private static NbtCompound encodeContainer(Container c) {
        NbtCompound tag = getNbtCompound(c);

        // 坐标集合
        NbtList blocks = new NbtList();
        for (BlockPos pos : c.getBlocks()) {
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
        String typeStr = tag.getString("type", ContainerType.CUSTOM.name());
        ContainerType type;
        try {
            type = ContainerType.valueOf(typeStr);
        } catch (Exception e) {
            type = ContainerType.CUSTOM;
        }
        Container c = new Container(id, name, type);
        c.setColor(tag.getInt("color", c.getColor()));
        c.setOpacity(tag.getFloat("opacity", c.getOpacity()));
        c.setLocked(tag.getBoolean("locked", c.isLocked()));
        String stateStr = tag.getString("state", c.getState().name());
        try {
            c.setState(ContainerState.valueOf(stateStr));
        } catch (Exception ignored) {}
        c.setIntakeEnabled(tag.getBoolean("intake", c.isIntakeEnabled()));
        String levelStr = tag.getString("level", "");
        if (!levelStr.isEmpty()) {
            try {
                Identifier levelId = Identifier.of(levelStr);
                c.setLevelKey(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, levelId));
            } catch (Exception ignored) {}
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
