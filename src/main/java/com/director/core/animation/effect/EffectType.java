package com.director.core.animation.effect;

import com.director.i18n.EffectTypeI18n;

/**
 * 特效类型枚举
 * 
 * <p>
 * 定义了 Director 支持的所有方块特效类型。
 * 特效系统用于在方块出现过程中添加额外的视觉/听觉装饰层，
 * 不改变主动画的位移/旋转/缩放，只提供装饰效果。
 * 
 * @author Director Team
 * @version 1.0
 */
public enum EffectType {

    /** 出场闪白光 - 方块出现瞬间闪白光，适合科技/传送风格 */
    FLASH_WHITE,

    /** 尘土爆裂 - 方块落地时喷出少量尘土/碎屑粒子 */
    DUST_BURST,

    /** 魔法光焰 - 环绕方块的小型魔法粒子光焰 */
    MAGIC_FLARE,

    /** 脉冲发光 - 方块出现时短暂发光并渐渐恢复正常 */
    GLOW_PULSE,

    /** 落地涟漪 - 方块落地时向四周扩散的平面环形涟漪 */
    IMPACT_RIPPLE,

    /** 位移尾迹 - 方块在移动过程中向后方产生的轨迹粒子 */
    MOTION_TRAIL;

    public String getDisplayName() {
        return EffectTypeI18n.name(this);
    }

    public String getDescription() {
        return EffectTypeI18n.description(this);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
