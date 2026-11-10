package com.director.ui.layout;

import com.director.ui.PanelWindows;
import com.director.ui.panels.ToolbarPanel;
import imgui.ImGui;
import imgui.flag.ImGuiDir;
import imgui.type.ImInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DockSpace布局构建器
 * <p>
 * 负责创建和管理Director的默认DockSpace布局。
 * 布局说明：
 * <ul>
 *   <li>左侧：容器面板（含容器属性），宽度与右侧属性列相同</li>
 *   <li>右侧：上方为工具栏（与属性同宽，初始高度按内容计算），下方为属性面板</li>
 *   <li>底部：时间线面板 (20% 高度)</li>
 *   <li>中央：Minecraft 游戏视图 (保持透明)</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public class DockSpaceLayoutBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockSpaceLayoutBuilder.class);

    // 面板窗口名称（与各面板 getWindowName() / PanelWindows 一致）
    public static final String CENTRAL_AREA_WINDOW = "MinecraftCentralArea";

    // 布局比例常量
    /** 左右侧栏相同宽度（左侧容器、右侧属性列各占 20%） */
    private static final float SIDE_PANEL_RATIO = 0.20f;
    /** 底部时间线占 20% 高度 */
    private static final float BOTTOM_PANEL_RATIO = 0.20f;
    private static final float MIN_PROPERTIES_HEIGHT = 96f;

    // 标记是否已经初始化过布局
    private static boolean layoutInitialized = false;

    /**
     * 构建默认的DockSpace布局
     *
     * @param dockspaceId DockSpace的ID
     * @param toolbar 工具栏实例，用于计算默认停靠高度
     */
    public static void buildDefaultLayout(int dockspaceId, ToolbarPanel toolbar) {
        if (layoutInitialized) {
            LOGGER.debug("[LayoutBuilder] 布局已初始化，跳过");
            return;
        }

        try {
            LOGGER.info("[LayoutBuilder] 开始构建默认DockSpace布局...");

            imgui.internal.ImGui.dockBuilderRemoveNode(dockspaceId);
            imgui.internal.ImGui.dockBuilderAddNode(dockspaceId, imgui.internal.flag.ImGuiDockNodeFlags.DockSpace);
            imgui.internal.ImGui.dockBuilderSetNodeSize(dockspaceId,
                ImGui.getMainViewport().getWorkSizeX(),
                ImGui.getMainViewport().getWorkSizeY());

            ImInt dockIdMainRef = new ImInt(dockspaceId);

            // 1. 底部分割出时间线 (20% 高度)
            ImInt dockIdBottom = new ImInt();
            imgui.internal.ImGui.dockBuilderSplitNode(
                dockIdMainRef.get(), ImGuiDir.Down, BOTTOM_PANEL_RATIO, dockIdBottom, dockIdMainRef
            );

            // 2. 在剩余区域左侧分割出容器列 (与右侧同宽：20%)
            ImInt dockIdLeft = new ImInt();
            imgui.internal.ImGui.dockBuilderSplitNode(
                dockIdMainRef.get(), ImGuiDir.Left, SIDE_PANEL_RATIO, dockIdLeft, dockIdMainRef
            );

            // 3. 在中间剩余区域右侧分割出“属性列”（整列：上工具栏 + 下属性），宽度 20%
            ImInt dockIdRightCol = new ImInt();
            imgui.internal.ImGui.dockBuilderSplitNode(
                dockIdMainRef.get(), ImGuiDir.Right, SIDE_PANEL_RATIO, dockIdRightCol, dockIdMainRef
            );

            // Size the default split once. Saved layouts and manual splitter adjustments are owned by ImGui.
            var rightColumn = imgui.internal.ImGui.dockBuilderGetNode(dockIdRightCol.get());
            float rightHeight = Math.max(1f, rightColumn.getSizeY());
            float preferredHeight = toolbar == null ? ImGui.getFrameHeight()
                    : toolbar.preferredDockHeight(rightColumn.getSizeX());
            float maxToolbarHeight = Math.max(rightHeight * 0.5f, rightHeight - MIN_PROPERTIES_HEIGHT);
            // DockBuilder subtracts its splitter before applying the ratio and rounds to whole pixels.
            float toolbarRatio = Math.min((float) Math.ceil(preferredHeight) + 2f, maxToolbarHeight) / rightHeight;
            ImInt dockIdToolbar = new ImInt();
            ImInt dockIdProps = new ImInt();
            imgui.internal.ImGui.dockBuilderSplitNode(
                dockIdRightCol.get(), ImGuiDir.Up, toolbarRatio, dockIdToolbar, dockIdProps
            );

            // 5. 将窗口停靠到对应节点
            imgui.internal.ImGui.dockBuilderDockWindow(PanelWindows.container(), dockIdLeft.get());
            imgui.internal.ImGui.dockBuilderDockWindow(PanelWindows.actors(), dockIdLeft.get());
            imgui.internal.ImGui.dockBuilderDockWindow(PanelWindows.toolbar(), dockIdToolbar.get());
            imgui.internal.ImGui.dockBuilderDockWindow(PanelWindows.properties(), dockIdProps.get());
            imgui.internal.ImGui.dockBuilderDockWindow(PanelWindows.timeline(), dockIdBottom.get());
            imgui.internal.ImGui.dockBuilderDockWindow(CENTRAL_AREA_WINDOW, dockIdMainRef.get());

            imgui.internal.ImGui.dockBuilderFinish(dockspaceId);

            layoutInitialized = true;
            LOGGER.info("[LayoutBuilder] 默认布局构建完成");
        } catch (Exception e) {
            LOGGER.error("[LayoutBuilder] 构建布局时发生错误: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 重置布局初始化状态
     * <p>
     * 调用此方法后，下次调用buildDefaultLayout会重新初始化布局
     */
    public static void resetLayoutState() {
        layoutInitialized = false;
        LOGGER.info("[LayoutBuilder] 布局状态已重置");
    }

}

