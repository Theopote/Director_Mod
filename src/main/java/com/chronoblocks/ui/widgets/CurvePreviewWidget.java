package com.chronoblocks.ui.widgets;

import com.chronoblocks.core.animation.CurveInterpolator;
import com.chronoblocks.core.animation.InterpolationType;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;

/**
 * 曲线预览组件 v4.5 - 可视化显示插值曲线
 * 
 * <p>在 ImGui 窗口中绘制插值曲线的实时预览图，支持：
 * <ul>
 *   <li>所有插值类型的曲线可视化</li>
 *   <li>贝塞尔控制点显示</li>
 *   <li>网格和坐标轴</li>
 *   <li>鼠标悬停显示坐标</li>
 *   <li>自适应颜色主题</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 在 ImGui 渲染循环中
 * CurvePreviewWidget.render(
 *     InterpolationType.EASE_IN_OUT,
 *     null,  // 贝塞尔控制点（可选）
 *     200,   // 宽度
 *     150    // 高度
 * );
 * }</pre>
 * 
 * @version 4.5
 * @since 2025-10-15
 */
public class CurvePreviewWidget {
    
    // ==================== 渲染配置 ====================
    
    /** 采样点数量（越多越平滑） */
    private static final int SAMPLE_COUNT = 100;
    
    /** 网格线数量 */
    private static final int GRID_LINES = 4;
    
    /** 控制点半径 */
    private static final float CONTROL_POINT_RADIUS = 4.0f;
    
    /** 曲线线宽 */
    private static final float CURVE_LINE_WIDTH = 2.0f;
    
    // ==================== 颜色配置 ====================
    
    /** 背景颜色 */
    private static final int COLOR_BACKGROUND = ImGui.getColorU32(0.1f, 0.1f, 0.12f, 1.0f);
    
    /** 网格颜色 */
    private static final int COLOR_GRID = ImGui.getColorU32(0.3f, 0.3f, 0.35f, 1.0f);
    
    /** 坐标轴颜色 */
    private static final int COLOR_AXIS = ImGui.getColorU32(0.5f, 0.5f, 0.6f, 1.0f);
    
    /** 曲线颜色 */
    private static final int COLOR_CURVE = ImGui.getColorU32(0.2f, 0.8f, 1.0f, 1.0f);
    
    /** 控制点颜色 */
    private static final int COLOR_CONTROL_POINT = ImGui.getColorU32(1.0f, 0.5f, 0.2f, 1.0f);
    
    /** 参考线颜色（LINEAR） */
    private static final int COLOR_REFERENCE = ImGui.getColorU32(0.6f, 0.6f, 0.6f, 0.5f);
    
    /** 文本颜色 */
    private static final int COLOR_TEXT = ImGui.getColorU32(0.8f, 0.8f, 0.8f, 1.0f);
    
    // ==================== 主渲染方法 ====================
    
    /**
     * 渲染曲线预览（只读）
     * 
     * @param type 插值类型
     * @param params 自定义参数（如贝塞尔控制点）
     * @param width 预览宽度
     * @param height 预览高度
     */
    public static void render(InterpolationType type, float[] params, float width, float height) {
        renderInternal(type, params, width, height, false, null);
    }
    
    /**
     * 渲染可交互的曲线预览（支持拖拽贝塞尔控制点）
     * 
     * @param type 插值类型
     * @param params 自定义参数（如贝塞尔控制点），会被修改
     * @param width 预览宽度
     * @param height 预览高度
     * @return true 如果参数被修改
     */
    public static boolean renderInteractive(InterpolationType type, float[] params, float width, float height) {
        boolean[] changed = {false};
        renderInternal(type, params, width, height, true, changed);
        return changed[0];
    }
    
    /**
     * 内部渲染方法
     */
    private static void renderInternal(InterpolationType type, float[] params, float width, float height,
                                         boolean interactive, boolean[] outChanged) {
        // 获取当前绘制位置
        ImVec2 cursorPos = ImGui.getCursorScreenPos();
        float x = cursorPos.x;
        float y = cursorPos.y;
        
        // 创建一个不可见的按钮作为绘制区域（用于接收鼠标事件）
        ImGui.invisibleButton("##curvePreview", width, height);
        
        // 获取绘制列表
        ImDrawList drawList = ImGui.getWindowDrawList();
        
        // 绘制背景
        drawList.addRectFilled(x, y, x + width, y + height, COLOR_BACKGROUND, 4.0f);
        
        // 绘制网格
        drawGrid(drawList, x, y, width, height);
        
        // 绘制参考线（LINEAR）
        if (type != InterpolationType.LINEAR) {
            drawReferenceLine(drawList, x, y, width, height);
        }
        
        // 绘制曲线
        drawCurve(drawList, type, params, x, y, width, height);
        
        // 如果是贝塞尔曲线，绘制控制点
        if (type == InterpolationType.BEZIER && params != null && params.length >= 4) {
            if (interactive && outChanged != null) {
                // 交互式控制点
                outChanged[0] = drawInteractiveBezierControlPoints(drawList, params, x, y, width, height);
            } else {
                // 只读控制点
                drawBezierControlPoints(drawList, params, x, y, width, height);
            }
        }
        
        // 绘制坐标轴标签
        drawLabels(drawList, x, y, width, height);
        
        // 鼠标悬停显示坐标
        if (ImGui.isItemHovered()) {
            ImVec2 mousePos = ImGui.getMousePos();
            showTooltip(type, params, mousePos.x - x, mousePos.y - y, width, height);
        }
        
        // 绘制边框
        drawList.addRect(x, y, x + width, y + height, COLOR_AXIS, 4.0f, 0, 1.0f);
    }
    
    /**
     * 渲染曲线预览（简化版，使用默认尺寸）
     * 
     * @param type 插值类型
     * @param params 自定义参数
     */
    public static void render(InterpolationType type, float[] params) {
        render(type, params, 200.0f, 150.0f);
    }
    
    // ==================== 绘制方法 ====================
    
    /**
     * 绘制网格
     */
    private static void drawGrid(ImDrawList drawList, float x, float y, float width, float height) {
        // 垂直网格线
        for (int i = 1; i < GRID_LINES; i++) {
            float gridX = x + (width * i) / GRID_LINES;
            drawList.addLine(gridX, y, gridX, y + height, COLOR_GRID, 1.0f);
        }
        
        // 水平网格线
        for (int i = 1; i < GRID_LINES; i++) {
            float gridY = y + (height * i) / GRID_LINES;
            drawList.addLine(x, gridY, x + width, gridY, COLOR_GRID, 1.0f);
        }
    }
    
    /**
     * 绘制参考线（LINEAR）
     */
    private static void drawReferenceLine(ImDrawList drawList, float x, float y, float width, float height) {
        // 线性参考线从左下到右上
        drawList.addLine(
            x, y + height,
            x + width, y,
            COLOR_REFERENCE,
            1.0f
        );
    }
    
    /**
     * 绘制曲线
     */
    private static void drawCurve(ImDrawList drawList, InterpolationType type, float[] params,
                                    float x, float y, float width, float height) {
        // 采样曲线点
        float[] points = sampleCurve(type, params, SAMPLE_COUNT);
        
        // 绘制曲线线段
        for (int i = 0; i < points.length - 2; i += 2) {
            float t1 = points[i];
            float v1 = points[i + 1];
            float t2 = points[i + 2];
            float v2 = points[i + 3];
            
            // 转换到屏幕坐标
            float x1 = x + t1 * width;
            float y1 = y + height - v1 * height; // Y轴反向
            float x2 = x + t2 * width;
            float y2 = y + height - v2 * height;
            
            drawList.addLine(x1, y1, x2, y2, COLOR_CURVE, CURVE_LINE_WIDTH);
        }
    }
    
    /**
     * 绘制贝塞尔控制点
     */
    private static void drawBezierControlPoints(ImDrawList drawList, float[] params,
                                                  float x, float y, float width, float height) {
        if (params == null || params.length < 4) return;
        
        float x1 = params[0];
        float y1 = params[1];
        float x2 = params[2];
        float y2 = params[3];
        
        // P0 (0, 0)
        float p0x = x;
        float p0y = y + height;
        
        // P1 (x1, y1)
        float p1x = x + x1 * width;
        float p1y = y + height - y1 * height;
        
        // P2 (x2, y2)
        float p2x = x + x2 * width;
        float p2y = y + height - y2 * height;
        
        // P3 (1, 1)
        float p3x = x + width;
        float p3y = y;
        
        // 绘制控制线（虚线效果）
        drawDashedLine(drawList, p0x, p0y, p1x, p1y, COLOR_CONTROL_POINT, 1.0f);
        drawDashedLine(drawList, p2x, p2y, p3x, p3y, COLOR_CONTROL_POINT, 1.0f);
        
        // 绘制控制点
        drawList.addCircleFilled(p1x, p1y, CONTROL_POINT_RADIUS, COLOR_CONTROL_POINT, 12);
        drawList.addCircleFilled(p2x, p2y, CONTROL_POINT_RADIUS, COLOR_CONTROL_POINT, 12);
        
        // 绘制端点（稍小）
        drawList.addCircleFilled(p0x, p0y, CONTROL_POINT_RADIUS * 0.7f, COLOR_AXIS, 12);
        drawList.addCircleFilled(p3x, p3y, CONTROL_POINT_RADIUS * 0.7f, COLOR_AXIS, 12);
    }
    
    /**
     * 绘制虚线
     */
    private static void drawDashedLine(ImDrawList drawList, float x1, float y1, float x2, float y2,
                                        int color, float thickness) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        
        float dashLength = 5.0f;
        float gapLength = 3.0f;
        float totalLength = dashLength + gapLength;
        
        int dashCount = (int) (length / totalLength);
        
        for (int i = 0; i < dashCount; i++) {
            float t1 = (i * totalLength) / length;
            float t2 = (i * totalLength + dashLength) / length;
            
            drawList.addLine(
                x1 + dx * t1, y1 + dy * t1,
                x1 + dx * t2, y1 + dy * t2,
                color, thickness
            );
        }
    }
    
    /**
     * 绘制坐标轴标签
     */
    private static void drawLabels(ImDrawList drawList, float x, float y, float width, float height) {
        // X轴标签（时间）
        drawList.addText(x + width - 20, y + height + 5, COLOR_TEXT, "1.0");
        drawList.addText(x - 5, y + height + 5, COLOR_TEXT, "0");
        
        // Y轴标签（值）
        drawList.addText(x - 15, y - 3, COLOR_TEXT, "1.0");
        drawList.addText(x - 5, y + height - 10, COLOR_TEXT, "0");
    }
    
    /**
     * 显示鼠标悬停提示
     */
    private static void showTooltip(InterpolationType type, float[] params,
                                      float mouseX, float mouseY, float width, float height) {
        // 计算归一化坐标
        float t = Math.max(0.0f, Math.min(1.0f, mouseX / width));
        float screenY = 1.0f - Math.max(0.0f, Math.min(1.0f, mouseY / height));
        
        // 计算曲线上的实际值
        float value = CurveInterpolator.interpolate(type, t, params);
        
        // 显示提示
        ImGui.beginTooltip();
        ImGui.text(String.format("时间: %.2f", t));
        ImGui.text(String.format("值: %.2f", value));
        ImGui.separator();
        ImGui.textColored(0.6f, 0.8f, 1.0f, 1.0f, type.getDisplayName());
        ImGui.endTooltip();
    }
    
    // ==================== 采样方法 ====================
    
    /**
     * 采样曲线点
     * 
     * @param type 插值类型
     * @param params 自定义参数
     * @param sampleCount 采样点数量
     * @return 采样点数组 [t0, v0, t1, v1, ...]
     */
    private static float[] sampleCurve(InterpolationType type, float[] params, int sampleCount) {
        float[] points = new float[sampleCount * 2];
        
        for (int i = 0; i < sampleCount; i++) {
            float t = (float) i / (sampleCount - 1);
            float value = CurveInterpolator.interpolate(type, t, params);
            
            // 限制值的范围（用于显示）
            value = Math.max(-0.5f, Math.min(1.5f, value));
            
            points[i * 2] = t;
            points[i * 2 + 1] = value;
        }
        
        return points;
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 渲染紧凑版曲线预览（带标题）
     * 
     * @param title 标题
     * @param type 插值类型
     * @param params 自定义参数
     * @param width 宽度
     * @param height 高度
     */
    public static void renderCompact(String title, InterpolationType type, float[] params,
                                       float width, float height) {
        // 标题
        if (title != null && !title.isEmpty()) {
            ImGui.textColored(0.6f, 0.8f, 1.0f, 1.0f, title);
            ImGui.spacing();
        }
        
        // 曲线预览
        render(type, params, width, height);
        
        // 类型名称
        ImGui.spacing();
        ImGui.textColored(0.8f, 0.8f, 0.8f, 1.0f, "📊 " + type.getDisplayName());
        ImGui.sameLine();
        ImGui.textColored(0.6f, 0.6f, 0.6f, 1.0f, "(" + type.getDescription() + ")");
    }
    
    /**
     * 渲染对比预览（显示两条曲线）
     * 
     * @param type1 第一种插值类型
     * @param params1 第一组参数
     * @param type2 第二种插值类型
     * @param params2 第二组参数
     * @param width 宽度
     * @param height 高度
     */
    public static void renderComparison(InterpolationType type1, float[] params1,
                                         InterpolationType type2, float[] params2,
                                         float width, float height) {
        // 左侧预览
        renderCompact(type1.getDisplayName(), type1, params1, width, height);
        
        ImGui.sameLine();
        ImGui.spacing();
        ImGui.sameLine();
        
        // 右侧预览
        renderCompact(type2.getDisplayName(), type2, params2, width, height);
    }
    
    /**
     * 绘制交互式贝塞尔控制点
     * 
     * @return true 如果控制点被修改
     */
    private static boolean drawInteractiveBezierControlPoints(ImDrawList drawList, float[] params,
                                                                float x, float y, float width, float height) {
        if (params == null || params.length < 4) return false;
        
        boolean changed = false;
        
        float x1 = params[0];
        float y1 = params[1];
        float x2 = params[2];
        float y2 = params[3];
        
        // P0 (0, 0)
        float p0x = x;
        float p0y = y + height;
        
        // P1 (x1, y1)
        float p1x = x + x1 * width;
        float p1y = y + height - y1 * height;
        
        // P2 (x2, y2)
        float p2x = x + x2 * width;
        float p2y = y + height - y2 * height;
        
        // P3 (1, 1)
        float p3x = x + width;
        float p3y = y;
        
        // 绘制控制线
        drawDashedLine(drawList, p0x, p0y, p1x, p1y, COLOR_CONTROL_POINT, 1.0f);
        drawDashedLine(drawList, p2x, p2y, p3x, p3y, COLOR_CONTROL_POINT, 1.0f);
        
        // 检查鼠标拖拽 P1
        ImVec2 mousePos = ImGui.getMousePos();
        boolean draggingP1 = false;
        boolean draggingP2 = false;
        
        // P1 拖拽检测
        float distP1 = distance(mousePos.x, mousePos.y, p1x, p1y);
        if (distP1 < CONTROL_POINT_RADIUS * 2.0f) {
            if (ImGui.isMouseDown(0)) {
                draggingP1 = true;
            }
        }
        
        // P2 拖拽检测
        float distP2 = distance(mousePos.x, mousePos.y, p2x, p2y);
        if (distP2 < CONTROL_POINT_RADIUS * 2.0f) {
            if (ImGui.isMouseDown(0)) {
                draggingP2 = true;
            }
        }
        
        // 更新控制点位置
        if (draggingP1) {
            float newX1 = (mousePos.x - x) / width;
            float newY1 = (y + height - mousePos.y) / height;
            params[0] = Math.max(0.0f, Math.min(1.0f, newX1));
            params[1] = newY1; // Y 可以超出范围
            changed = true;
        }
        
        if (draggingP2) {
            float newX2 = (mousePos.x - x) / width;
            float newY2 = (y + height - mousePos.y) / height;
            params[2] = Math.max(0.0f, Math.min(1.0f, newX2));
            params[3] = newY2;
            changed = true;
        }
        
        // 绘制控制点（高亮拖拽中的点）
        int p1Color = draggingP1 ? ImGui.getColorU32(1.0f, 0.8f, 0.2f, 1.0f) : COLOR_CONTROL_POINT;
        int p2Color = draggingP2 ? ImGui.getColorU32(1.0f, 0.8f, 0.2f, 1.0f) : COLOR_CONTROL_POINT;
        
        drawList.addCircleFilled(p1x, p1y, CONTROL_POINT_RADIUS, p1Color, 12);
        drawList.addCircle(p1x, p1y, CONTROL_POINT_RADIUS + 1.0f, ImGui.getColorU32(1.0f, 1.0f, 1.0f, 0.5f), 12, 1.5f);
        
        drawList.addCircleFilled(p2x, p2y, CONTROL_POINT_RADIUS, p2Color, 12);
        drawList.addCircle(p2x, p2y, CONTROL_POINT_RADIUS + 1.0f, ImGui.getColorU32(1.0f, 1.0f, 1.0f, 0.5f), 12, 1.5f);
        
        // 绘制端点
        drawList.addCircleFilled(p0x, p0y, CONTROL_POINT_RADIUS * 0.7f, COLOR_AXIS, 12);
        drawList.addCircleFilled(p3x, p3y, CONTROL_POINT_RADIUS * 0.7f, COLOR_AXIS, 12);
        
        return changed;
    }
    
    /**
     * 计算两点距离
     */
    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
    
    /**
     * 渲染所有插值类型的预览网格
     * 
     * @param columnCount 列数
     * @param previewWidth 每个预览的宽度
     * @param previewHeight 每个预览的高度
     */
    public static void renderAllTypes(int columnCount, float previewWidth, float previewHeight) {
        InterpolationType[] types = InterpolationType.values();
        
        for (int i = 0; i < types.length; i++) {
            InterpolationType type = types[i];
            
            // 获取默认参数
            float[] params = null;
            if (type == InterpolationType.BEZIER) {
                params = type.getDefaultBezierControlPoints();
            }
            
            // 渲染预览
            renderCompact(null, type, params, previewWidth, previewHeight);
            
            // 换行
            if ((i + 1) % columnCount != 0 && i < types.length - 1) {
                ImGui.sameLine();
            }
        }
    }
}

