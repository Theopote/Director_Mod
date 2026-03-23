package com.director.ui.properties.adapters;

import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.timeline.model.selection.TimelineKeyframeMultiSelection;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import imgui.ImGui;

/**
 * 多选关键帧属性面板（占位）
 *
 * <p>当选中多个关键帧时，不显示某一个关键帧的详细属性，避免误解/混乱。</p>
 */
public class TimelineKeyframeMultiSelectionPropertyAdapter implements IPropertyAdapter<TimelineKeyframeMultiSelection> {

    @Override
    public boolean renderProperties(TimelineKeyframeMultiSelection selection) {
        if (selection == null) {
            return false;
        }

        UITheme.title(Icons.KEYFRAME + " 多选关键帧");
        ImGui.spacing();

        int total = selection.getTotalCount();
        ImGui.textDisabled(String.format("已选中 %d 个关键帧", total));
        ImGui.textDisabled(String.format("- 片段关键帧: %d", selection.getClipKeyframes().size()));
        ImGui.textDisabled(String.format("- 轨道关键帧: %d", selection.getAutomationKeyframes().size()));

        ImGui.spacing();
        UITheme.separator();
        ImGui.spacing();

        ImGui.textWrapped("提示：当前为多选模式，为避免混乱不显示单个关键帧的属性。");
        ImGui.textWrapped("你仍然可以使用工具栏的删除按钮或按 Del 删除这些选中项。");
        return false;
    }

    @Override
    public Class<TimelineKeyframeMultiSelection> getTargetType() {
        return TimelineKeyframeMultiSelection.class;
    }

    @Override
    public String getAdapterName() {
        return "Keyframes (Multi) Properties";
    }
}


