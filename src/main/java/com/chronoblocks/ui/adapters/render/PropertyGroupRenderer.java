package com.chronoblocks.ui.adapters.render;

import com.chronoblocks.ui.adapters.util.PropertySearchFilter;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.flag.ImGuiCol;

import java.util.*;

/**
 * 🧱 PropertyGroupRenderer - 属性分组渲染器
 * -----------------------
 * ChronoBlocks 属性面板的分组渲染器。
 *
 * <h3>✨ 核心功能：</h3>
 * <ul>
 *     <li>按类别（Transform / Animation / Rendering / Physics 等）分组显示属性</li>
 *     <li>自动生成折叠面板</li>
 *     <li>与 PropertySearchFilter 联动</li>
 *     <li>提供渲染回调接口</li>
 *     <li>支持嵌套分组</li>
 *     <li>分组颜色管理</li>
 * </ul>
 *
 * <h3>📖 使用示例：</h3>
 * <pre>{@code
 * PropertyGroupRenderer renderer = new PropertyGroupRenderer(searchFilter);
 *
 * // 变换分组
 * if (renderer.beginGroup("🎯 变换", Arrays.asList("transform"))) {
 *     renderer.renderProperty("位置", 
 *         Arrays.asList("position", "transform"),
 *         Arrays.asList("transform"),
 *         () -> ImGui.dragFloat3("Position", pos, 0.1f));
 *     
 *     renderer.renderProperty("旋转",
 *         Arrays.asList("rotation", "transform"),
 *         Arrays.asList("transform"),
 *         () -> ImGui.dragFloat3("Rotation", rot, 1.0f));
 *     
 *     renderer.endGroup();
 * }
 *
 * // 动画分组
 * if (renderer.beginGroup("📈 动画", Arrays.asList("animation"))) {
 *     renderer.renderProperty("时长",
 *         Arrays.asList("duration"),
 *         Arrays.asList("animation"),
 *         () -> ImGui.dragFloat("Duration", duration, 0.1f));
 *     
 *     renderer.endGroup();
 * }
 * }</pre>
 *
 * @author ChronoBlocks Team
 * @version 4.5
 * @since 4.5
 */
public class PropertyGroupRenderer {

    /** 当前搜索过滤器 */
    private final PropertySearchFilter filter;

    /** 分组栈，用于嵌套折叠层 */
    private final Deque<String> groupStack = new ArrayDeque<>();

    /** 已打开的分组状态 */
    private final Map<String, Boolean> expandedGroups = new HashMap<>();

    /** 可选：全局样式控制 */
    private boolean highlightMatchedGroups = true;

    /** 分组颜色缓存 */
    private final Map<String, Integer> groupColors = new HashMap<>();

    /** 是否全局展开所有组 */
    private boolean expandAll = false;

    /**
     * 构造函数
     *
     * @param filter 搜索过滤器
     */
    public PropertyGroupRenderer(PropertySearchFilter filter) {
        this.filter = filter != null ? filter : new PropertySearchFilter();
    }

    // ==================== 分组生命周期管理 ====================

    /**
     * 开始一个属性分组
     *
     * @param groupName 分组名称
     * @return true 如果分组应该显示且已展开
     */
    public boolean beginGroup(String groupName) {
        return beginGroup(groupName, Collections.emptyList());
    }

    /**
     * 开始一个带标签的分组
     *
     * @param groupName 分组名称
     * @param tags      分组标签
     * @return true 如果分组应该显示且已展开
     */
    public boolean beginGroup(String groupName, Collection<String> tags) {
        // 检查是否应该渲染这个分组
        if (!shouldRenderGroup(groupName, tags)) {
            return false;
        }

        // 推入样式
        ImGui.pushStyleColor(ImGuiCol.Header, 0.2f, 0.25f, 0.3f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0.3f, 0.35f, 0.4f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, 0.4f, 0.45f, 0.5f, 1.0f);

        // 确定展开标志
        int flags = ImGuiTreeNodeFlags.None;
        if (expandAll || expandedGroups.getOrDefault(groupName, false)) {
            flags |= ImGuiTreeNodeFlags.DefaultOpen;
        }

        // 渲染折叠头
        boolean open = ImGui.collapsingHeader(groupName + "##group_" + groupStack.size(), flags);

        // 弹出样式
        ImGui.popStyleColor(3);

        if (open) {
            groupStack.push(groupName);
            expandedGroups.put(groupName, true);
            ImGui.indent();
        } else {
            expandedGroups.put(groupName, false);
        }

        return open;
    }

    /**
     * 结束当前属性分组
     */
    public void endGroup() {
        if (!groupStack.isEmpty()) {
            groupStack.pop();
            ImGui.unindent();
        }
    }

    // ==================== 属性渲染 ====================

    /**
     * 渲染一个属性项。
     *
     * @param propertyName  属性名
     * @param tags          属性标签（可为空）
     * @param categories    类别（可为空）
     * @param renderAction  属性渲染函数
     */
    public void renderProperty(String propertyName,
                                Collection<String> tags,
                                Collection<String> categories,
                                Runnable renderAction) {
        if (!filter.matches(propertyName, tags, categories)) {
            return;
        }

        ImGui.pushID(propertyName);

        try {
            // 执行渲染
            renderAction.run();
        } finally {
            ImGui.popID();
        }
    }

    /**
     * 渲染一个简单属性（无标签）
     *
     * @param propertyName  属性名
     * @param renderAction  渲染函数
     */
    public void renderProperty(String propertyName, Runnable renderAction) {
        renderProperty(propertyName, null, null, renderAction);
    }

    /**
     * 渲染带返回值的属性（用于检测修改）
     *
     * @param propertyName  属性名
     * @param tags          属性标签
     * @param categories    类别
     * @param renderFunc    渲染函数（返回是否修改）
     * @return true 如果属性被修改
     */
    public boolean renderPropertyWithResult(String propertyName,
                                           Collection<String> tags,
                                           Collection<String> categories,
                                           PropertyRenderFunction renderFunc) {
        if (!filter.matches(propertyName, tags, categories)) {
            return false;
        }

        ImGui.pushID(propertyName);

        try {
            return renderFunc.render();
        } finally {
            ImGui.popID();
        }
    }

    // ==================== 分组与样式逻辑 ====================

    /**
     * 判断该分组是否需要渲染（基于搜索过滤器）。
     *
     * @param groupName 分组名称
     * @param tags      分组标签
     * @return true 如果应该渲染
     */
    private boolean shouldRenderGroup(String groupName, Collection<String> tags) {
        if (filter.isEmpty()) {
            return true;
        }

        // 若搜索词与分组名匹配，也展示该分组
        return filter.matches(groupName, tags, Collections.singletonList(groupName));
    }

    /**
     * 获取分组颜色（可用于分组标题或边框）
     *
     * @param groupName 分组名称
     * @return ImGui 颜色值
     */
    public int getGroupColor(String groupName) {
        return groupColors.computeIfAbsent(groupName, n -> {
            // 基于分组名称生成确定性的颜色
            int hash = n.hashCode();
            float h = (hash & 0xFF) / 255.0f;
            float s = 0.5f + ((hash >> 8) & 0xFF) / 512.0f;
            float v = 0.7f + ((hash >> 16) & 0xFF) / 512.0f;
            
            // HSV to RGB
            float[] rgb = hsvToRgb(h, s, v);
            return ImGui.getColorU32(rgb[0], rgb[1], rgb[2], 1.0f);
        });
    }

    /**
     * HSV 转 RGB
     */
    private float[] hsvToRgb(float h, float s, float v) {
        h = h * 360.0f;
        int hi = (int) (h / 60) % 6;
        float f = (h / 60) - hi;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);

        switch (hi) {
            case 0: return new float[]{v, t, p};
            case 1: return new float[]{q, v, p};
            case 2: return new float[]{p, v, t};
            case 3: return new float[]{p, q, v};
            case 4: return new float[]{t, p, v};
            case 5: return new float[]{v, p, q};
            default: return new float[]{v, t, p};
        }
    }

    /**
     * 设置分组高亮开关。
     *
     * @param enabled 是否启用高亮
     */
    public void setHighlightMatchedGroups(boolean enabled) {
        this.highlightMatchedGroups = enabled;
    }

    /**
     * 检查分组是否展开
     *
     * @param groupName 分组名称
     * @return true 如果展开
     */
    public boolean isGroupExpanded(String groupName) {
        return expandedGroups.getOrDefault(groupName, false);
    }

    /**
     * 绘制分组边框（可选）
     *
     * @param groupName 分组名称
     */
    public void drawGroupBorder(String groupName) {
        if (!highlightMatchedGroups) {
            return;
        }
        if (filter.isEmpty()) {
            return;
        }

        if (filter.matches(groupName, null, Collections.singleton(groupName))) {
            int color = getGroupColor(groupName);
            ImGui.pushStyleColor(ImGuiCol.Border, color);
            ImGui.separator();
            ImGui.popStyleColor();
        }
    }

    /**
     * 绘制所有已展开分组的快速折叠控制（在属性面板顶部使用）
     */
    public void renderCollapseAllControls() {
        ImGui.separator();
        
        if (ImGui.button("展开全部", 80, 0)) {
            expandedGroups.replaceAll((k, v) -> true);
            expandAll = true;
        }
        
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("展开所有分组");
        }
        
        ImGui.sameLine();
        
        if (ImGui.button("折叠全部", 80, 0)) {
            expandedGroups.replaceAll((k, v) -> false);
            expandAll = false;
        }
        
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("折叠所有分组");
        }
    }

    // ==================== 分组状态访问 ====================

    /**
     * 获取所有展开的分组名称
     *
     * @return 展开的分组集合
     */
    public Set<String> getExpandedGroups() {
        Set<String> expanded = new HashSet<>();
        for (Map.Entry<String, Boolean> entry : expandedGroups.entrySet()) {
            if (entry.getValue()) {
                expanded.add(entry.getKey());
            }
        }
        return expanded;
    }

    /**
     * 获取所有分组名称
     *
     * @return 所有分组集合
     */
    public Set<String> getAllGroups() {
        return new HashSet<>(expandedGroups.keySet());
    }

    /**
     * 是否启用匹配分组高亮
     *
     * @return true 如果启用
     */
    public boolean isHighlightMatchedGroups() {
        return highlightMatchedGroups;
    }

    /**
     * 设置全局展开状态
     *
     * @param expandAll 是否展开所有
     */
    public void setExpandAll(boolean expandAll) {
        this.expandAll = expandAll;
        if (expandAll) {
            expandedGroups.replaceAll((k, v) -> true);
        }
    }

    /**
     * 获取全局展开状态
     *
     * @return true 如果展开所有
     */
    public boolean isExpandAll() {
        return expandAll;
    }

    /**
     * 清除所有分组状态
     */
    public void clear() {
        groupStack.clear();
        expandedGroups.clear();
        groupColors.clear();
        expandAll = false;
    }

    /**
     * 获取当前分组深度
     *
     * @return 嵌套深度
     */
    public int getGroupDepth() {
        return groupStack.size();
    }

    /**
     * 获取当前分组名称
     *
     * @return 当前分组名称，如果没有则返回 null
     */
    public String getCurrentGroup() {
        return groupStack.isEmpty() ? null : groupStack.peek();
    }

    // ==================== 函数式接口 ====================

    /**
     * 属性渲染函数接口（带返回值）
     */
    @FunctionalInterface
    public interface PropertyRenderFunction {
        /**
         * 渲染属性
         *
         * @return true 如果属性被修改
         */
        boolean render();
    }
}

