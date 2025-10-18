package com.chronoblocks.ui.layout;

import imgui.ImGui;
import imgui.flag.ImGuiDir;
import imgui.type.ImInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DockSpace布局构建器
 * <p>
 * 负责创建和管理ChronoBlocks的默认DockSpace布局。
 * 提供专业建模软件风格的布局：
 * <ul>
 *   <li>左侧：容器面板 (15%宽度)</li>
 *   <li>右侧：属性面板 (20%宽度)</li>
 *   <li>顶部：工具栏面板 (10%高度)</li>
 *   <li>底部：时间线面板 (20%高度)</li>
 *   <li>中央：Minecraft游戏视图 (保持透明)</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class DockSpaceLayoutBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockSpaceLayoutBuilder.class);
    
    // 面板窗口名称常量（需要匹配面板getWindowName()返回的窗口名称）
    public static final String CONTAINER_PANEL_WINDOW = "Container###ContainerPanel";
    public static final String PROPERTIES_PANEL_WINDOW = "Properties###PropertiesPanel";
    public static final String TOOLBAR_PANEL_WINDOW = "Toolbar###ToolbarPanel";
    public static final String TIMELINE_PANEL_WINDOW = "Timeline###TimelinePanel";
    public static final String CENTRAL_AREA_WINDOW = "MinecraftCentralArea";
    
    // 布局比例常量
    private static final float LEFT_PANEL_RATIO = 0.15f;    // 左侧面板占15%宽度
    private static final float RIGHT_PANEL_RATIO = 0.20f;   // 右侧面板占20%宽度
    private static final float TOP_PANEL_RATIO = 0.10f;     // 顶部面板占10%高度
    private static final float BOTTOM_PANEL_RATIO = 0.20f;  // 底部面板占20%高度
    
    // 标记是否已经初始化过布局
    private static boolean layoutInitialized = false;
    
    /**
     * 构建默认的DockSpace布局
     * 
     * @param dockspaceId DockSpace的ID
     */
    public static void buildDefaultLayout(int dockspaceId) {
        // 检查是否需要初始化布局
        if (layoutInitialized) {
            LOGGER.debug("[LayoutBuilder] 布局已初始化，跳过");
            return;
        }
        
        try {
            LOGGER.info("[LayoutBuilder] 开始构建默认DockSpace布局...");
            
            // 使用ImGui的内部API来构建布局
            imgui.internal.ImGui.dockBuilderRemoveNode(dockspaceId);
            imgui.internal.ImGui.dockBuilderAddNode(dockspaceId, imgui.internal.flag.ImGuiDockNodeFlags.DockSpace);
            imgui.internal.ImGui.dockBuilderSetNodeSize(dockspaceId, 
                ImGui.getMainViewport().getWorkSizeX(), 
                ImGui.getMainViewport().getWorkSizeY());
            
            // 分割DockSpace创建布局节点
            // 使用ImInt来接收输出的dock节点ID
            ImInt dockIdMainRef = new ImInt(dockspaceId);
            
            // ===== 关键：先分割顶部和底部，确保工具栏和时间线通长 =====
            
            // 1. 首先从主节点顶部分割出工具栏 (10%高度，横跨整个宽度)
            ImInt dockIdTop = new ImInt();
            imgui.internal.ImGui.dockBuilderSplitNode(
                dockIdMainRef.get(), ImGuiDir.Up, TOP_PANEL_RATIO, dockIdTop, dockIdMainRef
            );
            
            // 2. 从剩余节点底部分割出时间线面板 (调整比例，横跨整个宽度)
            float bottomAdjustedRatio = BOTTOM_PANEL_RATIO / (1.0f - TOP_PANEL_RATIO);
            ImInt dockIdBottom = new ImInt();
            imgui.internal.ImGui.dockBuilderSplitNode(
                dockIdMainRef.get(), ImGuiDir.Down, bottomAdjustedRatio, dockIdBottom, dockIdMainRef
            );
            
            // ===== 现在在中间区域分割左右面板 =====
            
            // 3. 从中间节点左侧分割出容器面板 (15%宽度)
            ImInt dockIdLeft = new ImInt();
            imgui.internal.ImGui.dockBuilderSplitNode(
                dockIdMainRef.get(), ImGuiDir.Left, LEFT_PANEL_RATIO, dockIdLeft, dockIdMainRef
            );
            
            // 4. 从中间剩余节点右侧分割出属性面板 (调整比例以获得20%的原始宽度)
            float rightAdjustedRatio = RIGHT_PANEL_RATIO / (1.0f - LEFT_PANEL_RATIO);
            ImInt dockIdRight = new ImInt();
            imgui.internal.ImGui.dockBuilderSplitNode(
                dockIdMainRef.get(), ImGuiDir.Right, rightAdjustedRatio, dockIdRight, dockIdMainRef
            );
            
            // 5. 剩余的就是中央游戏视图区域 (dockIdMainRef)
            
            // 将窗口停靠到指定节点
            imgui.internal.ImGui.dockBuilderDockWindow(CONTAINER_PANEL_WINDOW, dockIdLeft.get());
            imgui.internal.ImGui.dockBuilderDockWindow(PROPERTIES_PANEL_WINDOW, dockIdRight.get());
            imgui.internal.ImGui.dockBuilderDockWindow(TOOLBAR_PANEL_WINDOW, dockIdTop.get());
            imgui.internal.ImGui.dockBuilderDockWindow(TIMELINE_PANEL_WINDOW, dockIdBottom.get());
            imgui.internal.ImGui.dockBuilderDockWindow(CENTRAL_AREA_WINDOW, dockIdMainRef.get());
            
            // 完成布局构建
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

    /**
     * 应用布局（如果需要）
     * <p>
     * 检查是否需要应用默认布局，如果需要则构建
     * 
     * @param dockspaceId DockSpace的ID
     * @param hasCustomLayout 是否已有自定义布局
     */
    public static void applyLayoutIfNeeded(int dockspaceId, boolean hasCustomLayout) {
        // 总是在第一次渲染时构建默认布局（忽略hasCustomLayout）
        // 这确保面板能够正确停靠
        if (!layoutInitialized) {
            LOGGER.info("[LayoutBuilder] 应用默认DockSpace布局（首次打开）");
            buildDefaultLayout(dockspaceId);
        }
    }
}

