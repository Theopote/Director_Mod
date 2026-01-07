package com.chronoblocks.actor.prop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * PropRegistry v1.0
 *
 * <p>道具注册表：管理所有可用的道具描述符
 *
 * <p>设计原则：
 * <ul>
 *   <li>✅ 全局单例</li>
 *   <li>✅ 可序列化、可持久化</li>
 *   <li>✅ 支持运行时注册</li>
 * </ul>
 *
 * <p>职责边界：
 * <ul>
 *   <li>✅ 管理 PropDescriptor 的注册/查询</li>
 *   <li>❌ 不关心 Timeline / Runtime</li>
 *   <li>❌ 不关心渲染细节</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class PropRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropRegistry.class);
    private static final PropRegistry INSTANCE = new PropRegistry();

    public static PropRegistry getInstance() {
        return INSTANCE;
    }

    /** 道具描述符映射（id -> descriptor） */
    private final Map<String, PropDescriptor> descriptors = new HashMap<>();

    private PropRegistry() {
        // 注册默认道具
        registerDefaults();
    }

    // =========================================================
    // Registration
    // =========================================================

    /**
     * 注册道具描述符
     *
     * @param descriptor 道具描述符
     */
    public void register(PropDescriptor descriptor) {
        if (descriptor == null || descriptor.getId() == null) {
            LOGGER.warn("[PropRegistry] 尝试注册无效的道具描述符");
            return;
        }

        descriptors.put(descriptor.getId(), descriptor);
        LOGGER.debug("[PropRegistry] 注册道具: {}", descriptor.getId());
    }

    /**
     * 获取道具描述符
     *
     * @param propId 道具 ID
     * @return 道具描述符，如果不存在则返回 empty
     */
    public Optional<PropDescriptor> get(String propId) {
        if (propId == null || propId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(descriptors.get(propId));
    }

    /**
     * 检查道具是否存在
     *
     * @param propId 道具 ID
     * @return 如果存在则返回 true
     */
    public boolean has(String propId) {
        return propId != null && descriptors.containsKey(propId);
    }

    /**
     * 获取所有道具描述符
     *
     * @return 所有道具描述符的集合（只读）
     */
    public java.util.Collection<PropDescriptor> getAll() {
        return java.util.Collections.unmodifiableCollection(descriptors.values());
    }

    // =========================================================
    // Default Props
    // =========================================================

    /**
     * 注册默认道具
     * <p>未来可以从配置文件加载
     */
    private void registerDefaults() {
        // 示例：注册一些常用方块
        register(new PropDescriptor("oak_planks", "Oak Planks", PropVisualType.BLOCK, "minecraft:oak_planks"));
        register(new PropDescriptor("stone", "Stone", PropVisualType.BLOCK, "minecraft:stone"));
        register(new PropDescriptor("dirt", "Dirt", PropVisualType.BLOCK, "minecraft:dirt"));
        register(new PropDescriptor("wooden_sword", "Wooden Sword", PropVisualType.ITEM, "minecraft:wooden_sword"));

        LOGGER.info("[PropRegistry] 已注册 {} 个默认道具", descriptors.size());
    }
}

