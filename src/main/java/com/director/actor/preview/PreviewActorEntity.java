package com.director.actor.preview;

import com.director.actor.rig.Pose;
import com.director.actor.rig.skeleton.LegacyHumanoidPoseAdapter;
import com.director.actor.rig.skeleton.RigPose;
import com.director.actor.runtime.ActorRuntime;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.Vec3d;

/**
 * PreviewActorEntity v1.0
 *
 * <p>PreviewActorEntity = Actor 的客户端视觉投影
 *
 * <p>设计原则：
 * <ul>
 *   <li>继承 Entity（为了复用渲染 / hitbox / yaw / pitch）</li>
 *   <li>不注册到服务器</li>
 *   <li>不参与 tick AI</li>
 *   <li>生命周期完全由我们控制</li>
 * </ul>
 *
 * <p>核心特点：
 * <ul>
 *   <li>✅ 仅客户端存在</li>
 *   <li>✅ 不保存、不同步、不参与 AI</li>
 *   <li>✅ 作为 Actor 的"视觉投影"</li>
 *   <li>✅ 能被 Gizmo / Selection 使用</li>
 * </ul>
 *
 * <p>⚠️ 注意：此类的某些方法可能需要根据 Minecraft 1.21 的实际 API 进行调整。
 * <p>如果编译错误，请检查 Entity 类的实际方法签名并实现相应的抽象方法。
 *
 * @author Director Team
 * @version 1.0
 */
public class PreviewActorEntity extends ArmorStandEntity {

    /** ActorRuntime（身体控制参数，由 Renderer 读取） */
    private ActorRuntime actorRuntime;

    /** 最终混合姿态（由 ActorRuntimeController 设置，Renderer 读取） */
    private Pose finalPose;

    /** Standard indexed pose used by the bone animation/editor pipeline. */
    private RigPose finalRigPose;

    /**
     * 构造函数
     *
     * @param type 实体类型
     * @param world 客户端世界
     */
    public PreviewActorEntity(EntityType<? extends ArmorStandEntity> type, ClientWorld world) {
        super(type, world);
        this.noClip = true; // 无碰撞
    }

    /**
     * 设置 ActorRuntime（由 ActorRuntimeController 调用）。
     *
     * @param runtime ActorRuntime
     */
    public void setActorRuntime(ActorRuntime runtime) {
        this.actorRuntime = runtime;
    }

    /**
     * 获取 ActorRuntime（由 Renderer / Mixin 调用）。
     *
     * @return ActorRuntime，如果未设置则返回 null
     */
    public ActorRuntime getActorRuntime() {
        return actorRuntime;
    }

    /**
     * 设置最终混合姿态（由 ActorRuntimeController 调用）。
     *
     * @param pose 最终姿态
     */
    public void setFinalPose(Pose pose) {
        this.finalPose = pose;
        if (pose == null) {
            this.finalRigPose = null;
            return;
        }
        setFinalRigPose(LegacyHumanoidPoseAdapter.convert(pose));
    }

    public void setFinalRigPose(RigPose pose) {
        this.finalRigPose = pose == null ? null : pose.copy();
        if (this.finalRigPose != null) {
            HumanoidArmorStandRigBinding.apply(this, this.finalRigPose);
        }
    }

    public RigPose getFinalRigPose() {
        return finalRigPose == null ? null : finalRigPose.copy();
    }

    /**
     * 获取最终混合姿态（由 Renderer / Mixin 调用）。
     *
     * @return 最终姿态，如果未设置则返回 null
     */
    public Pose getFinalPose() {
        return finalPose;
    }

    // ======================
    // 生命周期
    // ======================

    @Override
    protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
        super.initDataTracker(builder);
    }

    // ======================
    // 序列化（不保存预览实体）
    // ======================

    @Override
    protected void readCustomData(ReadView readView) {
        // 不读取（预览实体不保存）
    }

    @Override
    protected void writeCustomData(WriteView writeView) {
        // 不写入（不进存档）
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        // 不可被伤害
        return false;
    }

    @Override
    public void tick() {
        // ❌ 不调用 super.tick()
        // ❌ 不执行 AI / 重力
        // 位置和状态完全由 EntityRuntimeController 每帧驱动
    }

    // ======================
    // 物理 / 移动禁用
    // ======================

    @Override
    public void move(MovementType movementType, Vec3d movement) {
        // 禁止物理移动（位置完全由 Timeline 控制）
    }

    @Override
    public boolean collidesWith(Entity other) {
        return false; // 不参与碰撞检测
    }

    @Override
    public boolean isAttackable() {
        return false; // 不可攻击
    }

    @Override
    public boolean isPushable() {
        return false; // 不可被推动
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false; // 不可被投射物击中
    }

    // ======================
    // 渲染 / 可见性
    // ======================

    @Override
    public boolean shouldRender(double distance) {
        // 始终可渲染（由 Timeline 控制可见性）
        return true;
    }

    @Override
    public boolean shouldRenderName() {
        // 名称标签由 Timeline 控制
        return super.shouldRenderName();
    }
}
