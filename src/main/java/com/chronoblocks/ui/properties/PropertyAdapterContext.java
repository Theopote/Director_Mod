package com.chronoblocks.ui.properties;

/**
 * 属性适配器用上下文 - 供 PropertyAdapter 获取 UI 能力（如打开选择过滤器面板）
 * <p>
 * 由 UIManager 或装配层在 ViewCommands 可用时调用
 * {@code setShowFilterPanelRunnable(() -> commands.view().showPanel(PanelId.SELECTION_FILTER))}。
 * 若未设置，SelectionToolPropertyAdapter 中「编辑过滤器」按钮不显示。
 * </p>
 *
 * @author ChronoBlocks Team
 */
public final class PropertyAdapterContext {
    private static volatile Runnable showFilterPanelRunnable;

    private PropertyAdapterContext() {}

    public static void setShowFilterPanelRunnable(Runnable r) {
        showFilterPanelRunnable = r;
    }

    public static Runnable getShowFilterPanelRunnable() {
        return showFilterPanelRunnable;
    }
}
