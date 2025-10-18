package com.chronoblocks.ui.adapters;

/**
 * 🎨 PropertyAdapter - 属性适配器接口
 *
 * <p>ChronoBlocks 属性系统的核心接口，为不同类型的对象提供统一的属性编辑能力。
 *
 * <h3>✨ 设计目标：</h3>
 * <ul>
 *     <li>统一属性编辑接口：为 Keyframe、Container、Effect 等对象提供一致的编辑体验</li>
 *     <li>类型安全：通过泛型确保编译时类型检查</li>
 *     <li>可扩展性：支持自定义适配器实现</li>
 *     <li>生命周期管理：提供初始化、更新、清理等完整生命周期</li>
 * </ul>
 *
 * <h3>📖 实现指南：</h3>
 * <pre>{@code
 * public class KeyframeAdapter implements PropertyAdapter<Keyframe> {
 *     
 *     @Override
 *     public boolean renderProperties(Keyframe keyframe) {
 *         boolean changed = false;
 *         
 *         // 渲染时间属性
 *         float[] time = {keyframe.getTime()};
 *         if (ImGui.dragFloat("时间", time, 0.1f)) {
 *             keyframe.setTime(time[0]);
 *             changed = true;
 *         }
 *         
 *         // 渲染其他属性...
 *         
 *         return changed;
 *     }
 *     
 *     @Override
 *     public void onPropertyChanged(Keyframe keyframe) {
 *         // 发布事件通知系统
 *         EventBus.post(new KeyframeChangedEvent(keyframe));
 *     }
 * }
 * }</pre>
 *
 * <h3>🔧 高级特性：</h3>
 * <ul>
 *     <li>动态更新：通过 {@link #update(Object, float)} 支持实时预览和动画</li>
 *     <li>验证机制：通过 {@link #validate(Object)} 确保数据有效性</li>
 *     <li>类型查询：通过 {@link #getTargetType()} 获取适配器支持的类型</li>
 *     <li>优先级系统：通过 {@link #getPriority()} 控制适配器选择顺序</li>
 * </ul>
 *
 * @param <T> 适配器支持的对象类型
 * @author ChronoBlocks Team
 * @version 4.5
 * @since 4.5
 */
public interface PropertyAdapter<T> {

    /**
     * 渲染属性编辑器 UI
     * <p>在此方法中使用 ImGui API 渲染属性编辑控件。
     *
     * <h4>实现注意事项：</h4>
     * <ul>
     *     <li>使用 ImGui 控件渲染属性（如 dragFloat、inputText 等）</li>
     *     <li>检测用户输入并更新对象属性</li>
     *     <li>返回 true 表示有属性被修改</li>
     *     <li>不要在此方法中调用 {@link #onPropertyChanged}，由框架自动调用</li>
     * </ul>
     *
     * @param target 要编辑的对象
     * @return true 如果有任何属性被修改
     */
    boolean renderProperties(T target);

    /**
     * 属性变更回调
     * <p>当 {@link #renderProperties} 返回 true 时自动调用。
     * 用于发布事件、更新缓存、触发重新渲染等后续操作。
     *
     * <h4>典型用途：</h4>
     * <ul>
     *     <li>发布属性变更事件到事件总线</li>
     *     <li>标记对象为"已修改"状态</li>
     *     <li>触发依赖对象的更新</li>
     *     <li>记录撤销/重做历史</li>
     * </ul>
     *
     * @param target 被修改的对象
     */
    void onPropertyChanged(T target);

    /**
     * 每帧更新
     * <p>在渲染前调用，允许适配器执行动态更新、动画预览等操作。
     *
     * <h4>使用场景：</h4>
     * <ul>
     *     <li>动画预览：实时更新动画效果</li>
     *     <li>缓存刷新：更新计算密集型的缓存数据</li>
     *     <li>状态同步：与外部系统同步状态</li>
     *     <li>性能优化：延迟加载或分帧计算</li>
     * </ul>
     *
     * <p>默认实现为空，子类可选择性覆写。
     *
     * @param target     要更新的对象
     * @param deltaTime  距离上一帧的时间增量（秒）
     */
    default void update(T target, float deltaTime) {
        // 默认不执行任何操作
    }

    /**
     * 验证对象属性
     * <p>检查对象的属性是否有效，用于防止非法数据。
     *
     * <h4>验证示例：</h4>
     * <ul>
     *     <li>检查必填字段是否为空</li>
     *     <li>验证数值是否在合法范围内</li>
     *     <li>检查引用完整性</li>
     *     <li>验证业务逻辑约束</li>
     * </ul>
     *
     * <p>默认返回 true（无验证）。
     *
     * @param target 要验证的对象
     * @return true 如果对象有效
     */
    default boolean validate(T target) {
        return true;
    }

    /**
     * 获取适配器支持的目标类型
     * <p>用于适配器注册表的类型匹配。
     *
     * <h4>实现建议：</h4>
     * <pre>{@code
     * @Override
     * public Class<Keyframe> getTargetType() {
     *     return Keyframe.class;
     * }
     * }</pre>
     *
     * @return 目标类型的 Class 对象
     */
    Class<T> getTargetType();

    /**
     * 获取适配器优先级
     * <p>当多个适配器都支持同一类型时，优先级高的会被选中。
     *
     * <h4>优先级规则：</h4>
     * <ul>
     *     <li>值越大，优先级越高</li>
     *     <li>默认优先级为 0</li>
     *     <li>通用适配器建议使用负值（如 -100）</li>
     *     <li>专用适配器建议使用正值（如 100）</li>
     * </ul>
     *
     * <p>默认返回 0。
     *
     * @return 优先级值
     */
    default int getPriority() {
        return 0;
    }

    /**
     * 初始化适配器
     * <p>在适配器注册到系统时调用，用于执行一次性初始化操作。
     *
     * <h4>典型用途：</h4>
     * <ul>
     *     <li>加载配置</li>
     *     <li>初始化缓存</li>
     *     <li>注册事件监听器</li>
     *     <li>预加载资源</li>
     * </ul>
     *
     * <p>默认实现为空。
     */
    default void initialize() {
        // 默认不执行任何操作
    }

    /**
     * 清理适配器资源
     * <p>在适配器从系统移除时调用，用于释放资源。
     *
     * <h4>清理内容：</h4>
     * <ul>
     *     <li>清理缓存</li>
     *     <li>注销事件监听器</li>
     *     <li>释放外部资源</li>
     *     <li>关闭连接</li>
     * </ul>
     *
     * <p>默认实现为空。
     */
    default void dispose() {
        // 默认不执行任何操作
    }

    /**
     * 检查适配器是否支持指定对象
     * <p>用于运行时类型检查，支持多态和继承关系。
     *
     * <h4>默认实现：</h4>
     * <pre>{@code
     * default boolean supports(Object obj) {
     *     return obj != null && getTargetType().isInstance(obj);
     * }
     * }</pre>
     *
     * @param obj 要检查的对象
     * @return true 如果适配器支持该对象
     */
    default boolean supports(Object obj) {
        return obj != null && getTargetType().isInstance(obj);
    }

    /**
     * 获取适配器的显示名称
     * <p>用于调试、日志记录和用户界面显示。
     *
     * <h4>默认实现：</h4>
     * <p>返回目标类型的简单类名 + " Adapter"
     *
     * @return 适配器显示名称
     */
    default String getDisplayName() {
        return getTargetType().getSimpleName() + " Adapter";
    }

    /**
     * 获取适配器的描述信息
     * <p>用于文档生成、工具提示等。
     *
     * <h4>默认实现：</h4>
     * <p>返回空字符串。
     *
     * @return 适配器描述
     */
    default String getDescription() {
        return "";
    }

    /**
     * 检查适配器是否可用
     * <p>允许适配器根据运行时条件（如许可证、功能开关等）决定是否启用。
     *
     * <h4>使用场景：</h4>
     * <ul>
     *     <li>功能开关控制</li>
     *     <li>许可证验证</li>
     *     <li>依赖检查</li>
     *     <li>环境兼容性检查</li>
     * </ul>
     *
     * <p>默认返回 true。
     *
     * @return true 如果适配器可用
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * 克隆对象（可选支持）
     * <p>用于实现撤销/重做、复制粘贴等功能。
     *
     * <h4>默认实现：</h4>
     * <p>抛出 UnsupportedOperationException，子类可选择性实现。
     *
     * @param target 要克隆的对象
     * @return 克隆后的对象
     * @throws UnsupportedOperationException 如果不支持克隆
     */
    default T clone(T target) {
        throw new UnsupportedOperationException(
            getDisplayName() + " does not support cloning");
    }

    /**
     * 序列化对象为字符串（可选支持）
     * <p>用于保存、导出、调试等场景。
     *
     * <h4>默认实现：</h4>
     * <p>返回对象的 toString() 结果。
     *
     * @param target 要序列化的对象
     * @return 序列化字符串
     */
    default String serialize(T target) {
        return target != null ? target.toString() : "null";
    }

    /**
     * 从字符串反序列化对象（可选支持）
     * <p>与 {@link #serialize(Object)} 配对使用。
     *
     * <h4>默认实现：</h4>
     * <p>抛出 UnsupportedOperationException。
     *
     * @param data 序列化数据
     * @return 反序列化后的对象
     * @throws UnsupportedOperationException 如果不支持反序列化
     */
    default T deserialize(String data) {
        throw new UnsupportedOperationException(
            getDisplayName() + " does not support deserialization");
    }
}

