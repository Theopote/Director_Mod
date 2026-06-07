package com.director.ui.input;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * @author Director Team
 * @version 1.0
 */
public final class ShortcutBindings {
    
    private static final Map<Integer, ShortcutAction> KEY_BINDINGS = new HashMap<>();
    
    /**
     * 带修饰键的快捷键绑定
     * Key: 键码，Value: Map<修饰键组合, ShortcutAction>
     * 修饰键组合使用位标志：Ctrl=1, Shift=2, Alt=4
     */
    private static final Map<Integer, Map<Integer, ShortcutAction>> MODIFIER_BINDINGS = new HashMap<>();
    
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
        // ==================== 项目操作 ====================
        registerModifierBinding(GLFW.GLFW_KEY_N, 1, ShortcutAction.PROJECT_NEW);
        registerModifierBinding(GLFW.GLFW_KEY_O, 1, ShortcutAction.PROJECT_RELOAD);
        registerModifierBinding(GLFW.GLFW_KEY_S, 1, ShortcutAction.PROJECT_SAVE);
        KEY_BINDINGS.put(GLFW.GLFW_KEY_F1, ShortcutAction.HELP_SHORTCUTS);

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
        // Delete 键：根据上下文决定是清除选择还是删除时间线项
        // 在 ShortcutActions 中会根据上下文处理
        
        // ==================== 时间线操作（带修饰键） ====================
        // Ctrl+Z: 撤销
        registerModifierBinding(GLFW.GLFW_KEY_Z, 1, ShortcutAction.TIMELINE_UNDO); // Ctrl = 1
        // Ctrl+Y: 重做
        registerModifierBinding(GLFW.GLFW_KEY_Y, 1, ShortcutAction.TIMELINE_REDO); // Ctrl = 1
        // 无修饰 Delete：由 ShortcutDispatcher 按 Container/Timeline 焦点路由到 CONTAINER_DELETE 或 TIMELINE_DELETE，此处不绑定
        // 带修饰 Delete：仍映射到 TIMELINE_DELETE
        registerModifierBinding(GLFW.GLFW_KEY_DELETE, 1, ShortcutAction.TIMELINE_DELETE); // Ctrl+Delete
        registerModifierBinding(GLFW.GLFW_KEY_DELETE, 2, ShortcutAction.TIMELINE_DELETE); // Shift+Delete
        registerModifierBinding(GLFW.GLFW_KEY_DELETE, 4, ShortcutAction.TIMELINE_DELETE); // Alt+Delete
        
        // ==================== Actor Gizmo 操作 ====================
        KEY_BINDINGS.put(GLFW.GLFW_KEY_W, ShortcutAction.GIZMO_MOVE);
        KEY_BINDINGS.put(GLFW.GLFW_KEY_E, ShortcutAction.GIZMO_ROTATE);
        // 注意：R 键已经用于 TOOL_ROTATE，Gizmo Scale 使用其他键或通过其他方式触发
        // 暂时保留 R 键用于工具旋转，Gizmo Scale 可以通过其他方式访问
    }
    
    /**
     * 注册带修饰键的快捷键绑定
     * 
     * @param key GLFW 键码
     * @param modifierFlags 修饰键标志（Ctrl=1, Shift=2, Alt=4，可以组合）
     * @param action 快捷键动作
     */
    private static void registerModifierBinding(int key, int modifierFlags, ShortcutAction action) {
        MODIFIER_BINDINGS.computeIfAbsent(key, k -> new HashMap<>()).put(modifierFlags, action);
    }
    
    /**
     * 根据按键获取对应的快捷键动作（无修饰键）
     * 
     * @param key GLFW 键码
     * @return 对应的 ShortcutAction，如果没有绑定则返回 null
     */
    public static ShortcutAction getAction(int key) {
        return KEY_BINDINGS.get(key);
    }
    
    /**
     * 根据按键和修饰键获取对应的快捷键动作
     * 
     * @param key GLFW 键码
     * @param ctrl Ctrl 键是否按下
     * @param shift Shift 键是否按下
     * @param alt Alt 键是否按下
     * @return 对应的 ShortcutAction，如果没有绑定则返回 null
     */
    public static ShortcutAction getAction(int key, boolean ctrl, boolean shift, boolean alt) {
        // 先检查带修饰键的绑定
        Map<Integer, ShortcutAction> modifierMap = MODIFIER_BINDINGS.get(key);
        if (modifierMap != null) {
            int modifierFlags = (ctrl ? 1 : 0) | (shift ? 2 : 0) | (alt ? 4 : 0);
            ShortcutAction action = modifierMap.get(modifierFlags);
            if (action != null) {
                return action;
            }
        }
        
        // 如果没有修饰键，检查无修饰键的绑定
        if (!ctrl && !shift && !alt) {
            return KEY_BINDINGS.get(key);
        }
        
        return null;
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
            case GLFW.GLFW_KEY_F1 -> "F1";
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
            case GLFW.GLFW_KEY_N -> "N";
            case GLFW.GLFW_KEY_O -> "O";
            case GLFW.GLFW_KEY_W -> "W";
            case GLFW.GLFW_KEY_E -> "E";
            case GLFW.GLFW_KEY_Z -> "Z";
            case GLFW.GLFW_KEY_Y -> "Y";
            case GLFW.GLFW_KEY_EQUAL -> "+";
            case GLFW.GLFW_KEY_MINUS -> "-";
            default -> "Key" + key;
        };
    }
    
    /**
     * 获取所有快捷键的格式化列表（按分类组织）
     * 
     * @return 分类的快捷键列表，Key 为分类名称，Value 为该分类下的快捷键列表（格式：快捷键 -> 描述）
     */
    public static Map<String, List<String>> getAllShortcutsByCategory() {
        Map<String, List<String>> categories = new LinkedHashMap<>();
        
        // ==================== 文件操作 ====================
        List<String> file = new ArrayList<>();
        file.add(formatShortcut("Ctrl+N", "新建项目"));
        file.add(formatShortcut("Ctrl+O", "重新加载当前世界项目"));
        file.add(formatShortcut("Ctrl+S", "保存"));
        file.add(formatShortcut("Alt+F4", "退出应用"));
        categories.put("文件操作", file);
        
        // ==================== 播放控制 ====================
        List<String> playback = new ArrayList<>();
        playback.add(formatShortcut("Space", "播放/暂停"));
        playback.add(formatShortcut("Home", "跳转到开始"));
        playback.add(formatShortcut("End", "跳转到结束"));
        playback.add(formatShortcut("L", "切换循环播放"));
        categories.put("播放控制", playback);
        
        // ==================== 工具切换 ====================
        List<String> tools = new ArrayList<>();
        tools.add(formatShortcut("1 或 V", "点击选择工具"));
        tools.add(formatShortcut("2 或 B", "框选工具"));
        tools.add(formatShortcut("3", "魔棒工具"));
        tools.add(formatShortcut("4", "笔刷工具"));
        tools.add(formatShortcut("5", "面选择工具"));
        tools.add(formatShortcut("G", "移动工具"));
        tools.add(formatShortcut("R", "旋转工具"));
        tools.add(formatShortcut("S", "缩放工具"));
        tools.add(formatShortcut("Tab", "下一个工具"));
        // 注意：上一个工具 (TOOL_PREVIOUS) 在 ShortcutAction 中定义，但尚未绑定快捷键
        categories.put("工具切换", tools);
        
        // ==================== 选择操作模式 ====================
        List<String> selection = new ArrayList<>();
        selection.add(formatShortcut("+", "加选模式"));
        selection.add(formatShortcut("-", "减选模式"));
        selection.add(formatShortcut("0", "交集模式"));
        categories.put("选择操作模式", selection);
        
        // ==================== 时间线操作 ====================
        List<String> timeline = new ArrayList<>();
        timeline.add(formatShortcut("Ctrl+Z", "撤销"));
        timeline.add(formatShortcut("Ctrl+Y", "重做"));
        timeline.add(formatShortcut("Ctrl+X", "剪切"));
        timeline.add(formatShortcut("Ctrl+C", "复制"));
        timeline.add(formatShortcut("Ctrl+V", "粘贴"));
        timeline.add(formatShortcut("Delete", "删除选中项"));
        timeline.add(formatShortcut("Ctrl+Delete", "删除选中项"));
        timeline.add(formatShortcut("Shift+Delete", "删除选中项"));
        timeline.add(formatShortcut("Alt+Delete", "删除选中项"));
        categories.put("时间线操作", timeline);
        
        // ==================== Actor Gizmo 操作 ====================
        List<String> gizmo = new ArrayList<>();
        gizmo.add(formatShortcut("W", "Gizmo 移动模式"));
        gizmo.add(formatShortcut("E", "Gizmo 旋转模式"));
        categories.put("Actor Gizmo 操作", gizmo);
        
        // ==================== 界面操作 ====================
        List<String> ui = new ArrayList<>();
        ui.add(formatShortcut("F1", "显示快捷键帮助"));
        ui.add(formatShortcut("Esc", "清除选择"));
        categories.put("界面操作", ui);
        
        return categories;
    }
    
    /**
     * 格式化快捷键显示文本
     * 
     * @param shortcut 快捷键文本
     * @param description 描述
     * @return 格式化后的字符串
     */
    private static String formatShortcut(String shortcut, String description) {
        return String.format("%-20s - %s", shortcut, description);
    }
}
