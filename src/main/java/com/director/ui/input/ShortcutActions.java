package com.director.ui.input;

import com.director.client.gizmo.GizmoMode;
import com.director.ui.actor.gizmo.ActorGizmoController;
import com.director.ui.commands.UICommandFacade;
import com.director.ui.PanelId;
import com.director.core.selection.SelectionOperation;
import com.director.ui.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 快捷键动作映射层 - 将 ShortcutAction 映射到 Facade 命令
 * 
 * <p>职责：
 * <ul>
 *   <li>将快捷键动作翻译成 Facade 命令调用</li>
 *   <li>保持快捷键系统与业务逻辑解耦</li>
 *   <li>统一所有快捷键的执行路径</li>
 * </ul>
 * 
 * <p>设计原则：
 * <ul>
 *   <li>只调用 Facade，不直接调用业务类</li>
 *   <li>所有快捷键最终都通过 Facade 执行</li>
 *   <li>支持未来扩展（宏、自动化等）</li>
 * </ul>
 * 
 * @author Director Team
 * @version 1.0
 */
public final class ShortcutActions {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ShortcutActions.class);
    
    private ShortcutActions() {
        throw new UnsupportedOperationException("ShortcutActions 是工具类，不能实例化");
    }
    
    /**
     * 分发快捷键动作到对应的 Facade 命令
     * 
     * @param action 快捷键动作
     * @param commands Facade 命令接口
     */
    public static void dispatch(ShortcutAction action, UICommandFacade commands) {
        if (commands == null || action == null) {
            LOGGER.warn("[ShortcutActions] 无效参数: action={}, commands={}", action, commands);
            return;
        }
        
        try {
            switch (action) {
                // ==================== 项目操作 ====================
                case PROJECT_NEW -> {
                    commands.project().newProject();
                    LOGGER.debug("[ShortcutActions] 执行: PROJECT_NEW");
                }
                case PROJECT_RELOAD -> {
                    commands.project().openProject();
                    LOGGER.debug("[ShortcutActions] 执行: PROJECT_RELOAD");
                }
                case PROJECT_SAVE -> {
                    commands.project().saveProject();
                    LOGGER.debug("[ShortcutActions] 执行: PROJECT_SAVE");
                }
                case HELP_SHORTCUTS -> {
                    commands.help().showShortcutHelp();
                    LOGGER.debug("[ShortcutActions] 执行: HELP_SHORTCUTS");
                }
                
                // ==================== 播放控制 ====================
                case PLAY_PAUSE -> {
                    boolean wasPlaying = commands.playback().isPlaying();
                    commands.playback().togglePlayPause();
                    boolean isPlaying = commands.playback().isPlaying();
                    LOGGER.info("[ShortcutActions] 执行: PLAY_PAUSE, 状态变化: {} -> {}", wasPlaying, isPlaying);
                }
                case STOP -> {
                    commands.playback().stop();
                    LOGGER.debug("[ShortcutActions] 执行: STOP");
                }
                case GO_TO_START -> {
                    commands.playback().goToStart();
                    LOGGER.debug("[ShortcutActions] 执行: GO_TO_START");
                }
                case GO_TO_END -> {
                    commands.playback().goToEnd();
                    LOGGER.debug("[ShortcutActions] 执行: GO_TO_END");
                }
                case TOGGLE_LOOP -> {
                    commands.playback().toggleLoop();
                    LOGGER.debug("[ShortcutActions] 执行: TOGGLE_LOOP");
                }
                
                // ==================== 工具切换 ====================
                case TOOL_SELECT -> {
                    commands.tools().activateTool("click");
                    LOGGER.debug("[ShortcutActions] 执行: TOOL_SELECT");
                }
                case TOOL_BOX_SELECT -> {
                    commands.tools().activateTool("box");
                    LOGGER.debug("[ShortcutActions] 执行: TOOL_BOX_SELECT");
                }
                case TOOL_WAND -> {
                    commands.tools().activateTool("wand");
                    LOGGER.debug("[ShortcutActions] 执行: TOOL_WAND");
                }
                case TOOL_BRUSH -> {
                    commands.tools().activateTool("brush");
                    LOGGER.debug("[ShortcutActions] 执行: TOOL_BRUSH");
                }
                case TOOL_FACE -> {
                    commands.tools().activateTool("face");
                    LOGGER.debug("[ShortcutActions] 执行: TOOL_FACE");
                }
                case TOOL_MOVE -> {
                    activateActorSelectWithGizmoMode(GizmoMode.TRANSLATE, commands);
                    LOGGER.debug("[ShortcutActions] 执行: TOOL_MOVE -> actor_select TRANSLATE");
                }
                case TOOL_ROTATE -> {
                    activateActorSelectWithGizmoMode(GizmoMode.ROTATE, commands);
                    LOGGER.debug("[ShortcutActions] 执行: TOOL_ROTATE -> actor_select ROTATE");
                }
                case TOOL_SCALE -> {
                    activateActorSelectWithGizmoMode(GizmoMode.SCALE, commands);
                    LOGGER.debug("[ShortcutActions] 执行: TOOL_SCALE -> actor_select SCALE");
                }
                case TOOL_NEXT -> {
                    commands.tools().switchToNextTool();
                    LOGGER.debug("[ShortcutActions] 执行: TOOL_NEXT");
                }
                case TOOL_PREVIOUS -> {
                    commands.tools().switchToPreviousTool();
                    LOGGER.debug("[ShortcutActions] 执行: TOOL_PREVIOUS");
                }
                
                // ==================== 选择操作模式 ====================
                case SELECTION_MODE_ADD -> {
                    commands.tools().setSelectionMode(SelectionOperation.ADD);
                    LOGGER.debug("[ShortcutActions] 执行: SELECTION_MODE_ADD");
                }
                case SELECTION_MODE_SUBTRACT -> {
                    commands.tools().setSelectionMode(SelectionOperation.SUBTRACT);
                    LOGGER.debug("[ShortcutActions] 执行: SELECTION_MODE_SUBTRACT");
                }
                case SELECTION_MODE_INTERSECT -> {
                    commands.tools().setSelectionMode(SelectionOperation.INTERSECT);
                    LOGGER.debug("[ShortcutActions] 执行: SELECTION_MODE_INTERSECT");
                }
                case CLEAR_SELECTION -> {
                    commands.tools().clearSelection();
                    LOGGER.debug("[ShortcutActions] 执行: CLEAR_SELECTION");
                }
                
                // ==================== 面板切换 ====================
                case TOGGLE_TOOL_PROPERTIES -> {
                    commands.view().togglePanel(PanelId.PROPERTIES);
                    LOGGER.debug("[ShortcutActions] 执行: TOGGLE_TOOL_PROPERTIES（已映射到属性面板）");
                }
                case TOGGLE_SELECTION_FILTER -> {
                    commands.view().togglePanel(PanelId.SELECTION_FILTER);
                    LOGGER.debug("[ShortcutActions] 执行: TOGGLE_SELECTION_FILTER");
                }
                case TOGGLE_SELECTION_HISTORY -> {
                    commands.view().togglePanel(PanelId.SELECTION_HISTORY);
                    LOGGER.debug("[ShortcutActions] 执行: TOGGLE_SELECTION_HISTORY");
                }
                
                // ==================== 时间线操作 ====================
                case TIMELINE_UNDO -> {
                    commands.timeline().undo();
                    LOGGER.debug("[ShortcutActions] 执行: TIMELINE_UNDO");
                }
                case TIMELINE_REDO -> {
                    commands.timeline().redo();
                    LOGGER.debug("[ShortcutActions] 执行: TIMELINE_REDO");
                }
                case TIMELINE_DELETE -> {
                    try {
                        commands.timeline().deleteSelected();
                        LOGGER.debug("[ShortcutActions] 执行: TIMELINE_DELETE");
                    } catch (Exception e) {
                        commands.tools().clearSelection();
                        LOGGER.debug("[ShortcutActions] TIMELINE_DELETE 失败，回退到 CLEAR_SELECTION");
                    }
                }
                case TIMELINE_CUT -> {
                    commands.timeline().cutSelected();
                    LOGGER.debug("[ShortcutActions] 执行: TIMELINE_CUT");
                }
                case TIMELINE_COPY -> {
                    commands.timeline().copySelected();
                    LOGGER.debug("[ShortcutActions] 执行: TIMELINE_COPY");
                }
                case TIMELINE_PASTE -> {
                    commands.timeline().paste();
                    LOGGER.debug("[ShortcutActions] 执行: TIMELINE_PASTE");
                }
                case TIMELINE_CANCEL_INTERACTION -> {
                    commands.timeline().cancelInteraction();
                    LOGGER.debug("[ShortcutActions] 执行: TIMELINE_CANCEL_INTERACTION");
                }
                case CONTAINER_DELETE -> {
                    commands.container().requestDeleteSelected();
                    LOGGER.debug("[ShortcutActions] 执行: CONTAINER_DELETE");
                }
                
                // ==================== Actor Gizmo 操作 ====================
                case GIZMO_MOVE -> {
                    if (isActorTransformGizmoToolActive()) {
                        ActorGizmoController.getInstance().setPreferredMode(GizmoMode.TRANSLATE);
                    } else if (isActorToolActive()) {
                        ActorGizmoController.getInstance().setPreferredMode(GizmoMode.TRANSLATE);
                    }
                    LOGGER.debug("[ShortcutActions] 执行: GIZMO_MOVE");
                }
                case GIZMO_ROTATE -> {
                    if (isActorTransformGizmoToolActive()) {
                        ActorGizmoController.getInstance().setPreferredMode(GizmoMode.ROTATE);
                    } else if (isActorToolActive()) {
                        ActorGizmoController.getInstance().setPreferredMode(GizmoMode.ROTATE);
                    }
                    LOGGER.debug("[ShortcutActions] 执行: GIZMO_ROTATE");
                }
                case GIZMO_SCALE -> {
                    if (isActorTransformGizmoToolActive()) {
                        ActorGizmoController.getInstance().setPreferredMode(GizmoMode.SCALE);
                    } else {
                        activateActorSelectWithGizmoMode(GizmoMode.SCALE, commands);
                    }
                    LOGGER.debug("[ShortcutActions] 执行: GIZMO_SCALE");
                }
            }
        } catch (Exception e) {
            LOGGER.error("[ShortcutActions] 执行快捷键动作失败: action={}", action, e);
        }
    }

    private static void activateActorSelectWithGizmoMode(GizmoMode mode, UICommandFacade commands) {
        commands.tools().activateTool(ToolRegistry.ACTOR_SELECT_TOOL_ID);
        ActorGizmoController.getInstance().setPreferredMode(mode);
    }

    private static boolean isActorToolActive() {
        return ToolRegistry.isActorToolId(
                ToolRegistry.get().getActiveNonSelectionToolId());
    }

    private static boolean isActorTransformGizmoToolActive() {
        return ToolRegistry.isActorTransformGizmoToolId(
                ToolRegistry.get().getActiveNonSelectionToolId());
    }
}
