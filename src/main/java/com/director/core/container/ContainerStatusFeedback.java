package com.director.core.container;

import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;

/**
 * 容器操作相关的瞬时状态栏提示（创建大容器、方块裁剪等）。
 */
public final class ContainerStatusFeedback {

    private static final long ADVISORY_DISPLAY_MS = 8000L;

    private static volatile String message;
    private static volatile long hideAfterMs;

    private ContainerStatusFeedback() {
    }

    public static void onLargeContainer(String context, int blockCount) {
        show(DirectorI18n.translate(
                DirectorLang.Status.LARGE_CONTAINER_ADVISORY,
                context != null ? context : "?",
                blockCount));
    }

    public static void onBlocksTruncated(String containerName, int requested, int applied, int maxBlocks) {
        show(DirectorI18n.translate(
                DirectorLang.Status.CONTAINER_BLOCKS_TRUNCATED,
                containerName != null ? containerName : "?",
                requested,
                applied,
                maxBlocks));
    }

    public static void show(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        message = text;
        hideAfterMs = System.currentTimeMillis() + ADVISORY_DISPLAY_MS;
    }

    public static boolean hasMessage() {
        return getMessage() != null;
    }

    public static String getMessage() {
        if (message == null) {
            return null;
        }
        if (System.currentTimeMillis() > hideAfterMs) {
            message = null;
            return null;
        }
        return message;
    }

    public static void reset() {
        message = null;
        hideAfterMs = 0L;
    }
}
