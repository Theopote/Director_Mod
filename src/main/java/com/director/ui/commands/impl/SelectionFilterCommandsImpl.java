package com.director.ui.commands.impl;

import com.director.core.DirectorServices;
import com.director.core.selection.block_filters.BlockFilter;
import com.director.core.selection.block_filters.BlockFilterPipeline;
import com.director.core.selection.block_filters.BlockFilterPresetManager;
import com.director.core.selection.block_filters.BlockFilterRegistry;
import com.director.core.selection.block_filters.FilterIds;
import com.director.ui.commands.SelectionFilterCommands;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 选择过滤器命令实现：唯一允许操作全局 BlockFilterPipeline 的入口。
 */
public final class SelectionFilterCommandsImpl implements SelectionFilterCommands {

    private static BlockFilterPipeline getPipeline() {
        return DirectorServices.get().selection().getPipeline();
    }

    @Override
    public List<BlockFilter> getFilters() {
        return getPipeline().getFilters();
    }

    @Override
    public boolean isEmpty() {
        return getPipeline().isEmpty();
    }

    @Override
    public int size() {
        return getPipeline().size();
    }

    @Override
    public void clear() {
        getPipeline().clear();
    }

    @Override
    public void replaceAll(List<BlockFilter> filters) {
        var p = getPipeline();
        p.clear();
        if (filters != null) {
            p.addAll(filters);
        }
    }

    @Override
    public void appendFilter(BlockFilter filter) {
        if (filter == null) {
            return;
        }
        String id = BlockFilterRegistry.getFilterId(filter);
        if (id != null && pipelineContains(id)) {
            return;
        }
        getPipeline().add(filter);
    }

    @Override
    public void removeFilterAt(int index) {
        var list = new ArrayList<>(getPipeline().getFilters());
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            replaceAll(list);
        }
    }

    @Override
    public void applyQuickFilter(String id) {
        var f = BlockFilterRegistry.getFilter(id);
        if (f != null) {
            replaceAll(List.of(f));
            return;
        }
        var preset = BlockFilterPresetManager.getPreset(id);
        if (preset != null) {
            replaceAll(preset.getFilters());
        }
    }

    @Override
    public boolean isFilterEnabled(String id) {
        if (id == null) {
            return false;
        }
        return pipelineContains(id);
    }

    @Override
    public void setFilterEnabled(String id, boolean enabled) {
        if (id == null) {
            return;
        }
        String c = BlockFilterRegistry.resolveFilterId(id);
        if (enabled) {
            if (FilterIds.EXCLUDE_AIR.equals(c)) {
                removeFilterById(FilterIds.ALL);
            }
            if (!pipelineContains(id)) {
                var f = BlockFilterRegistry.getFilter(id);
                if (f != null) {
                    if (isEmpty() && !FilterIds.EXCLUDE_AIR.equals(c)) {
                        var air = BlockFilterRegistry.getFilter(FilterIds.EXCLUDE_AIR);
                        if (air != null) {
                            appendFilter(air);
                        }
                    }
                    appendFilter(f);
                }
            }
        } else {
            removeFilterById(id);
        }
    }

    private boolean pipelineContains(String id) {
        String c = BlockFilterRegistry.resolveFilterId(id);
        if (c == null) {
            return false;
        }
        for (var f : getFilters()) {
            if (c.equals(BlockFilterRegistry.getFilterId(f))) {
                return true;
            }
        }
        return false;
    }

    private void removeFilterById(String id) {
        String c = BlockFilterRegistry.resolveFilterId(id);
        if (c == null) {
            return;
        }
        var list = new ArrayList<>(getFilters());
        for (int i = 0; i < list.size(); i++) {
            if (c.equals(BlockFilterRegistry.getFilterId(list.get(i)))) {
                removeFilterAt(i);
                return;
            }
        }
    }

    @Override
    public void loadPreset(String id) {
        var preset = BlockFilterPresetManager.getPreset(id);
        if (preset != null) {
            replaceAll(preset.getFilters());
        }
    }

    @Override
    public void savePreset(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        BlockFilterPresetManager.savePreset(name, getPipeline());
    }

    @Override
    public void deletePreset(String id) {
        BlockFilterPresetManager.deletePreset(id);
    }

    @Override
    public Set<String> getPresetIds() {
        return BlockFilterPresetManager.getPresetIds();
    }

    @Override
    public Set<String> getFilterIds() {
        return BlockFilterRegistry.getAllFilterIds();
    }

    @Override
    public BlockFilter getFilter(String id) {
        return BlockFilterRegistry.getFilter(id);
    }

    @Override
    public String getFilterId(BlockFilter filter) {
        return BlockFilterRegistry.getFilterId(filter);
    }

    @Override
    public void enableProfiling(boolean enable) {
        getPipeline().enableProfiling(enable);
    }

    @Override
    public void resetStats() {
        getPipeline().resetStats();
    }

    @Override
    public long getLastNanos() {
        return getPipeline().getLastNanos();
    }

    @Override
    public long getTotalChecked() {
        return getPipeline().getTotalChecked();
    }
}
