package com.director.ui.coordinator;

import com.director.ui.commands.UICommandFacade;
import com.director.ui.di.IPanelRegistry;
import com.director.ui.panels.IPanel;
import com.director.ui.aware.ICommandsAware;
import com.director.ui.aware.IPanelRegistryAware;
import com.director.ui.aware.IPlaybackModelProvider;
import com.director.ui.aware.IUIManagerAware;
import com.director.ui.DirectorUIManager;
import com.director.ui.imgui.ImGuiStateChecker;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 面板协调器 - 负责面板的注册、依赖注入、渲染与显示/隐藏。
 * <p>
 * 从 DirectorUIManager 拆出的职责：
 * <ul>
 *   <li>Panel 实例持有（通过 IPanelRegistry）</li>
 *   <li>Panel 注入：PanelRegistry、UIManager、UICommandFacade</li>
 *   <li>initializeAll / setupReferences</li>
 *   <li>renderAll（MenuBar 先行，再渲染其余面板）</li>
 *   <li>showPanel / hidePanel / togglePanel / showAllPanels / hideAllPanels</li>
 * </ul>
 *
 * @author Director Team
 */
public final class UIPanelManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(UIPanelManager.class);

    /** 渲染分组：菜单栏先渲染，UIPanelManager 不依赖具体类型，只认此常量。 */
    private static final String RENDER_GROUP_MENU_BAR = "menu_bar";

    private final IPanelRegistry panelRegistry;
    private final DirectorUIManager uiManager;
    private final Supplier<UICommandFacade> commandsGetter;
    private final Map<String, Integer> panelRenderFrameGuard = new ConcurrentHashMap<>();

    public UIPanelManager(
            IPanelRegistry panelRegistry,
            DirectorUIManager uiManager,
            Supplier<UICommandFacade> commandsGetter) {
        this.panelRegistry = Objects.requireNonNull(panelRegistry, "panelRegistry");
        this.uiManager = Objects.requireNonNull(uiManager, "uiManager");
        this.commandsGetter = Objects.requireNonNull(commandsGetter, "commandsGetter");
    }

    public IPanelRegistry getRegistry() {
        return panelRegistry;
    }

    /**
     * 是否具备最少渲染条件（存在 menu_bar 分组面板）。
     * 用于 Shell 层「只调用不决定」：由 PanelManager 判定，UIManager 仅据此选择 render 或 renderEmptyState。
     */
    public boolean hasMinimumPanels() {
        if (panelRegistry.isEmpty()) return false;
        return panelRegistry.getAllPanels().stream()
                .filter(Objects::nonNull)
                .anyMatch(p -> RENDER_GROUP_MENU_BAR.equals(p.getRenderGroup()));
    }

    /**
     * 渲染空状态（缺少关键面板时）。决策与实现均在本层，UIManager 不包含此逻辑。
     */
    public void renderEmptyState() {
        try {
            LOGGER.debug("[UIPanelManager] 渲染空状态UI提示");
            if (ImGui.begin("Director", ImGuiWindowFlags.AlwaysAutoResize)) {
                ImGui.textColored(0.8f, 0.2f, 0.2f, 1.0f, "Director UI 初始化失败");
                ImGui.separator();
                ImGui.text("面板状态: 无可用面板");
                ImGui.text("请检查面板配置和初始化");
                ImGui.separator();
                ImGui.text("如需详细调试信息，请查看控制台日志");
            }
            ImGui.end();
        } catch (Exception e) {
            LOGGER.error("[UIPanelManager] 渲染空状态失败", e);
        }
    }

    /**
     * 设置面板依赖引用（通过接口注入，不依赖具体 Panel 类型）。
     * PanelRegistry、UIManager、UICommandFacade、PlaybackOrchestrator 绑定。
     */
    public void setupReferences() {
        LOGGER.debug("[UIPanelManager] 为所有符合条件的面板设置依赖引用...");

        int registryAwareCount = 0;
        int commandFacadeCount = 0;
        UICommandFacade commands = commandsGetter.get();

        for (IPanel panel : panelRegistry.getAllPanels()) {
            if (panel == null) continue;

            if (panel instanceof IPanelRegistryAware) {
                try {
                    ((IPanelRegistryAware) panel).setPanelRegistry(panelRegistry);
                    registryAwareCount++;
                    LOGGER.debug("[UIPanelManager] 为面板 '{}' 注入了 PanelRegistry", panel.getPanelName());
                } catch (Exception e) {
                    LOGGER.error("[UIPanelManager] 为面板 '{}' 注入 PanelRegistry 失败", panel.getPanelName(), e);
                }
            }

            if (panel instanceof ICommandsAware) {
                try {
                    ((ICommandsAware) panel).setCommands(commands);
                    commandFacadeCount++;
                    LOGGER.debug("[UIPanelManager] 为面板 '{}' 注入了 UICommandFacade", panel.getPanelName());
                } catch (Exception e) {
                    LOGGER.error("[UIPanelManager] 为面板 '{}' 注入 UICommandFacade 失败", panel.getPanelName(), e);
                }
            }

            if (panel instanceof IPlaybackModelProvider) {
                try {
                    var model = ((IPlaybackModelProvider) panel).getModel();
                    if (model != null) {
                        com.director.ui.timeline.playback.PlaybackOrchestrator.get().bindModel(model);
                        LOGGER.debug("[UIPanelManager] 为面板 '{}' 绑定了 PlaybackOrchestrator", panel.getPanelName());
                    }
                } catch (Exception e) {
                    LOGGER.error("[UIPanelManager] 为面板 '{}' 绑定 PlaybackOrchestrator 失败", panel.getPanelName(), e);
                }
            }

            if (panel instanceof IUIManagerAware) {
                try {
                    ((IUIManagerAware) panel).setUIManager(uiManager);
                    LOGGER.debug("[UIPanelManager] 为面板 '{}' 注入了 UIManager", panel.getPanelName());
                } catch (Exception e) {
                    LOGGER.error("[UIPanelManager] 为面板 '{}' 注入 UIManager 失败", panel.getPanelName(), e);
                }
            }
        }

        LOGGER.debug("[UIPanelManager] 面板依赖引用设置完成 - PanelRegistry: {}, UICommandFacade: {}",
                registryAwareCount, commandFacadeCount);
    }

    /**
     * 初始化所有面板。
     */
    public void initializeAll() {
        LOGGER.debug("[UIPanelManager] 开始初始化所有面板");

        int successCount = 0;
        int failureCount = 0;

        for (IPanel panel : panelRegistry.getAllPanels()) {
            try {
                if (panel != null) {
                    LOGGER.debug("[UIPanelManager] 初始化面板: {}", panel.getPanelName());
                    panel.initialize();
                    successCount++;
                }
            } catch (Exception e) {
                failureCount++;
                LOGGER.error("[UIPanelManager] 初始化面板 {} 失败", panel.getPanelName(), e);
            }
        }

        LOGGER.info("[UIPanelManager] 面板初始化完成 - 成功: {}, 失败: {}", successCount, failureCount);
        if (failureCount > 0) {
            LOGGER.warn("[UIPanelManager] 有 {} 个面板初始化失败，可能影响UI功能", failureCount);
        }
    }

    /**
     * 安全渲染单个面板（try-catch + 记录错误）。
     */
    public void renderPanelSafe(IPanel panel) {
        try {
            final int frameCount = ImGui.getFrameCount();
            final String frameKey = panel.getWindowName();
            final Integer lastRenderedFrame = panelRenderFrameGuard.put(frameKey, frameCount);
            if (lastRenderedFrame != null && lastRenderedFrame == frameCount) {
                return;
            }
            panel.render();
        } catch (Exception e) {
            LOGGER.error("[UIPanelManager] 面板 {} 渲染失败", panel.getPanelName(), e);
            ImGuiStateChecker.recordError("UIPanelManager", panel.getPanelName(), e.getMessage());
        }
    }

    /**
     * 渲染「menu_bar」分组的面板（按 getRenderGroup()，不依赖具体类型）。
     */
    public void renderMenuBar() {
        panelRegistry.getAllPanels().stream()
                .filter(Objects::nonNull)
                .filter(panel -> RENDER_GROUP_MENU_BAR.equals(panel.getRenderGroup()))
                .filter(panel -> panel.isVisible() || (panel.isHidden() && panel.shouldReserveDockSpaceWhenHidden()))
                .forEach(this::renderPanelSafe);
    }

    /**
     * 渲染除 menu_bar 外的所有应渲染面板。
     */
    public void renderPanels() {
        panelRegistry.getAllPanels().stream()
                .filter(Objects::nonNull)
                .filter(panel -> !RENDER_GROUP_MENU_BAR.equals(panel.getRenderGroup()))
                .filter(panel -> panel.isVisible() || (panel.isHidden() && panel.shouldReserveDockSpaceWhenHidden()))
                .forEach(this::renderPanelSafe);
    }

    /**
     * 渲染所有面板：先菜单栏，再其余。
     */
    public void renderAll() {
        renderMenuBar();
        renderPanels();
    }

    // ---------- show / hide / toggle ----------

    public void showPanel(String panelId) {
        IPanel panel = findPanelById(panelId);
        if (panel != null) {
            panel.setVisible(true);
            LOGGER.debug("[UIPanelManager] 显示面板: {}", panelId);
        } else {
            LOGGER.warn("[UIPanelManager] 未找到面板: {}", panelId);
        }
    }

    public void hidePanel(String panelId) {
        IPanel panel = findPanelById(panelId);
        if (panel != null) {
            panel.setVisible(false);
            LOGGER.debug("[UIPanelManager] 隐藏面板: {}", panelId);
        } else {
            LOGGER.warn("[UIPanelManager] 未找到面板: {}", panelId);
        }
    }

    public void togglePanel(String panelId) {
        IPanel panel = findPanelById(panelId);
        if (panel != null) {
            panel.setVisible(!panel.isVisible());
            LOGGER.debug("[UIPanelManager] 切换面板可见性: {} -> {}", panelId, panel.isVisible());
        } else {
            LOGGER.warn("[UIPanelManager] 未找到面板: {}", panelId);
        }
    }

    public void showAllPanels() {
        int count = 0;
        for (IPanel panel : panelRegistry.getAllPanels()) {
            panel.setVisible(true);
            count++;
        }
        LOGGER.debug("[UIPanelManager] 显示所有面板: {} 个", count);
    }

    public void hideAllPanels() {
        int count = 0;
        for (IPanel panel : panelRegistry.getAllPanels()) {
            if (panel.isHideableInView()) {
                panel.setVisible(false);
                count++;
            }
        }
        LOGGER.debug("[UIPanelManager] 隐藏所有面板: {} 个", count);
    }

    /**
     * 根据 PanelId 或面板名称解析并查找面板。
     */
    private IPanel findPanelById(String panelId) {
        String actualName = switch (panelId) {
            case com.director.ui.PanelId.MENU_BAR -> "MenuBar";
            case com.director.ui.PanelId.STATUS_BAR -> "StatusBar";
            case com.director.ui.PanelId.TOOL_PROPERTIES -> "ToolProperties";
            case com.director.ui.PanelId.SELECTION_FILTER -> "SelectionFilter";
            case com.director.ui.PanelId.SELECTION_HISTORY -> "SelectionHistory";
            case com.director.ui.PanelId.RENDER_SETTINGS -> "RenderSettings";
            default -> panelId;
        };

        for (IPanel panel : panelRegistry.getAllPanels()) {
            if (actualName.equals(panel.getPanelName())) {
                return panel;
            }
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[UIPanelManager] 未找到面板: {} (映射后: {})", panelId, actualName);
        }
        return null;
    }

    /**
     * 根据显示名称解析并查找面板（用于 Layout 等）。
     */
    public IPanel findPanelByName(String panelName) {
        String actualName = switch (panelName) {
            case "Timeline" -> "Timeline";
            case "Tool Properties" -> "ToolProperties";
            case "Container" -> "Container";
            case "RenderSettings" -> "RenderSettings";
            case "Toolbar" -> "Toolbar";
            case "SelectionFiler" -> "SelectionFilter";
            case "Properties" -> "Properties";
            case "SelectionHistory" -> "SelectionHistory";
            default -> panelName;
        };

        for (IPanel panel : panelRegistry.getAllPanels()) {
            if (actualName.equals(panel.getPanelName())) {
                return panel;
            }
        }
        return null;
    }
}
