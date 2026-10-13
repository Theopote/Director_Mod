package com.director.ui.selection;

/**
 * SelectionType v1.0
 *
 * <p>选择类型枚举 - 明确区分不同的选择语义
 *
 * <p>设计原则：
 * <ul>
 *   <li>PROJECT - 项目级资产（环境、全局设置）</li>
 *   <li>ACTOR - 演员（Cast），Project 级资产</li>
 *   <li>TRACK - 时间线轨道（TimelineTrack）</li>
 *   <li>CLIP - 表演片段（EntityClip）</li>
 * </ul>
 *
 * <p>为什么必须有 SelectionType？
 * <ul>
 *   <li>避免 Actor 面板显示时机不可预测</li>
 *   <li>避免世界点选演员时 PropertiesPanel 跳错面板</li>
 *   <li>为未来 AI / Actor 分组功能预留扩展空间</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public enum SelectionType {
    /**
     * 项目级选择（默认）
     * <p>Show Project panel
     */
    PROJECT,
    LIGHT,
    
    /**
     * 演员选择
     * <p>显示 Actor 面板（ActorProjectPropertyAdapter）
     */
    ACTOR,
    
    /**
     * 轨道选择
     * <p>显示 Track 面板（TimelineTrackPropertyAdapter）
     */
    TRACK,
    
    /**
     * 片段选择
     * <p>显示 Clip 面板（Entity*ClipPropertyAdapter）
     */
    CLIP,

    /**
     * Keyframe selection.
     */
    KEYFRAME
}

