package com.director.ui.commands.impl;

/**
 * Layout-related callbacks that require {@link com.director.ui.DirectorUIManager} host state.
 */
public interface DirectorUILayoutActions {

    void clearCurrentDockSpaceState();

    void restoreDefaultPanelVisibility();

    /** Reset dock-space layout so the default layout is rebuilt on next frame. */
    void requestDockSpaceRelayout();
}
