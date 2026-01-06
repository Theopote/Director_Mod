package com.chronoblocks.ui.actor.selection;

import com.chronoblocks.actor.ActorRef;
import com.chronoblocks.ui.selection.SelectionManager;
import com.chronoblocks.ui.timeline.container.ContainerTimelineService;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * ActorSelectionHub v1.0
 *
 * <p>统一选中入口，做三件事：
 * <ul>
 *   <li>UI Selection（属性面板要知道当前选中的是哪个 actor/track/clip）</li>
 *   <li>Timeline 选中对应 EntityTrack</li>
 *   <li>GizmoTarget 绑定到该 PreviewActorEntity</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>静态类，避免循环依赖</li>
 *   <li>统一入口，避免重复逻辑</li>
 *   <li>与 TimelineInteractionHandler 配合，复用现有选择系统</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class ActorSelectionHub {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorSelectionHub.class);

    private ActorSelectionHub() {
        // 静态工具类
    }

    /**
     * 选中 Actor（统一入口）
     * <p>把一次"选中 actorId"的动作扩散到全系统
     *
     * @param actorId Actor ID
     */
    public static void selectActor(UUID actorId) {
        if (actorId == null) {
            LOGGER.warn("[ActorSelectionHub] 尝试选中 null actorId");
            return;
        }

        TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
        if (model == null) {
            LOGGER.warn("[ActorSelectionHub] TimelineModel 未初始化，无法选中 Actor");
            return;
        }

        // 1️⃣ 找到对应 EntityTrack
        TimelineTrack track = model.findEntityTrackByActorId(actorId);
        if (track == null) {
            LOGGER.warn("[ActorSelectionHub] 未找到 Actor {} 对应的 EntityTrack", actorId);
            return;
        }

        // 2️⃣ 选中 Timeline Track（通过 selectItem 方法）
        model.selectItem(track, false); // false = 单选模式，清空后选中

        // 3️⃣ 同步属性面板 selection（复用现有 UI selection manager）
        ActorRef actorRef = new ActorRef(actorId);
        SelectionManager.get().setCurrentSelection(actorRef);

        // 4️⃣ 通知 Gizmo 目标变更
        com.chronoblocks.ui.actor.gizmo.ActorGizmoController.getInstance().setTargetActor(actorId);

        LOGGER.debug("[ActorSelectionHub] 已选中 Actor: {}", actorId);
    }

    /**
     * 清除 Actor 选择
     */
    public static void clearSelection() {
        SelectionManager.get().clearSelection();
        com.chronoblocks.ui.actor.gizmo.ActorGizmoController.getInstance().clearTarget();
        
        TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
        if (model != null) {
            // TimelineModel 没有 setSelectedTrack，通过 selectItem(null, false) 清空选择
            model.selectItem(null, false);
        }

        LOGGER.debug("[ActorSelectionHub] 已清除 Actor 选择");
    }
}

