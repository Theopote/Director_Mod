package com.director.ui.properties.tool;

import com.director.core.selection.SelectionManager;

/**
 * 工具属性渲染上下文 - 供 ToolPropertyRendering 与各 ToolPropertyRenderer 使用
 * <p>
 * 提供缓冲区、选择管理器和「打开选择过滤器」回调，以便在 Properties 面板
 * 或独立 ToolProperties 面板中复用同一套渲染逻辑。
 * </p>
 *
 * @author Director Team
 */
public interface IToolPropertyRenderContext {

    SelectionManager getSelectionManager();

    float[] getFloatBuffer();

    int[] getIntBuffer1();

    int[] getIntBuffer2();

    /**
     * 请求打开选择过滤器面板；可为 null，表示不提供该能力（如来自 PropertyAdapter 时）。
     */
    Runnable getOnRequestShowFilterPanel();
}
