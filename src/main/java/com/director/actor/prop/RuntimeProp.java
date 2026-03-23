package com.director.actor.prop;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RuntimeProp v1.0
 *
 * <p>运行时道具：Actor 当前携带的道具实例
 *
 * <p>设计原则：
 * <ul>
 *   <li>✅ 纯客户端视觉对象</li>
 *   <li>✅ 和世界完全解耦</li>
 *   <li>✅ 跟随演员的位置/旋转</li>
 *   <li>❌ 不保存、不同步、不参与 AI</li>
 * </ul>
 *
 * <p>核心特点：
 * <ul>
 *   <li>✅ 绑定到 PreviewActorEntity</li>
 *   <li>✅ 支持局部偏移和旋转</li>
 *   <li>✅ 每帧更新 Transform</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public final class RuntimeProp {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeProp.class);

    /** 道具描述符 */
    private final PropDescriptor descriptor;

    /** 槽位 */
    private ActorPropSlot slot;

    /** 局部偏移（相对于槽位） */
    private final Vector3f localOffset = new Vector3f(0, 0, 0);

    /** 局部旋转（相对于槽位，度） */
    private final Vector3f localRotation = new Vector3f(0, 0, 0);

    /** 渲染对象（未来扩展） */
    private Object renderObject; // TODO: 未来实现 RenderPropObject

    /** 世界位置（用于投掷时的独立位置） */
    private Vec3d worldPosition = null;

    /** 是否绑定到 Actor（false 表示独立世界位置，如投掷中） */
    private boolean boundToActor = true;

    /**
     * 构造函数
     *
     * @param descriptor 道具描述符
     * @param slot 槽位
     */
    public RuntimeProp(PropDescriptor descriptor, ActorPropSlot slot) {
        this.descriptor = descriptor;
        this.slot = slot;
        LOGGER.debug("[RuntimeProp] 创建道具: {} -> {}", descriptor.getId(), slot);
    }

    // =========================================================
    // Getters and Setters
    // =========================================================

    public PropDescriptor getDescriptor() {
        return descriptor;
    }

    public ActorPropSlot getSlot() {
        return slot;
    }

    public void setSlot(ActorPropSlot slot) {
        this.slot = slot;
    }

    public Vector3f getLocalOffset() {
        return localOffset;
    }

    public void setLocalOffset(float x, float y, float z) {
        this.localOffset.set(x, y, z);
    }

    public Vector3f getLocalRotation() {
        return localRotation;
    }

    public void setLocalRotation(float x, float y, float z) {
        this.localRotation.set(x, y, z);
    }

    // =========================================================
    // Transform Update
    // =========================================================

    /**
     * 更新道具的 Transform（每帧调用）
     * <p>让道具跟随演员的位置、旋转
     *
     * @param actor Entity
     */
    public void updateTransform(Entity actor) {
        if (!boundToActor) {
            // 如果已解绑（如投掷中），不更新 Transform
            return;
        }

        if (actor == null) {
            return;
        }

        // TODO: 未来实现
        // 1. 找到骨骼/手的位置（根据 slot）
        // 2. 应用 localOffset / localRotation
        // 3. 更新 renderObject 的 transform

        // 当前简化实现：道具位置 = 演员位置 + 局部偏移
        Vec3d actorPos = new Vec3d(
                actor.getX(),
                actor.getY(),
                actor.getZ()
        );
        Vec3d propPos = actorPos.add(
                localOffset.x,
                localOffset.y,
                localOffset.z
        );

        // 更新世界位置（用于获取当前位置）
        this.worldPosition = propPos;

        // TODO: 更新 renderObject 的位置
        LOGGER.trace("[RuntimeProp] 更新道具 Transform: {} -> ({}, {}, {})",
                descriptor.getId(), propPos.x, propPos.y, propPos.z);
    }

    /**
     * 设置世界位置（用于投掷时的独立位置）
     * <p>调用此方法后，道具将不再跟随 Actor
     *
     * @param pos 世界位置
     */
    public void setWorldTransform(Vec3d pos) {
        this.worldPosition = pos;
        this.boundToActor = false;
        // TODO: 更新 renderObject 的位置
        LOGGER.trace("[RuntimeProp] 设置世界位置: {} -> ({}, {}, {})",
                descriptor.getId(), pos.x, pos.y, pos.z);
    }

    /**
     * 获取当前世界位置
     *
     * @return 世界位置，如果未设置则返回 null
     */
    public Vec3d getWorldPosition() {
        return worldPosition;
    }

    /**
     * 检查是否绑定到 Actor
     *
     * @return 如果绑定到 Actor 则返回 true
     */
    public boolean isBoundToActor() {
        return boundToActor;
    }

    // =========================================================
    // Lifecycle
    // =========================================================

    /**
     * 清理资源
     */
    public void dispose() {
        // TODO: 清理 renderObject
        LOGGER.debug("[RuntimeProp] 清理道具: {}", descriptor.getId());
    }
}

