package com.director.ui.commands;

import com.director.core.selection.block_filters.BlockFilter;

import java.util.List;
import java.util.Set;

/**
 * 选择过滤器（方块管道）命令接口
 * <p>
 * 配置「方块选择管道」的规则集合，供 SelectionFilterPanel 等 UI 调用。
 * <strong>红线：Panel 只允许调用本接口，不得直接触碰 SelectionManager / BlockFilterPipeline。</strong>
 * <p>
 * 设计：过滤 = 一组规则对象（BlockFilter），管道 = 有序规则列表；UI 只做启用/禁用/配置规则，不驱动选择行为。
 *
 * @author Director Team
 * @see com.director.core.selection.block_filters.BlockFilter
 * @see com.director.core.selection.block_filters.BlockFilterPipeline
 */
public interface SelectionFilterCommands {

    // ---------- 管道读取（只读，用于 UI 展示） ----------

    /** 当前管道中的过滤器列表（只读） */
    List<BlockFilter> getFilters();

    boolean isEmpty();

    int size();

    // ---------- 管道变更（唯一允许的写入入口） ----------

    void clear();

    /** 用给定列表完全替换当前管道 */
    void replaceAll(List<BlockFilter> filters);

    void appendFilter(BlockFilter filter);

    void removeFilterAt(int index);

    /** 快速应用：按 id 从 Registry 或 Preset 查找，并替换当前管道 */
    void applyQuickFilter(String id);

    // ---------- 层 1 基础内容过滤（状态型，人话入口） ----------

    /** 某 id 对应的过滤器是否已在管道中（仅查 pipeline，空管道时全为 false；空管道行为=全部通过）。 */
    boolean isFilterEnabled(String id);

    /** 启用/禁用：加入或从管道移除对应过滤器；启用 exclude_air 时会先移除 all；空管道下加非 exclude_air 规则时会先追加 exclude_air。 */
    void setFilterEnabled(String id, boolean enabled);

    // ---------- 预设 ----------

    void loadPreset(String id);

    /** 将当前管道保存为预设 */
    void savePreset(String name);

    void deletePreset(String id);

    Set<String> getPresetIds();

    // ---------- 元数据（Registry，用于下拉框等） ----------

    Set<String> getFilterIds();

    /** 按 id 获取过滤器实例（用于「添加到管道」）；null 表示不存在 */
    BlockFilter getFilter(String id);

    /** 获取过滤器在 Registry 中的 id（用于展示）；null 表示未注册 */
    String getFilterId(BlockFilter filter);

    // ---------- 性能统计（可选，用于调试 UI） ----------

    void enableProfiling(boolean enable);

    void resetStats();

    long getLastNanos();

    long getTotalChecked();
}
