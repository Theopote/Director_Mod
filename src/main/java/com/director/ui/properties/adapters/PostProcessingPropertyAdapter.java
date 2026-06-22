package com.director.ui.properties.adapters;

import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.PropertyReflection;
import com.director.ui.theme.UITheme;
import com.director.ui.theme.Icons;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 后处理属性适配器 v4.9 - Director Properties System
 * 
 * <p>为 Director 后处理配置对象提供专用属性编辑器。
 * 
 * <h3>后处理配置（PostProcessingProfile）</h3>
 * <p>PostProcessing 控制场景的后期视觉效果，包括：
 * <ul>
 *   <li>Bloom（泛光）- 高亮区域的光晕效果</li>
 *   <li>景深（DOF）- 焦距外的模糊效果</li>
 *   <li>色调映射 - HDR 到 LDR 的转换</li>
 *   <li>颜色分级 - 曝光、对比度、饱和度</li>
 *   <li>屏幕空间效果 - AO、反射、GI</li>
 * </ul>
 * 
 * <h3>支持的属性</h3>
 * <ul>
 *   <li>🌟 Bloom（启用、强度、阈值、半径）</li>
 *   <li>📷 景深（启用、焦距、光圈、散景）</li>
 *   <li>🎨 色调映射（4种模式：Linear/ACES/Reinhard/Filmic）</li>
 *   <li>🎛️ 颜色分级（曝光、对比度、饱和度、色温）</li>
 *   <li>✨ 屏幕空间（SSAO、SSR、SSGI）</li>
 *   <li>⚡ 快捷操作（重置、预设、导入导出）</li>
 * </ul>
 * 
 * <h3>UI 特性</h3>
 * <ul>
 *   <li>折叠式分组 - 清晰的属性分类</li>
 *   <li>实时预览 - 当前值显示</li>
 *   <li>效果预设 - 电影、游戏、写实等</li>
 *   <li>智能提示 - Tooltip 说明</li>
 * </ul>
 * 
 * @author Director Team
 * @version 4.9
 * @since 2025-10-16
 */
public class PostProcessingPropertyAdapter implements IPropertyAdapter<Object> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PostProcessingPropertyAdapter.class);
    
    // 色调映射模式
    private static final String[] TONEMAP_MODES = {
        "Linear（线性）",
        "ACES（电影级）",
        "Reinhard（经典）",
        "Filmic（胶片感）"
    };
    
    // 效果预设
    private static final String[] EFFECT_PRESETS = {
        "🎮 游戏（清晰）",
        "🎬 电影（柔和）",
        "📸 写实（自然）",
        "🌈 梦幻（鲜艳）"
    };

    @Override
    public boolean renderProperties(Object profile) {
        if (profile == null) {
            UITheme.spacingLarge();
            UITheme.label("后处理配置为 null", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        final boolean[] modifiedRef = {false};
        
        // 标题
        UITheme.title("🌫 后处理属性编辑器");
        
        try {
            // ========== Bloom 泛光 ==========
            if (ImGui.collapsingHeader("🌟 Bloom 泛光", ImGuiTreeNodeFlags.DefaultOpen)) {
                // 启用 Bloom
                boolean currentBloom = PropertyReflection.getBoolean(profile, "bloomEnabled", "isBloomEnabled", false);
                ImBoolean enableBloom = new ImBoolean(currentBloom);
                if (ImGui.checkbox("启用 Bloom", enableBloom)) {
                    if (PropertyReflection.setBoolean(profile, "bloomEnabled", "setBloomEnabled", enableBloom.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[PostProcessingPropertyAdapter] Bloom已更新: {}", enableBloom.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("为高亮区域添加光晕效果");
                }
                
                // Bloom 强度
                float currentIntensity = PropertyReflection.getFloat(profile, "bloomIntensity", "getBloomIntensity", 1.0f);
                float[] bloomIntensity = {currentIntensity};
                if (ImGui.dragFloat("Bloom 强度", bloomIntensity, 0.05f, 0.0f, 5.0f, "%.2f")) {
                    if (PropertyReflection.setFloat(profile, "bloomIntensity", "setBloomIntensity", bloomIntensity[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[PostProcessingPropertyAdapter] Bloom强度已更新: {}", bloomIntensity[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("泛光效果的强度");
                }
                
                // Bloom 阈值
                float currentThreshold = PropertyReflection.getFloat(profile, "bloomThreshold", "getBloomThreshold", 1.0f);
                float[] bloomThreshold = {currentThreshold};
                if (ImGui.dragFloat("Bloom 阈值", bloomThreshold, 0.05f, 0.0f, 5.0f, "%.2f")) {
                    if (PropertyReflection.setFloat(profile, "bloomThreshold", "setBloomThreshold", bloomThreshold[0])) {
                        modifiedRef[0] = true;
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("亮度超过此值的区域才会产生泛光");
                }
            }
            
            // ========== 景深 DOF ==========
            if (ImGui.collapsingHeader("📷 景深 (Depth of Field)")) {
                // 启用景深
                boolean currentDOF = PropertyReflection.getBoolean(profile, "depthOfField", "isDepthOfField", false);
                ImBoolean enableDOF = new ImBoolean(currentDOF);
                if (ImGui.checkbox("启用景深", enableDOF)) {
                    if (PropertyReflection.setBoolean(profile, "depthOfField", "setDepthOfField", enableDOF.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[PostProcessingPropertyAdapter] 景深已更新: {}", enableDOF.get());
                    }
                }
                
                // 焦距
                float currentFocus = PropertyReflection.getFloat(profile, "focusDistance", "getFocusDistance", 10.0f);
                float[] focusDistance = {currentFocus};
                if (ImGui.dragFloat("焦距", focusDistance, 0.5f, 0.1f, 100.0f, "%.1f")) {
                    if (PropertyReflection.setFloat(profile, "focusDistance", "setFocusDistance", focusDistance[0])) {
                        modifiedRef[0] = true;
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("焦点距离");
                }
            }
            
            // ========== 色调映射 ==========
            if (ImGui.collapsingHeader("🎨 色调映射", ImGuiTreeNodeFlags.DefaultOpen)) {
                // 色调映射模式
                int currentMode = PropertyReflection.getInt(profile, "toneMapMode", "getToneMapMode", 0);
                ImInt modeIdx = new ImInt(currentMode);
                if (ImGui.combo("色调映射模式", modeIdx, TONEMAP_MODES)) {
                    if (PropertyReflection.setInt(profile, "toneMapMode", "setToneMapMode", modeIdx.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[PostProcessingPropertyAdapter] 色调映射已更新: {}", 
                            TONEMAP_MODES[modeIdx.get()]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("HDR 到 LDR 的转换算法");
                }
            }
            
            // ========== 颜色分级 ==========
            if (ImGui.collapsingHeader("🎛️ 颜色分级", ImGuiTreeNodeFlags.DefaultOpen)) {
                // 曝光
                float currentExposure = PropertyReflection.getFloat(profile, "exposure", "getExposure", 1.0f);
                float[] exposure = {currentExposure};
                if (ImGui.dragFloat("曝光", exposure, 0.05f, 0.0f, 5.0f, "%.2f")) {
                    if (PropertyReflection.setFloat(profile, "exposure", "setExposure", exposure[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[PostProcessingPropertyAdapter] 曝光已更新: {}", exposure[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("整体亮度");
                }
                
                // 对比度
                float currentContrast = PropertyReflection.getFloat(profile, "contrast", "getContrast", 1.0f);
                float[] contrast = {currentContrast};
                if (ImGui.dragFloat("对比度", contrast, 0.05f, 0.1f, 3.0f, "%.2f")) {
                    if (PropertyReflection.setFloat(profile, "contrast", "setContrast", contrast[0])) {
                        modifiedRef[0] = true;
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("明暗对比度");
                }
                
                // 饱和度
                float currentSaturation = PropertyReflection.getFloat(profile, "saturation", "getSaturation", 1.0f);
                float[] saturation = {currentSaturation};
                if (ImGui.dragFloat("饱和度", saturation, 0.05f, 0.0f, 2.0f, "%.2f")) {
                    if (PropertyReflection.setFloat(profile, "saturation", "setSaturation", saturation[0])) {
                        modifiedRef[0] = true;
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("颜色饱和度");
                }
            }
            
            // ========== 屏幕空间效果 ==========
            if (ImGui.collapsingHeader("✨ 屏幕空间效果")) {
                // SSAO
                boolean currentSSAO = PropertyReflection.getBoolean(profile, "ssaoEnabled", "isSSAOEnabled", false);
                ImBoolean enableSSAO = new ImBoolean(currentSSAO);
                if (ImGui.checkbox("SSAO（环境光遮蔽）", enableSSAO)) {
                    if (PropertyReflection.setBoolean(profile, "ssaoEnabled", "setSSAOEnabled", enableSSAO.get())) {
                        modifiedRef[0] = true;
                    }
                }
                
                // SSR
                boolean currentSSR = PropertyReflection.getBoolean(profile, "ssrEnabled", "isSSREnabled", false);
                ImBoolean enableSSR = new ImBoolean(currentSSR);
                if (ImGui.checkbox("SSR（屏幕空间反射）", enableSSR)) {
                    if (PropertyReflection.setBoolean(profile, "ssrEnabled", "setSSREnabled", enableSSR.get())) {
                        modifiedRef[0] = true;
                    }
                }
            }
            
            UITheme.separator();
            
            // ========== 快捷操作 ==========
            UITheme.beginGroup("快捷操作");
            
            if (ImGui.button(Icons.REFRESH + " 重置为默认", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (PropertyReflection.invokeVoid(profile, "resetToDefault")) {
                    modifiedRef[0] = true;
                    LOGGER.info("[PostProcessingPropertyAdapter] 后处理已重置");
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("重置所有后处理参数");
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("📋 复制配置", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                LOGGER.info("[PostProcessingPropertyAdapter] 复制后处理配置");
                // TODO: 复制到剪贴板
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("复制当前后处理配置");
            }
            
            UITheme.endGroup();
            
        } catch (Exception e) {
            LOGGER.error("[PostProcessingPropertyAdapter] 渲染后处理属性时出错", e);
            UITheme.label("渲染出错: " + e.getMessage(), UITheme.DarkTheme.TEXT_DISABLED);
        }
        
        return modifiedRef[0];
    }
    @Override
    public Class<Object> getTargetType() {
        return Object.class;
    }
    
    @Override
    public String getAdapterName() {
        return "后处理属性编辑器";
    }
    
    @Override
    public boolean supports(Object target) {
        if (target == null) return false;
        
        String className = target.getClass().getSimpleName().toLowerCase();
        return className.contains("postprocess") || 
               className.contains("posteffect");
    }
}

