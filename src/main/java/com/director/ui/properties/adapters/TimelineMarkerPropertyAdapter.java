package com.director.ui.properties.adapters;

import com.director.i18n.DirectorLang;
import com.director.i18n.TimelinePropertyAdapterI18n;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.timeline.model.TimelineMarker;
import imgui.ImGui;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImFloat;
import imgui.type.ImString;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TimelineMarkerPropertyAdapter - 标记属性适配器
 *
 * <p>为时间线上的标记（TimelineMarker）提供属性编辑界面。
 */
public class TimelineMarkerPropertyAdapter implements IPropertyAdapter<TimelineMarker> {

    private final ImFloat timeFloat = new ImFloat();
    private final ImString nameString = new ImString("", 256);
    private final ImString noteString = new ImString("", 512);
    private final float[] colorArr = new float[3];

    @Override
    public boolean renderProperties(TimelineMarker marker) {
        if (marker == null) {
            UITheme.label(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineMarker.NO_SELECTION),
                    UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }

        AtomicBoolean modified = new AtomicBoolean(false);

        UITheme.title(Icons.TAG + " " + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineMarker.TITLE));

        if (ImGui.collapsingHeader(Icons.INFO + " "
                + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineMarker.SECTION_BASIC),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            timeFloat.set(marker.getTime());
            if (ImGui.dragFloat(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineMarker.TIME_POSITION),
                    timeFloat.getData(), 0.02f, 0.0f, 10_000f, "%.2fs")) {
                marker.setTime(timeFloat.get());
                modified.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineMarker.TIME_POSITION_TOOLTIP));
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();

            nameString.set(marker.getName());
            if (ImGui.inputText(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineMarker.MARKER_NAME),
                    nameString)) {
                marker.setName(nameString.get());
                modified.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineMarker.MARKER_NAME_TOOLTIP));
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();
            ImGui.unindent();
        }

        if (ImGui.collapsingHeader(Icons.PALETTE + " "
                + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineMarker.SECTION_APPEARANCE),
                ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            ImGui.text(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineMarker.PRESET_COLORS));
            UITheme.spacingSmall();

            int[] presetColors = {
                    0xFFFF0000,
                    0xFFFF8000,
                    0xFFFFFF00,
                    0xFF00FF00,
                    0xFF00FFFF,
                    0xFF0000FF,
                    0xFFFF00FF,
                    0xFFFFFFFF,
                    0xFF808080,
                    0xFF000000,
                    0xFFFF6B6B,
                    0xFF4ECDC4,
            };

            String[] presetColorNames = TimelinePropertyAdapterI18n.markerPresetColorNames();

            ImGui.beginGroup();
            for (int i = 0; i < presetColors.length; i++) {
                if (i > 0 && i % 4 == 0) {
                    ImGui.newLine();
                }

                int presetColor = presetColors[i];
                float[] presetRGB = new float[]{
                        ((presetColor >> 16) & 0xFF) / 255.0f,
                        ((presetColor >> 8) & 0xFF) / 255.0f,
                        (presetColor & 0xFF) / 255.0f
                };

                ImGui.pushID("preset_" + i);
                if (ImGui.colorButton(presetColorNames[i], presetRGB, ImGuiColorEditFlags.NoAlpha, 24, 24)) {
                    marker.setColor(presetColor);
                    colorArr[0] = presetRGB[0];
                    colorArr[1] = presetRGB[1];
                    colorArr[2] = presetRGB[2];
                    modified.set(true);
                }

                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(presetColorNames[i]);
                }

                ImGui.popID();

                if (i < presetColors.length - 1 && (i + 1) % 4 != 0) {
                    ImGui.sameLine();
                }
            }
            ImGui.endGroup();

            UITheme.spacingSmall();
            UITheme.separator();
            UITheme.spacingSmall();

            ImGui.text(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineMarker.CUSTOM_COLOR));
            UITheme.spacingSmall();

            int color = marker.getColor();
            colorArr[0] = ((color >> 16) & 0xFF) / 255.0f;
            colorArr[1] = ((color >> 8) & 0xFF) / 255.0f;
            colorArr[2] = (color & 0xFF) / 255.0f;

            int flags = ImGuiColorEditFlags.DisplayRGB
                    | ImGuiColorEditFlags.InputRGB
                    | ImGuiColorEditFlags.NoAlpha;

            if (ImGui.colorEdit3(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineMarker.MARKER_COLOR),
                    colorArr, flags)) {
                int r = (int) (colorArr[0] * 255.0f);
                int g = (int) (colorArr[1] * 255.0f);
                int b = (int) (colorArr[2] * 255.0f);
                int newColor = (0xFF << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
                marker.setColor(newColor);
                modified.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineMarker.MARKER_COLOR_TOOLTIP));
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();
            ImGui.unindent();
        }

        if (ImGui.collapsingHeader(Icons.INFO + " "
                + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineMarker.SECTION_DESCRIPTION))) {
            ImGui.indent();
            String currentNote = marker.getNote();
            if (currentNote == null) {
                currentNote = "";
            }
            noteString.set(currentNote);

            if (ImGui.inputTextMultiline(
                    TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineMarker.DESCRIPTION),
                    noteString, 512, 100)) {
                marker.setNote(noteString.get());
                modified.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineMarker.DESCRIPTION_TOOLTIP));
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();
            ImGui.unindent();
        }

        return modified.get();
    }

    @Override
    public Class<TimelineMarker> getTargetType() {
        return TimelineMarker.class;
    }

    @Override
    public String getAdapterName() {
        return TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineMarker.ADAPTER_NAME);
    }
}
