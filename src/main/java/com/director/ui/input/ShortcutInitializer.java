package com.director.ui.input;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// ⚠️ 以下导入已不再使用（Actor Gizmo 和时间线快捷键已迁移到 ShortcutDispatcher）
// import imgui.ImGui;
// import com.director.ui.tools.ToolRegistry;
// import com.director.ui.actor.gizmo.ActorGizmoController;
// import com.director.client.gizmo.GizmoMode;
// import imgui.flag.ImGuiKey;

/**
 * 🔧 快捷键初始化器
 * 
 * <p>负责在模组初始化时注册所有默认快捷键
 * 
 * <p>使用示例：
 * <pre>{@code
 * // 在模组初始化时调用
 * ShortcutInitializer.initialize();
 * 
 * // 在 DirectorScreen 打开时激活 UI_PANEL 上下文
 * GlobalShortcutManager.getInstance().setContextEnabled(
 *     GlobalShortcutManager.ShortcutContext.UI_PANEL, true);
 * 
 * // 在选择工具激活时激活 SELECTION_TOOL 上下文
 * GlobalShortcutManager.getInstance().setContextEnabled(
 *     GlobalShortcutManager.ShortcutContext.SELECTION_TOOL, true);
 * }</pre>
 * 
 * @author Director Team
 * @version 1.0
 */
public class ShortcutInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShortcutInitializer.class);
    
    /**
     * 初始化所有默认快捷键
     */
    public static void initialize() {
        GlobalShortcutManager manager = GlobalShortcutManager.getInstance();
        
        // ========== 全局快捷键 ==========
        // 这些快捷键在任何状态下都可用
        
        // ========== UI 面板快捷键 ==========
        // 这些快捷键只在 DirectorScreen 打开时可用
        
        // Esc 键：取消选区（如果存在）
        manager.registerKeyGLFW(
            "ui.clear_selection",
            "取消当前选区",
            GlobalShortcutManager.ShortcutContext.UI_PANEL,
            100,
            GLFW.GLFW_KEY_ESCAPE,
            () -> {
                try {
                    var selectionManager = com.director.core.selection.SelectionManager.get();
                    if (selectionManager != null && !selectionManager.getSelectionSet().isEmpty()) {
                        selectionManager.clearSelection();
                        LOGGER.info("[ShortcutInitializer] Esc 键：已清除选区");
                    }

                } catch (Exception e) {
                    LOGGER.warn("[ShortcutInitializer] 处理 Esc 键清除选区失败", e);
                }
            }
        );

        // ⚠️ Actor Gizmo 快捷键已迁移到 ShortcutDispatcher 系统
        // registerActorGizmoShortcuts(manager);
        
        // ========== 选择工具快捷键 ==========
        // 这些快捷键在选择工具模式时可用
        
        // ========== 时间线编辑快捷键 ==========
        // ⚠️ 以下快捷键已迁移到 ShortcutDispatcher 系统（Facade 架构）
        // 新的快捷键系统通过 ShortcutAction → ShortcutActions → UICommandFacade 统一处理
        // 不再需要在此处注册
        
        // Ctrl+Z: 撤销 - 已迁移到 ShortcutDispatcher (TIMELINE_UNDO)
        // manager.registerCtrlImGui(
        //     "timeline.undo",
        //     "撤销操作",
        //     GlobalShortcutManager.ShortcutContext.TIMELINE_EDITOR,
        //     100,
        //         ImGuiKey.Z,
        //     () -> {
        //         // 这个回调会在 TimelinePanel 中动态设置
        //         LOGGER.debug("[ShortcutInitializer] 撤销快捷键触发");
        //     }
        // );
        
        // Ctrl+Y: 重做 - 已迁移到 ShortcutDispatcher (TIMELINE_REDO)
        // manager.registerCtrlImGui(
        //     "timeline.redo",
        //     "重做操作",
        //     GlobalShortcutManager.ShortcutContext.TIMELINE_EDITOR,
        //     100,
        //         ImGuiKey.Y,
        //     () -> {
        //         // 这个回调会在 TimelinePanel 中动态设置
        //         LOGGER.debug("[ShortcutInitializer] 重做快捷键触发");
        //     }
        // );
        
        // Delete: 删除选中项 - 已迁移到 ShortcutDispatcher (TIMELINE_DELETE)
        // manager.registerKeyImGui(
        //     "timeline.delete",
        //     "删除选中项",
        //     GlobalShortcutManager.ShortcutContext.TIMELINE_EDITOR,
        //     100,
        //         ImGuiKey.Delete,
        //     () -> {
        //         // 这个回调会在 TimelinePanel 中动态设置
        //         LOGGER.debug("[ShortcutInitializer] 删除快捷键触发");
        //     }
        // );
        
        // ========== 容器管理快捷键 ==========
        // 这些快捷键在容器管理时可用
        
        LOGGER.info("[ShortcutInitializer] 已注册 {} 个默认快捷键", 
            manager.getActiveShortcuts().size());
    }
    
    // ⚠️ Actor gizmo mode shortcuts (W/E/R) - 已迁移到 ShortcutDispatcher 系统
    // 新的快捷键系统通过 ShortcutAction (GIZMO_MOVE/GIZMO_ROTATE/GIZMO_SCALE) → ShortcutActions → Facade 统一处理

    // private static void registerActorGizmoShortcuts(GlobalShortcutManager manager) {
    //     if (manager == null) {
    //         return;
    //     }
    //
    //     manager.registerGLFW(
    //         "actor.gizmo.translate",
    //         "Actor Gizmo: Move",
    //         GlobalShortcutManager.ShortcutContext.UI_PANEL,
    //         90,
    //         GLFW.GLFW_KEY_W,
    //         false, false, false,
    //         () -> setActorGizmoMode(GizmoMode.TRANSLATE)
    //     );
    //
    //     manager.registerGLFW(
    //         "actor.gizmo.rotate",
    //         "Actor Gizmo: Rotate",
    //         GlobalShortcutManager.ShortcutContext.UI_PANEL,
    //         90,
    //         GLFW.GLFW_KEY_E,
    //         false, false, false,
    //         () -> setActorGizmoMode(GizmoMode.ROTATE)
    //     );
    //
    //     manager.registerGLFW(
    //         "actor.gizmo.scale",
    //         "Actor Gizmo: Scale",
    //         GlobalShortcutManager.ShortcutContext.UI_PANEL,
    //         90,
    //         GLFW.GLFW_KEY_R,
    //         false, false, false,
    //         () -> setActorGizmoMode(GizmoMode.SCALE)
    //     );
    // }
    //
    // private static void setActorGizmoMode(GizmoMode mode) {
    //     if (ImGui.getIO() != null && ImGui.getIO().getWantTextInput()) {
    //         return;
    //     }
    //     if (!ToolRegistry.ACTOR_SELECT_TOOL_ID.equals(ToolRegistry.get().getActiveNonSelectionToolId())) {
    //         return;
    //     }
    //     ActorGizmoController.getInstance().setPreferredMode(mode);
    // }

    /**
     * Activate UI shortcut contexts when DirectorScreen opens.
     */
    public static void activateUIContexts() {
        GlobalShortcutManager manager = GlobalShortcutManager.getInstance();
        manager.setContextEnabled(GlobalShortcutManager.ShortcutContext.UI_PANEL, true);
        LOGGER.debug("[ShortcutInitializer] 激活 UI 面板上下文");
    }
    
    /**
     * 在 DirectorScreen 关闭时调用，停用 UI 相关上下文
     */
    public static void deactivateUIContexts() {
        GlobalShortcutManager manager = GlobalShortcutManager.getInstance();
        manager.setContextEnabled(GlobalShortcutManager.ShortcutContext.UI_PANEL, false);
        manager.setContextEnabled(GlobalShortcutManager.ShortcutContext.TIMELINE_EDITOR, false);
        manager.setContextEnabled(GlobalShortcutManager.ShortcutContext.CONTAINER_MANAGER, false);
        LOGGER.debug("[ShortcutInitializer] 停用 UI 相关上下文");
    }
    
    /**
     * 激活选择工具上下文
     */
    public static void activateSelectionToolContext() {
        GlobalShortcutManager manager = GlobalShortcutManager.getInstance();
        manager.setContextEnabled(GlobalShortcutManager.ShortcutContext.SELECTION_TOOL, true);
        LOGGER.debug("[ShortcutInitializer] 激活选择工具上下文");
    }
    
    /**
     * 停用选择工具上下文
     */
    public static void deactivateSelectionToolContext() {
        GlobalShortcutManager manager = GlobalShortcutManager.getInstance();
        manager.setContextEnabled(GlobalShortcutManager.ShortcutContext.SELECTION_TOOL, false);
        LOGGER.debug("[ShortcutInitializer] 停用选择工具上下文");
    }
}

