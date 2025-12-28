package com.chronoblocks.ui.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 工具元数据（UI 层）
 *
 * <p>用于把“分组/排序/教学提示”等 UI 信息从 ToolbarPanel 中抽离，
 * 避免未来新增工具导致 UI 代码膨胀。</p>
 */
public final class ToolMetadata {
    private final ToolGroup group;
    private final int order;
    private final List<String> tooltipLines;

    public ToolMetadata(ToolGroup group, int order, List<String> tooltipLines) {
        this.group = Objects.requireNonNull(group, "group");
        this.order = order;
        if (tooltipLines == null || tooltipLines.isEmpty()) {
            this.tooltipLines = Collections.emptyList();
        } else {
            this.tooltipLines = Collections.unmodifiableList(new ArrayList<>(tooltipLines));
        }
    }

    public ToolGroup getGroup() {
        return group;
    }

    public int getOrder() {
        return order;
    }

    public List<String> getTooltipLines() {
        return tooltipLines;
    }

    public static ToolMetadata of(ToolGroup group, int order, String... tooltipLines) {
        List<String> lines = new ArrayList<>();
        if (tooltipLines != null) {
            for (String line : tooltipLines) {
                if (line != null && !line.isBlank()) {
                    lines.add(line);
                }
            }
        }
        return new ToolMetadata(group, order, lines);
    }
}


