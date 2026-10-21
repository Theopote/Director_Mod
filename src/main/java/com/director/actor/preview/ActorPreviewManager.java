package com.director.actor.preview;

import com.director.actor.Actor;
import com.director.actor.ActorType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
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
 * @author Director Team
 * @version 1.0
 */
public final class ActorPreviewManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorPreviewManager.class);
    private static final ActorPreviewManager INSTANCE = new ActorPreviewManager();

    public static ActorPreviewManager getInstance() {
        return INSTANCE;
    }

    /** Actor ID 到 PreviewEntity 的映射 */
    private final Map<UUID, Entity> previews = new HashMap<>();

    /** Entity ID 到 Actor ID 的反向映射（用于点击检测） */
    private final Map<Integer, UUID> entityToActor = new HashMap<>();
    private final java.util.Map<Entity, org.joml.Vector3f> rootScales = new java.util.WeakHashMap<>();
    public void setRootScale(Entity entity, org.joml.Vector3f scale) {
        if(entity!=null && scale!=null && scale.isFinite()) rootScales.put(entity,new org.joml.Vector3f(scale));
    }
    public org.joml.Vector3f rootScale(Entity entity) {
        return new org.joml.Vector3f(rootScales.getOrDefault(entity,new org.joml.Vector3f(1)));
    }


    private final ActorPreviewWorldBinding<ClientWorld> worldBinding=new ActorPreviewWorldBinding<>(this::clear);
    public void syncWorld() {
        var client=MinecraftClient.getInstance();
        worldBinding.bind(client==null?null:client.world);
    }
    public long worldRevision() { return worldBinding.revision(); }
    public boolean hasWorld() { return worldBinding.world()!=null; }
    public void disconnectWorld() { worldBinding.disconnect(); }

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
    public Entity ensurePreview(Actor actor) {
        if (actor == null) {
            return null;
        }

        syncWorld();
        if(!hasWorld()) return null;
        var existing=getPreview(actor.getActorId());
        return existing!=null?existing:previews.computeIfAbsent(actor.getActorId(),id->createPreview(actor));
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

        Entity entity = previews.remove(actorId);
        if (entity != null) {
            try {
                // 清理反向映射
                entityToActor.remove(entity.getId());
                rootScales.remove(entity);
                entity.discard();
                LOGGER.debug("[ActorPreviewManager] 移除预览实体: {}", actorId);
            } catch (RuntimeException e) {
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
    public Entity getPreview(UUID actorId) {
        if (actorId == null) {
            return null;
        }
        syncWorld();
        var entity=previews.get(actorId);
        if(entity!=null && (entity.isRemoved() || entity.getEntityWorld()!=worldBinding.world())) {
            removePreview(actorId);return null;
        }
        return entity;
    }

    /**
     * 检查某个 Actor 是否有 PreviewEntity
     *
     * @param actorId 演员ID
     * @return 如果存在则返回 true
     */
    public java.util.Set<UUID> previewActorIds() {
        syncWorld();
        return java.util.Set.copyOf(previews.keySet());
    }

    public boolean hasPreview(UUID actorId) {
        if (actorId == null) {
            return false;
        }
        return getPreview(actorId)!=null;
    }

    /**
     * 清空所有预览实体（切换项目 / 关闭 Timeline 时）
     */
    public void clear() {
        for (Entity entity : previews.values()) {
            try {
                entity.discard();
            } catch (RuntimeException e) {
                LOGGER.warn("[ActorPreviewManager] 清空预览实体失败", e);
            }
        }
        previews.clear();
        entityToActor.clear();
        rootScales.clear();
        LOGGER.debug("[ActorPreviewManager] 清空所有预览实体");
    }

    /**
     * 根据 PreviewActorEntity 获取对应的 Actor ID
     * <p>用于点击检测：从实体反查 Actor
     *
     * @param entity PreviewActorEntity
     * @return Actor ID，如果不存在则返回 null
     */
    public UUID getActorId(Entity entity) {
        if (entity == null) {
            return null;
        }
        syncWorld();
        var id=entityToActor.get(entity.getId());
        return id!=null && previews.get(id)==entity && !entity.isRemoved()?id:null;
    }

    /**
     * 根据实体 ID 获取对应的 Actor ID
     *
     * @param entityId 实体 ID
     * @return Actor ID，如果不存在则返回 null
     */
    public UUID getActorIdByEntityId(int entityId) {
        syncWorld();
        var id=entityToActor.get(entityId);
        return id!=null && getPreview(id)!=null?id:null;
    }

    /**
     * 获取预览实体数量
     *
     * @return 预览实体数量
     */
    public int size() {
        syncWorld();
        return previews.size();
    }

    /**
     * 检查是否为空
     *
     * @return 如果为空则返回 true
     */
    public boolean isEmpty() {
        syncWorld();
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
    private Entity createPreview(Actor actor) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) {
            LOGGER.warn("[ActorPreviewManager] 客户端或世界未加载，无法创建预览实体");
            return null;
        }

        ClientWorld world = client.world;

        try {
            EntityType<?> type = previewEntityType(actor.getType());
            if (type == null) {
                LOGGER.warn("[ActorPreviewManager] 无法映射 ActorType: {}", actor.getType());
                return null;
            }

            Entity entity = actor.getType()==ActorType.PLAYER ? new PreviewPlayerActorEntity(world,actor)
                    : type == EntityType.ARMOR_STAND
                    ? new PreviewActorEntity(EntityType.ARMOR_STAND, world)
                    : type.create(world, SpawnReason.COMMAND);
            if (entity == null) {
                LOGGER.warn("[ActorPreviewManager] 创建预览实体失败: {}", actor.getType());
                return null;
            }

            // 设置名称
            entity.setCustomNameVisible(true);
            if (actor.getName() != null) {
                entity.setCustomName(net.minecraft.text.Text.literal(actor.getName()));
            }

            if (entity instanceof MobEntity mob) {
                mob.setAiDisabled(true);
            }
            entity.setNoGravity(true);
            entity.setVelocity(0.0, 0.0, 0.0);
            entity.setSilent(true);
            entity.setInvulnerable(true);

            // 默认放在原点，真正位置由 RuntimeController 每帧驱动
            entity.refreshPositionAndAngles(0, 0, 0, 0, 0);

            // 添加到世界（仅客户端）
            world.addEntity(entity);

            // 建立反向映射（entityId -> actorId）
            entityToActor.put(entity.getId(), actor.getActorId());

            LOGGER.debug("[ActorPreviewManager] 创建预览实体: {} ({})", actor.getName(), actor.getType());
            return entity;

        } catch (RuntimeException e) {
            LOGGER.error("[ActorPreviewManager] 创建预览实体失败: {}", actor.getName(), e);
            return null;
        }
    }

    /** Shared preview entity type for creation and entity-independent standing collision dimensions. */
    public static EntityType<?> previewEntityType(ActorType type) {
        if (type == null) {
            return EntityType.ARMOR_STAND; // 默认使用 ARMOR_STAND
        }

        return switch (type) {
            case PLAYER -> EntityType.PLAYER;
            case VILLAGER -> EntityType.VILLAGER;
            // CUSTOM 演员用 ARMOR_STAND 作为骨架驱动锚点：人形拓扑骨架直接复用其渲染，
            // 非人形拓扑自定义模型则由 CustomModelPreviewRenderer 在锚点位置叠加方块显示实体。
            case CUSTOM -> EntityType.ARMOR_STAND;
            case COW -> EntityType.COW;
            case SHEEP -> EntityType.SHEEP;
            case ZOMBIE -> EntityType.ZOMBIE;
            case SKELETON -> EntityType.SKELETON;
        };

        // 原始映射（已禁用，因为会导致 ClassCastException）：
        // return switch (type) {
        //     case PLAYER -> EntityType.PLAYER;
        //     case VILLAGER -> EntityType.VILLAGER;
        //     case COW -> EntityType.COW;
        //     case SHEEP -> EntityType.SHEEP;
        //     case ZOMBIE -> EntityType.ZOMBIE;
        //     case SKELETON -> EntityType.SKELETON;
        //     case CUSTOM -> EntityType.ARMOR_STAND;
        // };
    }

    public java.util.Collection<Entity> getPreviewEntities() {
        syncWorld();
        return java.util.Collections.unmodifiableCollection(previews.values());
    }
}

