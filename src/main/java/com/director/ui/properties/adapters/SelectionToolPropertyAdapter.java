package com.director.ui.properties.adapters;

import com.director.core.selection.SelectionManager;
import com.director.core.selection.SelectionTool;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.tool.IToolPropertyRenderContext;
import com.director.ui.properties.tool.ToolPropertyRendering;

/**
 * 选择工具属性适配器 - 将 SelectionTool 作为属性面板中的「对象」展示
 * <p>
 * 在 PropertyContext.TOOL 时由 PropertiesPanel 选用，复用 ToolPropertyRendering
 * 的渲染逻辑；若未注入「打开选择过滤器」回调，则 Brush 的「编辑过滤器」按钮不显示。
 * </p>
 *
 * @author Director Team
 */
public final class SelectionToolPropertyAdapter implements IPropertyAdapter<SelectionTool> {

    private final float[] floatBuffer = new float[1];
    private final int[] intBuffer1 = new int[1];
    private final int[] intBuffer2 = new int[1];

    private final IToolPropertyRenderContext context = new IToolPropertyRenderContext() {
        @Override
        public SelectionManager getSelectionManager() {
            return SelectionManager.get();
        }
        @Override
        public float[] getFloatBuffer() { return floatBuffer; }
        @Override
        public int[] getIntBuffer1() { return intBuffer1; }
        @Override
        public int[] getIntBuffer2() { return intBuffer2; }
        @Override
        public Runnable getOnRequestShowFilterPanel() {
            return com.director.ui.properties.PropertyAdapterContext.getShowFilterPanelRunnableStatic();
        }
    };

    @Override
    public Class<SelectionTool> getTargetType() {
        return SelectionTool.class;
    }

    @Override
    public int getPriority() { return 100; }

    @Override
    public String getAdapterName() {
        return "工具属性";
    }

    @Override
    public boolean renderProperties(SelectionTool target) {
        ToolPropertyRendering.render(target, context);
        return false;
    }
}
