package com.director.core;

import com.director.core.container.ContainerManager;
import com.director.core.selection.SelectionManager;
import com.director.ui.timeline.container.ContainerTimelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod 级核心服务注册表（阶段 1 依赖注入基础设施）。
 * <p>
 * 在 {@link com.director.DirectorMod} 启动时显式初始化，新代码应通过本类获取依赖；
 * 遗留的 {@code Xxx.getInstance()} / {@code SelectionManager.get()} 仍可用，但已标记为废弃。
 */
public final class DirectorServices {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectorServices.class);

    private static volatile DirectorServices instance;

    private final ContainerManager containers;
    private final SelectionManager selection;
    private final ContainerTimelineService containerTimeline;

    private DirectorServices(
            ContainerManager containers,
            SelectionManager selection,
            ContainerTimelineService containerTimeline) {
        this.containers = containers;
        this.selection = selection;
        this.containerTimeline = containerTimeline;
    }

    /**
     * 在模组启动时初始化所有已注册的核心服务（幂等）。
     */
    public static void initialize() {
        if (instance != null) {
            return;
        }
        synchronized (DirectorServices.class) {
            if (instance != null) {
                return;
            }
            ContainerTimelineService.initialize();
            SelectionManager.initialize();
            ContainerManager.initialize();

            instance = new DirectorServices(
                    ContainerManager.getInstance(),
                    SelectionManager.getInstance(),
                    ContainerTimelineService.getInstance());

            LOGGER.info("[DirectorServices] Core services ready (containers, selection, containerTimeline)");
        }
    }

    /**
     * @throws IllegalStateException 若 {@link #initialize()} 尚未执行
     */
    public static DirectorServices get() {
        DirectorServices current = instance;
        if (current != null) {
            return current;
        }
        throw new IllegalStateException(
                "[DirectorServices] Not initialized — call DirectorServices.initialize() during mod startup");
    }

    public ContainerManager containers() {
        return containers;
    }

    public SelectionManager selection() {
        return selection;
    }

    public ContainerTimelineService containerTimeline() {
        return containerTimeline;
    }
}
