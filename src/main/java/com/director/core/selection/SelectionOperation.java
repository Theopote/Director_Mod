package com.director.core.selection;

import com.director.ui.theme.Icons;

/**
 * 选择操作枚举
 * 定义 New / Add / Subtract / Intersect 四种逻辑
 * 
 * @author Director Team
 * @version 2.0
 */
public enum SelectionOperation {
    NEW,        // 新建选区（清除现有选区后添加）
    ADD,        // 合并到现有选区
    SUBTRACT,   // 从现有选区移除
    INTERSECT;  // 与现有选区求交集

    public String getDisplayName() {
        return switch (this) {
            case NEW -> "新建选区";
            case ADD -> "加选";
            case SUBTRACT -> "减选";
            case INTERSECT -> "交集";
        };
    }
    
    public String getIcon() {
        return switch (this) {
            case NEW -> Icons.FILE;
            case ADD -> Icons.ADD;
            case SUBTRACT -> Icons.MINUS;
            case INTERSECT -> Icons.COPY;
        };
    }
    
    public String getDescription() {
        return switch (this) {
            case NEW -> "清除现有选区，创建新的选区";
            case ADD -> "将新选择的方块添加到现有选区";
            case SUBTRACT -> "从现有选区中移除新选择的方块";
            case INTERSECT -> "只保留现有选区和新选择方块的交集";
        };
    }
}
