package com.chronoblocks.ui.panels;

import com.chronoblocks.core.container.*;
import com.chronoblocks.core.container.commands.ContainerCommand;
import com.chronoblocks.core.timeline.TimelineCommandHistory;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 容器属性编辑面板
 * 用于编辑选中容器的详细属性
 * 
 * 特性：
 * - 实时属性编辑
 * - 命令模式集成（支持撤销/重做）
 * - 多种动画模式选择
 * - 颜色选择器
 * - 元数据编辑
 * 
 * @author ChronoBlocks Team
 * @version 4.0
 */
public class ContainerPropertiesPanel {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerPropertiesPanel.class);
    
    // ========== 命令历史 ==========
    @SuppressWarnings("unused") // 保留用于未来统一命令系统
    private final TimelineCommandHistory commandHistory;
    
    // ========== UI 缓冲区 ==========
    private final ImString nameBuffer = new ImString(256);
    private final ImString descBuffer = new ImString(512);
    private final ImInt typeIndex = new ImInt(0);
    private final ImInt animModeIndex = new ImInt(0);
    private final float[] colorBuffer = new float[4];
    
    // ========== 动画模式名称 ==========
    private static final String[] ANIM_MODE_NAMES;
    private static final String[] CONTAINER_TYPE_NAMES;
    
    static {
        AnimationMode[] modes = AnimationMode.values();
        ANIM_MODE_NAMES = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            ANIM_MODE_NAMES[i] = modes[i].getDisplayName();
        }
        
        ContainerType[] types = ContainerType.values();
        CONTAINER_TYPE_NAMES = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            CONTAINER_TYPE_NAMES[i] = types[i].toString();
        }
    }
    
    public ContainerPropertiesPanel() {
        this(new TimelineCommandHistory());
    }
    
    public ContainerPropertiesPanel(TimelineCommandHistory commandHistory) {
        this.commandHistory = commandHistory;
    }
    
    /**
     * 渲染属性面板
     */
    public void render(Container container) {
        if (container == null) {
            renderEmptyState();
            return;
        }
        
        renderHeader(container);
        ImGui.separator();
        
        ImGui.beginChild("PropertiesContent", 0, 0, false);
        
        renderBasicProperties(container);
        ImGui.separator();
        
        renderAnimationProperties(container);
        ImGui.separator();
        
        renderVisualProperties(container);
        ImGui.separator();
        
        renderDataProperties(container);
        ImGui.separator();
        
        renderMetadata(container);
        
        ImGui.endChild();
    }
    
    // ========== 渲染方法 ==========
    
    private void renderEmptyState() {
        ImGui.textDisabled("未选择容器");
        ImGui.spacing();
        ImGui.textWrapped("在左侧容器树中选择一个容器以查看和编辑其属性。");
    }
    
    private void renderHeader(Container container) {
        ImGui.textColored(0.4f, 1.0f, 0.5f, 1.0f, 
            String.format("%s 容器属性", container.getType().getIcon()));
        ImGui.sameLine();
        ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, 
            String.format("(ID: %s)", container.getId().toString().substring(0, 8)));
    }
    
    private void renderBasicProperties(Container container) {
        if (ImGui.collapsingHeader("基本属性")) {
            // 名称
            nameBuffer.set(container.getName());
            if (ImGui.inputText("名称", nameBuffer)) {
                String newName = nameBuffer.get().trim();
                if (!newName.isEmpty() && !newName.equals(container.getName())) {
                    executeCommand(new SetPropertyCommand(container, "name", newName));
                }
            }
            
            // 类型
            typeIndex.set(container.getType().ordinal());
            if (ImGui.combo("类型", typeIndex, CONTAINER_TYPE_NAMES)) {
                ContainerType newType = ContainerType.values()[typeIndex.get()];
                executeCommand(new SetPropertyCommand(container, "type", newType));
            }
            
            // 描述
            Object desc = container.getMetadata("description", "");
            descBuffer.set(desc.toString());
            if (ImGui.inputTextMultiline("描述", descBuffer, 0, 60)) {
                container.setMetadata("description", descBuffer.get());
            }
            
            // 只读信息
            ImGui.spacing();
            ImGui.textDisabled("统计信息：");
            ImGui.text("  方块数: " + container.getBlockCount());
            ImGui.text("  子容器: " + container.getChildren().size());
            ImGui.text("  总方块数: " + container.getTotalBlockCount());
            
            if (container.getCenter() != null) {
                ImGui.text("  中心点: " + container.getCenter());
            }
            
            Container.BoundingBox bbox = container.getBoundingBox();
            ImGui.text(String.format("  大小: %dx%dx%d (体积: %d)", 
                bbox.getWidth(), bbox.getHeight(), bbox.getDepth(), bbox.getVolume()));
        }
    }
    
    private void renderAnimationProperties(Container container) {
        if (ImGui.collapsingHeader("动画属性")) {
            // 动画模式
            animModeIndex.set(container.getAnimationMode().ordinal());
            if (ImGui.combo("出现方式", animModeIndex, ANIM_MODE_NAMES)) {
                AnimationMode newMode = AnimationMode.values()[animModeIndex.get()];
                executeCommand(new SetPropertyCommand(container, "animationMode", newMode));
            }
            
            // 显示动画模式描述
            ImGui.pushStyleColor(ImGuiCol.Text, 0.6f, 0.6f, 0.6f, 1.0f);
            ImGui.textWrapped(container.getAnimationMode().getDescription());
            ImGui.popStyleColor();
            
            ImGui.spacing();
            
            // 动画时长
            float[] durationArr = new float[1];
            durationArr[0] = (float) container.getMetadata("animationDuration", 3.0f);
            if (ImGui.sliderFloat("动画时长（秒）", durationArr, 0.1f, 30.0f, "%.1fs")) {
                container.setMetadata("animationDuration", durationArr[0]);
            }
            
            // 延迟开始
            float[] delayArr = new float[1];
            delayArr[0] = (float) container.getMetadata("animationDelay", 0.0f);
            if (ImGui.sliderFloat("延迟开始（秒）", delayArr, 0.0f, 10.0f, "%.1fs")) {
                container.setMetadata("animationDelay", delayArr[0]);
            }
        }
    }
    
    private void renderVisualProperties(Container container) {
        if (ImGui.collapsingHeader("视觉属性")) {
            // 不透明度
            float[] opacityArr = new float[]{container.getOpacity()};
            if (ImGui.sliderFloat("不透明度", opacityArr, 0.0f, 1.0f, "%.2f")) {
                executeCommand(new SetPropertyCommand(container, "opacity", opacityArr[0]));
            }
            
            // 颜色
            int color = container.getColor();
            colorBuffer[0] = ((color) & 0xFF) / 255.0f;        // R
            colorBuffer[1] = ((color >> 8) & 0xFF) / 255.0f;   // G
            colorBuffer[2] = ((color >> 16) & 0xFF) / 255.0f;  // B
            colorBuffer[3] = ((color >> 24) & 0xFF) / 255.0f;  // A
            
            if (ImGui.colorEdit4("标记颜色", colorBuffer)) {
                int newColor = 
                    ((int)(colorBuffer[3] * 255) << 24) |
                    ((int)(colorBuffer[2] * 255) << 16) |
                    ((int)(colorBuffer[1] * 255) << 8) |
                    ((int)(colorBuffer[0] * 255));
                executeCommand(new SetPropertyCommand(container, "color", newColor));
            }
            
            ImGui.spacing();
            
            // 可见性
            boolean visible = container.isVisible();
            if (ImGui.checkbox("可见", visible)) {
                executeCommand(new SetPropertyCommand(container, "visible", !visible));
            }
            
            ImGui.sameLine();
            
            // 锁定
            boolean locked = container.isLocked();
            if (ImGui.checkbox("锁定", locked)) {
                executeCommand(new SetPropertyCommand(container, "locked", !locked));
            }
        }
    }
    
    private void renderDataProperties(Container container) {
        if (ImGui.collapsingHeader("数据属性")) {
            ImGui.text("创建时间: " + new java.util.Date(container.getCreatedTime()));
            ImGui.text("修改时间: " + new java.util.Date(container.getModifiedTime()));
            
            ImGui.spacing();
            
            if (ImGui.button("查看方块列表")) {
                LOGGER.info("[Properties] 方块列表: {}", container.getBlocks());
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("导出数据")) {
                LOGGER.info("[Properties] 导出容器: {}", container.getName());
            }
        }
    }
    
    private void renderMetadata(Container container) {
        if (ImGui.collapsingHeader("自定义元数据")) {
            ImGui.textDisabled("(可添加自定义键值对)");
            
            // TODO: 添加元数据编辑器
            if (ImGui.button("添加元数据...")) {
                LOGGER.info("[Properties] 添加元数据");
            }
        }
    }
    
    // ========== 命令执行 ==========
    
    private void executeCommand(ContainerCommand command) {
        // 直接执行命令（不使用历史记录，因为类型不匹配）
        // TODO: 创建统一的命令接口或适配器
        command.execute();
    }
    
    // ========== 内部命令类 ==========
    
    /**
     * 设置属性命令
     */
    private static class SetPropertyCommand implements ContainerCommand {
        private final Container container;
        private final String property;
        private final Object newValue;
        private final Object oldValue;
        
        public SetPropertyCommand(Container container, String property, Object newValue) {
            this.container = container;
            this.property = property;
            this.newValue = newValue;
            this.oldValue = getCurrentValue();
        }
        
        private Object getCurrentValue() {
            return switch (property) {
                case "name" -> container.getName();
                case "type" -> container.getType();
                case "animationMode" -> container.getAnimationMode();
                case "opacity" -> container.getOpacity();
                case "color" -> container.getColor();
                case "visible" -> container.isVisible();
                case "locked" -> container.isLocked();
                default -> null;
            };
        }
        
        @Override
        public void execute() {
            applyValue(newValue);
        }
        
        @Override
        public void undo() {
            applyValue(oldValue);
        }
        
        private void applyValue(Object value) {
            switch (property) {
                case "name" -> container.setName((String) value);
                case "type" -> container.setType((ContainerType) value);
                case "animationMode" -> container.setAnimationMode((AnimationMode) value);
                case "opacity" -> container.setOpacity((Float) value);
                case "color" -> container.setColor((Integer) value);
                case "visible" -> container.setVisible((Boolean) value);
                case "locked" -> container.setLocked((Boolean) value);
            }
        }
        
        @Override
        public String getDescription() {
            return "设置 " + property + ": " + newValue;
        }
    }
}

