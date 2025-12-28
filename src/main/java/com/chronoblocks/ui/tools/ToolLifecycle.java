package com.chronoblocks.ui.tools;

/**
 * 非 Selection 工具的生命周期钩子（UI 层）。
 *
 * <p>Selection 工具的生命周期由 core.selection.SelectionManager 负责，
 * 因此不要求 Selection 工具实现该接口。</p>
 */
public interface ToolLifecycle {
    default void onActivate() {}
    default void onDeactivate() {}
}


