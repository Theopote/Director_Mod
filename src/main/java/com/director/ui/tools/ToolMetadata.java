package com.director.ui.tools;

import com.director.i18n.DirectorI18n;

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
    private final List<String> tooltipLangKeys;

    public ToolMetadata(ToolGroup group, int order, List<String> tooltipLangKeys) {
        this.group = Objects.requireNonNull(group, "group");
        this.order = order;
        if (tooltipLangKeys == null || tooltipLangKeys.isEmpty()) {
            this.tooltipLangKeys = Collections.emptyList();
        } else {
            this.tooltipLangKeys = Collections.unmodifiableList(new ArrayList<>(tooltipLangKeys));
        }
    }

    public ToolGroup getGroup() {
        return group;
    }

    public int getOrder() {
        return order;
    }

    public List<String> getTooltipLines() {
        if (tooltipLangKeys.isEmpty()) {
            return tooltipLangKeys;
        }
        List<String> translated = new ArrayList<>(tooltipLangKeys.size());
        for (String key : tooltipLangKeys) {
            translated.add(DirectorI18n.translate(key));
        }
        return Collections.unmodifiableList(translated);
    }

    public static ToolMetadata of(ToolGroup group, int order, String... tooltipLangKeys) {
        List<String> lines = new ArrayList<>();
        if (tooltipLangKeys != null) {
            for (String key : tooltipLangKeys) {
                if (key != null && !key.isBlank()) {
                    lines.add(key);
                }
            }
        }
        return new ToolMetadata(group, order, lines);
    }
}


