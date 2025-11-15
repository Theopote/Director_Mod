package com.chronoblocks.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 时间轴命令历史管理器。
 * <p>
 * 提供撤销 / 重做栈，并负责命令执行的统一入口。
 */
public class TimelineCommandHistory {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineCommandHistory.class);

    private final List<TimelineCommand> undoStack = new ArrayList<>();
    private final List<TimelineCommand> redoStack = new ArrayList<>();
    private int maxHistorySize = 100;

    /**
     * 执行命令并加入撤销栈。
     */
    public void executeCommand(TimelineCommand command) {
        command.execute();

        if (command.canUndo()) {
            undoStack.add(command);
            redoStack.clear();

            if (undoStack.size() > maxHistorySize) {
                undoStack.remove(0);
            }

            LOGGER.debug("[Timeline] 执行命令: {}", command.getDescription());
        }
    }

    /**
     * 撤销最近一次命令。
     *
     * @return true 表示成功撤销
     */
    public boolean undo() {
        if (undoStack.isEmpty()) {
            LOGGER.debug("[Timeline] 无可撤销的命令");
            return false;
        }

        TimelineCommand command = undoStack.remove(undoStack.size() - 1);
        command.undo();
        redoStack.add(command);
        LOGGER.info("[Timeline] 撤销命令: {}", command.getDescription());
        return true;
    }

    /**
     * 重做上一次撤销的命令。
     *
     * @return true 表示成功重做
     */
    public boolean redo() {
        if (redoStack.isEmpty()) {
            LOGGER.debug("[Timeline] 无可重做的命令");
            return false;
        }

        TimelineCommand command = redoStack.remove(redoStack.size() - 1);
        command.execute();
        undoStack.add(command);
        LOGGER.info("[Timeline] 重做命令: {}", command.getDescription());
        return true;
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


