package com.director.ui.properties.adapters;

import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.PropertyReflection;
import com.director.ui.theme.UITheme;
import com.director.ui.theme.Icons;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiSelectableFlags;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 材质库属性适配器 v4.9 - Director Properties System
 * 
 * <p>为 Director 材质库对象提供专用属性编辑器。
 * 
 * <h3>材质库（MaterialLibrary）</h3>
 * <p>MaterialLibrary 管理项目中的所有材质资源，包括：
 * <ul>
 *   <li>材质列表 - 可选择、编辑、删除</li>
 *   <li>材质属性 - 颜色、纹理、金属度、粗糙度</li>
 *   <li>材质绑定 - 容器、方块可引用材质</li>
 *   <li>资源管理 - 导入、导出、预览</li>
 * </ul>
 * 
 * <h3>支持的属性</h3>
 * <ul>
 *   <li>📋 材质列表（可选择的材质列表）</li>
 *   <li>🎨 材质属性（基础颜色、金属度、粗糙度、自发光）</li>
 *   <li>🖼️ 纹理管理（纹理路径、选择器、预览）</li>
 *   <li>📊 统计信息（材质总数、已绑定数量）</li>
 *   <li>⚡ 快捷操作（新建、删除、复制、导入导出）</li>
 * </ul>
 * 
 * <h3>UI 特性</h3>
 * <ul>
 *   <li>材质列表 - 可选择的材质项</li>
 *   <li>颜色拾取器 - 基础颜色、自发光</li>
 *   <li>PBR 参数 - 金属度、粗糙度滑块</li>
 *   <li>纹理浏览 - 路径输入、文件选择</li>
 *   <li>智能提示 - Tooltip 说明</li>
 * </ul>
 * 
 * <h3>注意事项</h3>
 * <p>由于材质系统可能尚未完整实现，此适配器采用通用 Object 类型，
 * 使用反射和类型检查来动态渲染属性。
 * 
 * @author Director Team
 * @version 4.9
 * @since 2025-10-16
 */
public class MaterialLibraryPropertyAdapter implements IPropertyAdapter<Object> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MaterialLibraryPropertyAdapter.class);
    
    private int selectedMaterialIndex = -1;

    @Override
    public boolean renderProperties(Object library) {
        if (library == null) {
            UITheme.spacingLarge();
            UITheme.label("材质库为 null", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        // 使用数组包装以支持 lambda 修改
        final boolean[] modifiedRef = {false};
        
        // 标题
        UITheme.title("🧩 材质库属性编辑器");
        
        try {
            // ========== 材质列表 ==========
            if (ImGui.collapsingHeader("📋 材质列表", ImGuiTreeNodeFlags.DefaultOpen)) {
                // 获取材质列表
                Object materialsObj = PropertyReflection.readProperty(library, \"materials\", \"getMaterials\");
                
                if (materialsObj instanceof List<?>) {
                    @SuppressWarnings("unchecked")
                    List<Object> materials = (List<Object>) materialsObj;
                    
                    if (materials.isEmpty()) {
                        UITheme.label("材质: 无", UITheme.DarkTheme.TEXT_DISABLED);
                        UITheme.label("使用下方按钮创建第一个材质", UITheme.DarkTheme.TEXT_DISABLED);
                    } else {
                        // 材质选择列表
                        for (int i = 0; i < materials.size(); i++) {
                            Object mat = materials.get(i);
                            ImGui.pushID(i);
                            
                            String matName = PropertyReflection.getString(mat, "name", "getName", "材质 " + i);
                            boolean selected = (i == selectedMaterialIndex);
                            
                            if (ImGui.selectable(matName, selected, ImGuiSelectableFlags.None)) {
                                selectedMaterialIndex = i;
                                LOGGER.debug("[MaterialLibraryPropertyAdapter] 选中材质: {}", matName);
                            }
                            
                            // 显示材质预览色块
                            if (ImGui.isItemHovered()) {
                                float[] color = PropertyReflection.getFloatArray(mat, "baseColor", "getBaseColor");
                                if (color != null && color.length >= 3) {
                                    ImGui.setTooltip(String.format("RGB: (%.2f, %.2f, %.2f)", 
                                        color[0], color[1], color[2]));
                                }
                            }
                            
                            ImGui.popID();
                        }
                    }
                } else {
                    UITheme.label("无法获取材质列表", UITheme.DarkTheme.TEXT_DISABLED);
                }
                
                UITheme.spacingSmall();
                
                // 材质列表操作
                if (ImGui.button("➕ 新建材质", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                    LOGGER.info("[MaterialLibraryPropertyAdapter] 新建材质");
                    // TODO: 创建新材质
                    modifiedRef[0] = true;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("创建新的空白材质");
                }
                
                ImGui.sameLine();
                
                if (ImGui.button("🗑 删除材质", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                    if (selectedMaterialIndex >= 0) {
                        LOGGER.info("[MaterialLibraryPropertyAdapter] 删除材质索引: {}", selectedMaterialIndex);
                        // TODO: 删除材质
                        selectedMaterialIndex = -1;
                        modifiedRef[0] = true;
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("删除当前选中的材质");
                }
            }
            
            // ========== 材质属性（选中时显示）==========
            Object materialsObj2 = PropertyReflection.readProperty(library, \"materials\", \"getMaterials\");
            if (materialsObj2 instanceof List<?>) {
                @SuppressWarnings("unchecked")
                List<Object> materials = (List<Object>) materialsObj2;
                
                if (selectedMaterialIndex >= 0 && selectedMaterialIndex < materials.size()) {
                    Object material = materials.get(selectedMaterialIndex);
                    
                    if (ImGui.collapsingHeader("🎨 材质属性", ImGuiTreeNodeFlags.DefaultOpen)) {
                        // 材质名称
                        String currentName = PropertyReflection.getString(material, "name", "getName", "未命名材质");
                        ImString nameBuffer = new ImString(currentName, 256);
                        if (ImGui.inputText("材质名称", nameBuffer, ImGuiInputTextFlags.EnterReturnsTrue)) {
                            if (PropertyReflection.setString(material, "name", "setName", nameBuffer.get())) {
                                modifiedRef[0] = true;
                                LOGGER.debug("[MaterialLibraryPropertyAdapter] 材质名称已更新: {}", nameBuffer.get());
                            }
                        }
                        
                        // 基础颜色
                        float[] baseColor = PropertyReflection.getFloatArray(material, "baseColor", "getBaseColor");
                        if (baseColor == null || baseColor.length < 4) {
                            baseColor = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
                        }
                        
                        if (ImGui.colorEdit4("基础颜色", baseColor)) {
                            if (PropertyReflection.setFloatArray(material, "baseColor", "setBaseColor", baseColor)) {
                                modifiedRef[0] = true;
                                LOGGER.debug("[MaterialLibraryPropertyAdapter] 基础颜色已更新");
                            }
                        }
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip("材质的基础颜色（Albedo）");
                        }
                        
                        // 金属度
                        float currentMetallic = PropertyReflection.getFloat(material, "metallic", "getMetallic", 0.0f);
                        float[] metallic = {currentMetallic};
                        if (ImGui.sliderFloat("金属度", metallic, 0.0f, 1.0f, "%.2f")) {
                            if (PropertyReflection.setFloat(material, "metallic", "setMetallic", metallic[0])) {
                                modifiedRef[0] = true;
                                LOGGER.debug("[MaterialLibraryPropertyAdapter] 金属度已更新: {}", metallic[0]);
                            }
                        }
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip("材质的金属特性（0 = 非金属，1 = 纯金属）");
                        }
                        
                        // 粗糙度
                        float currentRoughness = PropertyReflection.getFloat(material, "roughness", "getRoughness", 0.5f);
                        float[] roughness = {currentRoughness};
                        if (ImGui.sliderFloat("粗糙度", roughness, 0.0f, 1.0f, "%.2f")) {
                            if (PropertyReflection.setFloat(material, "roughness", "setRoughness", roughness[0])) {
                                modifiedRef[0] = true;
                                LOGGER.debug("[MaterialLibraryPropertyAdapter] 粗糙度已更新: {}", roughness[0]);
                            }
                        }
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip("材质的表面粗糙度（0 = 光滑镜面，1 = 完全粗糙）");
                        }
                        
                        // 自发光
                        float[] emissive = PropertyReflection.getFloatArray(material, "emissive", "getEmissive");
                        if (emissive == null || emissive.length < 4) {
                            emissive = new float[]{0.0f, 0.0f, 0.0f, 1.0f};
                        }
                        
                        if (ImGui.colorEdit4("自发光颜色", emissive)) {
                            if (PropertyReflection.setFloatArray(material, "emissive", "setEmissive", emissive)) {
                                modifiedRef[0] = true;
                                LOGGER.debug("[MaterialLibraryPropertyAdapter] 自发光颜色已更新");
                            }
                        }
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip("材质的自发光颜色（用于发光方块）");
                        }
                    }
                    
                    // ========== 纹理管理 ==========
                    if (ImGui.collapsingHeader("🖼️ 纹理管理")) {
                        // 纹理路径
                        String currentPath = PropertyReflection.getString(material, "texturePath", "getTexturePath", "");
                        ImString pathBuffer = new ImString(currentPath, 512);
                        if (ImGui.inputText("纹理路径", pathBuffer, ImGuiInputTextFlags.None)) {
                            if (PropertyReflection.setString(material, "texturePath", "setTexturePath", pathBuffer.get())) {
                                modifiedRef[0] = true;
                                LOGGER.debug("[MaterialLibraryPropertyAdapter] 纹理路径已更新: {}", pathBuffer.get());
                            }
                        }
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip("材质纹理的文件路径");
                        }
                        
                        UITheme.spacingSmall();
                        
                        // 选择纹理按钮
                        if (ImGui.button("📂 选择纹理", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                            LOGGER.info("[MaterialLibraryPropertyAdapter] 打开纹理选择器");
                            // TODO: 打开文件选择对话框
                        }
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip("从文件浏览器选择纹理");
                        }
                        
                        ImGui.sameLine();
                        
                        if (ImGui.button(Icons.REFRESH + " 重载纹理", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                            if (PropertyReflection.invokeVoid(material, "reload") || PropertyReflection.invokeVoid(material, "reloadTexture")) {
                                LOGGER.info("[MaterialLibraryPropertyAdapter] 纹理已重载");
                            }
                        }
                        if (ImGui.isItemHovered()) {
                            ImGui.setTooltip("从磁盘重新加载纹理");
                        }
                        
                        // 纹理预览（如果支持）
                        UITheme.spacingSmall();
                        UITheme.label("纹理预览（未实现）", UITheme.DarkTheme.TEXT_DISABLED);
                    }
                } else {
                    UITheme.spacingLarge();
                    UITheme.label("请在左侧列表中选择一个材质", UITheme.DarkTheme.TEXT_DISABLED);
                }
            }
            
            // ========== 统计信息 ==========
            if (ImGui.collapsingHeader("📊 统计信息")) {
                Object materialsObj = PropertyReflection.readProperty(library, \"materials\", \"getMaterials\");
                int materialCount = 0;
                
                if (materialsObj instanceof List<?>) {
                    materialCount = ((List<?>) materialsObj).size();
                }
                
                UITheme.label("材质总数: " + materialCount, UITheme.DarkTheme.TEXT);
                UITheme.label("当前选中: " + 
                    (selectedMaterialIndex >= 0 ? "材质 " + selectedMaterialIndex : "无"), 
                    UITheme.DarkTheme.TEXT_DISABLED);
            }
            
            UITheme.separator();
            
            // ========== 快捷操作 ==========
            UITheme.beginGroup("快捷操作");
            
            // 材质操作
            if (ImGui.button("📋 复制材质", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (selectedMaterialIndex >= 0) {
                    LOGGER.info("[MaterialLibraryPropertyAdapter] 复制材质: {}", selectedMaterialIndex);
                    // TODO: 复制材质
                    modifiedRef[0] = true;
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("复制当前选中的材质");
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("📥 导入材质", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                LOGGER.info("[MaterialLibraryPropertyAdapter] 导入材质");
                // TODO: 导入材质文件
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("从文件导入材质");
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("📤 导出材质", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (selectedMaterialIndex >= 0) {
                    LOGGER.info("[MaterialLibraryPropertyAdapter] 导出材质: {}", selectedMaterialIndex);
                    // TODO: 导出材质文件
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("导出当前材质到文件");
            }
            
            // 库操作
            if (ImGui.button("💾 保存材质库", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (PropertyReflection.invokeVoid(library, "save")) {
                    LOGGER.info("[MaterialLibraryPropertyAdapter] 材质库已保存");
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("保存整个材质库");
            }
            
            ImGui.sameLine();
            
            if (ImGui.button(Icons.REFRESH + " 重载材质库", 120, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (PropertyReflection.invokeVoid(library, "reload")) {
                    LOGGER.info("[MaterialLibraryPropertyAdapter] 材质库已重载");
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("从磁盘重新加载材质库");
            }
            
            UITheme.endGroup();
            
        } catch (Exception e) {
            LOGGER.error("[MaterialLibraryPropertyAdapter] 渲染材质库属性时出错", e);
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
        return "材质库属性编辑器";
    }
    
    @Override
    public boolean supports(Object target) {
        if (target == null) return false;
        
        String className = target.getClass().getSimpleName().toLowerCase();
        return className.contains("material") && 
               (className.contains("library") || className.contains("manager"));
    }
}

