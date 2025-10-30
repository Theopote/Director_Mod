package com.chronoblocks.core.timeline;

import com.chronoblocks.core.timeline.model.TimelineProject;
import com.chronoblocks.core.timeline.model.TimelineSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 时间线上下文
 * 全局上下文管理器，管理当前活动的项目和序列
 */
public final class TimelineContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineContext.class);
    private static final TimelineContext INSTANCE = new TimelineContext();
    
    private TimelineProject project;
    private TimelineSequence activeSequence;

    private TimelineContext() {
        // 私有构造函数
    }

    /**
     * 获取单例实例
     */
    public static TimelineContext get() {
        return INSTANCE;
    }

    /**
     * 获取活动项目
     */
    public TimelineProject getActiveProject() {
        return project;
    }

    /**
     * 设置活动项目
     */
    public void setActiveProject(TimelineProject project) {
        this.project = project;
        LOGGER.debug("[TimelineContext] 设置活动项目: {}", project != null ? project.getName() : "null");
    }

    /**
     * 获取活动序列
     */
    public TimelineSequence getActiveSequence() {
        return activeSequence;
    }

    /**
     * 设置活动序列
     */
    public void setActiveSequence(TimelineSequence sequence) {
        this.activeSequence = sequence;
        LOGGER.debug("[TimelineContext] 设置活动序列: {}", sequence != null ? sequence.getName() : "null");
    }

    /**
     * 检查是否有活动项目
     */
    public boolean hasActiveProject() {
        return project != null;
    }

    /**
     * 检查是否有活动序列
     */
    public boolean hasActiveSequence() {
        return activeSequence != null;
    }

    /**
     * 创建默认序列
     */
    public TimelineSequence createDefaultSequence() {
        TimelineSequence sequence = new TimelineSequence("Default Sequence");
        setActiveSequence(sequence);
        LOGGER.info("[TimelineContext] 创建默认序列: {}", sequence.getName());
        return sequence;
    }

    /**
     * 重置上下文
     */
    public void reset() {
        this.project = null;
        this.activeSequence = null;
        LOGGER.debug("[TimelineContext] 重置上下文");
    }
}
