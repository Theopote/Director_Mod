package com.director.core.selection;

/**
 * 提供统一的操作模式访问接口。
 * <p>
 * 支持操作模式切换的选择工具应实现该接口，
 * 以便在 UI 面板和快捷键中进行统一处理。
 */
public interface SelectionOperationSupport {

    /**
     * 获取当前操作模式。
     */
    SelectionOperation getOperation();

    /**
     * 设置操作模式。
     */
    void setOperation(SelectionOperation operation);
}

