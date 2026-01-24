package com.chronoblocks.ui.properties;

import com.chronoblocks.ui.selection.SelectionManager;
import com.chronoblocks.ui.selection.SelectionType;
import com.chronoblocks.ui.tools.ToolRegistry;

/**
 * 属性上下文仲裁服务 - UI 语义仲裁器
 * <p>
 * 只决定「当前属性面板应该展示哪一类属性」，不决定展示什么内容，也不渲染 UI。
 * 提供 getCurrentContext / getContextTarget 供 PropertiesPanel 消费；
 * 通过 onToolActivated / onTimelineSelectionChanged 等接收 UI 信号（用于 clear 复位及后续「最近交互」扩展）。
 * </p>
 *
 * <h3>仲裁规则（优先级）</h3>
 * <ol>
 *   <li>若 {@link #clear()} 已调用 → NONE，target=null</li>
 *   <li>若时间线存在有效选择（TRACK / CLIP / KEYFRAME）→ TIMELINE</li>
 *   <li>否则若当前为选择工具且无「非选择工具」覆盖 → TOOL</li>
 *   <li>否则 → NONE</li>
 * </ol>
 *
 * @author ChronoBlocks Team
 */
public final class PropertyContextService {
    private static final PropertyContextService INSTANCE = new PropertyContextService();

    /** clear() 后强制 NONE，直到 onToolActivated / onTimelineSelectionChanged / onWorldSelectionChanged 被调用 */
    private volatile boolean cleared = false;

    static {
        SelectionManager.get().addSelectionChangedListener(ignore -> {
            SelectionType t = SelectionManager.get().getSelectionType();
            if (t == SelectionType.TRACK || t == SelectionType.CLIP || t == SelectionType.KEYFRAME) {
                PropertyContextService.get().onTimelineSelectionChanged(SelectionManager.get().getSelectionPayload());
            }
        });
    }

    private PropertyContextService() {}

    public static PropertyContextService get() {
        return INSTANCE;
    }

    // ===================== 查询接口（PropertiesPanel 用） =====================

    /**
     * 当前生效的属性上下文
     */
    public PropertyContext getCurrentContext() {
        if (cleared) {
            return PropertyContext.NONE;
        }
        SelectionType st = SelectionManager.get().getSelectionType();
        if (st == SelectionType.TRACK || st == SelectionType.CLIP || st == SelectionType.KEYFRAME) {
            return PropertyContext.TIMELINE;
        }
        if (!ToolRegistry.get().hasNonSelectionActiveTool()) {
            var core = com.chronoblocks.core.selection.SelectionManager.get();
            if (core != null && core.getCurrentTool() != null) {
                return PropertyContext.TOOL;
            }
        }
        return PropertyContext.NONE;
    }

    /**
     * 当前上下文关联的对象（TOOL→SelectionTool，TIMELINE→Clip/Keyframe/Track，WORLD/NONE→null）
     */
    public Object getContextTarget() {
        if (cleared) {
            return null;
        }
        switch (getCurrentContext()) {
            case TOOL:
                var core = com.chronoblocks.core.selection.SelectionManager.get();
                return core != null ? core.getCurrentTool() : null;
            case TIMELINE:
                return SelectionManager.get().getSelectionPayload();
            case WORLD:
            case NONE:
            default:
                return null;
        }
    }

    // ===================== UI 信号输入接口（Panels / 命令 调用） =====================

    /** Toolbar / 工具激活时调用，用于复位 cleared 及后续「最近交互」扩展 */
    public void onToolActivated(String toolId) {
        cleared = false;
    }

    /** 时间线选中变化（Clip / Keyframe / Track）时调用 */
    public void onTimelineSelectionChanged(Object selection) {
        cleared = false;
    }

    /** 世界 / Actor / Container 选中变化时调用（预留） */
    public void onWorldSelectionChanged(Object selection) {
        cleared = false;
    }

    /** 清空上下文，强制 NONE 直至下次 onToolActivated / onTimelineSelectionChanged / onWorldSelectionChanged */
    public void clear() {
        cleared = true;
    }
}
