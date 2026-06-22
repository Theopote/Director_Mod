package com.director.core;

import com.director.core.container.ContainerManager;
import com.director.core.selection.SelectionManager;
import com.director.ui.properties.PropertyContextService;
import com.director.ui.timeline.container.ContainerTimelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod 级服务注册表（依赖注入基础设施）。
 * <p>
 * 在 {@link com.director.DirectorMod} 启动时显式初始化，新代码应通过本类获取依赖；
 * 遗留的 {@code Xxx.getInstance()} / {@code SelectionManager.get()} 仍可用，但已标记为废弃。
 *
 * <h3>选择服务区分</h3>
 * <ul>
 *   <li>{@link #selection()} — 方块/世界选区（{@code com.director.core.selection.SelectionManager}）</li>
 *   <li>{@link #uiSelection()} — 编辑器选择（轨道/片段/属性，{@code com.director.ui.selection.SelectionManager}）</li>
 * </ul>
 */
public final class DirectorServices {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectorServices.class);

    private static volatile DirectorServices instance;

    private final ContainerManager containers;
    private final SelectionManager selection;
    private final com.director.ui.selection.SelectionManager uiSelection;
    private final ContainerTimelineService containerTimeline;

    private DirectorServices(
            ContainerManager containers,
            SelectionManager selection,
            com.director.ui.selection.SelectionManager uiSelection,
            ContainerTimelineService containerTimeline) {
        this.containers = containers;
        this.selection = selection;
        this.uiSelection = uiSelection;
        this.containerTimeline = containerTimeline;
    }

    /**
     * 在模组启动时初始化所有已注册服务（幂等）。
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
            com.director.ui.selection.SelectionManager.initialize();
            ContainerManager.initialize();
            PropertyContextService.initialize();

            instance = new DirectorServices(
                    ContainerManager.getInstance(),
                    SelectionManager.getInstance(),
                    com.director.ui.selection.SelectionManager.getInstance(),
                    ContainerTimelineService.getInstance());

            LOGGER.info("[DirectorServices] Services ready (containers, blockSelection, uiSelection, containerTimeline)");
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

    /** 方块/世界选区管理器 */
    public SelectionManager selection() {
        return selection;
    }

    /** 编辑器选择管理器（时间线轨道、片段、属性对象） */
    public com.director.ui.selection.SelectionManager uiSelection() {
        return uiSelection;
    }

    public ContainerTimelineService containerTimeline() {
        return containerTimeline;
    }
}
