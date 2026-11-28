package com.director.ui.timeline.commands;

/**
 * 时间轴命令接口（Command Pattern）
 * <p>
 * 提供基础的执行 / 撤销 / 描述能力，供时间轴面板与属性面板共享。
 */
public interface TimelineCommand {
    enum Result {
        APPLIED,
        NO_CHANGE,
        REJECTED
    }

    /**
     * 执行命令。
     */
    void execute();

    default Result executeResult() {
        execute();
        return Result.APPLIED;
    }

    /**
     * 撤销命令。
     */
    void undo();

    default Result undoResult() {
        undo();
        return Result.APPLIED;
    }

    /**
     * 描述当前命令（用于历史记录和调试）。
     *
     * @return 命令描述
     */
    String getDescription();

    /**
     * 是否允许撤销。默认可撤销。
     *
     * @return true 则允许撤销
     */
    default boolean canUndo() {
        return true;
    }
}


