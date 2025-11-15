package com.chronoblocks.ui.widgets;

import com.chronoblocks.animation.core.Keyframe.Easing;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;

/**
 * 简化版曲线预览组件，支持 Keyframe.Easing。
 */
public final class CurvePreviewWidget {

    private static final int SAMPLE_COUNT = 60;

    private CurvePreviewWidget() {
    }

    public static void render(Easing easing, float width, float height) {
        ImVec2 cursor = ImGui.getCursorScreenPos();
        float x = cursor.x;
        float y = cursor.y;

        ImGui.invisibleButton("##curvePreview", width, height);
        ImDrawList drawList = ImGui.getWindowDrawList();

        int background = ImGui.getColorU32(ImGuiCol.FrameBg);
        drawList.addRectFilled(x, y, x + width, y + height, background, 4.0f);

        drawGrid(drawList, x, y, width, height);
        drawReferenceLine(drawList, x, y, width, height);
        drawCurve(drawList, easing, x, y, width, height);
        drawList.addRect(x, y, x + width, y + height, ImGui.getColorU32(ImGuiCol.Border), 4.0f);
    }

    private static void drawGrid(ImDrawList drawList, float x, float y, float width, float height) {
        int gridColor = ImGui.getColorU32(ImGuiCol.Border, 0.2f);
        for (int i = 1; i < 4; i++) {
            float gx = x + width * i / 4.0f;
            drawList.addLine(gx, y, gx, y + height, gridColor);
        }
        for (int i = 1; i < 4; i++) {
            float gy = y + height * i / 4.0f;
            drawList.addLine(x, gy, x + width, gy, gridColor);
        }
    }

    private static void drawReferenceLine(ImDrawList drawList, float x, float y, float width, float height) {
        int refColor = ImGui.getColorU32(ImGuiCol.TextDisabled);
        drawList.addLine(x, y + height, x + width, y, refColor);
    }

    private static void drawCurve(ImDrawList drawList, Easing easing, float x, float y, float width, float height) {
        int curveColor = ImGui.getColorU32(ImGuiCol.PlotLinesHovered);
        float prevX = x;
        float prevY = y + height - (float) evaluate(easing, 0.0) * height;
        for (int i = 1; i <= SAMPLE_COUNT; i++) {
            float t = i / (float) SAMPLE_COUNT;
            float currentX = x + width * t;
            float currentY = y + height - (float) evaluate(easing, t) * height;
            drawList.addLine(prevX, prevY, currentX, currentY, curveColor, 2.0f);
            prevX = currentX;
            prevY = currentY;
        }
    }

    private static double evaluate(Easing easing, double t) {
        return switch (easing) {
            case LINEAR -> t;
            case EASE_IN -> t * t;
            case EASE_OUT -> 1.0 - (1.0 - t) * (1.0 - t);
        };
    }
}


