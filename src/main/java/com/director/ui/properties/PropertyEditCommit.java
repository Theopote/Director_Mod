package com.director.ui.properties;

/** Collects changes from numeric controls rendered during a frame. */
public final class PropertyEditCommit {
    private boolean pending;
    private boolean active;

    public void beginFrame() {
        active = false;
    }

    public void observe(boolean changed, boolean itemActive) {
        pending |= changed;
        active |= itemActive;
    }

    public boolean endFrame() {
        return !active && finish();
    }

    public boolean finish() {
        boolean changed = pending;
        pending = false;
        return changed;
    }
}
