package com.chronoblocks.ui.timeline.playback.camera;

import net.minecraft.client.MinecraftClient;

/**
 * CameraRuntime v3.1 - runtime switchboard for game vs. cinematic camera.
 *
 * Responsibilities:
 * - Single source of truth for which sample mixins should use.
 * - Cache and restore the player's view/FOV when taking control.
 * - Keep state transitions explicit so the two camera systems never fight each other.
 */
public final class CameraRuntime {
    private static final CameraRuntime INSTANCE = new CameraRuntime();

    public enum Mode {
        GAME,
        CINEMATIC
    }

    private Mode mode = Mode.GAME;
    private CameraSample activeSample;
    private CameraSample cachedPlayerView;
    private Float originalPlayerFov;

    public static CameraRuntime getInstance() {
        return INSTANCE;
    }

    private CameraRuntime() {}

    public boolean isCinematicEnabled() {
        return mode == Mode.CINEMATIC && activeSample != null;
    }

    /**
     * Backwards compatible alias.
     */
    @Deprecated
    public boolean isCinematicActive() {
        return isCinematicEnabled();
    }

    public Mode getMode() {
        return mode;
    }

    /**
     * Current camera sample consumed by mixins.
     */
    public CameraSample getActiveSample() {
        return activeSample;
    }

    /**
     * Enter cinematic mode with the given sample.
     */
    public void enterCinematic(CameraSample sample) {
        if (sample == null) {
            exitCinematicAndRestorePlayer();
            return;
        }

        if (mode != Mode.CINEMATIC) {
            // New session: snapshot the current player camera so we can restore correctly.
            cachePlayerView();
        }

        mode = Mode.CINEMATIC;
        activeSample = sample;
    }

    /**
     * Update the active sample while already in cinematic mode.
     */
    public void updateSample(CameraSample sample) {
        if (sample == null) {
            exitCinematicAndRestorePlayer();
            return;
        }
        if (mode != Mode.CINEMATIC) {
            enterCinematic(sample);
        } else {
            activeSample = sample;
        }
    }

    /**
     * Leave cinematic mode and restore the player's camera snapshot.
     */
    public void exitCinematicAndRestorePlayer() {
        if (mode == Mode.GAME) {
            restoreFovIfNeeded();
            resetPlayerSnapshot();
            return;
        }

        restorePlayerView();
        mode = Mode.GAME;
        activeSample = null;
        resetPlayerSnapshot();
    }

    /**
     * Leave cinematic mode without restoring the old view (used when baking the cinematic sample into gameplay).
     */
    public void exitCinematicWithoutRestore() {
        mode = Mode.GAME;
        activeSample = null;
        resetPlayerSnapshot();
    }

    @Deprecated
    public void enterGameMode() {
        exitCinematicAndRestorePlayer();
    }

    /**
     * Hard reset, e.g. when closing UI or unloading a world.
     */
    public void forceReleaseCinematic() {
        mode = Mode.GAME;
        activeSample = null;
        resetPlayerSnapshot();
    }

    public void reset() {
        forceReleaseCinematic();
    }

    // ========================= Internal helpers =========================

    private void cachePlayerView() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        // Always refresh the snapshot FOV per session so user option changes are respected.
        if (mode == Mode.GAME && client.options != null && client.options.getFov() != null) {
            originalPlayerFov = client.options.getFov().getValue().floatValue();
        }

        cachedPlayerView = CameraAccessor.sampleGameCamera(client);

        if (originalPlayerFov != null && cachedPlayerView != null) {
            cachedPlayerView = new CameraSample(
                cachedPlayerView.position(),
                cachedPlayerView.yaw(),
                cachedPlayerView.pitch(),
                cachedPlayerView.roll(),
                originalPlayerFov,
                cachedPlayerView.nearPlane(),
                cachedPlayerView.farPlane(),
                cachedPlayerView.depthOfFieldEnabled(),
                cachedPlayerView.focusDistance(),
                cachedPlayerView.aperture()
            );
        }
    }

    private void restorePlayerView() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        restoreFovIfNeeded();

        if (cachedPlayerView != null && client.player != null) {
            client.player.setPos(
                cachedPlayerView.position().x,
                cachedPlayerView.position().y,
                cachedPlayerView.position().z
            );
            client.player.setYaw(cachedPlayerView.yaw());
            client.player.setPitch(cachedPlayerView.pitch());
        }
    }

    private void restoreFovIfNeeded() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && originalPlayerFov != null && client.options != null && client.options.getFov() != null) {
            client.options.getFov().setValue(originalPlayerFov.intValue());
        }
    }

    private void resetPlayerSnapshot() {
        cachedPlayerView = null;
        originalPlayerFov = null;
    }
}
