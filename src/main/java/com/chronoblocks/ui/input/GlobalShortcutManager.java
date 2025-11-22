package com.chronoblocks.ui.input;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiKey;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiPredicate;

/**
 * 🌐 全局快捷键管理器
 * 
 * <p>统一管理 ChronoBlocks 模组的所有快捷键，支持：
 * <ul>
 *   <li>多种上下文（UI 面板、Minecraft 游戏内、选择工具模式等）</li>
 *   <li>多种按键系统（ImGuiKey、GLFW 键码、Minecraft KeyBinding）</li>
 *   <li>优先级和冲突处理</li>
 *   <li>上下文感知（在不同状态下启用/禁用特定快捷键）</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class GlobalShortcutManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalShortcutManager.class);
    
    private static GlobalShortcutManager INSTANCE;
    
    /**
     * 快捷键上下文枚举
     */
    public enum ShortcutContext {
        /** UI 面板（ChronoBlocksScreen 打开时） */
        UI_PANEL,
        /** Minecraft 游戏内（ChronoBlocksScreen 关闭时） */
        MINECRAFT_GAME,
        /** 选择工具模式 */
        SELECTION_TOOL,
        /** 时间线编辑 */
        TIMELINE_EDITOR,
        /** 容器管理 */
        CONTAINER_MANAGER,
        /** 全局（任何状态下都可用） */
        GLOBAL
    }
    
    /**
     * 快捷键定义
     */
    public static class ShortcutDefinition {
        private final String id;
        private final String description;
        private final ShortcutContext context;
        private final int priority; // 优先级，数字越大优先级越高
        private final Runnable action;
        
        // 按键定义（支持多种按键系统）
        private final Integer imguiKey;
        private final Integer glfwKey;
        private final boolean ctrl;
        private final boolean shift;
        private final boolean alt;
        
        // 启用状态
        private boolean enabled = true;
        
        // 匹配条件（可选，用于更复杂的条件判断）
        private BiPredicate<ImGuiIO, Integer> customMatcher;
        
        public ShortcutDefinition(String id, String description, ShortcutContext context, 
                                int priority, Runnable action,
                                Integer imguiKey, Integer glfwKey,
                                boolean ctrl, boolean shift, boolean alt) {
            this.id = id;
            this.description = description;
            this.context = context;
            this.priority = priority;
            this.action = action;
            this.imguiKey = imguiKey;
            this.glfwKey = glfwKey;
            this.ctrl = ctrl;
            this.shift = shift;
            this.alt = alt;
        }
        
        public String getId() { return id; }
        public String getDescription() { return description; }
        public ShortcutContext getContext() { return context; }
        public int getPriority() { return priority; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        /**
         * 检查快捷键是否匹配当前的按键状态
         */
        public boolean matches(int pressedKey, ImGuiIO io) {
            if (!enabled) return false;
            
            // 检查按键是否匹配
            boolean keyMatches = false;
            if (imguiKey != null && pressedKey == imguiKey) {
                keyMatches = true;
            } else if (glfwKey != null && pressedKey == glfwKey) {
                keyMatches = true;
            }
            
            if (!keyMatches) return false;
            
            // 使用自定义匹配器（如果有）
            if (customMatcher != null && !customMatcher.test(io, pressedKey)) {
                return false;
            }
            
            // 检查修饰键
            if (ctrl && !io.getKeyCtrl()) return false;
            if (shift && !io.getKeyShift()) return false;
            if (alt && !io.getKeyAlt()) return false;
            if (!ctrl && io.getKeyCtrl()) return false;  // 精确匹配
            if (!shift && io.getKeyShift()) return false;
            return alt || !io.getKeyAlt();
        }
        
        /**
         * 检查快捷键是否匹配当前的按键状态（使用 GLFW 键码）
         */
        public boolean matchesGLFW(int pressedGLFWKey, boolean ctrlPressed, 
                                   boolean shiftPressed, boolean altPressed) {
            if (!enabled) return false;
            if (glfwKey == null || pressedGLFWKey != glfwKey) return false;
            if (ctrl != ctrlPressed) return false;
            if (shift != shiftPressed) return false;
            return alt == altPressed;
        }
        
        public void execute() {
            if (action != null && enabled) {
                try {
                    action.run();
                } catch (Exception e) {
                    LOGGER.error("[GlobalShortcutManager] 执行快捷键 {} 失败", id, e);
                }
            }
        }
        
        public String getKeyString() {
            StringBuilder sb = new StringBuilder();
            if (ctrl) sb.append("Ctrl+");
            if (shift) sb.append("Shift+");
            if (alt) sb.append("Alt+");
            sb.append(getKeyName());
            return sb.toString();
        }
        
        private String getKeyName() {
            if (imguiKey != null) {
                return getImGuiKeyName(imguiKey);
            }
            if (glfwKey != null) {
                return getGLFWKeyName(glfwKey);
            }
            return "Unknown";
        }
        
        private String getImGuiKeyName(int key) {
            if (key == ImGuiKey.Escape) return "Esc";
            if (key == ImGuiKey.Space) return "Space";
            if (key == ImGuiKey.Delete) return "Del";
            if (key == ImGuiKey.Backspace) return "Backspace";
            if (key == ImGuiKey.Enter) return "Enter";
            if (key == ImGuiKey.Tab) return "Tab";
            if (key >= ImGuiKey.A && key <= ImGuiKey.Z) {
                return String.valueOf((char)('A' + (key - ImGuiKey.A)));
            }
            // 数字键 (ImGuiKey 中数字键通常是按 ASCII 码值)
            if (key >= 48 && key <= 57) { // ASCII '0' to '9'
                return String.valueOf((char)key);
            }
            return "Key" + key;
        }
        
        private String getGLFWKeyName(int key) {
            if (key == GLFW.GLFW_KEY_ESCAPE) return "Esc";
            if (key == GLFW.GLFW_KEY_SPACE) return "Space";
            if (key == GLFW.GLFW_KEY_DELETE) return "Del";
            if (key == GLFW.GLFW_KEY_BACKSPACE) return "Backspace";
            if (key == GLFW.GLFW_KEY_ENTER) return "Enter";
            if (key == GLFW.GLFW_KEY_TAB) return "Tab";
            if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
                return String.valueOf((char)('1' + (key - GLFW.GLFW_KEY_1)));
            }
            if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
                return String.valueOf((char)('A' + (key - GLFW.GLFW_KEY_A)));
            }
            if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12) {
                return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
            }
            return "GLFW" + key;
        }
        
        public void setCustomMatcher(BiPredicate<ImGuiIO, Integer> matcher) {
            this.customMatcher = matcher;
        }
    }
    
    // 快捷键注册表（按上下文分组）
    private final Map<ShortcutContext, List<ShortcutDefinition>> shortcutsByContext = new EnumMap<>(ShortcutContext.class);
    
    // 快捷键 ID 到定义的映射（用于快速查找）
    private final Map<String, ShortcutDefinition> shortcutsById = new HashMap<>();
    
    // 当前激活的上下文列表
    private final Set<ShortcutContext> activeContexts = new HashSet<>();
    
    private GlobalShortcutManager() {
        // 初始化所有上下文的列表
        for (ShortcutContext context : ShortcutContext.values()) {
            shortcutsByContext.put(context, new ArrayList<>());
        }
        
        // 默认激活全局上下文
        activeContexts.add(ShortcutContext.GLOBAL);
        
        LOGGER.info("[GlobalShortcutManager] 全局快捷键管理器已初始化");
    }
    
    public static GlobalShortcutManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new GlobalShortcutManager();
        }
        return INSTANCE;
    }
    
    /**
     * 注册快捷键（使用 ImGuiKey - 接受 int 类型）
     */
    public void registerImGui(String id, String description, ShortcutContext context,
                              int priority, int key, boolean ctrl, boolean shift, boolean alt,
                              Runnable action) {
        register(id, description, context, priority, 
                key, null, 
                ctrl, shift, alt, action);
    }
    
    /**
     * 注册快捷键（使用 GLFW 键码）
     */
    public void registerGLFW(String id, String description, ShortcutContext context,
                            int priority, int glfwKey, boolean ctrl, boolean shift, boolean alt,
                            Runnable action) {
        register(id, description, context, priority, 
                null, glfwKey, 
                ctrl, shift, alt, action);
    }
    
    /**
     * 注册快捷键（同时支持 ImGuiKey 和 GLFW 键码）
     */
    public void register(String id, String description, ShortcutContext context,
                        int priority, Integer imguiKey, Integer glfwKey,
                        boolean ctrl, boolean shift, boolean alt, Runnable action) {
        if (shortcutsById.containsKey(id)) {
            LOGGER.warn("[GlobalShortcutManager] 快捷键 {} 已存在，将被覆盖", id);
            unregister(id);
        }
        
        ShortcutDefinition shortcut = new ShortcutDefinition(
            id, description, context, priority, action,
            imguiKey, glfwKey, ctrl, shift, alt
        );
        
        shortcutsByContext.get(context).add(shortcut);
        shortcutsById.put(id, shortcut);
        
        // 按优先级排序（优先级高的在前）
        shortcutsByContext.get(context).sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        
        LOGGER.debug("[GlobalShortcutManager] 注册快捷键: {} ({}) [{}] - {}", 
            id, shortcut.getKeyString(), context, description);
    }
    
    /**
     * 便捷方法：注册单键快捷键（ImGui - 接受 int 类型）
     */
    public void registerKeyImGui(String id, String description, ShortcutContext context,
                                 int priority, int key, Runnable action) {
        registerImGui(id, description, context, priority, key, false, false, false, action);
    }
    
    /**
     * 便捷方法：注册单键快捷键（GLFW）
     */
    public void registerKeyGLFW(String id, String description, ShortcutContext context,
                               int priority, int glfwKey, Runnable action) {
        registerGLFW(id, description, context, priority, glfwKey, false, false, false, action);
    }
    
    /**
     * 便捷方法：注册 Ctrl+ 快捷键（ImGui - 接受 int 类型）
     */
    public void registerCtrlImGui(String id, String description, ShortcutContext context,
                                  int priority, int key, Runnable action) {
        registerImGui(id, description, context, priority, key, true, false, false, action);
    }
    
    /**
     * 便捷方法：注册 Ctrl+ 快捷键（GLFW）
     */
    public void registerCtrlGLFW(String id, String description, ShortcutContext context,
                                int priority, int glfwKey, Runnable action) {
        registerGLFW(id, description, context, priority, glfwKey, true, false, false, action);
    }
    
    /**
     * 移除快捷键
     */
    public void unregister(String id) {
        ShortcutDefinition shortcut = shortcutsById.remove(id);
        if (shortcut != null) {
            shortcutsByContext.get(shortcut.getContext()).remove(shortcut);
            LOGGER.debug("[GlobalShortcutManager] 移除快捷键: {}", id);
        }
    }
    
    /**
     * 启用/禁用快捷键
     */
    public void setEnabled(String id, boolean enabled) {
        ShortcutDefinition shortcut = shortcutsById.get(id);
        if (shortcut != null) {
            shortcut.setEnabled(enabled);
            LOGGER.debug("[GlobalShortcutManager] {} 快捷键: {}", enabled ? "启用" : "禁用", id);
        }
    }
    
    /**
     * 启用/禁用上下文的所有快捷键
     */
    public void setContextEnabled(ShortcutContext context, boolean enabled) {
        if (enabled) {
            activeContexts.add(context);
        } else {
            activeContexts.remove(context);
        }
        LOGGER.debug("[GlobalShortcutManager] {} 上下文: {}", enabled ? "激活" : "停用", context);
    }
    
    /**
     * 检查上下文是否激活
     */
    public boolean isContextActive(ShortcutContext context) {
        return activeContexts.contains(context);
    }
    
    /**
     * 处理 ImGui 输入事件（在 UI 面板中使用）
     */
    public boolean handleImGuiInput(ImGuiIO io) {
        if (io == null) return false;
        
        // 收集所有激活上下文的快捷键，按优先级排序
        List<ShortcutDefinition> activeShortcuts = new ArrayList<>();
        for (ShortcutContext context : activeContexts) {
            activeShortcuts.addAll(shortcutsByContext.get(context));
        }
        
        // 按优先级排序（优先级高的在前）
        activeShortcuts.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        
        // 检查所有可能的按键
        for (ShortcutDefinition shortcut : activeShortcuts) {
            if (shortcut.imguiKey != null && ImGui.isKeyPressed(shortcut.imguiKey)) {
                if (shortcut.matches(shortcut.imguiKey, io)) {
                    shortcut.execute();
                    LOGGER.debug("[GlobalShortcutManager] 触发快捷键: {} ({})", 
                        shortcut.getKeyString(), shortcut.getDescription());
                    return true; // 已处理，阻止进一步处理
                }
            }
        }
        
        return false;
    }
    
    /**
     * 处理 GLFW 输入事件（在 Minecraft 游戏内使用）
     */
    public boolean handleGLFWInput(int key, boolean ctrlPressed, boolean shiftPressed, boolean altPressed) {
        // 收集所有激活上下文的快捷键，按优先级排序
        List<ShortcutDefinition> activeShortcuts = new ArrayList<>();
        for (ShortcutContext context : activeContexts) {
            activeShortcuts.addAll(shortcutsByContext.get(context));
        }
        
        // 按优先级排序
        activeShortcuts.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        
        // 检查所有快捷键
        for (ShortcutDefinition shortcut : activeShortcuts) {
            if (shortcut.matchesGLFW(key, ctrlPressed, shiftPressed, altPressed)) {
                shortcut.execute();
                LOGGER.debug("[GlobalShortcutManager] 触发快捷键: {} ({})", 
                    shortcut.getKeyString(), shortcut.getDescription());
                return true; // 已处理
            }
        }
        
        return false;
    }
    
    /**
     * 处理 GLFW 输入事件（完整版本，包含 action 和 modifiers）
     */
    public boolean handleGLFWInput(int key, int scancode, int action, int modifiers) {
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) {
            return false;
        }
        
        boolean ctrlPressed = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shiftPressed = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean altPressed = (modifiers & GLFW.GLFW_MOD_ALT) != 0;
        
        return handleGLFWInput(key, ctrlPressed, shiftPressed, altPressed);
    }
    
    /**
     * 获取指定上下文的所有快捷键
     */
    public List<ShortcutDefinition> getShortcuts(ShortcutContext context) {
        return new ArrayList<>(shortcutsByContext.get(context));
    }
    
    /**
     * 获取所有激活上下文的快捷键
     */
    public List<ShortcutDefinition> getActiveShortcuts() {
        List<ShortcutDefinition> all = new ArrayList<>();
        for (ShortcutContext context : activeContexts) {
            all.addAll(shortcutsByContext.get(context));
        }
        return all;
    }
    
    /**
     * 获取快捷键帮助文档
     */
    public String getHelpText(ShortcutContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(context.name()).append(" 快捷键 ===\n");
        
        List<ShortcutDefinition> shortcuts = shortcutsByContext.get(context);
        if (shortcuts.isEmpty()) {
            sb.append("  无快捷键\n");
        } else {
            for (ShortcutDefinition shortcut : shortcuts) {
                sb.append(String.format("  %-20s : %s\n", 
                    shortcut.getKeyString(), shortcut.getDescription()));
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 获取所有快捷键的帮助文档
     */
    public String getAllHelpText() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ChronoBlocks 快捷键帮助 ===\n\n");
        
        for (ShortcutContext context : ShortcutContext.values()) {
            List<ShortcutDefinition> shortcuts = shortcutsByContext.get(context);
            if (!shortcuts.isEmpty()) {
                sb.append(getHelpText(context)).append("\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 打印所有快捷键（调试用）
     */
    public void printAllShortcuts() {
        LOGGER.info("[GlobalShortcutManager] 已注册的快捷键:");
        for (ShortcutContext context : ShortcutContext.values()) {
            List<ShortcutDefinition> shortcuts = shortcutsByContext.get(context);
            if (!shortcuts.isEmpty()) {
                LOGGER.info("  {} ({})", context, shortcuts.size());
                for (ShortcutDefinition shortcut : shortcuts) {
                    LOGGER.info("    {} : {} - {}", 
                        shortcut.getKeyString(), shortcut.getId(), shortcut.getDescription());
                }
            }
        }
    }
    
    /**
     * 清空所有快捷键
     */
    public void clear() {
        shortcutsByContext.clear();
        shortcutsById.clear();
        for (ShortcutContext context : ShortcutContext.values()) {
            shortcutsByContext.put(context, new ArrayList<>());
        }
        activeContexts.clear();
        activeContexts.add(ShortcutContext.GLOBAL);
        LOGGER.info("[GlobalShortcutManager] 已清空所有快捷键");
    }
}

