package com.director.ui.container;

import com.director.core.container.AnimationMode;

import java.util.UUID;

/**
 * 负责容器动画预设相关的业务入口，隔离到独立控制器以减轻 PanelController 职责。
 */
public class ContainerAnimationController {

    private final ContainerCommandService commandService;

    public ContainerAnimationController(ContainerCommandService commandService) {
        this.commandService = commandService;
    }

    public void setPresetMode(UUID containerId, AnimationMode mode) {
        commandService.setPresetMode(containerId, mode);
    }

    public void setPresetDuration(UUID containerId, float duration) {
        commandService.setPresetDuration(containerId, duration);
    }

    public void setPresetDelay(UUID containerId, float delay) {
        commandService.setPresetDelay(containerId, delay);
    }

    public void setPresetEasing(UUID containerId, String easing) {
        commandService.setPresetEasing(containerId, easing);
    }

    public void setPresetOrdering(UUID containerId, String ordering) {
        commandService.setPresetOrdering(containerId, ordering);
    }
}