package com.chronoblocks.ui.adapters.impl;

import com.chronoblocks.core.animation.Track;
import com.chronoblocks.ui.adapters.BasePropertyAdapter;
import imgui.ImGui;

/**
 * 🎵 TrackAdapter - 轨道属性适配器
 *
 * <p>为 ChronoBlocks 动画轨道对象提供专用属性编辑器。
 *
 * <h3>✨ 支持的属性：</h3>
 * <ul>
 *     <li>📝 基础属性：名称、目标ID</li>
 *     <li>🎯 绑定信息：目标类型（方块/容器）</li>
 *     <li>⏱ 时间线：关键帧数量、时长</li>
 *     <li>📊 统计信息：关键帧分布、采样信息</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 4.5
 * @since 4.5
 */
public class TrackAdapter extends BasePropertyAdapter<Track> {

    @Override
    protected boolean doRenderProperties(Track track) {
        boolean changed = false;

        // ========== 基础属性 ==========
        renderSectionHeader("📝 基础信息");
        
        changed |= renderTextProperty("轨道名称",
                track::getName,
                track::setName,
                128);
        
        changed |= renderTextProperty("目标ID",
                track::getTargetId,
                track::setTargetId,
                128);
        
        changed |= renderBoolProperty("目标是容器",
                track::isTargetContainer,
                track::setTargetIsContainer);
        
        renderHelpMarker("勾选表示目标是容器，否则是单个方块");

        // ========== 时间线信息 ==========
        renderSectionHeader("⏱ 时间线");
        
        int keyframeCount = track.getKeyframes().size();
        renderReadOnlyText("关键帧数量", String.valueOf(keyframeCount));
        
        if (keyframeCount > 0) {
            float firstTime = track.getKeyframes().get(0).getTime();
            float lastTime = track.getKeyframes().get(keyframeCount - 1).getTime();
            float duration = lastTime - firstTime;
            
            renderReadOnlyText("起始时间", String.format("%.2f 秒", firstTime));
            renderReadOnlyText("结束时间", String.format("%.2f 秒", lastTime));
            renderReadOnlyText("时长", String.format("%.2f 秒", duration));
        } else {
            ImGui.textDisabled("尚无关键帧");
        }

        // ========== 关键帧列表（只读预览） ==========
        if (keyframeCount > 0) {
            renderSectionHeader("🎬 关键帧");
            
            ImGui.beginChild("##keyframe_list", 0, 150, true);
            
            for (int i = 0; i < Math.min(keyframeCount, 10); i++) {
                var kf = track.getKeyframes().get(i);
                ImGui.text(String.format("[%d] %.2f 秒", i, kf.getTime()));
                
                if (ImGui.isItemHovered()) {
                    ImGui.beginTooltip();
                    ImGui.text(String.format("位置: (%.1f, %.1f, %.1f)",
                            kf.getPosition()[0],
                            kf.getPosition()[1],
                            kf.getPosition()[2]));
                    ImGui.text(String.format("旋转: (%.1f°, %.1f°, %.1f°)",
                            kf.getRotation()[0],
                            kf.getRotation()[1],
                            kf.getRotation()[2]));
                    ImGui.text(String.format("缩放: (%.2f, %.2f, %.2f)",
                            kf.getScale()[0],
                            kf.getScale()[1],
                            kf.getScale()[2]));
                    ImGui.text(String.format("不透明度: %.2f", kf.getOpacity()));
                    ImGui.endTooltip();
                }
            }
            
            if (keyframeCount > 10) {
                ImGui.textDisabled(String.format("... 还有 %d 个关键帧", keyframeCount - 10));
            }
            
            ImGui.endChild();
        }

        // ========== 采样预览 ==========
        if (keyframeCount >= 2) {
            renderSectionHeader("🔍 采样预览");
            
            float[] sampleTime = {0.0f};
            if (ImGui.sliderFloat("采样时间", sampleTime, 
                    track.getKeyframes().get(0).getTime(),
                    track.getKeyframes().get(keyframeCount - 1).getTime(),
                    "%.2f 秒")) {
                
                var sampledKf = track.sample(sampleTime[0]);
                
                ImGui.spacing();
                ImGui.textDisabled("采样结果:");
                ImGui.text(String.format("  位置: (%.1f, %.1f, %.1f)",
                        sampledKf.getPosition()[0],
                        sampledKf.getPosition()[1],
                        sampledKf.getPosition()[2]));
                ImGui.text(String.format("  旋转: (%.1f°, %.1f°, %.1f°)",
                        sampledKf.getRotation()[0],
                        sampledKf.getRotation()[1],
                        sampledKf.getRotation()[2]));
                ImGui.text(String.format("  不透明度: %.2f", sampledKf.getOpacity()));
            }
        }

        return changed;
    }

    @Override
    public void onPropertyChanged(Track track) {
        super.onPropertyChanged(track);
        // 重新排序关键帧
        track.sortByTime();
        logger.debug("[TrackAdapter] 轨道属性已更新: {}", track.getName());
    }

    @Override
    public boolean validate(Track track) {
        // 验证名称不为空
        if (track.getName() == null || track.getName().trim().isEmpty()) {
            logger.warn("[TrackAdapter] 轨道名称为空");
            return false;
        }

        // 验证目标ID不为空
        if (track.getTargetId() == null || track.getTargetId().trim().isEmpty()) {
            logger.warn("[TrackAdapter] 目标ID为空");
            return false;
        }

        return true;
    }

    @Override
    public Class<Track> getTargetType() {
        return Track.class;
    }

    @Override
    public int getPriority() {
        return 100; // 专用适配器，高优先级
    }

    @Override
    public String getDisplayName() {
        return "🎵 轨道编辑器";
    }

    @Override
    public String getDescription() {
        return "用于编辑动画轨道的名称、目标绑定和关键帧信息";
    }
}

