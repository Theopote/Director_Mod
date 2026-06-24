package com.director.persistence;

import org.jetbrains.annotations.Nullable;

/**
 * 工程持久化保存结果的全局状态，供状态栏等 UI 读取。
 */
public final class PersistenceSaveStatus {

    public static final String TIMELINE_FAILURE = "时间线数据未能写入存档";
    public static final String ACTOR_FAILURE = "Actor 数据未能写入存档";
    public static final String CONTAINER_FAILURE = "容器数据未能写入存档";

    private static volatile boolean timelineFailed;
    private static volatile boolean actorFailed;
    private static volatile boolean containerFailed;

    private PersistenceSaveStatus() {
    }

    public static void reportTimelineResult(boolean success) {
        timelineFailed = !success;
    }

    public static void reportActorResult(boolean success) {
        actorFailed = !success;
    }

    public static void reportContainerResult(boolean success) {
        containerFailed = !success;
    }

    public static boolean hasSaveFailure() {
        return timelineFailed || actorFailed || containerFailed;
    }

    public static @Nullable String getLastFailureReason() {
        if (!hasSaveFailure()) {
            return null;
        }
        StringBuilder reason = new StringBuilder();
        if (timelineFailed) {
            reason.append(TIMELINE_FAILURE);
        }
        if (actorFailed) {
            if (!reason.isEmpty()) {
                reason.append('；');
            }
            reason.append(ACTOR_FAILURE);
        }
        if (containerFailed) {
            if (!reason.isEmpty()) {
                reason.append('；');
            }
            reason.append(CONTAINER_FAILURE);
        }
        return reason.toString();
    }
}
