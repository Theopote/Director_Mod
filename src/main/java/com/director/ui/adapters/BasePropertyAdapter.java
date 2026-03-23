package com.director.ui.adapters;

import com.director.ui.adapters.events.PropertyChangeNotifier;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🎨 BasePropertyAdapter - 属性适配器抽象基类
 *
 * <p>为所有属性适配器提供通用功能和便利方法，减少重复代码。
 *
 * <h3>✨ 提供的通用功能：</h3>
 * <ul>
 *     <li>日志记录：统一的日志管理</li>
 *     <li>UI 组件：常用的 ImGui 控件封装</li>
 *     <li>样式管理：统一的样式和间距</li>
 *     <li>验证辅助：常见的验证逻辑</li>
 *     <li>事件标记：变更追踪和通知</li>
 * </ul>
 *
 * <h3>📖 使用示例：</h3>
 * <pre>{@code
 * public class KeyframeAdapter extends BasePropertyAdapter<Keyframe> {
 *     
 *     @Override
 *     protected boolean doRenderProperties(Keyframe keyframe) {
 *         boolean changed = false;
 *         
 *         renderSectionHeader("时间属性");
 *         changed |= renderFloatProperty("时间", 
 *             keyframe::getTime, 
 *             keyframe::setTime, 
 *             0.01f, 0.0f, Float.MAX_VALUE);
 *         
 *         renderSectionHeader("插值属性");
 *         changed |= renderComboProperty("类型",
 *             keyframe::getInterpolationType,
 *             keyframe::setInterpolationType,
 *             "Linear", "Ease In", "Ease Out");
 *         
 *         return changed;
 *     }
 *     
 *     @Override
 *     public Class<Keyframe> getTargetType() {
 *         return Keyframe.class;
 *     }
 * }
 * }</pre>
 *
 * @param <T> 适配器支持的对象类型
 * @author Director Team
 * @version 4.5
 * @since 4.5
 */
public abstract class BasePropertyAdapter<T> implements PropertyAdapter<T> {

    protected final Logger logger;

    /** 是否显示调试信息 */
    protected boolean debugMode;

    /** 属性标签宽度 */
    protected float labelWidth = 120.0f;

    /** 是否使用紧凑布局 */
    protected boolean compactMode = false;

    /**
     * 构造函数
     */
    protected BasePropertyAdapter() {
        this.logger = LoggerFactory.getLogger(getClass());
        this.debugMode = Boolean.getBoolean("director.debug.properties");
    }

    // ==================== 核心方法 ====================

    @Override
    public final boolean renderProperties(T target) {
        if (target == null) {
            renderNullTarget();
            return false;
        }

        try {
            pushStyles();
            boolean changed = doRenderProperties(target);
            popStyles();
            return changed;
        } catch (Exception e) {
            logger.error("[{}] 渲染属性失败: {}", getDisplayName(), e.getMessage(), e);
            renderError(e);
            return false;
        }
    }

    /**
     * 子类实现此方法来渲染具体属性
     * <p>框架会自动处理空值检查、异常处理和样式管理。
     *
     * @param target 要渲染的对象
     * @return 是否有属性被修改
     */
    protected abstract boolean doRenderProperties(T target);

    @Override
    public void onPropertyChanged(T target) {
        if (debugMode) {
            logger.debug("[{}] 属性已修改: {}", getDisplayName(), target);
        }
        
        // 自动通知属性变更事件
        notifyPropertyChange(target, target);
        
        // 子类可覆写以添加自定义逻辑
    }
    
    /**
     * 通知属性变更（立即）
     * <p>子类可以调用此方法发送详细的属性变更事件。
     *
     * @param target   目标对象
     * @param newValue 新值
     */
    protected void notifyPropertyChange(T target, Object newValue) {
        PropertyChangeNotifier.get().notifyChange(target, "properties", null, newValue);
    }

    @Override
    public boolean validate(T target) {
        return target != null;
        // 子类可覆写以添加自定义验证
    }

    // ==================== UI 组件方法 ====================

    /**
     * 渲染节标题
     *
     * @param title 标题文本
     */
    protected void renderSectionHeader(String title) {
        ImGui.spacing();
        ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 0.7f, 1.0f, 1.0f);
        ImGui.text(title);
        ImGui.popStyleColor();
        ImGui.separator();
        if (!compactMode) {
            ImGui.spacing();
        }
    }

    /**
     * 渲染浮点数属性
     *
     * @param label    标签
     * @param getter   获取值的方法
     * @param setter   设置值的方法
     * @param speed    拖动速度
     * @param min      最小值
     * @param max      最大值
     * @return 是否有修改
     */
    protected boolean renderFloatProperty(String label, 
                                         FloatGetter getter, 
                                         FloatSetter setter,
                                         float speed, float min, float max) {
        float[] value = {getter.get()};
        
        ImGui.pushItemWidth(-labelWidth);
        boolean changed = ImGui.dragFloat("##" + label, value, speed, min, max, "%.3f");
        ImGui.popItemWidth();
        
        ImGui.sameLine();
        ImGui.text(label);
        
        if (changed) {
            setter.set(value[0]);
        }
        
        return changed;
    }

    /**
     * 渲染布尔属性
     *
     * @param label  标签
     * @param getter 获取值的方法
     * @param setter 设置值的方法
     * @return 是否有修改
     */
    protected boolean renderBoolProperty(String label,
                                        BoolGetter getter,
                                        BoolSetter setter) {
        boolean currentValue = getter.get();
        
        boolean changed = ImGui.checkbox(label, currentValue);
        
        if (changed) {
            setter.set(!currentValue); // 切换值
        }
        
        return changed;
    }

    /**
     * 渲染文本属性
     *
     * @param label  标签
     * @param getter 获取值的方法
     * @param setter 设置值的方法
     * @return 是否有修改
     */
    protected boolean renderTextProperty(String label,
                                        StringGetter getter,
                                        StringSetter setter) {
        return renderTextProperty(label, getter, setter, 256);
    }

    /**
     * 渲染文本属性（带长度限制）
     *
     * @param label     标签
     * @param getter    获取值的方法
     * @param setter    设置值的方法
     * @param maxLength 最大长度
     * @return 是否有修改
     */
    protected boolean renderTextProperty(String label,
                                        StringGetter getter,
                                        StringSetter setter,
                                        int maxLength) {
        String currentValue = getter.get();
        if (currentValue == null) {
            currentValue = "";
        }
        
        ImGui.text(label);
        ImGui.pushItemWidth(-1);
        
        ImString buffer = new ImString(currentValue, maxLength);
        boolean changed = ImGui.inputText("##" + label, buffer);
        ImGui.popItemWidth();
        
        if (changed) {
            setter.set(buffer.get());
        }
        
        return changed;
    }

    /**
     * 渲染下拉框属性
     *
     * @param label   标签
     * @param getter  获取当前索引的方法
     * @param setter  设置索引的方法
     * @param options 选项数组
     * @return 是否有修改
     */
    protected boolean renderComboProperty(String label,
                                         IntGetter getter,
                                         IntSetter setter,
                                         String... options) {
        ImInt current = new ImInt(getter.get());
        
        ImGui.text(label);
        ImGui.pushItemWidth(-1);
        boolean changed = ImGui.combo("##" + label, current, options);
        ImGui.popItemWidth();
        
        if (changed) {
            setter.set(current.get());
        }
        
        return changed;
    }

    /**
     * 渲染颜色属性（RGB）
     *
     * @param getter 获取颜色的方法（返回 float[3]）
     * @param setter 设置颜色的方法（接收 float[3]）
     * @return 是否有修改
     */
    protected boolean renderColorProperty(ColorGetter getter,
                                          ColorSetter setter) {
        float[] color = getter.get();
        
        ImGui.text("颜色");
        ImGui.pushItemWidth(-1);
        boolean changed = ImGui.colorEdit3("##" + "颜色", color);
        ImGui.popItemWidth();
        
        if (changed) {
            setter.set(color);
        }
        
        return changed;
    }

    /**
     * 渲染颜色属性（RGBA）
     *
     * @param getter 获取颜色的方法（返回 float[4]）
     * @param setter 设置颜色的方法（接收 float[4]）
     * @return 是否有修改
     */
    protected boolean renderColorAlphaProperty(ColorAlphaGetter getter,
                                               ColorAlphaSetter setter) {
        float[] color = getter.get();
        
        ImGui.text("颜色");
        ImGui.pushItemWidth(-1);
        boolean changed = ImGui.colorEdit4("##" + "颜色", color);
        ImGui.popItemWidth();
        
        if (changed) {
            setter.set(color);
        }
        
        return changed;
    }

    /**
     * 渲染只读文本
     *
     * @param label 标签
     * @param value 值
     */
    protected void renderReadOnlyText(String label, String value) {
        ImGui.text(label + ":");
        ImGui.sameLine();
        ImGui.textDisabled(value);
    }

    /**
     * 渲染帮助提示
     *
     * @param text 提示文本
     */
    protected void renderHelpMarker(String text) {
        ImGui.sameLine();
        ImGui.textDisabled("(?)");
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.pushTextWrapPos(ImGui.getFontSize() * 35.0f);
            ImGui.textUnformatted(text);
            ImGui.popTextWrapPos();
            ImGui.endTooltip();
        }
    }

    // ==================== 样式管理 ====================

    /**
     * 推入自定义样式
     */
    protected void pushStyles() {
        if (compactMode) {
            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 4.0f, 2.0f);
            ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 4.0f, 2.0f);
        }
    }

    /**
     * 弹出自定义样式
     */
    protected void popStyles() {
        if (compactMode) {
            ImGui.popStyleVar(2);
        }
    }

    // ==================== 错误处理 ====================

    /**
     * 渲染空对象提示
     */
    protected void renderNullTarget() {
        ImGui.textDisabled("对象为空");
    }

    /**
     * 渲染错误信息
     *
     * @param e 异常对象
     */
    protected void renderError(Exception e) {
        ImGui.textColored(1.0f, 0.3f, 0.3f, 1.0f, "❌ 渲染错误");
        ImGui.spacing();
        ImGui.textWrapped("错误: " + e.getMessage());
        
        if (ImGui.button("查看详情")) {
            logger.error("[{}] 详细错误信息:", getDisplayName(), e);
        }
    }

    // ==================== Getter/Setter 接口 ====================

    @FunctionalInterface
    protected interface FloatGetter {
        float get();
    }

    @FunctionalInterface
    protected interface FloatSetter {
        void set(float value);
    }

    @FunctionalInterface
    protected interface IntGetter {
        int get();
    }

    @FunctionalInterface
    protected interface IntSetter {
        void set(int value);
    }

    @FunctionalInterface
    protected interface BoolGetter {
        boolean get();
    }

    @FunctionalInterface
    protected interface BoolSetter {
        void set(boolean value);
    }

    @FunctionalInterface
    protected interface StringGetter {
        String get();
    }

    @FunctionalInterface
    protected interface StringSetter {
        void set(String value);
    }

    @FunctionalInterface
    protected interface ColorGetter {
        float[] get(); // RGB
    }

    @FunctionalInterface
    protected interface ColorSetter {
        void set(float[] color); // RGB
    }

    @FunctionalInterface
    protected interface ColorAlphaGetter {
        float[] get(); // RGBA
    }

    @FunctionalInterface
    protected interface ColorAlphaSetter {
        void set(float[] color); // RGBA
    }


    // ==================== 默认实现 ====================

    @Override
    public String getDisplayName() {
        return getTargetType().getSimpleName() + " 属性编辑器";
    }

    @Override
    public String getDescription() {
        return "用于编辑 " + getTargetType().getSimpleName() + " 对象的属性";
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public void initialize() {
        logger.debug("[{}] 适配器已初始化", getDisplayName());
    }

    @Override
    public void dispose() {
        logger.debug("[{}] 适配器已清理", getDisplayName());
    }
}

