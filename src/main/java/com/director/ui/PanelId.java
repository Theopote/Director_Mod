package com.director.ui;

/**
 * 面板标识符常量类 - 统一管理所有面板的 ID
 * 
 * <p>职责：
 * <ul>
 *   <li>提供类型安全的面板 ID 常量</li>
 *   <li>避免字符串字面量散落在代码中</li>
 *   <li>便于维护和重构</li>
 * </ul>
 * 
 * <p>使用示例：
 * <pre>{@code
 * commands.view().togglePanel(PanelId.TIMELINE);
 * commands.view().showPanel(PanelId.INSPECTOR);
 * }</pre>
 * 
 * @author Director Team
 * @version 1.0
 */
public final class PanelId {
    
    // 私有构造函数，防止实例化
    private PanelId() {
        throw new UnsupportedOperationException("PanelId 是常量类，不能实例化");
    }
    
    // ==================== 核心面板 ====================
    
    /** 时间线面板 */
    public static final String TIMELINE = "Timeline";
    
    /** 工具栏面板 */
    public static final String TOOLBAR = "Toolbar";
    
    /** 菜单栏面板 */
    public static final String MENU_BAR = "MenuBar";
    
    /** 状态栏面板 */
    public static final String STATUS_BAR = "StatusBar";
    
    // ==================== 属性与检查器面板 ====================
    
    /** 属性面板 */
    public static final String PROPERTIES = "Properties";
    
    /** 检查器面板 */
    public static final String INSPECTOR = "Inspector";
    
    /** 工具属性面板 */
    public static final String TOOL_PROPERTIES = "ToolProperties";
    
    // ==================== 容器与选择面板 ====================
    
    /** 容器面板 */
    public static final String CONTAINER = "Container";

    /** 演员面板 */
    public static final String ACTORS = "Actors";
    
    /** 选择过滤器面板 */
    public static final String SELECTION_FILTER = "SelectionFilter";
    
    /** 选择历史面板 */
    public static final String SELECTION_HISTORY = "SelectionHistory";
    
    // ==================== 渲染与设置面板 ====================
    
    /** 渲染设置面板 */
    public static final String RENDER_SETTINGS = "RenderSettings";
    
    // ==================== 工具方法 ====================
    
    /**
     * 根据面板实例获取对应的 PanelId
     * @param panel 面板实例
     * @return 对应的 PanelId 常量，如果未找到则返回 null
     */
    public static String fromPanel(com.director.ui.panels.IPanel panel) {
        if (panel == null) {
            return null;
        }
        
        String panelName = panel.getPanelName();
        if (panelName == null) {
            return null;
        }
        
        // 根据面板名称映射到 PanelId 常量
        return switch (panelName) {
            case "Timeline" -> TIMELINE;
            case "Toolbar" -> TOOLBAR;
            case "MenuBar" -> MENU_BAR;
            case "StatusBar" -> STATUS_BAR;
            case "Properties" -> PROPERTIES;
            case "Inspector" -> INSPECTOR;
            case "ToolProperties" -> TOOL_PROPERTIES;
            case "Container" -> CONTAINER;
            case "Actors" -> ACTORS;
            case "SelectionFilter" -> SELECTION_FILTER;
            case "SelectionHistory" -> SELECTION_HISTORY;
            case "RenderSettings" -> RENDER_SETTINGS;
            default -> null;
        };
    }
}
