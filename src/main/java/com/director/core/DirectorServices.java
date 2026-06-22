package com.director.core;

import com.director.core.container.ContainerManager;
import com.director.core.container.visibility.ContainerVisibilityService;
import com.director.core.selection.SelectionManager;
import com.director.ui.input.GlobalShortcutManager;
import com.director.ui.properties.PropertyContextService;
import com.director.ui.timeline.container.ContainerTimelineService;
import org.jetbrains.annotations.Nullable;
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
 *
 * <h3>生命周期说明</h3>
 * <ul>
 *   <li>{@link #visibilityOrNull()} — 服务器 {@code SERVER_STARTED} 后可用（集成服务器/单人游戏同样适用）</li>
 *   <li>{@link #globalShortcuts()} — 客户端 {@link #initializeClient()} 后可用</li>
 * </ul>
 */
public final class DirectorServices {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectorServices.class);

    private static volatile DirectorServices instance;

    private final ContainerManager containers;
    private final SelectionManager selection;
    private final com.director.ui.selection.SelectionManager uiSelection;
    private final ContainerTimelineService containerTimeline;

    /** 服务器启动后绑定，此前为 {@code null} */
    private volatile ContainerVisibilityService visibilityService;
    /** 客户端初始化后绑定，专用服务器上为 {@code null} */
    private volatile GlobalShortcutManager globalShortcuts;

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
     * 初始化客户端专属服务（在 {@link com.director.client.DirectorClient} 中调用，幂等）。
     */
    public static void initializeClient() {
        get();
        synchronized (DirectorServices.class) {
            if (get().globalShortcuts != null) {
                return;
            }
            GlobalShortcutManager.initialize();
            get().globalShortcuts = GlobalShortcutManager.getInstance();
            LOGGER.info("[DirectorServices] Client services ready (globalShortcuts)");
        }
    }

    /**
     * 由 {@link com.director.core.container.visibility.ContainerServices#init} 在服务器启动时调用。
     */
    public static void bindVisibilityService(ContainerVisibilityService service) {
        get().visibilityService = service;
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

    /**
     * 容器方块可见性服务；服务器未启动时为 {@code null}。
     */
    public @Nullable ContainerVisibilityService visibilityOrNull() {
        return visibilityService;
    }

    /**
     * 全局快捷键管理器（仅客户端）。
     *
     * @throws IllegalStateException 若 {@link #initializeClient()} 尚未执行
     */
    public GlobalShortcutManager globalShortcuts() {
        GlobalShortcutManager shortcuts = globalShortcuts;
        if (shortcuts != null) {
            return shortcuts;
        }
        throw new IllegalStateException(
                "[DirectorServices] Client shortcuts not initialized — call DirectorServices.initializeClient()");
    }

    public @Nullable GlobalShortcutManager globalShortcutsOrNull() {
        return globalShortcuts;
    }
}
