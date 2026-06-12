package com.director.actor.query;

import com.director.actor.ActorRef;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.TrackType;
import com.director.ui.timeline.model.entity.ActorTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ActorUsageQuery v1.0
 *
 * <p>只读查询工具：查询一个 Actor 在当前 Timeline 中被哪些 ActorTrack 使用
 *
 * <p>设计原则：
 * <ul>
 *   <li>✅ 只读查询，不修改任何数据</li>
 *   <li>✅ 不缓存、不持久化</li>
 *   <li>✅ 不持有 Timeline / Track 的引用</li>
 *   <li>✅ 可自然扩展到多 Timeline（未来）</li>
 * </ul>
 *
 * <p>架构约束：
 * <ul>
 *   <li>❌ Actor 不持有 Timeline / Track</li>
 *   <li>❌ ActorRegistry 不反向引用 Timeline</li>
 *   <li>✅ 只在渲染时查询，不存储查询结果</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public final class ActorUsageQuery {

    private ActorUsageQuery() {
        // 工具类，禁止实例化
    }

    /**
     * 查找使用指定 Actor 的所有 ActorTrack
     *
     * @param timeline TimelineModel 实例
     * @param actorId Actor ID
     * @return 使用该 Actor 的 ActorTrack 列表（只读）
     */
    public static List<ActorTrack> findTracksUsingActor(
            TimelineModel timeline,
            UUID actorId
    ) {
        List<ActorTrack> result = new ArrayList<>();
        if (timeline == null || actorId == null) {
            return result;
        }

        for (TimelineTrack track : timeline.getTracks()) {
            if (track == null) {
                continue;
            }

            // 只处理 ENTITY 类型的轨道
            if (track.getType() != TrackType.ACTOR) {
                continue;
            }

            // 获取 ActorTrack payload
            ActorTrack actorTrack = track.getActorTrackPayload();
            if (actorTrack == null) {
                continue;
            }

            // 检查 ActorRef 是否匹配
            ActorRef ref = actorTrack.getActorRef();
            if (ref != null && actorId.equals(ref.getActorId())) {
                result.add(actorTrack);
            }
        }

        return result;
    }

    /**
     * 查找使用指定 Actor 的所有 TimelineTrack
     * <p>用于 "Focus Track" 功能
     *
     * @param timeline TimelineModel 实例
     * @param actorId Actor ID
     * @return 使用该 Actor 的 TimelineTrack 列表（只读）
     */
    public static List<TimelineTrack> findTimelineTracksUsingActor(
            TimelineModel timeline,
            UUID actorId
    ) {
        List<TimelineTrack> result = new ArrayList<>();
        if (timeline == null || actorId == null) {
            return result;
        }

        for (TimelineTrack track : timeline.getTracks()) {
            if (track == null) {
                continue;
            }

            // 只处理 ENTITY 类型的轨道
            if (track.getType() != TrackType.ACTOR) {
                continue;
            }

            // 获取 ActorTrack payload
            ActorTrack actorTrack = track.getActorTrackPayload();
            if (actorTrack == null) {
                continue;
            }

            // 检查 ActorRef 是否匹配
            ActorRef ref = actorTrack.getActorRef();
            if (ref != null && actorId.equals(ref.getActorId())) {
                result.add(track);
            }
        }

        return result;
    }
}

