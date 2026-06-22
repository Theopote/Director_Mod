package com.director.core.animation.playback;

import com.director.core.DirectorServices;
import com.director.core.animation.container.EvaluatedPose;
import com.director.core.animation.timeline.AnimationTimelineService;
import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.core.container.ContainerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClipPlaybackRuntimeManager - 负责驱动时间线求值并缓存结果。
 * 
 * <p>v5.3: 添加可见性状态同步，将时间线的可见性求值结果应用到容器的 ContainerState。
 */
public final class ClipPlaybackRuntimeManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClipPlaybackRuntimeManager.class);
    private static final ClipPlaybackRuntimeManager INSTANCE = new ClipPlaybackRuntimeManager();

    private final Map<UUID, EvaluatedPose> evaluatedPoses = new ConcurrentHashMap<>();

    private ClipPlaybackRuntimeManager() {
    }

    public static ClipPlaybackRuntimeManager get() {
        return INSTANCE;
    }

    public void update(double timelineTimeSec) {
        AnimationTimelineService service = AnimationTimelineService.get();
        service.setCurrentTime(timelineTimeSec);

        evaluatedPoses.clear();
        List<UUID> containers = service.getControlledContainers();
        for (UUID containerId : containers) {
            EvaluatedPose pose = service.getTimelineController().evaluate(containerId);
            evaluatedPoses.put(containerId, pose);
            
            // v5.3: 同步可见性状态到容器
            syncVisibilityState(containerId, pose);
        }
        if (!containers.isEmpty()) {
            LOGGER.trace("[ClipRuntime] Evaluated {} container poses at t={}", containers.size(), timelineTimeSec);
        }
    }

    /**
     * 同步可见性状态到容器
     * 
     * <p>根据 EvaluatedPose 的 visibility 值（1.0 = 可见，0.0 = 隐藏）
     * 更新容器的 ContainerState，这会自动触发 ContainerVisibilityService。
     */
    private void syncVisibilityState(UUID containerId, EvaluatedPose pose) {
        ContainerManager manager = DirectorServices.get().containers();
        Container container = null;
        if (manager != null) {
            container = manager.getContainer(containerId);
        }
        if (container == null) {
            return;
        }
        
        // EvaluatedPose.visibility: 1.0 = 可见, 0.0 = 隐藏
        boolean shouldBeVisible = pose.visibility() >= 0.5;
        ContainerState targetState = shouldBeVisible ? ContainerState.VISIBLE : ContainerState.HIDDEN;
        
        // 只有当状态需要改变时才更新（避免不必要的状态切换）
        if (container.getState() != targetState) {
            container.setState(targetState);
            LOGGER.debug("[ClipRuntime] 容器 {} 可见性同步: {} (visibility={})", 
                container.getName(), targetState.getDisplayName(), pose.visibility());
        }
    }

}

