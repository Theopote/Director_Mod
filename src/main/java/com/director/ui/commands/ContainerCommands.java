package com.director.ui.commands;

/**
 * 容器命令接口 - 容器树的删除等操作，经 Facade 统一入口供 Shortcut / 菜单 调用。
 *
 * @author Director Team
 */
public interface ContainerCommands {

    /**
     * 请求删除当前选中的容器（弹出确认对话框，与右键删除一致）。
     * 若无选中则 no-op。
     */
    void requestDeleteSelected();
}
