package com.director.ui.aware;

import com.director.ui.commands.UICommandFacade;

/**
 * 命令门面感知接口 - 需要注入 UICommandFacade 的面板实现此接口。
 * <p>
 * UIPanelManager 只做 {@code if (panel instanceof ICommandsAware)} 注入，
 * 不关心具体是 MenuBar、Toolbar 还是 Timeline，避免“面板语义”泄漏。
 *
 * @author Director Team
 */
public interface ICommandsAware {

    void setCommands(UICommandFacade commands);
}
