package com.chronoblocks.animation.integration;

import com.chronoblocks.animation.core.EvaluatedPose;
import com.chronoblocks.animation.timeline.TimelineController;
import com.chronoblocks.client.renderer.ContainerRenderer;

import java.util.UUID;

/**
 * ContainerAnimationBridge - TimelineController 与 ContainerRenderer 之间的桥梁。
 */
public class ContainerAnimationBridge {

    private final TimelineController timelineController;
    private final ContainerRenderer containerRenderer;

    public ContainerAnimationBridge(TimelineController controller, ContainerRenderer renderer) {
        this.timelineController = controller;
        this.containerRenderer = renderer;
    }

    public void apply(UUID containerId) {
        EvaluatedPose pose = timelineController.evaluate(containerId);
        containerRenderer.applyEvaluatedPose(containerId, pose);
    }
}


