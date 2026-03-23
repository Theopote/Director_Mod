package com.director.ui.timeline.playback.entity;

import com.director.actor.Actor;
import com.director.actor.ActorType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PreviewEntity v1.0
 *
 * <p>Preview Entity（预览实体）是 Actor 在客户端的"投影"。
 *
 * <p>核心特点：
 * <ul>
 *   <li>只存在于客户端，不写入世界</li>
 *   <li>无 AI，完全受 Timeline 控制</li>
 *   <li>无碰撞，不影响场景</li>
 *   <li>可渲染，看起来像真实实体</li>
 *   <li>可选中，支持 Selection / Gizmo</li>
 *   <li>可消失，跟随 Spawn / Despawn</li>
 * </ul>
 *
 * <p>这是方案 C 成立的关键技术点。
 *
 * @author Director Team
 * @version 1.0
 */
public class PreviewEntity {

    private static final Logger LOGGER = LoggerFactory.getLogger(PreviewEntity.class);

    /** 关联的 Actor */
    private final Actor actor;

    /** Minecraft 实体实例（客户端投影） */
    private Entity entity;

    /** 是否已创建 */
    private boolean created = false;

    /**
     * 构造函数
     *
     * @param actor Actor
     */
    public PreviewEntity(Actor actor) {
        this.actor = actor;
    }

    /**
     * 创建预览实体（客户端）
     *
     * @param client Minecraft 客户端
     * @return 是否成功创建
     */
    public boolean create(MinecraftClient client) {
        if (created || client == null || client.world == null) {
            return false;
        }

        try {
            if (!(client.world instanceof ClientWorld world)) {
                LOGGER.warn("[PreviewEntity] 世界不是 ClientWorld");
                return false;
            }

            // 根据 Actor 类型创建实体
            EntityType<?> entityType = getEntityTypeForActor(actor);
            if (entityType == null) {
                LOGGER.warn("[PreviewEntity] 无法为 Actor {} 创建实体类型", actor.getType());
                return false;
            }

            // 创建实体（客户端）
            // 注意：EntityType.create 需要 World 和 SpawnReason
            this.entity = entityType.create(world, net.minecraft.entity.SpawnReason.COMMAND);
            if (this.entity == null) {
                LOGGER.warn("[PreviewEntity] 实体创建失败: {}", actor.getType());
                return false;
            }

            // 设置为客户端实体（不参与服务器同步）
            // 注意：这里需要确保实体不会被保存到世界
            if (this.entity instanceof LivingEntity living) {
                // 无重力（防止下落）
                living.setNoGravity(true);
                // 禁用 AI（通过设置速度为零，实际 AI 禁用需要 Mixin）
                living.setVelocity(0, 0, 0);
            }

            // 设置名称
            if (actor.getName() != null) {
                this.entity.setCustomName(net.minecraft.text.Text.literal(actor.getName()));
            }

            // 添加到世界（仅客户端）
            // ⚠️ 注意：这里需要确保实体不会被保存
            // 实际实现可能需要使用 Mixin 或特殊标记
            world.addEntity(this.entity);

            this.created = true;
            LOGGER.debug("[PreviewEntity] 创建预览实体: {} ({})", actor.getName(), actor.getType());
            return true;

        } catch (Throwable e) {
            LOGGER.error("[PreviewEntity] 创建预览实体失败: {}", actor.getName(), e);
            return false;
        }
    }

    /**
     * 销毁预览实体
     *
     * @param client Minecraft 客户端
     */
    public void destroy(MinecraftClient client) {
        if (!created || entity == null || client == null || client.world == null) {
            return;
        }

        try {
            // 从世界中移除
            client.world.removeEntity(entity.getId(), net.minecraft.entity.Entity.RemovalReason.DISCARDED);
            this.entity = null;
            this.created = false;
            LOGGER.debug("[PreviewEntity] 销毁预览实体: {}", actor.getName());
        } catch (Throwable e) {
            LOGGER.warn("[PreviewEntity] 销毁预览实体失败: {}", actor.getName(), e);
        }
    }

    /**
     * 获取实体实例
     *
     * @return 实体，如果未创建则返回 null
     */
    public Entity getEntity() {
        return entity;
    }

    /**
     * 检查是否已创建
     *
     * @return 如果已创建则返回 true
     */
    public boolean isCreated() {
        return created && entity != null && entity.isAlive();
    }

    /**
     * 获取关联的 Actor
     *
     * @return Actor
     */
    public Actor getActor() {
        return actor;
    }

    /**
     * 根据 Actor 类型获取 EntityType
     */
    private EntityType<?> getEntityTypeForActor(Actor actor) {
        if (actor == null) {
            return null;
        }

        // 从注册表获取实体类型
        try {
            ActorType type = actor.getType();
            if (type == null) {
                return EntityType.VILLAGER; // 默认
            }
            
            return switch (type) {
                case VILLAGER -> EntityType.VILLAGER;
                case COW -> EntityType.COW;
                case ZOMBIE -> EntityType.ZOMBIE;
                case SKELETON -> EntityType.SKELETON;
                case PLAYER -> null; // 玩家需要特殊处理
                case CUSTOM -> // 自定义类型：默认使用村民
                    // TODO: 未来可以从 Actor 的扩展字段中获取 modelId
                    // 默认
                        EntityType.VILLAGER;
                case SHEEP -> EntityType.SHEEP; // 添加 SHEEP 类型支持
            };
        } catch (Throwable e) {
            LOGGER.warn("[PreviewEntity] 获取实体类型失败: {}", actor.getType(), e);
            return EntityType.VILLAGER; // 默认
        }
    }
}

