package com.chronoblocks.ui.input;

import com.chronoblocks.core.selection.SelectionOperationSupport;
import com.chronoblocks.client.input.ChronoKeyCategories;
import com.chronoblocks.ui.tools.ToolRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 🎯 选择工具快捷键绑定管理器
 * 
 * 负责管理选择工具相关的快捷键绑定。
 * 支持工具切换、操作模式切换等快捷键功能。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class SelectionToolKeyBindings {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionToolKeyBindings.class);
    
    /** 快捷键绑定映射表 */
    private static final Map<String, KeyBinding> KEY_BINDINGS = new HashMap<>();
    
    /** 是否启用快捷键 */
    private static boolean enabled = true;
    
    static {
        initializeKeyBindings();
    }
    
    /**
     * 初始化快捷键绑定
     */
    private static void initializeKeyBindings() {
        // 基础选择工具快捷键
        KEY_BINDINGS.put("click_tool", new KeyBinding(
            "key.chronoblocks.selection.click",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_1,
            ChronoKeyCategories.SELECTION
        ));
        
        KEY_BINDINGS.put("box_tool", new KeyBinding(
            "key.chronoblocks.selection.box",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_2,
            ChronoKeyCategories.SELECTION
        ));
        
        KEY_BINDINGS.put("wand_tool", new KeyBinding(
            "key.chronoblocks.selection.wand",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_3,
            ChronoKeyCategories.SELECTION
        ));
        
        KEY_BINDINGS.put("brush_tool", new KeyBinding(
            "key.chronoblocks.selection.brush",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_4,
            ChronoKeyCategories.SELECTION
        ));
        
        // 高级工具快捷键
        KEY_BINDINGS.put("face_tool", new KeyBinding(
            "key.chronoblocks.selection.face",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_5,
            ChronoKeyCategories.SELECTION
        ));
        
        // 几何选择工具已合并到刷选工具中，不再需要独立的快捷键
        // KEY_BINDINGS.put("geometry_tool", new KeyBinding(...));
        
        // 操作模式快捷键
        KEY_BINDINGS.put("add_mode", new KeyBinding(
            "key.chronoblocks.selection.add",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_EQUAL, // + 键
            ChronoKeyCategories.SELECTION
        ));
        
        KEY_BINDINGS.put("subtract_mode", new KeyBinding(
            "key.chronoblocks.selection.subtract",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_MINUS, // - 键
            ChronoKeyCategories.SELECTION
        ));
        
        KEY_BINDINGS.put("intersect_mode", new KeyBinding(
            "key.chronoblocks.selection.intersect",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_0,
            ChronoKeyCategories.SELECTION
        ));
        
        // 选择操作快捷键
        KEY_BINDINGS.put("clear_selection", new KeyBinding(
            "key.chronoblocks.selection.clear",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_DELETE,
            ChronoKeyCategories.SELECTION
        ));
        
        KEY_BINDINGS.put("next_tool", new KeyBinding(
            "key.chronoblocks.selection.next",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_TAB,
            ChronoKeyCategories.SELECTION
        ));
        
        KEY_BINDINGS.put("previous_tool", new KeyBinding(
            "key.chronoblocks.selection.previous",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT,
            ChronoKeyCategories.SELECTION
        ));
        
        LOGGER.info("[SelectionToolKeyBindings] 初始化了 {} 个快捷键绑定", KEY_BINDINGS.size());
    }
    
    /**
     * 处理快捷键输入
     */
    public static void handleKeyInput() {
        if (!enabled) return;
        
        try {
            // 检查工具切换快捷键
            if (KEY_BINDINGS.get("click_tool").wasPressed()) {
                switchToTool("click");
            } else if (KEY_BINDINGS.get("box_tool").wasPressed()) {
                switchToTool("box");
            } else if (KEY_BINDINGS.get("wand_tool").wasPressed()) {
                switchToTool("wand");
            } else if (KEY_BINDINGS.get("brush_tool").wasPressed()) {
                switchToTool("brush");
            } else if (KEY_BINDINGS.get("face_tool").wasPressed()) {
                switchToTool("face");
            }
            // 几何选择工具已合并到刷选工具中
            // else if (KEY_BINDINGS.get("geometry_tool").wasPressed()) {
            //     switchToTool("geometry");
            // }
            
            // 检查操作模式快捷键
            else if (KEY_BINDINGS.get("add_mode").wasPressed()) {
                setOperationMode(com.chronoblocks.core.selection.SelectionOperation.ADD);
            } else if (KEY_BINDINGS.get("subtract_mode").wasPressed()) {
                setOperationMode(com.chronoblocks.core.selection.SelectionOperation.SUBTRACT);
            } else if (KEY_BINDINGS.get("intersect_mode").wasPressed()) {
                setOperationMode(com.chronoblocks.core.selection.SelectionOperation.INTERSECT);
            }
            
            // 检查选择操作快捷键
            else if (KEY_BINDINGS.get("clear_selection").wasPressed()) {
                clearSelection();
            } else if (KEY_BINDINGS.get("next_tool").wasPressed()) {
                switchToNextTool();
            } else if (KEY_BINDINGS.get("previous_tool").wasPressed()) {
                switchToPreviousTool();
            }
            
        } catch (Exception e) {
            LOGGER.error("[SelectionToolKeyBindings] 处理快捷键输入失败", e);
        }
    }
    
    /**
     * 切换到指定工具
     */
    private static void switchToTool(String toolId) {
        boolean ok = ToolRegistry.get().activate(toolId);
        if (ok) {
            LOGGER.info("[SelectionToolKeyBindings] 快捷键切换到工具: {}", toolId);
        }
    }
    
    /**
     * 设置操作模式
     */
    private static void setOperationMode(com.chronoblocks.core.selection.SelectionOperation operation) {
        var currentTool = com.chronoblocks.core.selection.SelectionManager.get().getCurrentTool();
        if (currentTool instanceof SelectionOperationSupport operationSupport) {
            operationSupport.setOperation(operation);
            LOGGER.info("[SelectionToolKeyBindings] 快捷键设置操作模式: {}", operation.getDisplayName());
        } else {
            LOGGER.debug("[SelectionToolKeyBindings] 当前工具不支持操作模式切换: {}", currentTool != null ? currentTool.getId() : "null");
        }
    }
    
    /**
     * 清除选择
     */
    private static void clearSelection() {
        com.chronoblocks.core.selection.SelectionManager.get().clearSelection();
        LOGGER.info("[SelectionToolKeyBindings] 快捷键清除选择");
    }
    
    /**
     * 切换到下一个工具
     */
    private static void switchToNextTool() {
        var world = net.minecraft.client.MinecraftClient.getInstance().world;
        if (world != null) {
            com.chronoblocks.core.selection.SelectionManager.get().switchToNextTool(world);
            LOGGER.info("[SelectionToolKeyBindings] 快捷键切换到下一个工具");
        }
    }
    
    /**
     * 切换到上一个工具
     */
    private static void switchToPreviousTool() {
        var world = net.minecraft.client.MinecraftClient.getInstance().world;
        if (world != null) {
            com.chronoblocks.core.selection.SelectionManager.get().switchToPreviousTool(world);
            LOGGER.info("[SelectionToolKeyBindings] 快捷键切换到上一个工具");
        }
    }

    /**
     * 获取工具切换快捷键文本（用于 Toolbar Tooltip）。
     *
     * <p>若当前工具未绑定切换键，返回 null。</p>
     */
    public static String getToolSwitchShortcut(String toolId) {
        KeyBinding binding = getToolBindingForToolId(toolId);
        if (binding == null) return null;
        try {
            // 1.20+：getBoundKeyLocalizedText() 返回 Text
            return binding.getBoundKeyLocalizedText().getString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static KeyBinding getToolBindingForToolId(String toolId) {
        if (toolId == null) return null;
        return switch (toolId) {
            case "click" -> KEY_BINDINGS.get("click_tool");
            case "box" -> KEY_BINDINGS.get("box_tool");
            case "wand" -> KEY_BINDINGS.get("wand_tool");
            case "brush" -> KEY_BINDINGS.get("brush_tool");
            case "face" -> KEY_BINDINGS.get("face_tool");
            default -> null;
        };
    }
    
    /**
     * 启用/禁用快捷键
     */
    public static void setEnabled(boolean enabled) {
        SelectionToolKeyBindings.enabled = enabled;
        LOGGER.info("[SelectionToolKeyBindings] 快捷键{}", enabled ? "启用" : "禁用");
    }
    
    /**
     * 检查快捷键是否启用
     */
    public static boolean isEnabled() {
        return enabled;
    }
    
    /**
     * 获取所有快捷键绑定
     */
    public static Map<String, KeyBinding> getKeyBindings() {
        return new HashMap<>(KEY_BINDINGS);
    }
    
    /**
     * 获取快捷键帮助文本
     */
    public static String getHelpText() {
        return """
            选择工具快捷键：
            1 - 点击选择工具
            2 - 框选工具
            3 - 魔棒工具
            4 - 笔刷工具
            5 - 面选择工具
            6 - 几何选择工具
            + - 加选模式
            - - 减选模式
            0 - 交集模式
            Delete - 清除选择
            Tab - 下一个工具
            Shift - 上一个工具
            """;
    }
}
