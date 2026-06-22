package com.director.ui.properties.adapters;

import com.director.i18n.DirectorLang;
import com.director.i18n.TimelinePropertyAdapterI18n;
import com.director.ui.properties.IPropertyAdapter;
import com.director.timeline.model.selection.TimelineKeyframeMultiSelection;
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

        UITheme.title(Icons.KEYFRAME + " " + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineKeyframeMulti.TITLE));
        ImGui.spacing();

        int total = selection.getTotalCount();
        ImGui.textDisabled(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineKeyframeMulti.SELECTED_COUNT, total));
        ImGui.textDisabled(TimelinePropertyAdapterI18n.tr(
                DirectorLang.Properties.AdapterTimelineKeyframeMulti.CLIP_KEYFRAMES_COUNT,
                selection.getClipKeyframes().size()));
        ImGui.textDisabled(TimelinePropertyAdapterI18n.tr(
                DirectorLang.Properties.AdapterTimelineKeyframeMulti.TRACK_KEYFRAMES_COUNT,
                selection.getAutomationKeyframes().size()));

        ImGui.spacing();
        UITheme.separator();
        ImGui.spacing();

        ImGui.textWrapped(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineKeyframeMulti.HINT_MULTI_MODE));
        ImGui.textWrapped(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineKeyframeMulti.HINT_DELETE));
        return false;
    }

    @Override
    public Class<TimelineKeyframeMultiSelection> getTargetType() {
        return TimelineKeyframeMultiSelection.class;
    }

    @Override
    public String getAdapterName() {
        return TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineKeyframeMulti.ADAPTER_NAME);
    }
}
