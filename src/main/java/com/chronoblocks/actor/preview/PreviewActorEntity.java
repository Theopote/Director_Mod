package com.chronoblocks.actor.preview;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
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
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class PreviewActorEntity extends Entity {

    /**
     * 构造函数
     *
     * @param type 实体类型
     * @param world 客户端世界
     */
    public PreviewActorEntity(EntityType<?> type, ClientWorld world) {
        super(type, world);
        this.noClip = true; // 无碰撞
    }

    // ======================
    // 生命周期
    // ======================

    @Override
    protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
        // 不需要 DataTracker（预览实体不需要同步数据）
    }

    // ======================
    // 序列化（不保存预览实体）
    // ======================
    //
    // ⚠️ 需要实现以下抽象方法（使用 IDE 自动生成）：
    // 1. 在类名上按 Alt+Enter（或右键菜单）
    // 2. 选择 "Implement methods" 或 "实现抽象方法"
    // 3. IDE 会自动生成正确的方法签名
    // 4. 在方法体中添加注释即可：
    //    - readCustomData: // 不读取（预览实体不保存）
    //    - writeCustomData: // 不写入（不进存档）
    //
    // 如果 IDE 无法自动生成，请查看 net.minecraft.entity.Entity 类的实际抽象方法定义
    // 可能的类型：
    // - Entity.ReadView / Entity.WriteView
    // - net.minecraft.entity.data.DataTracker.ReadView / WriteView
    // - 或其他类型（取决于 Minecraft 1.21 的实际 API）

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
