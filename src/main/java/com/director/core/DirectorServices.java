package com.director.core;

import com.director.core.container.ContainerManager;
import com.director.core.container.visibility.ContainerVisibilityService;
import com.director.core.selection.SelectionManager;
import com.director.ui.imgui.ImGuiRenderer;
import com.director.ui.properties.PropertyContextService;
import com.director.timeline.container.ContainerTimelineService;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod 级服务注册表（依赖注入基础设施）。
 * <p>
 * 在 {@link com.director.DirectorMod} 启动时显式初始化，新代码应通过本类获取依赖；
 * 遗留的 {@code get()/getInstance()} 已废弃；业务代码请用本类实例方法（如 {@link #selection()}）。
 * 启动装配使用各服务的 {@code requireInitialized()}（{@link ApiStatus.Internal}，仅限本类）。
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
 *   <li>{@link #imgui()} — 客户端 {@link #initializeClient()} 后可用；{@code init()} 在 {@code CLIENT_STARTED} 后调用</li>
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
    /** ImGui 渲染器实例；{@link ImGuiRenderer#init()} 在 CLIENT_STARTED 后执行 */
    private volatile ImGuiRenderer imgui;

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

            instance = new DirectorServices(
                    ContainerManager.requireInitialized(),
                    SelectionManager.requireInitialized(),
                    com.director.ui.selection.SelectionManager.requireInitialized(),
                    ContainerTimelineService.requireInitialized());

            PropertyContextService.wireUiSelection(instance.uiSelection());

            LOGGER.info("[DirectorServices] Services ready (containers, blockSelection, uiSelection, containerTimeline)");
        }
    }

    /**
     * 初始化客户端专属服务（在 {@link com.director.client.DirectorClient} 中调用，幂等）。
     */
    public static void initializeClient() {
        get();
        synchronized (DirectorServices.class) {
            if (get().imgui != null) {
                return;
            }
            ImGuiRenderer.initialize();
            get().imgui = ImGuiRenderer.requireInitialized();
            LOGGER.info("[DirectorServices] Client services ready (imgui)");
        }
    }

    /**
     * 由 {@link com.director.core.container.visibility.ContainerServices#init} 在服务器启动时调用。
     */
    public static void bindVisibilityService(ContainerVisibilityService service) {
        get().visibilityService = service;
    }

    /**
     * 是否已完成核心服务初始化。
     * <p>用于启动早期（服务装配阶段）避免调用 {@link #get()} 触发异常/刷屏日志。
     */
    public static boolean isInitialized() {
        return instance != null;
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
     * ImGui 渲染器（仅客户端）。实例在 {@link #initializeClient()} 时创建；
     * OpenGL 后端须调用 {@link ImGuiRenderer#init()}（通常在 {@code CLIENT_STARTED}）。
     *
     * @throws IllegalStateException 若 {@link #initializeClient()} 尚未执行
     */
    public ImGuiRenderer imgui() {
        ImGuiRenderer renderer = imgui;
        if (renderer != null) {
            return renderer;
        }
        throw new IllegalStateException(
                "[DirectorServices] ImGui not initialized — call DirectorServices.initializeClient()");
    }

    /**
     * @return ImGui 实例，若客户端服务未初始化则为 {@code null}
     */
    public @Nullable ImGuiRenderer imguiOrNull() {
        return imgui;
    }
}
