package com.director.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.director.timeline.model.TimelineModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 时间轴命令历史管理器。
 * <p>
 * 提供撤销 / 重做栈，并负责命令执行的统一入口。
 * 
 * <p>功能：
 * <ul>
 *   <li>自动管理撤销/重做栈</li>
 *   <li>自动触发模型更新事件（如果提供了 TimelineModel）</li>
 *   <li>支持最大历史记录数量限制</li>
 * </ul>
 */
public class TimelineCommandHistory {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineCommandHistory.class);

    private final List<TimelineCommand> undoStack = new ArrayList<>();
    private final List<TimelineCommand> redoStack = new ArrayList<>();
    private int maxHistorySize = 100;
    
    // 可选的 TimelineModel 引用（用于自动触发模型更新）
    private TimelineModel model;
    private final TimelineModel.ModelChangeListener workspaceListener = change -> {
        if ("timeline_reset".equals(change)) clear();
    };
    
    /**
     * 设置 TimelineModel（可选）
     * 如果设置了，命令执行/撤销/重做后会自动调用 model.markDirty()
     */
    public void setModel(TimelineModel model) {
        if (this.model != null) this.model.removeModelChangeListener(workspaceListener);
        this.model = model;
        if (model != null) model.addModelChangeListener(workspaceListener);
    }
    
    /**
     * 获取关联的 TimelineModel
     */
    public TimelineModel getModel() {
        return model;
    }

    /**
     * 执行命令并加入撤销栈。
     * 如果设置了 TimelineModel，会自动触发模型更新事件。
     * ⭐ 性能优化：BaseTimelineCommand 已经会调用 markDirty()，这里不需要重复调用
     */
    public void executeCommand(TimelineCommand command) {
        command.execute();

        if (command.canUndo()) {
            undoStack.add(command);
            redoStack.clear();

            if (undoStack.size() > maxHistorySize) {
                undoStack.removeFirst();
            }

            LOGGER.debug("[Timeline] 执行命令: {}", command.getDescription());
        }
        
        // ⭐ 性能优化：BaseTimelineCommand.execute() 已经会调用 notifyModelChanged() → markDirty()
        // 这里不需要再次调用，避免重复触发模型变更通知和保存操作
        // 只有非 BaseTimelineCommand 的命令才需要手动触发更新
        if (!(command instanceof BaseTimelineCommand)) {
            notifyModelChanged("command_executed");
        }
    }

    /**
     * 撤销最近一次命令。
     *
     * @return true 表示成功撤销
     * ⭐ 性能优化：BaseTimelineCommand 已经会调用 markDirty()，这里不需要重复调用
     */
    private static void cancelActorTransformPreview() {
        var actor=com.director.ui.actor.gizmo.ActorGizmoController.getInstance();
        if(actor.isDragging()) actor.cancelGizmoDrag();
    }

    public boolean undo() {
        if (undoStack.isEmpty()) {
            LOGGER.debug("[Timeline] 无可撤销的命令");
            return false;
        }

        TimelineCommand command = undoStack.getLast();
        command.undo();
        cancelActorTransformPreview();
        undoStack.removeLast();
        redoStack.add(command);
        LOGGER.info("[Timeline] 撤销命令: {}", command.getDescription());
        
        // ⭐ 性能优化：BaseTimelineCommand.undo() 已经会调用 notifyModelChanged() → markDirty()
        // 这里不需要再次调用，避免重复触发模型变更通知和保存操作
        // 只有非 BaseTimelineCommand 的命令才需要手动触发更新
        if (!(command instanceof BaseTimelineCommand)) {
            notifyModelChanged("command_undone");
        }
        return true;
    }

    /**
     * 重做上一次撤销的命令。
     *
     * @return true 表示成功重做
     * ⭐ 性能优化：BaseTimelineCommand 已经会调用 markDirty()，这里不需要重复调用
     */
    public boolean redo() {
        if (redoStack.isEmpty()) {
            LOGGER.debug("[Timeline] 无可重做的命令");
            return false;
        }

        TimelineCommand command = redoStack.getLast();
        command.execute();
        cancelActorTransformPreview();
        redoStack.removeLast();
        undoStack.add(command);
        LOGGER.info("[Timeline] 重做命令: {}", command.getDescription());
        
        // ⭐ 性能优化：BaseTimelineCommand.execute() 已经会调用 notifyModelChanged() → markDirty()
        // 这里不需要再次调用，避免重复触发模型变更通知和保存操作
        // 只有非 BaseTimelineCommand 的命令才需要手动触发更新
        if (!(command instanceof BaseTimelineCommand)) {
            notifyModelChanged("command_redone");
        }
        return true;
    }
    
    /**
     * 通知模型已变更（如果设置了 model）
     */
    private void notifyModelChanged(String changeType) {
        if (model != null) {
            model.markDirty();
        }
    }

    /**
     * 清空历史记录。
     */
    public void clear() {
        undoStack.clear();
        redoStack.clear();
        LOGGER.debug("[Timeline] 清空命令历史");
    }

    public int getUndoSize() {
        return undoStack.size();
    }

    public int getRedoSize() {
        return redoStack.size();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void setMaxHistorySize(int size) {
        this.maxHistorySize = Math.max(10, size);
    }
}


