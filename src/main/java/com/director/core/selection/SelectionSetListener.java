package com.director.core.selection;

/**
 * 选区变化回调接口
 * 用于驱动属性面板/时间线刷新
 * 
 * @author Director Team
 * @version 1.0
 */
@FunctionalInterface
public interface SelectionSetListener {
    /**
     * 当选区发生变化时调用
     * @param set 变化后的选区集合
     */
    void onSelectionChanged(SelectionSet set);
}
