package com.director.ui.actor.selection;

import com.director.core.DirectorServices;
import com.director.actor.Actor;
import com.director.actor.ActorRef;
import com.director.ui.actor.gizmo.ActorGizmoController;
import com.director.ui.selection.SelectionManager;
import com.director.ui.timeline.container.ContainerTimelineService;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.TrackType;
import com.director.ui.timeline.model.entity.ActorTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/**
 * ActorSelectionHub v2.0
 *
 * <p>统一的"演员选中"和"演员放置"入口。
 *
 * <p>任何地方（世界点击 / UI 点击 / 脚本）只要想选中一个 Actor：
 *     → 调用 ActorSelectionHub.selectActor(actorId)
 *
 * <p>任何地方想放置一个 Actor：
 *     → 调用 ActorSelectionHub.beginPlace(actor)
 *
 * <p>它会负责：
 * <ol>
 *   <li>在 Timeline 中找到对应的 ActorTrack</li>
 *   <li>选中该 Track（TimelineInteractionHandler）</li>
 *   <li>同步到 SelectionManager（属性面板 / 其他 UI）</li>
 *   <li>通知 Gizmo 目标更新（ActorGizmoController）</li>
 * </ol>
 *
 * <p>⚠️ 重要原则：
 * <ul>
 *   <li>ActorSelectionHub 只描述"意图"，不执行任何操作</li>
 *   <li>它只是把一次 selection/placement 行为广播到各系统</li>
 *   <li>严格遵守方案 C：Timeline 只通过 ActorRef 认识 Actor</li>
 * </ul>
 *
 * @author Director Team
 * @version 2.0
 */
public final class ActorSelectionHub {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorSelectionHub.class);

    /** 当前选中的 Actor ID（只读，供 Visualizer 使用） */
    private static UUID currentActorId = null;

    /** 放置模式状态 */
    private static ActorSelectionMode mode = ActorSelectionMode.NONE;

    /** 等待放置的 Actor（放置模式时） */
    private static Actor pendingActor = null;

    /**
     * Actor 选择模式
     */
    public enum ActorSelectionMode {
        /** 无选择 */
        NONE,
        /** 选择模式（点击世界中的 Actor） */
        SELECT,
        /** 放置模式（在世界中放置新 Actor） */
        PLACE
    }

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

        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model == null) {
            LOGGER.warn("[ActorSelectionHub] TimelineModel 未初始化，无法选中 Actor");
            clearSelection();
            return;
        }

        // 1️⃣ 找到对应的 ActorTrack
        TimelineTrack track = findActorTrackByActorId(model, actorId);
        if (track == null) {
            // Actor 存在，但当前 Timeline 没有使用它
            LOGGER.debug("[ActorSelectionHub] 未找到 Actor {} 对应的 ActorTrack，清空选择", actorId);
            clearSelection();
            return;
        }

        // 2️⃣ Timeline：选中 Track
        // 注意：TimelineInteractionHandler 没有单例，由 TimelinePanel 持有
        // 为了保持解耦，这里直接使用 model.selectItem()
        // 如果未来需要更复杂的选中逻辑，可以通过服务注入 TimelineInteractionHandler
        model.selectItem(track, false); // false = 单选模式，清空后选中

        // ⭐ 明确清除方块选区（Actor 选择不应该有方块选区）
        // 必须在 SelectionManager.selectActor() 之前清除，避免干扰
        try {
            DirectorServices.get().selection().clearSelection();
        } catch (Exception e) {
            LOGGER.debug("[ActorSelectionHub] 清除方块选区失败: {}", e.getMessage());
        }

        // 3️⃣ SelectionManager：明确设置为 ACTOR 类型
        SelectionManager selectionManager = SelectionManager.get();
        // ⭐ 关键：使用 selectActor() 明确设置 SelectionType.ACTOR
        selectionManager.selectActor(actorId);

        // 4️⃣ Gizmo：设置目标 Actor
        ActorGizmoController gizmoController = ActorGizmoController.getInstance();
        gizmoController.setTargetActor(actorId);

        // 5️⃣ LookAt Gizmo：设置目标 Actor
        com.director.ui.actor.lookat.ActorLookAtGizmoController lookAtController =
                com.director.ui.actor.lookat.ActorLookAtGizmoController.getInstance();
        lookAtController.setTargetActor(actorId);

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
        TimelineModel model = DirectorServices.get().containerTimeline().getTimelineModel();
        if (model != null) {
            // 清空 Timeline 选择
            model.clearSelection();
        }

        SelectionManager selectionManager = SelectionManager.get();
        selectionManager.clearSelection();

        // 清除方块选区（Minecraft 世界中的选区）
        try {
            DirectorServices.get().selection().clearSelection();
        } catch (Exception e) {
            LOGGER.debug("[ActorSelectionHub] 清除方块选区失败（可能不在客户端）: {}", e.getMessage());
        }

        ActorGizmoController gizmoController = ActorGizmoController.getInstance();
        gizmoController.clearTarget();

        com.director.ui.actor.lookat.ActorLookAtGizmoController lookAtController =
                com.director.ui.actor.lookat.ActorLookAtGizmoController.getInstance();
        lookAtController.clearTarget();

        // 清除 LookAt 选择上下文
        com.director.ui.actor.lookat.ActorLookAtSelectionContext.setActiveLookAtClip(null);

        // 清除当前选中的 Actor ID
        currentActorId = null;

        LOGGER.debug("[ActorSelectionHub] 已清除 Actor 选择和方块选区");
    }

    // =========================================================
    // Placement API (v2.0 新增)
    // =========================================================

    /**
     * 开始放置 Actor（进入放置模式）
     * <p>告诉系统："我现在想放置这个 Actor"
     * <p>Hub 只描述"意图"，不执行任何操作
     *
     * @param actor 要放置的 Actor
     */
    public static void beginPlace(Actor actor) {
        if (actor == null) {
            LOGGER.warn("[ActorSelectionHub] beginPlace: actor is null");
            return;
        }

        mode = ActorSelectionMode.PLACE;
        pendingActor = actor;

        LOGGER.info("[ActorSelectionHub] 进入放置模式: {} ({})", actor.getName(), actor.getType());
    }

    /**
     * 取消放置（退出放置模式）
     */
    public static void cancelPlace() {
        mode = ActorSelectionMode.NONE;
        pendingActor = null;

        LOGGER.debug("[ActorSelectionHub] 已取消放置模式");
    }

    /**
     * 检查是否正在放置 Actor
     *
     * @return 如果正在放置则返回 true
     */
    public static boolean isPlacingActor() {
        return mode == ActorSelectionMode.PLACE && pendingActor != null;
    }

    /**
     * 获取等待放置的 Actor
     *
     * @return 等待放置的 Actor，如果不在放置模式则返回 null
     */
    public static Actor getPendingActor() {
        return pendingActor;
    }

    /**
     * 获取当前选择模式
     *
     * @return 当前选择模式
     */
    public static ActorSelectionMode getMode() {
        return mode;
    }

    // =========================================================
    // Internal helpers
    // =========================================================

    /**
     * 在 TimelineModel 中查找绑定了指定 Actor 的 ActorTrack
     * <p>Timeline 只通过 ActorRef 间接引用 Actor，这是方案 C 的核心约束。
     *
     * @param model TimelineModel
     * @param actorId Actor ID
     * @return TimelineTrack，如果未找到则返回 null
     */
    private static TimelineTrack findActorTrackByActorId(TimelineModel model, UUID actorId) {
        if (model == null || actorId == null) {
            return null;
        }

        for (TimelineTrack track : model.getTracks()) {
            if (track == null || track.getType() != TrackType.ACTOR) {
                continue;
            }

            // 使用 getActorTrackPayload() 获取 ActorTrack
            ActorTrack actorTrack = track.getActorTrackPayload();
            if (actorTrack == null) {
                continue;
            }

            // 通过 ActorRef 获取 Actor ID
            ActorRef ref = actorTrack.getActorRef();
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
