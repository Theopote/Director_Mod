package com.chronoblocks.actor.preview;

import com.chronoblocks.actor.Actor;
import com.chronoblocks.actor.ActorType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ActorPreviewManager v1.0
 *
 * <p>ActorPreviewManager = 管理所有 Actor 的客户端投影
 *
 * <p>职责：
 * <ul>
 *   <li>管理 Actor ↔ PreviewEntity 的映射</li>
 *   <li>控制 PreviewEntity 的生命周期</li>
 *   <li>只存在于 Client</li>
 * </ul>
 *
 * <p>使用方式：
 * <ul>
 *   <li>在 SpawnClip 生效时：ensurePreview(actor)</li>
 *   <li>在 DespawnClip 生效时：removePreview(actorId)</li>
 *   <li>切换项目/关闭 Timeline 时：clear()</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class ActorPreviewManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorPreviewManager.class);
    private static final ActorPreviewManager INSTANCE = new ActorPreviewManager();

    public static ActorPreviewManager getInstance() {
        return INSTANCE;
    }

    /** Actor ID 到 PreviewEntity 的映射 */
    private final Map<UUID, PreviewActorEntity> previews = new HashMap<>();

    private ActorPreviewManager() {
    }

    // ======================
    // Public API
    // ======================

    /**
     * 确保某个 Actor 有 PreviewEntity
     * <p>如果不存在则创建，如果已存在则返回现有实例
     *
     * @param actor Actor
     * @return PreviewEntity，如果创建失败则返回 null
     */
    public PreviewActorEntity ensurePreview(Actor actor) {
        if (actor == null) {
            return null;
        }

        return previews.computeIfAbsent(actor.getActorId(), id -> createPreview(actor));
    }

    /**
     * 移除某个 Actor 的 PreviewEntity
     *
     * @param actorId 演员ID
     */
    public void removePreview(UUID actorId) {
        if (actorId == null) {
            return;
        }

        PreviewActorEntity entity = previews.remove(actorId);
        if (entity != null) {
            try {
                entity.discard();
                LOGGER.debug("[ActorPreviewManager] 移除预览实体: {}", actorId);
            } catch (Throwable e) {
                LOGGER.warn("[ActorPreviewManager] 移除预览实体失败: {}", actorId, e);
            }
        }
    }

    /**
     * 获取 PreviewEntity（若不存在则返回 null）
     *
     * @param actorId 演员ID
     * @return PreviewEntity，如果不存在则返回 null
     */
    public PreviewActorEntity getPreview(UUID actorId) {
        if (actorId == null) {
            return null;
        }
        return previews.get(actorId);
    }

    /**
     * 检查某个 Actor 是否有 PreviewEntity
     *
     * @param actorId 演员ID
     * @return 如果存在则返回 true
     */
    public boolean hasPreview(UUID actorId) {
        if (actorId == null) {
            return false;
        }
        return previews.containsKey(actorId);
    }

    /**
     * 清空所有预览实体（切换项目 / 关闭 Timeline 时）
     */
    public void clear() {
        for (PreviewActorEntity entity : previews.values()) {
            try {
                entity.discard();
            } catch (Throwable e) {
                LOGGER.warn("[ActorPreviewManager] 清空预览实体失败", e);
            }
        }
        previews.clear();
        LOGGER.debug("[ActorPreviewManager] 清空所有预览实体");
    }

    /**
     * 获取预览实体数量
     *
     * @return 预览实体数量
     */
    public int size() {
        return previews.size();
    }

    /**
     * 检查是否为空
     *
     * @return 如果为空则返回 true
     */
    public boolean isEmpty() {
        return previews.isEmpty();
    }

    // ======================
    // 内部创建逻辑
    // ======================

    /**
     * 创建预览实体
     *
     * @param actor Actor
     * @return PreviewEntity，如果创建失败则返回 null
     */
    private PreviewActorEntity createPreview(Actor actor) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            LOGGER.warn("[ActorPreviewManager] 客户端或世界未加载，无法创建预览实体");
            return null;
        }

        ClientWorld world = client.world;

        try {
            EntityType<?> type = mapActorType(actor.getType());
            if (type == null) {
                LOGGER.warn("[ActorPreviewManager] 无法映射 ActorType: {}", actor.getType());
                return null;
            }

            PreviewActorEntity entity = new PreviewActorEntity(type, world);

            // 设置名称
            entity.setCustomNameVisible(true);
            if (actor.getName() != null) {
                entity.setCustomName(net.minecraft.text.Text.literal(actor.getName()));
            }

            // 默认放在原点，真正位置由 RuntimeController 每帧驱动
            entity.refreshPositionAndAngles(0, 0, 0, 0, 0);

            // 添加到世界（仅客户端）
            world.addEntity(entity);

            LOGGER.debug("[ActorPreviewManager] 创建预览实体: {} ({})", actor.getName(), actor.getType());
            return entity;

        } catch (Throwable e) {
            LOGGER.error("[ActorPreviewManager] 创建预览实体失败: {}", actor.getName(), e);
            return null;
        }
    }

    /**
     * ActorType → EntityType 映射
     * （导演语义 → Minecraft 表现）
     *
     * @param type ActorType
     * @return EntityType，如果无法映射则返回 null
     */
    private EntityType<?> mapActorType(ActorType type) {
        if (type == null) {
            return null;
        }

        return switch (type) {
            case PLAYER -> EntityType.PLAYER;
            case VILLAGER -> EntityType.VILLAGER;
            case COW -> EntityType.COW;
            case SHEEP -> EntityType.SHEEP;
            case ZOMBIE -> EntityType.ZOMBIE;
            case SKELETON -> EntityType.SKELETON;
            case CUSTOM -> EntityType.ARMOR_STAND; // 临时占位，后续可扩展
        };
    }
}

