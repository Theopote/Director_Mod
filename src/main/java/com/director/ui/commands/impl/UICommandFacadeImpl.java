package com.director.ui.commands.impl;

import com.director.ui.PanelId;
import com.director.ui.commands.CameraCommands;
import com.director.ui.commands.ContainerCommands;
import com.director.ui.commands.HelpCommands;
import com.director.ui.commands.LayoutCommands;
import com.director.ui.commands.PlaybackCommands;
import com.director.ui.commands.ProjectCommands;
import com.director.ui.commands.SelectionFilterCommands;
import com.director.ui.commands.TimelineCommands;
import com.director.ui.commands.ToolCommands;
import com.director.ui.commands.UICommandFacade;
import com.director.ui.commands.ViewCommands;
import com.director.ui.coordinator.UIPanelManager;
import com.director.ui.dialogs.DialogManager;
import com.director.ui.di.IPanelRegistry;
import com.director.ui.layout.LayoutPersistenceManager;
import com.director.ui.panels.MenuBarPanel;
import com.director.ui.panels.TimelinePanel;
import com.director.ui.properties.PropertyAdapterContext;
import com.director.ui.timeline.container.ContainerTimelineService;
import com.director.ui.timeline.model.ITimelineProject;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.snapping.TimelineSnapSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * UI 命令门面实现 — 所有用户操作的统一入口。
 */
public final class UICommandFacadeImpl implements UICommandFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(UICommandFacadeImpl.class);

    private final ProjectCommands projectCommands;
    private final TimelineCommands timelineCommands;
    private final PlaybackCommands playbackCommands;
    private final CameraCommands cameraCommands;
    private final ViewCommands viewCommands;
    private final LayoutCommands layoutCommands;
    private final HelpCommands helpCommands;
    private final ToolCommands toolCommands;
    private final ContainerCommands containerCommands;
    private final SelectionFilterCommands selectionFilterCommands;

    public UICommandFacadeImpl(
            IPanelRegistry panelRegistry,
            UIPanelManager panelManager,
            LayoutPersistenceManager layoutManager,
            DialogManager dialogManager,
            DirectorUILayoutActions layoutActions,
            Runnable exportVideoAction) {
        TimelinePanel timelinePanel = panelRegistry.getPanel(TimelinePanel.class);

        TimelineModel timelineModel;
        if (timelinePanel != null) {
            timelineModel = timelinePanel.getModel();
        } else {
            timelineModel = ContainerTimelineService.getInstance().getTimelineModel();
            if (timelineModel == null) {
                LOGGER.warn("[Commands] TimelineModel not available, some commands may not work");
            }
        }

        this.projectCommands = new ProjectCommandsImpl(timelineModel, dialogManager, exportVideoAction);
        this.timelineCommands = new TimelineCommandsImpl(timelinePanel, timelineModel, dialogManager);
        this.playbackCommands = new PlaybackCommandsImpl(timelineModel);
        this.cameraCommands = new CameraCommandsImpl(timelineModel);
        this.viewCommands = new ViewCommandsImpl(panelManager);
        PropertyAdapterContext.setShowFilterPanelRunnable(
                () -> viewCommands.showPanel(PanelId.SELECTION_FILTER));
        this.layoutCommands = new LayoutCommandsImpl(layoutManager, dialogManager, layoutActions);
        this.helpCommands = new HelpCommandsImpl(dialogManager);
        this.toolCommands = new ToolCommandsImpl();
        this.containerCommands = new ContainerCommandsImpl(panelRegistry);
        this.selectionFilterCommands = new SelectionFilterCommandsImpl();

        if (timelineModel != null) {
            dialogManager.setTimelineSettingsAccessor(new DialogManager.TimelineSettingsAccessor() {
                @Override
                public TimelineSnapSettings getSnapSettings() {
                    return timelineModel.getSnapSettings();
                }

                @Override
                public float getFrameRate() {
                    ITimelineProject project = timelineModel;
                    float fps = project.getFrameRate();
                    LOGGER.debug("[TimelineSettings] 获取帧率: {} (项目对象: {})", fps, project.getClass().getSimpleName());
                    return fps;
                }

                @Override
                public boolean setFrameRate(float fps) {
                    timelineModel.setFrameRate(fps);
                    LOGGER.debug("[TimelineSettings] 帧率已设置为: {}", fps);
                    return true;
                }

                @Override
                public int getTimeUnit() {
                    return timelineModel.getTimeUnit();
                }

                @Override
                public boolean setTimeUnit(int unit) {
                    timelineModel.setTimeUnit(unit);
                    return true;
                }
            });
        }

        dialogManager.setLayoutAccessor(new DialogManager.LayoutAccessor() {
            @Override
            public boolean saveLayoutPreset(String presetName) {
                return layoutCommands.saveLayoutPreset(presetName);
            }

            @Override
            public List<String> listLayoutPresets() {
                return layoutCommands.listLayoutPresets();
            }

            @Override
            public boolean loadLayoutPreset(String presetName) {
                return layoutCommands.loadLayoutPreset(presetName);
            }

            @Override
            public boolean deleteLayoutPreset(String presetName) {
                return layoutCommands.deleteLayoutPreset(presetName);
            }
        });
    }

    @Override
    public ProjectCommands project() {
        return projectCommands;
    }

    @Override
    public TimelineCommands timeline() {
        return timelineCommands;
    }

    @Override
    public PlaybackCommands playback() {
        return playbackCommands;
    }

    @Override
    public CameraCommands camera() {
        return cameraCommands;
    }

    @Override
    public ViewCommands view() {
        return viewCommands;
    }

    @Override
    public LayoutCommands layout() {
        return layoutCommands;
    }

    @Override
    public HelpCommands help() {
        return helpCommands;
    }

    @Override
    public ToolCommands tools() {
        return toolCommands;
    }

    @Override
    public ContainerCommands container() {
        return containerCommands;
    }

    @Override
    public SelectionFilterCommands selectionFilter() {
        return selectionFilterCommands;
    }
}
