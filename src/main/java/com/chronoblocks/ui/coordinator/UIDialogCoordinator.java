package com.chronoblocks.ui.coordinator;

import com.chronoblocks.ui.dialogs.DialogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 对话框协调器 - 集中管理 DialogManager，统一弹窗渲染入口。
 * <p>
 * 职责：
 * <ul>
 *   <li>持有 DialogManager</li>
 *   <li>render() 委托 dialogManager.renderDialogs()</li>
 *   <li>getDialogManager() 供 UIManager、CommandFacade 等配置与调用</li>
 * </ul>
 * <p>
 * UIManager 不再直接关心“哪个 dialog 打开”，只调用 dialogCoordinator.render()。
 * 对 DialogManager 的配置（TimelineSettingsAccessor、LayoutAccessor、NewAnimationConfirm 等）
 * 仍在 UIManager / UICommandFacadeImpl 中完成，通过 getDialogManager() 获取。
 *
 * @author ChronoBlocks Team
 */
public final class UIDialogCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(UIDialogCoordinator.class);

    private final DialogManager dialogManager = new DialogManager();

    public UIDialogCoordinator() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[UIDialogCoordinator] DialogManager 已创建");
        }
    }

    public DialogManager getDialogManager() {
        return dialogManager;
    }

    /**
     * 渲染所有已打开的对话框。
     */
    public void render() {
        dialogManager.renderDialogs();
    }
}
