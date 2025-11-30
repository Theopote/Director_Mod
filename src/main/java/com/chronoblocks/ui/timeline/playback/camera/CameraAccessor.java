package com.chronoblocks.ui.timeline.playback.camera;

/**
 * Accessor interface for Camera class to expose protected methods.
 * This interface is implemented by CameraMixin to allow external code
 * to access protected Camera methods.
 */
public interface CameraAccessor {
    /**
     * Set camera position (accesses protected setPos method)
     */
    void chronoblocks$setPos(double x, double y, double z);
    
    /**
     * Set camera rotation (accesses protected setRotation method)
     */
    void chronoblocks$setRotation(float yaw, float pitch);
}