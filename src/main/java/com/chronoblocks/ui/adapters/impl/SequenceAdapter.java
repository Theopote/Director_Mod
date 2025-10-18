package com.chronoblocks.ui.adapters.impl;

import com.chronoblocks.core.timeline.Timeline;
import com.chronoblocks.ui.adapters.BasePropertyAdapter;

/**
 * 🎬 SequenceAdapter - 时间线序列属性适配器
 *
 * <p>为 ChronoBlocks 时间线序列对象提供专用属性编辑器。
 *
 * <h3>✨ 支持的属性：</h3>
 * <ul>
 *     <li>⏱ 时长设置：序列总时长</li>
 *     <li>🎵 轨道管理：轨道数量、轨道列表</li>
 *     <li>📊 统计信息：总关键帧数、总时长</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 4.5
 * @since 4.5
 */
public class SequenceAdapter extends BasePropertyAdapter<Timeline> {

    @Override
    protected boolean doRenderProperties(Timeline timeline) {
        boolean changed = false;

        // ========== 时长设置 ==========
        renderSectionHeader("⏱ 时长设置");
        
        if (renderDoubleProperty("总时长 (秒)",
                timeline::getDurationSeconds,
                timeline::setDurationSeconds,
                0.1, 0.1, 3600.0)) {
            changed = true;
        }

        renderHelpMarker("时间线的总时长，单位为秒");

        // ========== 轨道管理 ==========
        renderSectionHeader("🎵 轨道管理");
        
        int trackCount = timeline.getTracks().size();
        renderReadOnlyText("轨道数量", String.valueOf(trackCount));

        if (trackCount > 0) {
            renderReadOnlyText("时长", String.format("%.2f 秒", timeline.getDurationSeconds()));
        } else {
            renderReadOnlyText("状态", "暂无轨道");
        }

        // ========== 轨道列表 ==========
        if (trackCount > 0) {
            renderSectionHeader("📋 轨道列表");
            
            var tracks = timeline.getTracks();
            for (int i = 0; i < Math.min(tracks.size(), 10); i++) {
                var track = tracks.get(i);
                String trackInfo = String.format("[%d] %s (%d 关键帧)",
                        i,
                        track.getName() != null ? track.getName() : "未命名",
                        track.getKeyframes().size());
                
                renderReadOnlyText("", trackInfo);
            }
            
            if (tracks.size() > 10) {
                renderReadOnlyText("", String.format("... 还有 %d 个轨道", tracks.size() - 10));
            }
        }

        // ========== 统计信息 ==========
        if (trackCount > 0) {
            renderSectionHeader("📊 统计");
            
            int totalKeyframes = timeline.getTracks().stream()
                    .mapToInt(track -> track.getKeyframes().size())
                    .sum();
            
            renderReadOnlyText("总关键帧数", String.valueOf(totalKeyframes));
            renderReadOnlyText("平均关键帧/轨道", 
                    trackCount > 0 ? String.format("%.1f", (float) totalKeyframes / trackCount) : "0");
        }

        return changed;
    }

    /**
     * 渲染 double 类型属性
     */
    private boolean renderDoubleProperty(String label,
                                        DoubleGetter getter,
                                        DoubleSetter setter,
                                        double speed, double min, double max) {
        float[] value = {(float) getter.get()};
        
        pushItemWidth(-labelWidth);
        boolean changed = imgui.ImGui.dragFloat("##" + label, value, (float) speed, (float) min, (float) max, "%.2f");
        popItemWidth();
        
        imgui.ImGui.sameLine();
        imgui.ImGui.text(label);
        
        if (changed) {
            setter.set(value[0]);
        }
        
        return changed;
    }

    @Override
    public void onPropertyChanged(Timeline timeline) {
        super.onPropertyChanged(timeline);
        logger.debug("[SequenceAdapter] 时间线属性已更新: 时长={}秒", timeline.getDurationSeconds());
    }

    @Override
    public boolean validate(Timeline timeline) {
        // 验证时长为正数
        if (timeline.getDurationSeconds() <= 0) {
            logger.warn("[SequenceAdapter] 时长必须大于0: {}", timeline.getDurationSeconds());
            return false;
        }

        return true;
    }

    @Override
    public Class<Timeline> getTargetType() {
        return Timeline.class;
    }

    @Override
    public int getPriority() {
        return 100; // 专用适配器，高优先级
    }

    @Override
    public String getDisplayName() {
        return "🎬 序列编辑器";
    }

    @Override
    public String getDescription() {
        return "用于编辑时间线序列的时长和轨道信息";
    }

    // ==================== 辅助方法 ====================

    private void pushItemWidth(float width) {
        imgui.ImGui.pushItemWidth(width);
    }

    private void popItemWidth() {
        imgui.ImGui.popItemWidth();
    }

    // ==================== Getter/Setter 接口 ====================

    @FunctionalInterface
    private interface DoubleGetter {
        double get();
    }

    @FunctionalInterface
    private interface DoubleSetter {
        void set(double value);
    }
}

