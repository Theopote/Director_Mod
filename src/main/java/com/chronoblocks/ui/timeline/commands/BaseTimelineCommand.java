package com.chronoblocks.ui.timeline.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chronoblocks.ui.timeline.model.TimelineModel;

/**
 * Timeline 命令基类
 * 
 * <p>提供统一的模型更新和事件通知机制，所有 Timeline 命令应该继承此类。
 * 
 * <p>功能：
 * <ul>
 *   <li>自动调用 model.markDirty() 标记模型已变更</li>
 *   <li>自动触发 onModelChanged 事件通知</li>
 *   <li>提供统一的变更类型标识</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 6.0
 */
public abstract class BaseTimelineCommand implements TimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseTimelineCommand.class);
    
    protected final TimelineModel model;
    private final String changeType;
    
    /**
     * 构造函数
     * @param model Timeline 模型实例
     * @param changeType 变更类型（如 "clip_added", "track_deleted" 等）
     */
    protected BaseTimelineCommand(TimelineModel model, String changeType) {
        this.model = model;
        this.changeType = changeType;
    }
    
    /**
     * 执行命令（模板方法）
     * 子类实现 doExecute() 来执行具体逻辑
     */
    @Override
    public final void execute() {
        doExecute();
        notifyModelChanged();
    }
    
    /**
     * 撤销命令（模板方法）
     * 子类实现 doUndo() 来执行具体撤销逻辑
     */
    @Override
    public final void undo() {
        doUndo();
        notifyModelChanged();
    }
    
    /**
     * 子类实现此方法来执行具体命令逻辑
     */
    protected abstract void doExecute();
    
    /**
     * 子类实现此方法来执行具体撤销逻辑
     */
    protected abstract void doUndo();
    
    /**
     * 通知模型已变更
     */
    protected void notifyModelChanged() {
        if (model != null) {
            model.markDirty();
            LOGGER.debug("[{}] 命令执行完成，通知模型变更", changeType);
        }
    }
    
    /**
     * 获取变更类型
     */
    protected String getChangeType() {
        return changeType;
    }
    
    @Override
    public abstract String getDescription();
}

