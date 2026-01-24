package com.chronoblocks.ui.properties;

/**
 * 属性适配器用上下文 - 供 Adapter 在 {@code renderProperties} 时获取渲染辅助能力。
 *
 * <p><b>⚠️ 边界（必须遵守）：</b> AdapterContext <b>不得用于决定是否渲染</b>，仅用于 render 辅助。
 * 「是否渲染、用哪个 Adapter」由 {@link PropertyContextService} + {@link PropertyContext} 仲裁，
 * Adapter 只负责对已选定的 target 做 UI 绘制。
 *
 * <h4>✅ 合理内容（渲染辅助）</h4>
 * <ul>
 *   <li>UI scale、是否只读</li>
 *   <li>Facade / 命令引用（如打开选择过滤器面板）</li>
 *   <li>临时渲染状态、字体、主题等</li>
 * </ul>
 *
 * <h4>🚫 不应出现（否则与 PropertyContextService 职责打架）</h4>
 * <ul>
 *   <li>当前 Tool、当前 Selection</li>
 *   <li>当前 PropertyContext（TOOL/TIMELINE/WORLD）</li>
 * </ul>
 *
 * <p>通过 {@link #of(float, boolean)} 创建实例；{@link #setShowFilterPanelRunnable} 设置全局
 * 「打开选择过滤器」回调，由 of() 注入。若未设置，SelectionToolPropertyAdapter 中「编辑过滤器」按钮不显示。
 *
 * @author ChronoBlocks Team
 */
public final class PropertyAdapterContext {
    private static volatile Runnable showFilterPanelRunnable;

    private final float uiScale;
    private final boolean readOnly;
    private final Runnable showFilterPanelRunnableRef;

    private PropertyAdapterContext(float uiScale, boolean readOnly, Runnable showFilterPanelRunnableRef) {
        this.uiScale = uiScale > 0 ? uiScale : 1f;
        this.readOnly = readOnly;
        this.showFilterPanelRunnableRef = showFilterPanelRunnableRef;
    }

    /**
     * 创建渲染用 AdapterContext。使用当前通过 {@link #setShowFilterPanelRunnable} 设置的回调。
     *
     * @param uiScale  UI 缩放，≤0 时按 1f
     * @param readOnly 是否只读
     */
    public static PropertyAdapterContext of(float uiScale, boolean readOnly) {
        return new PropertyAdapterContext(uiScale, readOnly, showFilterPanelRunnable);
    }

    public float getUiScale() { return uiScale; }
    public boolean isReadOnly() { return readOnly; }
    /** 打开选择过滤器面板的回调；未设置时为 null。 */
    public Runnable getShowFilterPanelRunnable() { return showFilterPanelRunnableRef; }

    /** 设置全局「打开选择过滤器」回调，供 {@link #of} 注入。由 UIManager 或装配层在 ViewCommands 可用时调用。 */
    public static void setShowFilterPanelRunnable(Runnable r) {
        showFilterPanelRunnable = r;
    }

    /** 获取全局「打开选择过滤器」回调（用于未持有 PropertyAdapterContext 实例的调用，如 Adapter 内静态上下文）。 */
    public static Runnable getShowFilterPanelRunnableStatic() {
        return showFilterPanelRunnable;
    }
}
