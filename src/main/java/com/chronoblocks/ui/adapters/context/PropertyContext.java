package com.chronoblocks.ui.adapters.context;

import com.chronoblocks.ui.adapters.PropertyAdapter;
import com.chronoblocks.ui.adapters.PropertyAdapterRegistry;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🧩 PropertyContext - 属性上下文对象
 *
 * <p>用于在 ChronoBlocks 属性系统中存储：
 * <ul>
 *     <li>当前选中对象（selectedObject）</li>
 *     <li>对应的 PropertyAdapter 实例（adapter）</li>
 *     <li>属性变更状态（dirty）</li>
 *     <li>类型缓存与安全刷新逻辑</li>
 * </ul>
 *
 * <h3>✨ 设计目标：</h3>
 * <ul>
 *     <li>提供选中对象到适配器的桥梁</li>
 *     <li>缓存适配器实例，避免重复查找</li>
 *     <li>跟踪属性是否被修改（dirty）</li>
 *     <li>支持动态切换不同对象类型</li>
 * </ul>
 *
 * <h3>📖 使用示例：</h3>
 * <pre>{@code
 * private final PropertyContext context = new PropertyContext();
 *
 * // 设置选中对象（自动匹配适配器）
 * context.setSelectedObject(keyframe);
 *
 * // 渲染属性编辑器
 * if (context.render()) {
 *     // 属性已修改
 *     notifyPropertyChanged();
 * }
 *
 * // 保存后重置状态
 * context.markSaved();
 * }</pre>
 *
 * @author ChronoBlocks Team
 * @version 4.5
 * @since 4.5
 */
public class PropertyContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyContext.class);

    /** 当前选中的对象（可能为 Keyframe / Container / Effect 等） */
    private Object selectedObject;

    /** 当前对象对应的适配器（可能为通用或专用类型） */
    private PropertyAdapter<?> adapter;

    /** 当前属性是否有修改 */
    private boolean dirty = false;

    /** 上一次选中对象的类型缓存（避免频繁类型切换） */
    private Class<?> lastType;

    /**
     * 设置当前选中对象（外部调用）
     * <p>自动匹配对应的 PropertyAdapter，如果对象类型发生变化，会重新查找适配器。
     *
     * @param obj 当前选中对象，可以为 null
     */
    public void setSelectedObject(Object obj) {
        if (obj == selectedObject) {
            return; // 无变化则忽略
        }

        this.selectedObject = obj;
        this.dirty = false;

        if (obj == null) {
            this.adapter = null;
            this.lastType = null;
            LOGGER.debug("[PropertyContext] 属性上下文已清空");
            return;
        }

        Class<?> type = obj.getClass();
        if (type != lastType) {
            this.adapter = PropertyAdapterRegistry.get().findAdapter(type);
            this.lastType = type;
            
            if (adapter != null) {
                LOGGER.info("[PropertyContext] 属性上下文切换 → {} (适配器: {})", 
                    type.getSimpleName(), 
                    adapter.getClass().getSimpleName());
            } else {
                LOGGER.warn("[PropertyContext] 未找到类型 {} 的适配器", type.getSimpleName());
            }
        }
    }

    /**
     * 渲染当前选中对象的属性编辑器。
     * <p>自动调用匹配的适配器进行渲染。
     *
     * @return 是否有属性被修改
     */
    public boolean render() {
        if (selectedObject == null) {
            return renderEmpty();
        }

        if (adapter == null) {
            return renderNoAdapter();
        }

        try {
            boolean changed = renderWithAdapter();
            if (changed) {
                dirty = true;
                LOGGER.trace("[PropertyContext] 属性已修改: {}", selectedObject.getClass().getSimpleName());
            }
            return changed;
        } catch (Exception e) {
            LOGGER.error("[PropertyContext] 属性渲染异常: {}", e.getMessage(), e);
            renderError(e);
            return false;
        }
    }

    /**
     * 使用当前适配器渲染
     * <p>此方法会调用适配器的 renderProperties 方法，并在属性变化时触发 onPropertyChanged 回调。
     *
     * @return 属性是否发生变化
     */
    @SuppressWarnings("unchecked")
    private boolean renderWithAdapter() {
        PropertyAdapter<Object> genericAdapter = (PropertyAdapter<Object>) adapter;
        boolean changed = genericAdapter.renderProperties(selectedObject);
        
        if (changed) {
            genericAdapter.onPropertyChanged(selectedObject);
        }
        
        return changed;
    }

    /**
     * 渲染空面板（无选中对象）
     */
    private boolean renderEmpty() {
        ImGui.textDisabled("未选中任何对象");
        ImGui.spacing();
        ImGui.textDisabled("请在时间轴或画布中选择对象以编辑其属性");
        return false;
    }

    /**
     * 渲染没有匹配适配器的对象
     */
    private boolean renderNoAdapter() {
        ImGui.textColored(1.0f, 0.8f, 0.3f, 1.0f,
                "⚠ 无可用的属性适配器");
        ImGui.spacing();
        ImGui.text("对象类型: " + selectedObject.getClass().getSimpleName());
        ImGui.textDisabled("请确保已注册该类型的适配器");
        return false;
    }

    /**
     * 渲染错误信息
     *
     * @param e 捕获的异常
     */
    private void renderError(Exception e) {
        ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "❌ 渲染错误");
        ImGui.spacing();
        ImGui.textWrapped("错误信息: " + e.getMessage());
        
        if (ImGui.button("查看详情")) {
            LOGGER.error("[PropertyContext] 详细错误堆栈:", e);
        }
    }

    /**
     * 获取当前选中对象
     *
     * @return 当前选中对象，可能为 null
     */
    public Object getSelectedObject() {
        return selectedObject;
    }

    /**
     * 返回当前匹配的适配器（可能为 null）
     *
     * @return 当前适配器实例
     */
    public PropertyAdapter<?> getAdapter() {
        return adapter;
    }

    /**
     * 当前属性是否已被修改
     *
     * @return true 如果有未保存的修改
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * 标记当前属性已保存（重置 dirty 状态）
     */
    public void markSaved() {
        this.dirty = false;
        LOGGER.debug("[PropertyContext] 属性已保存，dirty 状态已重置");
    }

    /**
     * 手动标记为 dirty（外部修改时使用）
     */
    public void markDirty() {
        this.dirty = true;
        LOGGER.trace("[PropertyContext] 属性已标记为 dirty");
    }

    /**
     * 检查当前上下文是否有效
     * <p>有效的上下文需要同时满足：选中对象不为 null，且找到了对应的适配器。
     *
     * @return true 如果上下文有效
     */
    public boolean isValid() {
        return selectedObject != null && adapter != null;
    }

    /**
     * 每帧更新（供 UIManager 调用）
     * <p>允许适配器在渲染前做动画或动态刷新。
     *
     * @param deltaTime 距离上一帧的时间增量（秒）
     */
    @SuppressWarnings("unchecked")
    public void tick(float deltaTime) {
        if (selectedObject == null || adapter == null) {
            return;
        }
        
        try {
            PropertyAdapter<Object> genericAdapter = (PropertyAdapter<Object>) adapter;
            genericAdapter.update(selectedObject, deltaTime);
        } catch (Exception e) {
            LOGGER.warn("[PropertyContext] 属性适配器更新失败: {}", e.getMessage());
        }
    }

    /**
     * 清除当前上下文
     * <p>释放选中对象、适配器引用，并重置所有状态。
     */
    public void clear() {
        selectedObject = null;
        adapter = null;
        dirty = false;
        lastType = null;
        LOGGER.info("[PropertyContext] 上下文已清空");
    }

    /**
     * 获取当前对象的类型
     *
     * @return 当前对象的类型，如果没有选中对象则返回 null
     */
    public Class<?> getCurrentType() {
        return lastType;
    }

    /**
     * 强制刷新适配器（即使对象类型未变化）
     * <p>用于适配器注册表更新后的同步。
     */
    public void refreshAdapter() {
        if (selectedObject == null) {
            return;
        }
        
        Class<?> type = selectedObject.getClass();
        this.adapter = PropertyAdapterRegistry.get().findAdapter(type);
        this.lastType = type;
        
        LOGGER.debug("[PropertyContext] 适配器已强制刷新");
    }

    @Override
    public String toString() {
        return "PropertyContext{" +
                "selectedObject=" + (selectedObject != null ? selectedObject.getClass().getSimpleName() : "null") +
                ", adapter=" + (adapter != null ? adapter.getClass().getSimpleName() : "null") +
                ", dirty=" + dirty +
                ", valid=" + isValid() +
                '}';
    }
}

