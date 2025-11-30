package com.chronoblocks.ui.timeline.playback.camera;

import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineSelectable;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.camera.CameraKeyframe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime controller for the cinematic camera track.
 *
 * <p>Responsibilities:
 * <ul>
 *     <li>Sample camera keyframes based on the current timeline time</li>
 *     <li>Lock player input while cinematic playback is active</li>
 *     <li>Push sampled pose to {@link CameraRuntime} for rendering</li>
 * </ul>
 */
public final class TimelineCameraController {
    private static final TimelineCameraController INSTANCE = new TimelineCameraController();

    private final CameraTrackSampler sampler = new CameraTrackSampler();
    private TimelineModel timelineModel;

    private TimelineCameraController() {
    }

    public static TimelineCameraController getInstance() {
        return INSTANCE;
    }

    public void setTimelineModel(TimelineModel model) {
        this.timelineModel = model;
    }

    /**
     * Advance the camera controller with the latest timeline time.
     * Should be called once per frame from UI/update loop.
     */
    public void update() {
        CameraRuntime runtime = CameraRuntime.getInstance();
        if (timelineModel == null) {
            runtime.exitToGame();
            return;
        }

        List<CameraKeyframe> keyframes = collectCameraKeyframes();
        sampler.setKeyframes(keyframes);

        boolean shouldUseCinematic = timelineModel.isPlaying() && !sampler.isEmpty();
        if (!shouldUseCinematic) {
            runtime.exitToGame();
            return;
        }

        // 使用新的 API
        runtime.enterPlayingMode(true);
        CameraSample sample = sampler.sample(timelineModel.getCurrentTime()).orElse(null);
        runtime.applySample(sample);
        
        if (runtime.isCinematicActive()) {
            lockPlayerInput();
        }
    }

    /**
     * 当用户点击某个摄像机关键帧，希望预览画面时，可以调用这个方法。
     *
     * @param timeSeconds 该关键帧对应的时间
     */
    public void previewAt(float timeSeconds) {
        List<CameraKeyframe> keyframes = collectCameraKeyframes();
        sampler.setKeyframes(keyframes);

        CameraRuntime runtime = CameraRuntime.getInstance();
        boolean hasTrack = !keyframes.isEmpty();
        
        if (!hasTrack) {
            runtime.enterPreviewMode(false);
            return;
        }

        CameraSample sample = sampler.sample(timeSeconds).orElse(null);
        if (sample == null) {
            runtime.enterPreviewMode(false);
            return;
        }

        runtime.enterPreviewMode(true);
        runtime.applySample(sample);
    }

    /**
     * Populate a keyframe using the current game (player) camera pose.
     */
    public void captureFromGameCamera(CameraKeyframe target) {
        if (target == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        var camera = client.gameRenderer != null ? client.gameRenderer.getCamera() : null;
        if (camera == null) {
            return;
        }

        Vec3d pos = camera.getPos();
        target.setPosition(new Vector3f((float) pos.x, (float) pos.y, (float) pos.z));
        target.setRotation(new Vector3f(camera.getYaw(), camera.getPitch(), 0.0f));

        if (client.options != null && client.options.getFov() != null) {
            double fovValue = client.options.getFov().getValue();
            target.setFov((float) fovValue);
        }
    }

    private void lockPlayerInput() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.input != null) {
            var input = client.player.input;
            input.movementForward = 0.0f;
            input.movementSideways = 0.0f;
            // Note: jumping and sneaking are handled through key bindings
        }

        if (client.options != null) {
            clearKey(client.options.forwardKey);
            clearKey(client.options.backKey);
            clearKey(client.options.leftKey);
            clearKey(client.options.rightKey);
            clearKey(client.options.jumpKey);
            clearKey(client.options.sneakKey);
            clearKey(client.options.sprintKey);
        }
    }

    private void clearKey(KeyBinding key) {
        if (key != null) {
            key.setPressed(false);
        }
    }

    private List<CameraKeyframe> collectCameraKeyframes() {
        List<CameraKeyframe> result = new ArrayList<>();
        if (timelineModel == null) {
            return result;
        }
        for (TimelineTrack track : timelineModel.getAllTracks()) {
            if (track == null) {
                continue;
            }
            TrackType type = track.getType();
            if (type == null || !type.supportsCameraPath() || track.isLocked() || !track.isVisible()) {
                continue;
            }
            for (TimelineSelectable selectable : track.getAutomationKeyframes()) {
                if (selectable instanceof CameraKeyframe ck) {
                    result.add(ck);
                }
            }
        }
        return result;
    }
}
