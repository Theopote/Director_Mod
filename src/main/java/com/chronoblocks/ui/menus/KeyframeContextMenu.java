package com.chronoblocks.ui.menus;

import com.chronoblocks.core.animation.CurveInterpolator;
import com.chronoblocks.core.animation.InterpolationType;
import com.chronoblocks.core.animation.Keyframe;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * 关键帧右键菜单 v4.5 - 快速选择插值类型和其他操作
 * 
 * <h3>功能特性</h3>
 * <ul>
 *   <li>快速选择插值类型（所有8种类型）</li>
 *   <li>应用预设贝塞尔曲线</li>
 *   <li>关键帧操作（复制/粘贴/删除）</li>
 *   <li>批量操作支持</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 在 Timeline 中检测右键点击
 * if (ImGui.isItemClicked(1)) {  // 右键
 *     ImGui.openPopup("KeyframeContextMenu");
 * }
 * 
 * // 渲染菜单
 * KeyframeContextMenu.render(
 *     selectedKeyframe,
 *     kf -> {
 *         // 关键帧被修改的回调
 *         updateTimeline();
 *     }
 * );
 * }</pre>
 * 
 * @version 4.5
 * @since 2025-10-15
 */
public class KeyframeContextMenu {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeyframeContextMenu.class);
    
    // ==================== 菜单 ID ====================
    
    /** 单个关键帧菜单 ID */
    private static final String MENU_ID_SINGLE = "KeyframeContextMenu";
    
    /** 多个关键帧菜单 ID */
    private static final String MENU_ID_BATCH = "KeyframeBatchContextMenu";
    
    // ==================== 剪贴板 ====================
    
    /** 复制的关键帧（用于粘贴） */
    private static Keyframe copiedKeyframe = null;
    
    // ==================== 主渲染方法 ====================
    
    /**
     * 渲染单个关键帧的右键菜单
     * 
     * @param keyframe 关键帧
     * @param onModified 修改回调
     */
    public static void render(Keyframe keyframe, Consumer<Keyframe> onModified) {
        if (ImGui.beginPopup(MENU_ID_SINGLE)) {
            renderMenuContent(keyframe, null, onModified);
            ImGui.endPopup();
        }
    }
    
    /**
     * 渲染多个关键帧的右键菜单（批量操作）
     * 
     * @param keyframes 关键帧列表
     * @param onModified 修改回调
     */
    public static void renderBatch(List<Keyframe> keyframes, Consumer<List<Keyframe>> onModified) {
        if (ImGui.beginPopup(MENU_ID_BATCH)) {
            renderBatchMenuContent(keyframes, onModified);
            ImGui.endPopup();
        }
    }
    
    /**
     * 打开单个关键帧菜单
     */
    public static void open() {
        ImGui.openPopup(MENU_ID_SINGLE);
    }
    
    /**
     * 打开批量关键帧菜单
     */
    public static void openBatch() {
        ImGui.openPopup(MENU_ID_BATCH);
    }
    
    // ==================== 菜单内容 ====================
    
    /**
     * 渲染菜单内容
     */
    private static void renderMenuContent(Keyframe keyframe, List<Keyframe> batchKeyframes, 
                                           Consumer<Keyframe> onModified) {
        if (keyframe == null) return;
        
        // 标题
        ImGui.textColored(0.6f, 0.8f, 1.0f, 1.0f, "🔑 关键帧菜单");
        ImGui.separator();
        
        // 关键帧信息
        renderKeyframeInfo(keyframe);
        ImGui.separator();
        
        // 插值类型子菜单
        if (ImGui.beginMenu("📊 插值类型")) {
            renderInterpolationTypeMenu(keyframe, onModified);
            ImGui.endMenu();
        }
        
        // 贝塞尔预设子菜单（仅当选中贝塞尔时）
        if (keyframe.getInterpolationType() == InterpolationType.BEZIER) {
            if (ImGui.beginMenu("🎨 贝塞尔预设")) {
                renderBezierPresetsMenu(keyframe, onModified);
                ImGui.endMenu();
            }
        }
        
        ImGui.separator();
        
        // 关键帧操作
        renderKeyframeActions(keyframe, onModified);
    }
    
    /**
     * 渲染批量菜单内容
     */
    private static void renderBatchMenuContent(List<Keyframe> keyframes, 
                                                Consumer<List<Keyframe>> onModified) {
        if (keyframes == null || keyframes.isEmpty()) return;
        
        // 标题
        ImGui.textColored(0.6f, 0.8f, 1.0f, 1.0f, "🔑 批量编辑");
        ImGui.text(String.format("已选中 %d 个关键帧", keyframes.size()));
        ImGui.separator();
        
        // 批量设置插值类型
        if (ImGui.beginMenu("📊 批量设置插值类型")) {
            for (InterpolationType type : InterpolationType.values()) {
                if (ImGui.menuItem(getInterpolationTypeIcon(type) + " " + type.getDisplayName())) {
                    // 应用到所有选中的关键帧
                    for (Keyframe kf : keyframes) {
                        kf.setInterpolationType(type);
                        
                        // 如果是贝塞尔类型，初始化默认控制点
                        if (type == InterpolationType.BEZIER && kf.getBezierControlPoints() == null) {
                            kf.setBezierControlPoints(type.getDefaultBezierControlPoints());
                        }
                    }
                    
                    if (onModified != null) {
                        onModified.accept(keyframes);
                    }
                    
                    LOGGER.info("[KeyframeContextMenu] 批量设置 {} 个关键帧为 {}", 
                        keyframes.size(), type.getDisplayName());
                }
            }
            ImGui.endMenu();
        }
        
        // 批量设置贝塞尔预设
        if (ImGui.beginMenu("🎨 批量应用预设")) {
            renderBatchBezierPresetsMenu(keyframes, onModified);
            ImGui.endMenu();
        }
        
        ImGui.separator();
        
        // 批量操作
        renderBatchActions(keyframes, onModified);
    }
    
    // ==================== 子菜单渲染 ====================
    
    /**
     * 渲染关键帧信息
     */
    private static void renderKeyframeInfo(Keyframe keyframe) {
        ImGui.text(String.format("⏱ 时间: %.2fs", keyframe.getTime()));
        ImGui.text(String.format("📊 类型: %s", keyframe.getInterpolationType().getDisplayName()));
        
        float[] pos = keyframe.getPosition();
        ImGui.text(String.format("📍 位置: (%.1f, %.1f, %.1f)", pos[0], pos[1], pos[2]));
    }
    
    /**
     * 渲染插值类型菜单
     */
    private static void renderInterpolationTypeMenu(Keyframe keyframe, Consumer<Keyframe> onModified) {
        InterpolationType current = keyframe.getInterpolationType();
        
        // 基础类型
        ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, "基础类型");
        ImGui.separator();
        
        for (InterpolationType type : InterpolationType.getSmoothTypes()) {
            boolean selected = (type == current);
            String icon = getInterpolationTypeIcon(type);
            
            if (selected) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.2f, 0.8f, 1.0f, 1.0f);
            }
            
            if (ImGui.menuItem(icon + " " + type.getDisplayName(), "", selected)) {
                keyframe.setInterpolationType(type);
                
                // 如果是贝塞尔类型，初始化默认控制点
                if (type == InterpolationType.BEZIER && keyframe.getBezierControlPoints() == null) {
                    keyframe.setBezierControlPoints(type.getDefaultBezierControlPoints());
                }
                
                if (onModified != null) {
                    onModified.accept(keyframe);
                }
                
                LOGGER.info("[KeyframeContextMenu] 插值类型更改为: {}", type.getDisplayName());
            }
            
            if (selected) {
                ImGui.popStyleColor();
            }
            
            // 显示描述
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(type.getDescription());
            }
        }
        
        // 特效类型
        ImGui.separator();
        ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, "特效类型");
        ImGui.separator();
        
        for (InterpolationType type : InterpolationType.getEffectTypes()) {
            boolean selected = (type == current);
            String icon = getInterpolationTypeIcon(type);
            
            if (selected) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.2f, 0.8f, 1.0f, 1.0f);
            }
            
            if (ImGui.menuItem(icon + " " + type.getDisplayName(), "", selected)) {
                keyframe.setInterpolationType(type);
                
                if (onModified != null) {
                    onModified.accept(keyframe);
                }
                
                LOGGER.info("[KeyframeContextMenu] 插值类型更改为: {}", type.getDisplayName());
            }
            
            if (selected) {
                ImGui.popStyleColor();
            }
            
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(type.getDescription());
            }
        }
        
        // STEP 类型
        ImGui.separator();
        InterpolationType stepType = InterpolationType.STEP;
        boolean stepSelected = (stepType == current);
        
        if (stepSelected) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.2f, 0.8f, 1.0f, 1.0f);
        }
        
        if (ImGui.menuItem("⏹ " + stepType.getDisplayName(), "", stepSelected)) {
            keyframe.setInterpolationType(stepType);
            
            if (onModified != null) {
                onModified.accept(keyframe);
            }
            
            LOGGER.info("[KeyframeContextMenu] 插值类型更改为: {}", stepType.getDisplayName());
        }
        
        if (stepSelected) {
            ImGui.popStyleColor();
        }
        
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(stepType.getDescription());
        }
    }
    
    /**
     * 渲染贝塞尔预设菜单
     */
    private static void renderBezierPresetsMenu(Keyframe keyframe, Consumer<Keyframe> onModified) {
        String[] presets = {
            "Linear", "Ease", "Ease-In", "Ease-Out", "Ease-In-Out",
            "Ease-In-Quad", "Ease-Out-Quad", "Ease-In-Cubic", "Ease-Out-Cubic"
        };
        
        for (String preset : presets) {
            if (ImGui.menuItem("📐 " + preset)) {
                float[] controls = CurveInterpolator.getBezierPreset(preset.toLowerCase());
                keyframe.setBezierControlPoints(controls);
                
                if (onModified != null) {
                    onModified.accept(keyframe);
                }
                
                LOGGER.info("[KeyframeContextMenu] 应用预设: {}", preset);
            }
        }
    }
    
    /**
     * 渲染批量贝塞尔预设菜单
     */
    private static void renderBatchBezierPresetsMenu(List<Keyframe> keyframes, 
                                                      Consumer<List<Keyframe>> onModified) {
        String[] presets = {
            "Linear", "Ease", "Ease-In", "Ease-Out", "Ease-In-Out",
            "Ease-In-Quad", "Ease-Out-Quad", "Ease-In-Cubic", "Ease-Out-Cubic"
        };
        
        for (String preset : presets) {
            if (ImGui.menuItem("📐 " + preset)) {
                float[] controls = CurveInterpolator.getBezierPreset(preset.toLowerCase());
                
                for (Keyframe kf : keyframes) {
                    // 先设置为贝塞尔类型
                    kf.setInterpolationType(InterpolationType.BEZIER);
                    // 然后应用预设
                    kf.setBezierControlPoints(controls);
                }
                
                if (onModified != null) {
                    onModified.accept(keyframes);
                }
                
                LOGGER.info("[KeyframeContextMenu] 批量应用预设 {} 到 {} 个关键帧", 
                    preset, keyframes.size());
            }
        }
    }
    
    /**
     * 渲染关键帧操作
     */
    private static void renderKeyframeActions(Keyframe keyframe, Consumer<Keyframe> onModified) {
        // 复制
        if (ImGui.menuItem("📋 复制", "Ctrl+C")) {
            copiedKeyframe = new Keyframe(keyframe);
            LOGGER.info("[KeyframeContextMenu] 关键帧已复制");
        }
        
        // 粘贴
        boolean canPaste = (copiedKeyframe != null);
        if (!canPaste) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1.0f);
        }
        
        if (ImGui.menuItem("📄 粘贴", "Ctrl+V", false, canPaste)) {
            if (copiedKeyframe != null) {
                // 粘贴属性（保留时间）
                float originalTime = keyframe.getTime();
                
                keyframe.setPosition(copiedKeyframe.getPosition()[0], 
                                   copiedKeyframe.getPosition()[1], 
                                   copiedKeyframe.getPosition()[2]);
                keyframe.setRotation(copiedKeyframe.getRotation()[0], 
                                   copiedKeyframe.getRotation()[1], 
                                   copiedKeyframe.getRotation()[2]);
                keyframe.setScale(copiedKeyframe.getScale()[0], 
                                copiedKeyframe.getScale()[1], 
                                copiedKeyframe.getScale()[2]);
                keyframe.setOpacity(copiedKeyframe.getOpacity());
                keyframe.setInterpolationType(copiedKeyframe.getInterpolationType());
                
                if (copiedKeyframe.getBezierControlPoints() != null) {
                    keyframe.setBezierControlPoints(copiedKeyframe.getBezierControlPoints());
                }
                
                keyframe.setTime(originalTime);  // 恢复原始时间
                
                if (onModified != null) {
                    onModified.accept(keyframe);
                }
                
                LOGGER.info("[KeyframeContextMenu] 关键帧属性已粘贴");
            }
        }
        
        if (!canPaste) {
            ImGui.popStyleColor();
        }
        
        ImGui.separator();
        
        // 重置为默认
        if (ImGui.menuItem("🔄 重置插值", "")) {
            keyframe.setInterpolationType(InterpolationType.LINEAR);
            keyframe.setBezierControlPoints(null);
            
            if (onModified != null) {
                onModified.accept(keyframe);
            }
            
            LOGGER.info("[KeyframeContextMenu] 插值类型已重置");
        }
    }
    
    /**
     * 渲染批量操作
     */
    private static void renderBatchActions(List<Keyframe> keyframes, 
                                            Consumer<List<Keyframe>> onModified) {
        // 批量重置
        if (ImGui.menuItem("🔄 批量重置插值")) {
            for (Keyframe kf : keyframes) {
                kf.setInterpolationType(InterpolationType.LINEAR);
                kf.setBezierControlPoints(null);
            }
            
            if (onModified != null) {
                onModified.accept(keyframes);
            }
            
            LOGGER.info("[KeyframeContextMenu] 批量重置 {} 个关键帧", keyframes.size());
        }
        
        // 批量复制插值类型
        if (ImGui.menuItem("📋 复制插值类型到剪贴板")) {
            if (!keyframes.isEmpty()) {
                copiedKeyframe = new Keyframe(keyframes.get(0));
                LOGGER.info("[KeyframeContextMenu] 插值类型已复制");
            }
        }
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 获取插值类型的图标
     */
    private static String getInterpolationTypeIcon(InterpolationType type) {
        return switch (type) {
            case LINEAR -> "━";
            case EASE_IN -> "╱";
            case EASE_OUT -> "╲";
            case EASE_IN_OUT -> "╱╲";
            case BEZIER -> "〜";
            case STEP -> "⏹";
            case SPRING -> "〰";
            case BOUNCE -> "⬇";
        };
    }
    
    /**
     * 清除剪贴板
     */
    public static void clearClipboard() {
        copiedKeyframe = null;
    }
    
    /**
     * 获取剪贴板内容
     */
    public static Keyframe getClipboard() {
        return copiedKeyframe;
    }
}

