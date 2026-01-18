package com.chronoblocks.ui.timeline.interaction;

import com.chronoblocks.actor.action.ActorActionType;
import com.chronoblocks.ui.timeline.model.entity.ActorActionClip;

import java.util.ArrayList;
import java.util.List;

/**
 * ActionClipIntervalResolver v1.0
 *
 * <p>ActionClip 区间切割算法（涂抹式编辑的核心）。
 *
 * <p>设计目标：
 * <ul>
 *   <li>新 Action 覆盖旧 Action</li>
 *   <li>部分覆盖时自动切割旧的区间</li>
 *   <li>完全覆盖时删除旧的区间</li>
 *   <li>无重叠时保留原有区间</li>
 * </ul>
 *
 * <p>核心算法（区间切割）：
 * <pre>
 * 输入：
 *   - 已有 ActionClips（同一 Track）
 *   - 新 Action 区间 [a, b]
 *
 * 输出：
 *   - 一组新的 ActionClips（无重叠）
 *
 * 规则：
 *   1. 如果 old.end <= a || old.start >= b → 完全不相交 → 保留
 *   2. 如果有重叠 → 切割：
 *      - 如果 old.start < a → 保留 [old.start, a)
 *      - 如果 old.end > b → 保留 (b, old.end]
 * </pre>
 *
 * <p>示例：
 * <pre>
 * 假设已有：
 *   [  WALK  ][   RUN   ][ IDLE ]
 *
 * 现在涂抹：
 *       [ DANCE ]
 *
 * 结果：
 *   [ WALK ][ DANCE ][ RUN ][ IDLE ]
 * </pre>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class ActionClipIntervalResolver {

    private ActionClipIntervalResolver() {
        // 工具类，不允许实例化
    }

    /**
     * 解析 ActionClip 区间（涂抹式编辑的核心算法）
     *
     * @param existingClips 已有的 ActionClips（同一 Track）
     * @param startTime 新 Action 开始时间（秒）
     * @param endTime 新 Action 结束时间（秒）
     * @param actionType 新 Action 类型
     * @return 解析后的 ActionClips 列表（无重叠）
     */
    public static List<ActorActionClip> resolve(
            List<ActorActionClip> existingClips,
            float startTime,
            float endTime,
            ActorActionType actionType
    ) {
        if (existingClips == null || existingClips.isEmpty()) {
            // 如果没有已有 Clips，直接创建新的
            List<ActorActionClip> result = new ArrayList<>();
            result.add(createActionClip(startTime, endTime, actionType));
            return result;
        }

        List<ActorActionClip> result = new ArrayList<>();

        // 遍历已有 Clips，进行区间切割
        for (ActorActionClip old : existingClips) {
            float oldStart = old.getStartTime();
            float oldEnd = old.getEndTime();

            // 1. 完全不相交 → 保留
            if (oldEnd <= startTime || oldStart >= endTime) {
                result.add(old);
                continue;
            }

            // 2. 有重叠 → 切割
            // 2.1 如果 old.start < a → 保留 [old.start, a)
            if (oldStart < startTime) {
                ActorActionClip left = createActionClip(
                    oldStart,
                    startTime,
                    old.getActionType(),
                    old.getIntensity(),
                    old.isLoop()
                );
                left.setName(old.getName() + " (left)");
                result.add(left);
            }

            // 2.2 如果 old.end > b → 保留 (b, old.end]
            if (oldEnd > endTime) {
                ActorActionClip right = createActionClip(
                    endTime,
                    oldEnd,
                    old.getActionType(),
                    old.getIntensity(),
                    old.isLoop()
                );
                right.setName(old.getName() + " (right)");
                result.add(right);
            }

            // 重叠部分被新 Action 覆盖，不保留
        }

        // 3. 添加新 Action
        result.add(createActionClip(startTime, endTime, actionType));

        // 4. 按时间排序（保证结果有序）
        result.sort((a, b) -> Float.compare(a.getStartTime(), b.getStartTime()));

        return result;
    }

    /**
     * 创建 ActionClip（辅助方法）
     */
    private static ActorActionClip createActionClip(
            float startTime,
            float endTime,
            ActorActionType actionType
    ) {
        return createActionClip(startTime, endTime, actionType, 1.0f, true);
    }

    /**
     * 创建 ActionClip（完整参数）
     */
    private static ActorActionClip createActionClip(
            float startTime,
            float endTime,
            ActorActionType actionType,
            float intensity,
            boolean loop
    ) {
        ActorActionClip clip = new ActorActionClip(
            actionType.name(),
            startTime,
            endTime
        );
        clip.setActionType(actionType);
        clip.setIntensity(intensity);
        clip.setLoop(loop);
        return clip;
    }
}
