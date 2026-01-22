package com.chronoblocks.ui.input;

import com.chronoblocks.ui.commands.UICommandFacade;
import com.chronoblocks.ui.PanelId;
import com.chronoblocks.core.selection.SelectionOperation;
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
 * @author ChronoBlocks Team
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
                
                // ==================== 播放控制 ====================
                case PLAY_PAUSE -> {
                    commands.playback().togglePlayPause();
                    LOGGER.debug("[ShortcutActions] 执行: PLAY_PAUSE");
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
                    commands.tools().activateTool("move");
                    LOGGER.debug("[ShortcutActions] 执行: TOOL_MOVE");
                }
                case TOOL_ROTATE -> {
                    commands.tools().activateTool("rotate");
                    LOGGER.debug("[ShortcutActions] 执行: TOOL_ROTATE");
                }
                case TOOL_SCALE -> {
                    commands.tools().activateTool("scale");
                    LOGGER.debug("[ShortcutActions] 执行: TOOL_SCALE");
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
                    commands.view().togglePanel(PanelId.TOOL_PROPERTIES);
                    LOGGER.debug("[ShortcutActions] 执行: TOGGLE_TOOL_PROPERTIES");
                }
                case TOGGLE_HELP -> {
                    commands.view().togglePanel(PanelId.HELP);
                    LOGGER.debug("[ShortcutActions] 执行: TOGGLE_HELP");
                }
                case TOGGLE_SELECTION_FILTER -> {
                    commands.view().togglePanel(PanelId.SELECTION_FILTER);
                    LOGGER.debug("[ShortcutActions] 执行: TOGGLE_SELECTION_FILTER");
                }
                case TOGGLE_SELECTION_HISTORY -> {
                    commands.view().togglePanel(PanelId.SELECTION_HISTORY);
                    LOGGER.debug("[ShortcutActions] 执行: TOGGLE_SELECTION_HISTORY");
                }
            }
        } catch (Exception e) {
            LOGGER.error("[ShortcutActions] 执行快捷键动作失败: action={}", action, e);
        }
    }
}