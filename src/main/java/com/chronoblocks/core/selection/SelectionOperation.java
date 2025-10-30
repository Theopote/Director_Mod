package com.chronoblocks.core.selection;

/**
 * 选择操作枚举
 * 定义 Add / Subtract / Intersect 三种逻辑
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public enum SelectionOperation {
    ADD,        // 合并到现有选区
    SUBTRACT,   // 从现有选区移除
    INTERSECT;  // 与现有选区求交集

    public String getDisplayName() {
        return switch (this) {
            case ADD -> "加选";
            case SUBTRACT -> "减选";
            case INTERSECT -> "交集";
        };
    }
    
    public String getIcon() {
        return switch (this) {
            case ADD -> "\uE900"; // Icons.ADD
            case SUBTRACT -> "\uE924"; // Icons.MINUS
            case INTERSECT -> "\uE921"; // Icons.CONTAINER (交集)
        };
    }
    
    public String getDescription() {
        return switch (this) {
            case ADD -> "将新选择的方块添加到现有选区";
            case SUBTRACT -> "从现有选区中移除新选择的方块";
            case INTERSECT -> "只保留现有选区和新选择方块的交集";
        };
    }
}
