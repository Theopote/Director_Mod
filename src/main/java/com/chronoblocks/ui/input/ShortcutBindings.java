package com.chronoblocks.ui.input;

import java.util.HashMap;
import java.util.Map;
import org.lwjgl.glfw.GLFW;

/**
 * 快捷键绑定映射 - 将按键映射到 ShortcutAction
 * 
 * <p>职责：
 * <ul>
 *   <li>定义按键到动作的映射关系（纯数据）</li>
 *   <li>不包含任何业务逻辑</li>
 *   <li>不调用 Facade 或业务类</li>
 * </ul>
 * 
 * <p>设计原则：
 * <ul>
 *   <li>只存储映射关系，不执行任何操作</li>
 *   <li>使用 GLFW 键码作为标准</li>
 *   <li>支持未来扩展（用户自定义快捷键）</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class ShortcutBindings {
    
    private static final Map<Integer, ShortcutAction> KEY_BINDINGS = new HashMap<>();
    
    static {
        initializeKeyBindings();
    }
    
    private ShortcutBindings() {
        throw new UnsupportedOperationException("ShortcutBindings 是工具类，不能实例化");
    }
    
    /**
     * 初始化快捷键绑定
     */
    private static void initializeKeyBindings() {
        // ==================== 播放控制 ====================
        KEY_BINDINGS.put(GLFW.GLFW_KEY_SPACE, ShortcutAction.PLAY_PAUSE);
        KEY_BINDINGS.put(GLFW.GLFW_KEY_HOME, ShortcutAction.GO_TO_START);
        KEY_BINDINGS.put(GLFW.GLFW_KEY_END, ShortcutAction.GO_TO_END);
        KEY_BINDINGS.put(GLFW.GLFW_KEY_L, ShortcutAction.TOGGLE_LOOP);
        
        // ==================== 工具切换 ====================
        KEY_BINDINGS.put(GLFW.GLFW_KEY_1, ShortcutAction.TOOL_SELECT);
        KEY_BINDINGS.put(GLFW.GLFW_KEY_2, ShortcutAction.TOOL_BOX_SELECT);
        KEY_BINDINGS.put(GLFW.GLFW_KEY_3, ShortcutAction.TOOL_WAND);
        KEY_BINDINGS.put(GLFW.GLFW_KEY_4, ShortcutAction.TOOL_BRUSH);
        KEY_BINDINGS.put(GLFW.GLFW_KEY_5, ShortcutAction.TOOL_FACE);
        
        KEY_BINDINGS.put(GLFW.GLFW_KEY_V, ShortcutAction.TOOL_SELECT);
        KEY_BINDINGS.put(GLFW.GLFW_KEY_B, ShortcutAction.TOOL_BOX_SELECT);
        KEY_BINDINGS.put(GLFW.GLFW_KEY_G, ShortcutAction.TOOL_MOVE);
        KEY_BINDINGS.put(GLFW.GLFW_KEY_R, ShortcutAction.TOOL_ROTATE);
        KEY_BINDINGS.put(GLFW.GLFW_KEY_S, ShortcutAction.TOOL_SCALE);
        
        KEY_BINDINGS.put(GLFW.GLFW_KEY_TAB, ShortcutAction.TOOL_NEXT);
        // 注意：Shift 键通常作为修饰键，需要特殊处理
        
        // ==================== 选择操作模式 ====================
        KEY_BINDINGS.put(GLFW.GLFW_KEY_EQUAL, ShortcutAction.SELECTION_MODE_ADD);      // + 键
        KEY_BINDINGS.put(GLFW.GLFW_KEY_MINUS, ShortcutAction.SELECTION_MODE_SUBTRACT);  // - 键
        KEY_BINDINGS.put(GLFW.GLFW_KEY_0, ShortcutAction.SELECTION_MODE_INTERSECT);
        
        // ==================== 选择操作 ====================
        KEY_BINDINGS.put(GLFW.GLFW_KEY_DELETE, ShortcutAction.CLEAR_SELECTION);
    }
    
    /**
     * 根据按键获取对应的快捷键动作
     * 
     * @param key GLFW 键码
     * @return 对应的 ShortcutAction，如果没有绑定则返回 null
     */
    public static ShortcutAction getAction(int key) {
        return KEY_BINDINGS.get(key);
    }
    
    /**
     * 获取所有快捷键绑定（用于显示帮助信息等）
     * 
     * @return 按键到动作的映射副本
     */
    public static Map<Integer, ShortcutAction> getAllBindings() {
        return new HashMap<>(KEY_BINDINGS);
    }
    
    /**
     * 检查指定按键是否已绑定快捷键
     * 
     * @param key GLFW 键码
     * @return true 如果已绑定，false 如果未绑定
     */
    public static boolean isBound(int key) {
        return KEY_BINDINGS.containsKey(key);
    }
    
    /**
     * 根据工具 ID 获取对应的快捷键显示文本（用于 Tooltip 等）
     * 
     * @param toolId 工具 ID
     * @return 快捷键显示文本（如 "1" 或 "V"），如果没有绑定则返回 null
     */
    public static String getToolShortcutText(String toolId) {
        if (toolId == null) {
            return null;
        }
        
        // 映射工具 ID 到 ShortcutAction
        ShortcutAction action = switch (toolId) {
            case "click" -> ShortcutAction.TOOL_SELECT;
            case "box" -> ShortcutAction.TOOL_BOX_SELECT;
            case "wand" -> ShortcutAction.TOOL_WAND;
            case "brush" -> ShortcutAction.TOOL_BRUSH;
            case "face" -> ShortcutAction.TOOL_FACE;
            case "move" -> ShortcutAction.TOOL_MOVE;
            case "rotate" -> ShortcutAction.TOOL_ROTATE;
            case "scale" -> ShortcutAction.TOOL_SCALE;
            default -> null;
        };
        
        if (action == null) {
            return null;
        }
        
        // 查找对应的按键
        for (var entry : KEY_BINDINGS.entrySet()) {
            if (entry.getValue() == action) {
                int key = entry.getKey();
                return glfwKeyToString(key);
            }
        }
        
        return null;
    }
    
    /**
     * 将 GLFW 键码转换为显示文本
     * 
     * @param key GLFW 键码
     * @return 显示文本
     */
    private static String glfwKeyToString(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_DELETE -> "Delete";
            case GLFW.GLFW_KEY_HOME -> "Home";
            case GLFW.GLFW_KEY_END -> "End";
            case GLFW.GLFW_KEY_1 -> "1";
            case GLFW.GLFW_KEY_2 -> "2";
            case GLFW.GLFW_KEY_3 -> "3";
            case GLFW.GLFW_KEY_4 -> "4";
            case GLFW.GLFW_KEY_5 -> "5";
            case GLFW.GLFW_KEY_0 -> "0";
            case GLFW.GLFW_KEY_V -> "V";
            case GLFW.GLFW_KEY_B -> "B";
            case GLFW.GLFW_KEY_G -> "G";
            case GLFW.GLFW_KEY_R -> "R";
            case GLFW.GLFW_KEY_S -> "S";
            case GLFW.GLFW_KEY_L -> "L";
            case GLFW.GLFW_KEY_EQUAL -> "+";
            case GLFW.GLFW_KEY_MINUS -> "-";
            default -> "Key" + key;
        };
    }
}