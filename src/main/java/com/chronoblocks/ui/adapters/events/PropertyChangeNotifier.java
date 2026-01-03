package com.chronoblocks.ui.adapters.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * ⚙️ PropertyChangeNotifier - 属性变更事件通知器
 * --------------------------------
 * ChronoBlocks 属性变更事件系统。
 *
 * <h3>✨ 核心功能：</h3>
 * <ul>
 *     <li>🔔 监听属性变更：当属性在 PropertyAdapter 编辑中被修改时，广播事件</li>
 *     <li>🧱 绑定目标类型：支持监听某一类对象（如 Container、Effect、Camera）</li>
 *     <li>🧩 模块级回调：Timeline、Renderer、SceneView 可注册监听器</li>
 *     <li>🔄 UI 同步：让属性面板、时间线与渲染器保持一致</li>
 *     <li>🧠 缓存优化：使用弱引用避免内存泄漏</li>
 * </ul>
 *
 * <h3>📖 使用示例：</h3>
 * <pre>{@code
 * // 1. 添加全局监听器
 * PropertyChangeNotifier.get().addGlobalListener(event -> {
 *     System.out.println("属性更新: " + event.getPropertyName());
 *     System.out.println("旧值: " + event.getOldValue());
 *     System.out.println("新值: " + event.getNewValue());
 * });
 *
 * // 2. 添加类型监听器（只监听 Container）
 * PropertyChangeNotifier.get().addTypeListener(Container.class, event -> {
 *     if (event.getPropertyName().startsWith("position")) {
 *         timeline.requestRepaint();
 *     }
 * });
 *
 * // 3. 添加对象监听器（只监听特定对象）
 * Container myContainer = ...;
 * PropertyChangeNotifier.get().addObjectListener(myContainer, event -> {
 *     System.out.println("我的容器属性变化了！");
 * });
 *
 * // 4. 在适配器中通知变更
 * float oldValue = target.getOpacity();
 * if (ImGui.sliderFloat("Opacity", opacity)) {
 *     target.setOpacity(opacity[0]);
 *     PropertyChangeNotifier.get().notifyChange(
 *         target, "opacity", oldValue, opacity[0]);
 * }
 * }</pre>
 *
 * <h3>🔧 三层监听器系统：</h3>
 * <table border="1" cellpadding="5">
 *   <tr>
 *     <th>层级</th>
 *     <th>范围</th>
 *     <th>用途</th>
 *   </tr>
 *   <tr>
 *     <td>🌍 全局监听器</td>
 *     <td>所有对象</td>
 *     <td>日志记录、全局状态同步</td>
 *   </tr>
 *   <tr>
 *     <td>🎯 类型监听器</td>
 *     <td>特定类型</td>
 *     <td>模块级更新（如所有容器变化触发重绘）</td>
 *   </tr>
 *   <tr>
 *     <td>🔍 对象监听器</td>
 *     <td>特定实例</td>
 *     <td>精确跟踪（如监听当前编辑的关键帧）</td>
 *   </tr>
 * </table>
 *
 * @author ChronoBlocks Team
 * @version 4.5
 * @since 4.5
 */
public class PropertyChangeNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyChangeNotifier.class);

    // ==================== 单例实例 ====================

    private static final PropertyChangeNotifier INSTANCE = new PropertyChangeNotifier();

    public static PropertyChangeNotifier get() {
        return INSTANCE;
    }

    // ==================== 内部数据结构 ====================

    /** 全局监听器 */
    private final Set<WeakReference<Consumer<PropertyChangeEvent>>> globalListeners =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** 类型级监听器（如 Container、Camera 等） */
    private final Map<Class<?>, Set<WeakReference<Consumer<PropertyChangeEvent>>>> typeListeners =
            new ConcurrentHashMap<>();

    /** 对象级监听器（精确到实例） */
    private final Map<Integer, Set<WeakReference<Consumer<PropertyChangeEvent>>>> objectListeners =
            new ConcurrentHashMap<>();

    /** 是否启用调试日志 */
    private boolean debugMode;

    /**
     * 私有构造函数
     */
    private PropertyChangeNotifier() {
        this.debugMode = Boolean.getBoolean("chronoblocks.debug.events");
    }


    /**
     * 清理失效引用
     * <p>移除所有已被GC的弱引用。
     */
    private void cleanup() {
        globalListeners.removeIf(ref -> ref.get() == null);
        typeListeners.values().forEach(s -> s.removeIf(ref -> ref.get() == null));
        objectListeners.values().forEach(s -> s.removeIf(ref -> ref.get() == null));
    }

    // ==================== 通知派发 ====================

    /**
     * 通知属性变化（主入口）
     *
     * @param target       目标对象
     * @param propertyName 属性名称
     * @param oldValue     旧值
     * @param newValue     新值
     */
    public void notifyChange(Object target, String propertyName, Object oldValue, Object newValue) {
        if (target == null || propertyName == null) {
            return;
        }

        PropertyChangeEvent event = new PropertyChangeEvent(target, propertyName, oldValue, newValue);
        dispatch(event);

        if (debugMode) {
            LOGGER.debug("[PropertyChangeNotifier] {}", event);
        }
    }

    /**
     * 实际派发逻辑
     *
     * @param event 属性变更事件
     */
    private void dispatch(PropertyChangeEvent event) {
        // 清理失效引用
        cleanup();

        // 派发给全局监听器
        for (WeakReference<Consumer<PropertyChangeEvent>> ref : globalListeners) {
            Consumer<PropertyChangeEvent> listener = ref.get();
            if (listener != null) {
                try {
                    listener.accept(event);
                } catch (Exception e) {
                    LOGGER.error("[PropertyChangeNotifier] 全局监听器异常", e);
                }
            }
        }

        // 派发给类型监听器
        typeListeners.forEach((cls, set) -> {
            if (cls.isAssignableFrom(event.getTarget().getClass())) {
                for (WeakReference<Consumer<PropertyChangeEvent>> ref : set) {
                    Consumer<PropertyChangeEvent> listener = ref.get();
                    if (listener != null) {
                        try {
                            listener.accept(event);
                        } catch (Exception e) {
                            LOGGER.error("[PropertyChangeNotifier] 类型监听器异常", e);
                        }
                    }
                }
            }
        });

        // 派发给对象监听器
        int id = System.identityHashCode(event.getTarget());
        Set<WeakReference<Consumer<PropertyChangeEvent>>> objSet = objectListeners.get(id);
        if (objSet != null) {
            for (WeakReference<Consumer<PropertyChangeEvent>> ref : objSet) {
                Consumer<PropertyChangeEvent> listener = ref.get();
                if (listener != null) {
                    try {
                        listener.accept(event);
                    } catch (Exception e) {
                        LOGGER.error("[PropertyChangeNotifier] 对象监听器异常", e);
                    }
                }
            }
        }
    }

    // ==================== 事件类型定义 ====================

    /**
     * 属性变更事件
     */
    public static class PropertyChangeEvent {
        private final Object target;
        private final String propertyName;
        private final Object oldValue;
        private final Object newValue;
        private final long timestamp;

        /**
         * 构造函数
         *
         * @param target       目标对象
         * @param propertyName 属性名称
         * @param oldValue     旧值
         * @param newValue     新值
         */
        public PropertyChangeEvent(Object target, String propertyName, Object oldValue, Object newValue) {
            this.target = target;
            this.propertyName = propertyName;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.timestamp = System.currentTimeMillis();
        }

        /**
         * 获取目标对象
         *
         * @return 目标对象
         */
        public Object getTarget() {
            return target;
        }


        @Override
        public String toString() {
            return String.format("[PropertyChangeEvent] %s.%s: %s → %s (on %s)",
                    target.getClass().getSimpleName(),
                    propertyName,
                    oldValue,
                    newValue,
                    new Date(timestamp));
        }
    }

}

