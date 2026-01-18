package com.chronoblocks.actor.rig;

/**
 * PoseMask v1.0
 *
 * <p>姿态控制权掩码，声明谁有控制权。
 *
 * <p>用于 Animation 和 RigProfile 混合时，决定哪些部位由哪个系统控制。
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class PoseMask {

    /** 身体控制权 */
    public boolean body = false;

    /** 头部控制权 */
    public boolean head = false;

    /** 手臂控制权 */
    public boolean arms = false;

    /** 腿部控制权 */
    public boolean legs = false;

    /**
     * 创建完全控制掩码（所有部位）。
     *
     * @return 完全控制掩码
     */
    public static PoseMask full() {
        PoseMask m = new PoseMask();
        m.body = true;
        m.head = true;
        m.arms = true;
        m.legs = true;
        return m;
    }

    /**
     * 创建无控制掩码（所有部位都不控制）。
     *
     * @return 无控制掩码
     */
    public static PoseMask none() {
        return new PoseMask();
    }
}
