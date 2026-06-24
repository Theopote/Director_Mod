package com.director.core.container.visibility;

import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;

/**
 * 容器可见性批量操作的瞬时 UI 反馈（供状态栏读取）。
 */
public final class ContainerVisibilityFeedback {

  private static final long COMPLETE_DISPLAY_MS = 2500L;
  private static final int FEEDBACK_BLOCK_THRESHOLD = 256;

  private static volatile String containerName;
  private static volatile String actionLabel;
  private static volatile int totalBlocks;
  private static volatile int remainingBlocks;
  private static volatile boolean inProgress;
  private static volatile long completeUntilMs;
  private static volatile String completeContainerName;
  private static volatile String completeActionLabel;

  private ContainerVisibilityFeedback() {}

  public static int getFeedbackBlockThreshold() {
    return FEEDBACK_BLOCK_THRESHOLD;
  }

  public static void onTaskQueued(String name, String action, int blockCount) {
    if (blockCount < FEEDBACK_BLOCK_THRESHOLD) {
      return;
    }
    containerName = name;
    actionLabel = action;
    totalBlocks = blockCount;
    remainingBlocks = blockCount;
    inProgress = true;
    completeUntilMs = 0L;
    completeContainerName = null;
    completeActionLabel = null;
  }

  public static void onTaskProgress(String name, String action, int remaining) {
    if (!inProgress) {
      return;
    }
    if (name != null) {
      containerName = name;
    }
    if (action != null) {
      actionLabel = action;
    }
    remainingBlocks = Math.max(0, remaining);
  }

  public static void onTaskComplete(String name, String action) {
    if (!inProgress && completeUntilMs <= System.currentTimeMillis()) {
      return;
    }
    inProgress = false;
    completeContainerName = name;
    completeActionLabel = action;
    completeUntilMs = System.currentTimeMillis() + COMPLETE_DISPLAY_MS;
    containerName = null;
    actionLabel = null;
    totalBlocks = 0;
    remainingBlocks = 0;
  }

  public static boolean hasMessage() {
    long now = System.currentTimeMillis();
    if (inProgress) {
      return true;
    }
    return completeUntilMs > now && completeContainerName != null;
  }

  public static String getStatusMessage() {
    if (inProgress) {
      return DirectorI18n.translate(
          DirectorLang.Status.VISIBILITY_PROGRESS,
          containerName != null ? containerName : "?",
          actionLabel != null ? actionLabel : "",
          remainingBlocks,
          totalBlocks);
    }
    long now = System.currentTimeMillis();
    if (completeUntilMs > now && completeContainerName != null) {
      return DirectorI18n.translate(
          DirectorLang.Status.VISIBILITY_COMPLETE,
          completeContainerName,
          completeActionLabel != null ? completeActionLabel : "");
    }
    return null;
  }

  public static void reset() {
    inProgress = false;
    completeUntilMs = 0L;
    containerName = null;
    actionLabel = null;
    completeContainerName = null;
    completeActionLabel = null;
    totalBlocks = 0;
    remainingBlocks = 0;
  }
}
