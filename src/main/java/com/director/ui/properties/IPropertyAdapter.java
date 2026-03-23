package com.director.ui.properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🧩 IPropertyAdapter&lt;T&gt; 接口 - Director Properties System v4.9
 * <p>
 * 属性适配器（PropertyAdapter）定义了一个对象在属性面板中如何被编辑与展示。
 * 它是 Director 属性系统的基础抽象层。
 * </p>
 *
 * <h3>✨ 核心职责</h3>
 * <ul>
 *   <li>🎯 提供目标类型（getTargetType）</li>
 *   <li>🎨 渲染属性编辑 UI（renderProperties）</li>
 *   <li>🔄 当属性变化时进行同步（onPropertyChanged）</li>
 *   <li>⚙️ 可根据上下文进行扩展（update / reset / validate）</li>
 * </ul>
 * 
 * <h3>📋 方法概览</h3>
 * <table border="1" cellpadding="5" cellspacing="0">
 *   <tr>
 *     <th>方法</th>
 *     <th>功能</th>
 *     <th>是否必需</th>
 *   </tr>
 *   <tr>
 *     <td>getTargetType()</td>
 *     <td>指定适配器适用的类型</td>
 *     <td>✅ 必需</td>
 *   </tr>
 *   <tr>
 *     <td>renderProperties()</td>
 *     <td>渲染 UI（核心）</td>
 *     <td>✅ 必需</td>
 *   </tr>
 *   <tr>
 *     <td>getAdapterName()</td>
 *     <td>获取显示名称</td>
 *     <td>⚙️ 可选</td>
 *   </tr>
 *   <tr>
 *     <td>supports()</td>
 *     <td>复杂类型匹配检查</td>
 *     <td>⚙️ 可选</td>
 *   </tr>
 *   <tr>
 *     <td>onPropertyChanged()</td>
 *     <td>属性更新回调</td>
 *     <td>⚙️ 可选</td>
 *   </tr>
 *   <tr>
 *     <td>update()</td>
 *     <td>每帧刷新（动画场景用）</td>
 *     <td>⚙️ 可选</td>
 *   </tr>
 *   <tr>
 *     <td>resetToDefault()</td>
 *     <td>重置为默认值</td>
 *     <td>⚙️ 可选</td>
 *   </tr>
 *   <tr>
 *     <td>validate()</td>
 *     <td>导出前合法性验证</td>
 *     <td>⚙️ 可选</td>
 *   </tr>
 *   <tr>
 *     <td>getPriority()</td>
 *     <td>多适配器竞争同一类型时，Registry 按此值择优；值越大越优先</td>
 *     <td>⚙️ 可选（默认 0）</td>
 *   </tr>
 * </table>
 * 
 * <h3>🧠 使用示例</h3>
 * <pre>{@code
 * public class Vector3fPropertyAdapter implements IPropertyAdapter<Vector3f> {
 *     @Override
 *     public Class<Vector3f> getTargetType() {
 *         return Vector3f.class;
 *     }
 *     
 *     @Override
 *     public boolean renderProperties(Vector3f vec) {
 *         float[] values = {vec.x, vec.y, vec.z};
 *         boolean changed = ImGui.dragFloat3("XYZ", values, 0.05f);
 *         if (changed) {
 *             vec.set(values[0], values[1], values[2]);
 *             onPropertyChanged(vec);
 *         }
 *         return changed;
 *     }
 *     
 *     @Override
 *     public void onPropertyChanged(Vector3f vec) {
 *         // 标记 dirty 状态，触发重绘
 *         SceneManager.markDirty();
 *     }
 * }
 * }</pre>
 * 
 * @param <T> 适配器处理的目标类型
 * @version 4.9
 * @since 2025-10-16
 * @author Director Team
 */
public interface IPropertyAdapter<T> {
    
    /** 日志记录器（可选使用） */
    Logger LOGGER = LoggerFactory.getLogger(IPropertyAdapter.class);
    
    // ========================================
    // ✅ 核心必需方法
    // ========================================
    
    /**
     * 🎨 渲染属性编辑 UI
     * <p>
     * 在此方法中，适配器应使用 ImGui 命令绘制属性字段。
     * ❗ 只渲染，不做上下文判断；是否渲染由 PropertyContextService + Registry 决定。
     * </p>
     *
     * @param target 当前被选中的对象实例
     * @return 如果有任何属性被修改则返回 true，否则返回 false
     */
    boolean renderProperties(T target);

    /**
     * 🎨 渲染属性编辑 UI（传入 AdapterContext，用于 scale/readOnly/打开过滤器等）
     * <p>
     * 默认委托给 {@link #renderProperties(Object)}；子类可覆写以使用 ctx 的 {@code getUiScale()}、
     * {@code isReadOnly()}、{@code getShowFilterPanelRunnable()} 等。
     * </p>
     *
     * @param target  当前被选中的对象实例
     * @param adapterContext 渲染辅助（可为 null，表示使用默认）
     * @return 如果有任何属性被修改则返回 true，否则返回 false
     */
    default boolean renderProperties(T target, PropertyAdapterContext adapterContext) {
        return renderProperties(target);
    }
    
    /**
     * 🎯 获取此适配器适用的目标类型
     * <p>
     * 例如：Vector3fPropertyAdapter → Vector3f.class
     * </p>
     * 
     * @return 目标类型的 Class 对象
     */
    Class<T> getTargetType();
    
    // ========================================
    // ⚙️ 可选扩展方法
    // ========================================
    
    /**
     * 📐 获取适配器优先级（多适配器竞争同一 target 时，Registry 按此择优）
     * <p>
     * 值越大越优先。专用适配器建议 100，通用/回退建议 -100，默认 0。
     * 仅当多个适配器同时匹配（继承或 supports）时使用；Bootstrap 只做注册，不负责排序。
     * </p>
     *
     * @return 优先级，默认 0
     */
    default int getPriority() {
        return 0;
    }

    /**
     * 📝 获取适配器的显示名称
     * <p>
     * 用于在 UI 中显示适配器标题。
     * 默认实现返回类型的简单名称 + " Properties"。
     * </p>
     * 
     * @return 显示名称，例如 "Keyframe Properties"
     */
    default String getAdapterName() {
        Class<T> type = getTargetType();
        if (type == null || type == Object.class) {
            return "属性编辑器";
        }
        return type.getSimpleName() + " Properties";
    }
    
    /**
     * 🔍 检查是否支持给定对象
     * <p>
     * 用于更复杂的类型匹配场景，例如：
     * <ul>
     *   <li>反射式适配器（需要检查类名包含特定字符串）</li>
     *   <li>多态类型（需要检查对象是否实现特定接口）</li>
     *   <li>泛型容器（需要检查元素类型）</li>
     * </ul>
     * </p>
     *
     * <p>❗ 不得在此方法中访问 Registry / Service；仅做纯函数判断。
     *
     * @param target 要检查的对象
     * @return 如果支持则返回 true
     */
    default boolean supports(Object target) {
        if (target == null) {
            return false;
        }
        Class<T> type = getTargetType();
        if (type == null) {
            return false;
        }
        return type.isAssignableFrom(target.getClass());
    }

    /**
     * 🔍 检查是否支持给定上下文与目标（Registry 按 ctx+target 过滤时调用）
     * <p>
     * 默认委托给 {@link #supports(Object)}；子类可覆写以实现「仅 TOOL」「仅 TIMELINE」等语义。
     * ❗ 不得在此方法中访问 Registry / Service。
     * </p>
     *
     * @param context 当前 PropertyContext，可为 null（表示不按 context 过滤）
     * @param target  要检查的对象
     * @return 如果支持则返回 true
     */
    default boolean supports(PropertyContext context, Object target) {
        return supports(target);
    }
    
    /**
     * 🔄 当属性修改后触发
     * <p>
     * 用于同步或标记 dirty 状态。
     * 可选实现：如果不需要响应，可使用默认实现（空操作）。
     * </p>
     * 
     * <h4>典型用例：</h4>
     * <ul>
     *   <li>标记场景为 dirty 状态，触发重绘</li>
     *   <li>触发撤销/重做命令记录</li>
     *   <li>发布事件通知其他组件</li>
     *   <li>验证属性合法性</li>
     * </ul>
     * 
     * @param target 当前对象
     */
    default void onPropertyChanged(T target) {
        LOGGER.debug("[PropertyAdapter] 属性已更新: {}", 
            target != null ? target.getClass().getSimpleName() : "null");
    }
    
    /**
     * ⏱️ 每帧更新
     * <p>
     * 用于在每帧更新（例如动画状态、动态属性刷新）。
     * 可选实现。
     * </p>
     * 
     * <h4>典型用例：</h4>
     * <ul>
     *   <li>更新实时预览</li>
     *   <li>刷新动画状态</li>
     *   <li>计算派生属性</li>
     *   <li>更新统计信息</li>
     * </ul>
     * 
     * @param target 当前对象
     * @param deltaTime 每帧时间（秒）
     */
    default void update(T target, float deltaTime) {
        // 默认空实现
    }
    
    /**
     * 🔄 重置对象属性到默认值
     * <p>
     * 可选实现。
     * </p>
     * 
     * <h4>典型用例：</h4>
     * <ul>
     *   <li>重置关键帧到起始状态</li>
     *   <li>清空容器内容</li>
     *   <li>恢复默认材质</li>
     * </ul>
     * 
     * @param target 当前对象
     */
    default void resetToDefault(T target) {
        LOGGER.debug("[PropertyAdapter] 属性重置: {}", 
            target != null ? target.getClass().getSimpleName() : "null");
    }
    
    /**
     * ✅ 属性合法性验证
     * <p>
     * 在保存或导出前，可进行属性合法性检查。
     * 可选实现。
     * </p>
     * 
     * <h4>典型用例：</h4>
     * <ul>
     *   <li>检查时间戳是否为负数</li>
     *   <li>检查向量是否为 NaN</li>
     *   <li>检查引用是否为 null</li>
     *   <li>检查范围是否合法</li>
     * </ul>
     * 
     * @param target 当前对象
     * @return 如果合法则返回 true，否则返回 false
     */
    default boolean validate(T target) {
        return true; // 默认总是合法
    }
    
    /**
     * 📊 获取调试信息
     * <p>
     * 用于调试和日志记录。
     * 可选实现。
     * </p>
     * 
     * @param target 当前对象
     * @return 调试信息字符串
     */
    default String getDebugInfo(T target) {
        if (target == null) {
            return "[PropertyAdapter] Target is null";
        }
        return String.format("[PropertyAdapter] %s - %s", 
            getAdapterName(), 
            target.getClass().getName());
    }
}

