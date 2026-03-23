package com.director.ui.coordinator;

import com.director.ui.input.ShortcutDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 全局快捷键协调器 - 集中管理 ShortcutDispatcher，统一快捷键入口。
 * <p>
 * 职责：
 * <ul>
 *   <li>持有 ShortcutDispatcher（由 UIManager 在 getOrCreateCommandFacade 时注入）</li>
 *   <li>handleKeyEvent(key, action, modifiers) 委托给 ShortcutDispatcher</li>
 *   <li>getShortcutDispatcher() 供 DirectorScreen、KeyboardMixin 等使用</li>
 * </ul>
 * <p>
 * 快捷键为事件驱动（GLFW 按键），无 tick；此处仅做协调与转发，
 * 避免 UIManager 直接持有 ShortcutDispatcher 与分散的 shortcut 逻辑。
 *
 * @author Director Team
 */
public final class UIShortcutCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(UIShortcutCoordinator.class);

    private ShortcutDispatcher shortcutDispatcher;

    public UIShortcutCoordinator() {}

    /**
     * 设置 ShortcutDispatcher（由 UIManager 在懒加载 CommandFacade 时调用）。
     */
    public void setShortcutDispatcher(ShortcutDispatcher dispatcher) {
        this.shortcutDispatcher = dispatcher;
        if (dispatcher != null && LOGGER.isDebugEnabled()) {
            LOGGER.debug("[UIShortcutCoordinator] ShortcutDispatcher 已注入");
        }
    }

    /**
     * 获取 ShortcutDispatcher，供 Screen / Mixin 等使用。
     * 在未调用 setShortcutDispatcher 之前可能为 null。
     */
    public ShortcutDispatcher getShortcutDispatcher() {
        return shortcutDispatcher;
    }

    /**
     * 处理全局按键事件，转发给 ShortcutDispatcher。
     * 若 Dispatcher 尚未注入则忽略。
     */
    public void handleKeyEvent(int key, int action, int modifiers) {
        if (shortcutDispatcher != null) {
            shortcutDispatcher.handleGLFWKeyEvent(key, action, modifiers);
        }
    }
}
