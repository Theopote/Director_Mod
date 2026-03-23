package com.director.core.container.commands;

import java.util.List;
import java.util.Objects;

/**
 * 同父容器内多选拖拽时的 index 修正与 no-op 判断。
 * <p>
 * 核心：目标 index 是「插入时的 index」，但插入前有些元素已被移走，故需扣掉「在目标前且会被移走的数量」。
 * 另：拖到「选中块内部」应为 no-op，否则会出现顺序错乱、undo 错位等问题。
 * <p>
 * 可复用于：容器树、时间线轨道、图层等「在正在被修改的列表里插入」的场景。
 */
public final class MoveIndexCorrection {

    private MoveIndexCorrection() {}

    /**
     * 判断是否应 no-op：目标插入位置落在「同父且被选中的旧下标区间」内。
     * <p>
     * 例：children A B C D E F，选中 B(1), C(2), D(3)。若 drop 到 C 前（target=2）或 D 前（target=3）
     * 或「紧挨 D 之后」（target=4），均视为拖入选中块内部，应 no-op。
     *
     * @param sameParentOldIndices 同父且被移动项在「原父」中的 oldIndex 集合（可未排序）
     * @param targetIndex          目标插入 index（「插入到谁前面」），&lt;0 表示追加，本方法对追加不判 no-op
     * @return 若应 no-op 则 true
     */
    public static boolean isNoOpWhenDroppingIntoSelectedBlock(
        List<Integer> sameParentOldIndices,
        int targetIndex
    ) {
        if (sameParentOldIndices == null || sameParentOldIndices.isEmpty() || targetIndex < 0) {
            return false;
        }
        int min = sameParentOldIndices.stream().mapToInt(Integer::intValue).min().orElseThrow();
        int max = sameParentOldIndices.stream().mapToInt(Integer::intValue).max().orElseThrow();
        // [min, max+1]：落在选中块内或紧挨块后（块内重排无意义）
        return targetIndex >= min && targetIndex <= max + 1;
    }

    /**
     * 同父移动时，修正「第一个插入位置」的 index。
     * <p>
     * 公式：correctedBase = targetIndex - count(selectedOldIndex &lt; targetIndex)。
     * 后续第 i 个同父项插入 at correctedBase + i（由 Command 在循环中应用）。
     * <p>
     * 跨父移动不需要修正，直接使用 targetIndex、targetIndex+1、… 即可。
     *
     * @param sameParentOldIndices 同父且被移动项的原 oldIndex，已按升序
     * @param targetIndex          目标插入 index
     * @return 修正后的起始插入 index
     */
    public static int fixSameParentBaseIndex(List<Integer> sameParentOldIndices, int targetIndex) {
        if (sameParentOldIndices == null || sameParentOldIndices.isEmpty()) {
            return targetIndex;
        }
        int correction = 0;
        for (int idx : sameParentOldIndices) {
            if (idx < targetIndex) {
                correction++;
            }
        }
        return targetIndex - correction;
    }

    /**
     * 判断两 parentId 是否「同引用」（均 null 或 equals）。
     */
    public static boolean sameRef(Object a, Object b) {
        return Objects.equals(a, b);
    }
}
