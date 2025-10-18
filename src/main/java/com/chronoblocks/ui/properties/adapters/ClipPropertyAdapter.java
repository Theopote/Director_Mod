package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.core.animation.AnimationClip;
import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;

/**
 * 动画片段属性适配器 - ChronoBlocks Properties System v4.5
 * 
 * <p>提供动画片段对象的属性编辑界面，包括：
 * <ul>
 *   <li>片段名称</li>
 *   <li>持续时间</li>
 *   <li>轨道数量</li>
 *   <li>关键帧数量</li>
 * </ul>
 * 
 * @version 4.5
 * @since 2025-10-15
 */
public class ClipPropertyAdapter implements IPropertyAdapter<AnimationClip> {

    @Override
    public boolean renderProperties(AnimationClip clip) {
        boolean modified = false;
        
        // 标题
        UITheme.title("🎬 动画片段属性");
        
        // 基本信息
        UITheme.beginGroup("基本信息");
        ImGui.text("名称: " + (clip.getName() != null ? clip.getName() : "未命名"));
        UITheme.endGroup();
        
        UITheme.separator();
        
        // 时间信息
        UITheme.beginGroup("时间");
        float duration = clip.computeDuration();
        ImGui.text(String.format("持续时间: %.2f 秒", duration));
        UITheme.endGroup();
        
        UITheme.separator();
        
        // 轨道信息
        UITheme.beginGroup("轨道");
        int trackCount = clip.getTracks().size();
        ImGui.text("轨道数量: " + trackCount);
        
        // 统计关键帧总数
        int totalKeyframes = clip.getTracks().stream()
            .mapToInt(track -> track.getKeyframes().size())
            .sum();
        ImGui.text("关键帧总数: " + totalKeyframes);
        UITheme.endGroup();
        
        UITheme.separator();
        
        // 操作按钮
        UITheme.beginGroup("操作");
        if (ImGui.button("播放片段", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
            // TODO: 实现播放逻辑
            modified = true;
        }
        
        ImGui.sameLine();
        
        if (ImGui.button("导出片段", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
            // TODO: 实现导出逻辑
            modified = true;
        }
        UITheme.endGroup();
        
        return modified;
    }

    @Override
    public Class<AnimationClip> getTargetType() {
        return AnimationClip.class;
    }
}

