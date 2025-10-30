package com.chronoblocks.core.timeline.model;

import java.util.*;

/**
 * 时间线项目
 * 包含多个序列的项目
 */
public class TimelineProject {
    private final List<TimelineSequence> sequences = new ArrayList<>();
    private String name = "Project";
    private String description = "";
    private UUID id = UUID.randomUUID();

    public TimelineProject() {
    }

    public TimelineProject(String name) {
        this.name = name;
    }

    /**
     * 添加序列
     */
    public void addSequence(TimelineSequence sequence) {
        if (sequence != null && !sequences.contains(sequence)) {
            sequences.add(sequence);
        }
    }

    /**
     * 移除序列
     */
    public boolean removeSequence(TimelineSequence sequence) {
        return sequences.remove(sequence);
    }

    /**
     * 根据名称移除序列
     */
    public boolean removeSequenceByName(String name) {
        return sequences.removeIf(sequence -> sequence.getName().equals(name));
    }

    /**
     * 获取所有序列
     */
    public List<TimelineSequence> getSequences() {
        return Collections.unmodifiableList(sequences);
    }

    /**
     * 根据名称获取序列
     */
    public Optional<TimelineSequence> getSequenceByName(String name) {
        return sequences.stream()
                .filter(sequence -> sequence.getName().equals(name))
                .findFirst();
    }

    /**
     * 获取序列数量
     */
    public int getSequenceCount() {
        return sequences.size();
    }

    /**
     * 设置名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取ID
     */
    public UUID getId() {
        return id;
    }

    /**
     * 设置ID
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * 清空所有序列
     */
    public void clearSequences() {
        sequences.clear();
    }

    /**
     * 检查是否为空
     */
    public boolean isEmpty() {
        return sequences.isEmpty();
    }

    @Override
    public String toString() {
        return String.format("TimelineProject{name='%s', sequences=%d, id=%s}", 
                name, sequences.size(), id);
    }
}
