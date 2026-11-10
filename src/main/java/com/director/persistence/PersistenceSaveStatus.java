package com.director.persistence;

import com.director.i18n.DirectorLang.Status;

import java.util.ArrayList;
import java.util.List;

/**
 * 工程持久化保存结果的全局状态，供状态栏等 UI 读取。
 */
public final class PersistenceSaveStatus {

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

    public static List<String> getFailureReasonKeys() {
        List<String> reasons = new ArrayList<>(3);
        if (timelineFailed) {
            reasons.add(Status.SAVE_TIMELINE_FAILED);
        }
        if (actorFailed) {
            reasons.add(Status.SAVE_ACTOR_FAILED);
        }
        if (containerFailed) {
            reasons.add(Status.SAVE_CONTAINER_FAILED);
        }
        return reasons;
    }
}
