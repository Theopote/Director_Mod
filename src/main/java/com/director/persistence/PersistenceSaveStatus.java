package com.director.persistence;

import org.jetbrains.annotations.Nullable;

/**
 * 工程持久化保存结果的全局状态，供状态栏等 UI 读取。
 */
public final class PersistenceSaveStatus {

    private static volatile boolean saveFailed;
    private static volatile @Nullable String lastFailureReason;

    private PersistenceSaveStatus() {
    }

    public static void reportSuccess() {
        saveFailed = false;
        lastFailureReason = null;
    }

    public static void reportFailure(String reason) {
        saveFailed = true;
        lastFailureReason = reason != null && !reason.isEmpty() ? reason : "保存失败";
    }

    public static boolean hasSaveFailure() {
        return saveFailed;
    }

    public static @Nullable String getLastFailureReason() {
        return lastFailureReason;
    }
}
