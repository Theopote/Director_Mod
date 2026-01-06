package com.chronoblocks.ui.actor.selection;

import com.chronoblocks.actor.ActorRef;
import com.chronoblocks.ui.actor.gizmo.ActorGizmoController;
import com.chronoblocks.ui.selection.SelectionManager;
import com.chronoblocks.ui.timeline.container.ContainerTimelineService;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TrackType;
import com.chronoblocks.ui.timeline.model.entity.EntityTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/**
 * ActorSelectionHub v1.0
 *
 * <p>统一的"演员选中"入口。
 *
 * <p>任何地方（世界点击 / UI 点击 / 脚本）只要想选中一个 Actor：
 *     → 调用 ActorSelectionHub.selectActor(actorId)
 *
 * <p>它会负责：
 * <ol>
 *   <li>在 Timeline 中找到对应的 EntityTrack</li>
 *   <li>选中该 Track（TimelineInteractionHandler）</li>
 *   <li>同步到 SelectionManager（属性面板 / 其他 UI）</li>
 *   <li>通知 Gizmo 目标更新（ActorGizmoController）</li>
 * </ol>
 *
 * <p>⚠️ 重要原则：
 * <ul>
 *   <li>ActorSelectionHub 不"存状态"</li>
 *   <li>它只是把一次 selection 行为广播到各系统</li>
 *   <li>严格遵守方案 C：Timeline 只通过 ActorRef 认识 Actor</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class ActorSelectionHub {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorSelectionHub.class);

    /** 当前选中的 Actor ID（只读，供 Visualizer 使用） */
    private static UUID currentActorId = null;

    private ActorSelectionHub() {
        // 静态工具类
    }

    // =========================================================
    // Public API
    // =========================================================

    /**
     * 选中一个 Actor（唯一入口）
     * <p>把一次"选中 actorId"的动作扩散到全系统
     *
     * @param actorId Actor ID
     */
    public static void selectActor(UUID actorId) {
        if (actorId == null) {
            clearSelection();
            return;
        }

        TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
        if (model == null) {
            LOGGER.warn("[ActorSelectionHub] TimelineModel 未初始化，无法选中 Actor");
            clearSelection();
            return;
        }

        // 1️⃣ 找到对应的 EntityTrack
        TimelineTrack track = findEntityTrackByActorId(model, actorId);
        if (track == null) {
            // Actor 存在，但当前 Timeline 没有使用它
            LOGGER.debug("[ActorSelectionHub] 未找到 Actor {} 对应的 EntityTrack，清空选择", actorId);
            clearSelection();
            return;
        }

        // 2️⃣ Timeline：选中 Track
        // 注意：TimelineInteractionHandler 没有单例，由 TimelinePanel 持有
        // 为了保持解耦，这里直接使用 model.selectItem()
        // 如果未来需要更复杂的选中逻辑，可以通过服务注入 TimelineInteractionHandler
        model.selectItem(track, false); // false = 单选模式，清空后选中

        // 3️⃣ SelectionManager：同步 selection（属性面板等）
        SelectionManager selectionManager = SelectionManager.get();
        if (selectionManager != null) {
            // 直接设置 track，让属性面板显示轨道属性
            selectionManager.setCurrentSelection(track);
        }

        // 4️⃣ Gizmo：设置目标 Actor
        ActorGizmoController gizmoController = ActorGizmoController.getInstance();
        if (gizmoController != null) {
            gizmoController.setTargetActor(actorId);
        }

        // 5️⃣ LookAt Gizmo：设置目标 Actor
        com.chronoblocks.ui.actor.lookat.ActorLookAtGizmoController lookAtController =
                com.chronoblocks.ui.actor.lookat.ActorLookAtGizmoController.getInstance();
        if (lookAtController != null) {
            lookAtController.setTargetActor(actorId);
        }

        // 6️⃣ 更新当前选中的 Actor ID（供 Visualizer 使用）
        currentActorId = actorId;

        LOGGER.debug("[ActorSelectionHub] 已选中 Actor: {} (Track: {})", actorId, track.getName());
    }

    /**
     * 获取当前选中的 Actor ID（只读）
     * <p>供 Visualizer 等只读组件使用
     *
     * @return 当前选中的 Actor ID，如果未选中则返回 null
     */
    public static UUID getSelectedActorId() {
        return currentActorId;
    }

    /**
     * 清空当前 Actor / Track selection
     * <p>（比如点到空白区域）
     */
    public static void clearSelection() {
        TimelineModel model = ContainerTimelineService.getInstance().getTimelineModel();
        if (model != null) {
            // 清空 Timeline 选择
            model.clearSelection();
        }

        SelectionManager selectionManager = SelectionManager.get();
        if (selectionManager != null) {
            selectionManager.clearSelection();
        }

        ActorGizmoController gizmoController = ActorGizmoController.getInstance();
        if (gizmoController != null) {
            gizmoController.clearTarget();
        }

        com.chronoblocks.ui.actor.lookat.ActorLookAtGizmoController lookAtController =
                com.chronoblocks.ui.actor.lookat.ActorLookAtGizmoController.getInstance();
        if (lookAtController != null) {
            lookAtController.clearTarget();
        }

        // 清除 LookAt 选择上下文
        com.chronoblocks.ui.actor.lookat.ActorLookAtSelectionContext.setActiveLookAtClip(null);

        // 清除当前选中的 Actor ID
        currentActorId = null;

        LOGGER.debug("[ActorSelectionHub] 已清除 Actor 选择");
    }

    // =========================================================
    // Internal helpers
    // =========================================================

    /**
     * 在 TimelineModel 中查找绑定了指定 Actor 的 EntityTrack
     * <p>Timeline 只通过 ActorRef 间接引用 Actor，这是方案 C 的核心约束。
     *
     * @param model TimelineModel
     * @param actorId Actor ID
     * @return TimelineTrack，如果未找到则返回 null
     */
    private static TimelineTrack findEntityTrackByActorId(TimelineModel model, UUID actorId) {
        if (model == null || actorId == null) {
            return null;
        }

        for (TimelineTrack track : model.getTracks()) {
            if (track == null || track.getType() != TrackType.ENTITY) {
                continue;
            }

            // 使用 getEntityTrackPayload() 获取 EntityTrack
            EntityTrack entityTrack = track.getEntityTrackPayload();
            if (entityTrack == null) {
                continue;
            }

            // 通过 ActorRef 获取 Actor ID
            ActorRef ref = entityTrack.getActorRef();
            if (ref == null) {
                continue;
            }

            if (Objects.equals(ref.getActorId(), actorId)) {
                return track;
            }
        }

        return null;
    }

}
