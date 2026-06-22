package com.director.ui.commands.impl;

import com.director.ui.DirectorScreen;
import com.director.ui.commands.ProjectCommands;
import com.director.ui.dialogs.DialogManager;
import com.director.timeline.model.TimelineModel;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProjectCommandsImpl implements ProjectCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectCommandsImpl.class);

    private final TimelineModel timelineModel;
    private final DialogManager dialogManager;
    private final Runnable exportVideoAction;

    public ProjectCommandsImpl(TimelineModel timelineModel, DialogManager dialogManager, Runnable exportVideoAction) {
        this.timelineModel = timelineModel;
        this.dialogManager = dialogManager;
        this.exportVideoAction = exportVideoAction;
    }

    @Override
    public void newProject() {
        dialogManager.showNewAnimationConfirm();
    }

    @Override
    public void openProject() {
        if (timelineModel != null) {
            try {
                timelineModel.reload();
                LOGGER.info("[Project] 项目已重新加载");
            } catch (Exception e) {
                LOGGER.error("[Project] 打开项目失败: {}", e.getMessage(), e);
            }
        } else {
            LOGGER.warn("[Project] TimelineModel 不可用，无法打开项目");
        }
    }

    @Override
    public void saveProject() {
        if (timelineModel != null) {
            try {
                timelineModel.save();
                LOGGER.info("[Project] 项目已保存");
            } catch (Exception e) {
                LOGGER.error("[Project] 保存项目失败: {}", e.getMessage(), e);
            }
        } else {
            LOGGER.warn("[Project] TimelineModel 不可用，无法保存项目");
        }
    }

    @Override
    public void saveProjectAs() {
        saveProject();
        LOGGER.info("[Project] 项目已另存为（当前按世界自动保存）");
    }

    @Override
    public void exportVideo() {
        exportVideoAction.run();
    }

    @Override
    public void exitApplication() {
        try {
            if (timelineModel != null) {
                timelineModel.save();
                LOGGER.info("[Project] 退出前已保存项目");
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.currentScreen instanceof DirectorScreen screen) {
                screen.close();
                client.setScreen(null);
                LOGGER.info("[Project] Director 窗口已关闭");
            } else if (client != null) {
                client.setScreen(null);
            }
        } catch (Exception e) {
            LOGGER.error("[Project] 退出应用失败: {}", e.getMessage(), e);
        }
    }
}
