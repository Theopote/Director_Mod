package com.director.ui.properties;

import com.director.core.DirectorServices;
import com.director.ui.selection.SelectionManager;
import com.director.ui.selection.SelectionType;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.ui.tools.ToolRegistry;

/**
 * 属性上下文仲裁服务 - UI 语义仲裁器
 * <p>
 * 只决定「当前属性面板应该展示哪一类属性」，不决定展示什么内容，也不渲染 UI。
 * 提供 getCurrentContext / getContextTarget 供 PropertiesPanel 消费；
 * 通过 onToolActivated / onTimelineSelectionChanged 等接收 UI 信号（用于 clear 复位及「最近交互」去抖）。
 * </p>
 *
 * <h3>仲裁规则（优先级）</h3>
 * <ol>
 *   <li>若 {@link #clear()} 已调用 → NONE，target=null</li>
 *   <li>若时间线存在有效选择（TRACK / CLIP / KEYFRAME）<b>且</b>当前为选择工具且无「非选择工具」覆盖：
 *       按 <b>最近交互</b> 择一：{@code lastToolInteraction > lastTimelineInteraction} → TOOL，否则 → TIMELINE（避免 Tool 激活与 Timeline 选中并存时来回抖动）</li>
 *   <li>若时间线存在有效选择 → TIMELINE</li>
 *   <li>若当前为选择工具且无「非选择工具」覆盖 → TOOL</li>
 *   <li>否则 → NONE</li>
 * </ol>
 *
 * @author Director Team
 */
public final class PropertyContextService {
    private static final PropertyContextService INSTANCE = new PropertyContextService();

    /** clear() 后强制 NONE，直到 onToolActivated / onTimelineSelectionChanged / onWorldSelectionChanged 被调用 */
    private volatile boolean cleared = false;

    /** 最近一次由 onToolActivated 更新的时间戳（纳秒），用于与 Timeline 竞争时择一，避免抖动 */
    private volatile long lastToolInteraction = 0L;
    /** 最近一次由 onTimelineSelectionChanged 更新的时间戳（纳秒），用于与 Tool 竞争时择一 */
    private volatile long lastTimelineInteraction = 0L;
    /** 上一回时间线选中的 payload；仅当 payload 真正变化时才更新 lastTimelineInteraction，避免切到工具后因重复的「同对象」选中事件导致又切回 TIMELINE */
    private volatile Object lastTimelinePayload = null;

    private static volatile boolean listenerRegistered = false;

    /**
     * 注册 UI 选择变化监听（由 {@link DirectorServices#initialize()} 在实例创建后调用）。
     */
    public static void wireUiSelection(SelectionManager uiSelection) {
        if (listenerRegistered) {
            return;
        }
        synchronized (PropertyContextService.class) {
            if (listenerRegistered) {
                return;
            }
            uiSelection.addSelectionChangedListener(ignore -> {
                PropertyContextService svc = PropertyContextService.get();
                SelectionType t = uiSelection.getSelectionType();
                if (t == SelectionType.TRACK || t == SelectionType.CLIP || t == SelectionType.KEYFRAME) {
                    svc.onTimelineSelectionChanged(uiSelection.getSelectionPayload());
                } else {
                    svc.onTimelineSelectionChanged(null);
                }
            });
            listenerRegistered = true;
        }
    }

    private Object actorInspection;
    public void inspectActorObject(Object target) {
        if(target instanceof com.director.actor.Actor || target instanceof com.director.actor.ActorGroup) {
            actorInspection=target; cleared=false;
        }
    }
    public Object getActorInspection() {
        var registry=com.director.actor.ActorRegistryManager.getInstance();
        boolean valid=actorInspection instanceof com.director.actor.Actor actor
                ? registry.getActor(actor.getActorId()).orElse(null)==actorInspection
                : actorInspection instanceof com.director.actor.ActorGroup group
                    && registry.getGroup(group.getGroupId()).orElse(null)==actorInspection;
        if(!valid) actorInspection=null;
        return actorInspection;
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
        SelectionType st = DirectorServices.get().uiSelection().getSelectionType();
        boolean hasTimeline = (st == SelectionType.TRACK || st == SelectionType.CLIP || st == SelectionType.KEYFRAME);
        boolean hasTool = ToolRegistry.get().hasNonSelectionActiveTool();
        if (!hasTool) {
            var core = DirectorServices.get().selection();
            if (core.getCurrentTool() != null) {
                hasTool = true;
            }
        }
        // 两者同时成立时，按最近交互择一，避免 Tool 激活后 Timeline 选中仍存在导致的抖动
        if (hasTimeline && hasTool) {
            return (lastToolInteraction > lastTimelineInteraction) ? PropertyContext.TOOL : PropertyContext.TIMELINE;
        }
        if (hasTimeline) {
            return PropertyContext.TIMELINE;
        }
        if (hasTool) {
            return PropertyContext.TOOL;
        }
        return PropertyContext.NONE;
    }

    /**
     * 当 ctx=TIMELINE 时，是否应将 target 按「项目面板」渲染而非 Adapter。
     * 用于 ActorTrack、EntityClip 等尚未提供专用 Adapter 的类型，由 Service 统一决策，Panel 不判断。
     */
    public boolean isTimelineTargetRenderedAsProject(Object target) {
        if (target instanceof TimelineTrack tt) {
            return tt.getType() == TrackType.ACTOR;
        }
        return false;
    }

    /**
     * 当前上下文关联的对象（TOOL→SelectionTool，TIMELINE→Clip/Keyframe/Track，WORLD/NONE→null）
     */
    public Object getContextTarget() {
        if (cleared) {
            return null;
        }
        return switch (getCurrentContext()) {
            case TOOL -> ToolRegistry.get().hasNonSelectionActiveTool()
                    ? ToolRegistry.get().getActiveTool()
                    : DirectorServices.get().selection().getCurrentTool();
            case TIMELINE -> {
                Object payload = DirectorServices.get().uiSelection().getSelectionPayload();
                if (payload instanceof EntityTimelineClip entityTimelineClip) {
                    yield entityTimelineClip.getEntityClip();
                }
                yield payload;
            }
            default -> null;
        };
    }

    // ===================== UI 信号输入接口（Panels / 命令 调用） =====================

    /** Toolbar / 工具激活时调用，用于复位 cleared 并更新最近交互时间戳（与 Timeline 竞争时择一） */
    public void onToolActivated(String toolId) {
        actorInspection=null;
        lastToolInteraction = System.nanoTime();
        cleared = false;
    }

    /**
     * 时间线选中变化（Clip / Keyframe / Track）时调用，更新最近交互时间戳（与 Tool 竞争时择一）。
     * 若 payload 与上次相同则不再更新，避免：先选时间线再切到选择工具时，SelectionManager 重发
     * 「同对象」的选中事件把 lastTimelineInteraction 刷晚，导致无法及时切回 TOOL。
     * 传 null 表示已离开时间线选择，仅清 lastTimelinePayload。
     */
    public void onTimelineSelectionChanged(Object selection) {
        actorInspection=null;
        if (selection == null) {
            lastTimelinePayload = null;
            return;
        }
        if (selection == lastTimelinePayload) {
            return;
        }
        lastTimelinePayload = selection;
        lastTimelineInteraction = System.nanoTime();
        cleared = false;
    }

    /** 世界 / Actor / Container 选中变化时调用（预留） */
    public void onWorldSelectionChanged(Object selection) {
        actorInspection=null;
        cleared = false;
    }

    /** 清空上下文，强制 NONE 直至下次 onToolActivated / onTimelineSelectionChanged / onWorldSelectionChanged */
    public void clear() {
        actorInspection=null;
        cleared = true;
    }
}
