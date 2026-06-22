package com.director.ui.timeline.commands;

import com.director.core.DirectorServices;
import com.director.core.container.ContainerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;

import java.util.Objects;

/**
 * 重命名片段命令（支持撤销）。
 */
public class RenameClipCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenameClipCommand.class);

    private final TimelineClip clip;
    private final String newLabel;
    private String oldLabel;
    private String oldContainerName; // 保存容器旧名称用于撤销

    public RenameClipCommand(TimelineModel model, TimelineClip clip, String newLabel) {
        super(model, "clip_renamed");
        this.clip = clip;
        this.newLabel = newLabel;
    }

    @Override
    protected void doExecute() {
        if (clip == null || newLabel == null) {
            return;
        }
        oldLabel = clip.getLabel();
        if (newLabel.equals(oldLabel)) {
            return;
        }
        clip.setLabel(newLabel);
        clip.setBoundContainerName(newLabel); // 同步更新绑定容器名称
        LOGGER.debug("[Timeline] 重命名片段: {} -> {}", oldLabel, newLabel);
        
        // ★ 功能：片段重命名时，同步到对应的容器
        // 注意：需要检查片段名称是否与容器名称不同，避免循环更新
        java.util.UUID containerId = clip.getBoundContainerId();
        if (containerId != null) {
            com.director.core.container.Container container = 
                DirectorServices.get().containers().getContainer(containerId);
            if (container != null && !newLabel.equals(container.getName())) {
                // 保存容器旧名称用于撤销
                oldContainerName = container.getName();
                // 使用ContainerManager的方法重命名容器（会触发onContainerRenamed事件）
                // 但我们在onContainerRenamed中会检查片段名称是否已更新，避免循环
                DirectorServices.get().containers().renameContainer(container, newLabel);
                LOGGER.info("[Timeline] 片段重命名已同步到容器: {} -> {}", oldContainerName, newLabel);
            }
        }
    }

    @Override
    protected void doUndo() {
        if (clip == null || oldLabel == null) {
            return;
        }
        clip.setLabel(oldLabel);
        clip.setBoundContainerName(oldLabel); // 同步更新绑定容器名称
        LOGGER.debug("[Timeline] 撤销片段重命名: {} -> {}", newLabel, oldLabel);
        
        // ★ 功能：撤销时，同步恢复容器名称
        java.util.UUID containerId = clip.getBoundContainerId();
        if (containerId != null && oldContainerName != null) {
            com.director.core.container.Container container = 
                DirectorServices.get().containers().getContainer(containerId);
            if (container != null && !oldContainerName.equals(container.getName())) {
                DirectorServices.get().containers().renameContainer(container, oldContainerName);
                LOGGER.info("[Timeline] 撤销片段重命名已同步到容器: {} -> {}", newLabel, oldContainerName);
            }
        }
    }

    @Override
    public String getDescription() {
        return "重命名片段";
    }
}

