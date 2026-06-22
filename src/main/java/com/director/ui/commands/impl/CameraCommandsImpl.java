package com.director.ui.commands.impl;

import com.director.ui.commands.CameraCommands;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.camera.path.CameraPathType;

public final class CameraCommandsImpl implements CameraCommands {

    private final TimelineModel timelineModel;

    public CameraCommandsImpl(TimelineModel timelineModel) {
        this.timelineModel = timelineModel;
    }

    private UnsupportedOperationException unsupportedCameraCommand(String commandName) {
        String modelState = (timelineModel != null) ? "model-bound" : "model-missing";
        return new UnsupportedOperationException("Camera command not implemented yet: " + commandName + " (" + modelState + ")");
    }

    @Override
    public void createCameraPath(CameraPathType type) {
        throw unsupportedCameraCommand("createCameraPath(" + type + ")");
    }

    @Override
    public void alignCameraToCurrentView() {
        throw unsupportedCameraCommand("alignCameraToCurrentView");
    }

    @Override
    public void createShotFromCurrentView() {
        throw unsupportedCameraCommand("createShotFromCurrentView");
    }

    @Override
    public void setDefaultCameraView() {
        throw unsupportedCameraCommand("setDefaultCameraView");
    }
}
