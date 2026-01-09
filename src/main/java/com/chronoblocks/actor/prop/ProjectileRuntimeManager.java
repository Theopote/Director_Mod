package com.chronoblocks.actor.prop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * ProjectileRuntimeManager v1.0
 *
 * <p>投掷物运行时管理器：统一管理所有投掷中的道具
 *
 * <p>设计原则：
 * <ul>
 *   <li>✅ 只存在于 Client / Preview</li>
 *   <li>✅ 每帧更新所有投掷物</li>
 *   <li>✅ 自动清理已完成的投掷物</li>
 *   <li>❌ 不影响世界</li>
 * </ul>
 *
 * <p>职责边界：
 * <ul>
 *   <li>✅ 管理 RuntimeProjectile 的生命周期</li>
 *   <li>✅ 每帧更新投掷物位置</li>
 *   <li>❌ 不关心 Timeline / Clip</li>
 *   <li>❌ 不关心渲染细节</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class ProjectileRuntimeManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectileRuntimeManager.class);
    private static final ProjectileRuntimeManager INSTANCE = new ProjectileRuntimeManager();

    public static ProjectileRuntimeManager getInstance() {
        return INSTANCE;
    }

    /** 所有投掷中的道具 */
    private final List<RuntimeProjectile> projectiles = new ArrayList<>();

    private ProjectileRuntimeManager() {
    }

    // =========================================================
    // Projectile Management
    // =========================================================

    /**
     * 生成投掷物
     *
     * @param projectile 投掷物实例
     */
    public void spawn(RuntimeProjectile projectile) {
        if (projectile == null) {
            return;
        }
        projectiles.add(projectile);
        LOGGER.debug("[ProjectileRuntimeManager] 生成投掷物，当前数量: {}", projectiles.size());
    }

    /**
     * 更新所有投掷物（每帧调用）
     *
     * @param deltaTime 时间增量（秒）
     */
    public void update(float deltaTime) {
        if (deltaTime <= 0f) {
            return;
        }

        Iterator<RuntimeProjectile> it = projectiles.iterator();
        while (it.hasNext()) {
            RuntimeProjectile p = it.next();
            p.update(deltaTime);
            if (p.isFinished()) {
                it.remove();
            }
        }
    }

    /**
     * 清空所有投掷物
     */
    public void clear() {
        for (RuntimeProjectile p : projectiles) {
            p.prop.dispose();
        }
        projectiles.clear();
        LOGGER.debug("[ProjectileRuntimeManager] 清空所有投掷物");
    }

    /**
     * 获取当前投掷物数量
     *
     * @return 投掷物数量
     */
    public int size() {
        return projectiles.size();
    }
}

