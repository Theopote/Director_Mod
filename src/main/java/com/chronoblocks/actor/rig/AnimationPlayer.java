package com.chronoblocks.actor.rig;

/**
 * AnimationPlayer v1.0
 *
 * <p>动画播放器接口，用于从 AnimationClip 获取当前姿态。
 *
 * <p>这是一个抽象层，实际实现可能来自：
 * <ul>
 *   <li>Minecraft 原版动画系统</li>
 *   <li>GeckoLib 动画系统</li>
 *   <li>自定义动画系统</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>如果当前没有播放动画，返回 null</li>
 *   <li>如果动画存在，返回 AnimationPoseResult（包含姿态和控制掩码）</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public interface AnimationPlayer {

    /**
     * 获取当前动画姿态结果。
     *
     * <p>如果当前没有播放动画，返回 null。
     * 如果动画存在，返回 AnimationPoseResult（包含姿态和控制掩码）。
     *
     * @return AnimationPoseResult，如果没有动画则返回 null
     */
    AnimationPoseResult getCurrentPose();
}
