package com.chronoblocks.ui.adapters.util;

import imgui.ImGui;
import imgui.flag.ImGuiCol;

import java.util.*;

/**
 * 🔍 PropertySearchFilter
 * -----------------------
 * ChronoBlocks v4.5 属性面板搜索过滤器。
 *
 * <h3>✨ 支持特性：</h3>
 * <ul>
 *     <li>模糊搜索（忽略大小写）</li>
 *     <li>标签/类别过滤（以 # 或 @ 前缀）</li>
 *     <li>匹配结果高亮渲染</li>
 *     <li>多关键词搜索</li>
 *     <li>Levenshtein 距离算法</li>
 * </ul>
 *
 * <h3>📖 使用示例：</h3>
 * <pre>{@code
 * PropertySearchFilter filter = new PropertySearchFilter();
 * filter.setQuery("position #transform");
 *
 * // 检查属性是否匹配
 * if (filter.matches("Position X", 
 *                    Arrays.asList("transform", "xyz"),
 *                    Arrays.asList("animation", "position"))) {
 *     // 渲染属性（带高亮）
 *     filter.renderHighlightedText("Position X");
 * }
 * }</pre>
 *
 * <h3>🔧 搜索语法：</h3>
 * <ul>
 *     <li><code>position</code> - 搜索包含 "position" 的属性</li>
 *     <li><code>#transform</code> - 只显示带 "transform" 标签的属性</li>
 *     <li><code>@animation</code> - 只显示 "animation" 类别的属性</li>
 *     <li><code>pos #transform</code> - 组合搜索</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 4.5
 * @since 4.5
 */
public class PropertySearchFilter {

    /** 当前搜索关键词 */
    private String query = "";

    /** 模糊匹配 token */
    private final List<String> tokens = new ArrayList<>();

    /** 标签筛选（#transform, #animation等） */
    private final Set<String> tagFilters = new HashSet<>();

    /** 类别筛选（例如 Position、Rotation、Effect） */
    private final Set<String> categoryFilters = new HashSet<>();

    /** 是否启用模糊匹配 */
    private boolean fuzzy = true;

    /** 模糊匹配允许的最大字符距离 */
    private int fuzzyTolerance = 2;

    // ==================== 解析与初始化 ====================

    /**
     * 设置新的搜索字符串。
     * <p>自动解析出关键词、标签、类别。
     *
     * @param newQuery 搜索字符串
     */
    public void setQuery(String newQuery) {
        if (Objects.equals(newQuery, this.query)) {
            return;
        }

        this.query = (newQuery == null) ? "" : newQuery.trim().toLowerCase(Locale.ROOT);
        parseQuery();
    }

    /**
     * 解析搜索字符串
     * <p>提取关键词、标签（#）和类别（@）
     */
    private void parseQuery() {
        tokens.clear();
        tagFilters.clear();
        categoryFilters.clear();

        if (query.isEmpty()) {
            return;
        }

        for (String token : query.split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }

            if (token.startsWith("#")) {
                // 标签过滤：#transform
                tagFilters.add(token.substring(1));
            } else if (token.startsWith("@")) {
                // 类别过滤：@animation
                categoryFilters.add(token.substring(1));
            } else {
                // 普通关键词
                tokens.add(token);
            }
        }
    }

    /**
     * 检查是否为空搜索
     *
     * @return true 如果没有搜索条件
     */
    public boolean isEmpty() {
        return query.isEmpty();
    }

    // ==================== 匹配逻辑 ====================

    /**
     * 检查一个属性是否通过过滤。
     *
     * @param propertyName 属性名称
     * @param tags         属性的标签（如 ["transform", "xyz"]）
     * @param categories   属性的分类（如 ["animation", "position"]）
     * @return true 如果匹配
     */
    public boolean matches(String propertyName, Collection<String> tags, Collection<String> categories) {
        if (query.isEmpty()) {
            return true;
        }

        String name = propertyName.toLowerCase(Locale.ROOT);

        // 标签匹配
        if (!tagFilters.isEmpty()) {
            if (tags == null || tags.isEmpty()) {
                return false;
            }

            boolean tagMatched = tags.stream()
                    .map(String::toLowerCase)
                    .anyMatch(tagFilters::contains);

            if (!tagMatched) {
                return false;
            }
        }

        // 类别匹配
        if (!categoryFilters.isEmpty()) {
            if (categories == null || categories.isEmpty()) {
                return false;
            }

            boolean catMatched = categories.stream()
                    .map(String::toLowerCase)
                    .anyMatch(categoryFilters::contains);

            if (!catMatched) {
                return false;
            }
        }

        // 关键词匹配
        if (tokens.isEmpty()) {
            return true;
        }

        for (String token : tokens) {
            if (fuzzy) {
                if (!fuzzyMatch(name, token)) {
                    return false;
                }
            } else if (!name.contains(token)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 简化版匹配（只检查属性名称）
     *
     * @param propertyName 属性名称
     * @return true 如果匹配
     */
    public boolean matches(String propertyName) {
        return matches(propertyName, null, null);
    }

    // ==================== 高亮渲染 ====================

    /**
     * 高亮匹配的部分文本。
     * <p>将匹配的关键词用高亮颜色渲染。
     *
     * @param text 要渲染的文本
     */
    public void renderHighlightedText(String text) {
        if (query.isEmpty() || tokens.isEmpty()) {
            ImGui.textUnformatted(text);
            return;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        int start = 0;

        for (String token : tokens) {
            int index = lower.indexOf(token, start);
            if (index == -1) {
                continue;
            }

            // 渲染前面的普通文本
            if (index > start) {
                ImGui.textUnformatted(text.substring(start, index));
                ImGui.sameLine(0, 0);
            }

            // 渲染高亮部分
            ImGui.pushStyleColor(ImGuiCol.Text, 0.9f, 0.8f, 0.2f, 1.0f);
            ImGui.textUnformatted(text.substring(index, index + token.length()));
            ImGui.popStyleColor();

            ImGui.sameLine(0, 0);
            start = index + token.length();
        }

        // 渲染剩余文本
        if (start < text.length()) {
            ImGui.textUnformatted(text.substring(start));
        }
    }

    // ==================== 模糊匹配算法 ====================

    /**
     * 模糊匹配（使用 Levenshtein 距离）
     *
     * @param str     目标字符串
     * @param pattern 模式字符串
     * @return true 如果匹配
     */
    private boolean fuzzyMatch(String str, String pattern) {
        // 先尝试精确包含
        if (str.contains(pattern)) {
            return true;
        }

        // 计算编辑距离
        int distance = levenshtein(str, pattern);
        return distance <= fuzzyTolerance;
    }

    /**
     * Levenshtein 距离算法（编辑距离）
     *
     * @param a 字符串A
     * @param b 字符串B
     * @return 编辑距离
     */
    private int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(
                                dp[i - 1][j] + 1,      // 删除
                                dp[i][j - 1] + 1       // 插入
                        ),
                        dp[i - 1][j - 1] + cost        // 替换
                );
            }
        }

        return dp[a.length()][b.length()];
    }

    // ==================== 配置方法 ====================

    /**
     * 启用/禁用模糊匹配
     *
     * @param fuzzy true 启用模糊匹配
     */
    public void setFuzzy(boolean fuzzy) {
        this.fuzzy = fuzzy;
    }

    /**
     * 设置模糊匹配容差
     *
     * @param fuzzyTolerance 允许的最大编辑距离
     */
    public void setFuzzyTolerance(int fuzzyTolerance) {
        this.fuzzyTolerance = Math.max(0, fuzzyTolerance);
    }

    /**
     * 获取当前搜索字符串
     *
     * @return 搜索字符串
     */
    public String getQuery() {
        return query;
    }

    /**
     * 获取标签过滤器
     *
     * @return 标签集合
     */
    public Set<String> getTagFilters() {
        return Collections.unmodifiableSet(tagFilters);
    }

    /**
     * 获取类别过滤器
     *
     * @return 类别集合
     */
    public Set<String> getCategoryFilters() {
        return Collections.unmodifiableSet(categoryFilters);
    }

    /**
     * 获取关键词列表
     *
     * @return 关键词列表
     */
    public List<String> getTokens() {
        return Collections.unmodifiableList(tokens);
    }

    /**
     * 清除所有过滤条件
     */
    public void clear() {
        query = "";
        tokens.clear();
        tagFilters.clear();
        categoryFilters.clear();
    }

    @Override
    public String toString() {
        return "PropertySearchFilter{" +
                "query='" + query + '\'' +
                ", tokens=" + tokens +
                ", tags=" + tagFilters +
                ", categories=" + categoryFilters +
                ", fuzzy=" + fuzzy +
                ", tolerance=" + fuzzyTolerance +
                '}';
    }
}

