package com.director.ui.input;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 快捷键初始化器。
 *
 * <p>Director 界面内快捷键已统一由 {@link ShortcutDispatcher} 经 {@link ShortcutBindings} 分发。
 * 此类保留生命周期钩子，供 DirectorScreen 打开/关闭时做上下文切换（当前为占位，便于未来扩展）。
 */
public class ShortcutInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShortcutInitializer.class);

    /**
     * 模组客户端启动时调用。快捷键绑定在 {@link ShortcutBindings} 静态初始化中完成。
     */
    public static void initialize() {
        LOGGER.info("[ShortcutInitializer] 快捷键由 ShortcutDispatcher 统一管理");
    }

    /** DirectorScreen 打开时调用。 */
    public static void activateUIContexts() {
        LOGGER.debug("[ShortcutInitializer] Director UI 已打开");
    }

    /** DirectorScreen 关闭时调用。 */
    public static void deactivateUIContexts() {
        LOGGER.debug("[ShortcutInitializer] Director UI 已关闭");
    }

    /** 选择工具激活时调用。 */
    public static void activateSelectionToolContexts() {
        LOGGER.debug("[ShortcutInitializer] 选择工具上下文已激活");
    }

    /** 选择工具停用时调用。 */
    public static void deactivateSelectionToolContexts() {
        LOGGER.debug("[ShortcutInitializer] 选择工具上下文已停用");
    }
}
