package com.chronoblocks.ui.status;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/**
 * 面板状态报告 - 使用现代Java Record类型
 * <p>
 * 使用Java 14+的record特性简化数据类的定义，提供不可变的状态报告。
 * 自动生成equals、hashCode、toString等方法，减少样板代码。
 * 
 * <h3>Record特性：</h3>
 * <ul>
 *   <li><strong>不可变性</strong> - 所有字段都是final的</li>
 *   <li><strong>自动方法</strong> - 自动生成equals、hashCode、toString</li>
 *   <li><strong>简洁语法</strong> - 大幅减少样板代码</li>
 *   <li><strong>类型安全</strong> - 编译时类型检查</li>
 * </ul>
 * 
 * @param totalPanels 总面板数量
 * @param healthyPanels 健康面板数量
 * @param problematicPanels 有问题面板数量
 * @param missingPanels 缺失面板数量
 * @param criticalErrors 关键错误数量
 * @param warnings 警告数量
 * @param initializationErrors 初始化错误数量
 * @param renderingErrors 渲染错误数量
 * @param averageRenderTime 平均渲染时间（毫秒）
 * @param performanceHealthy 性能是否健康
 * @param renderCount 渲染次数
 * @param healthy 整体是否健康
 * @param summary 状态摘要
 * @param issues 问题列表
 * @param recommendations 建议列表
 * @param detailedReport 详细报告
 * 
 * @author ChronoBlocks Team
 * @version 2.0
 * @since 2.0
 */
public record PanelStatusReport(
    int totalPanels,
    int healthyPanels,
    int problematicPanels,
    int missingPanels,
    int criticalErrors,
    int warnings,
    int initializationErrors,
    int renderingErrors,
    double averageRenderTime,
    boolean performanceHealthy,
    long renderCount,
    boolean healthy,
    String summary,
    List<String> issues,
    List<String> recommendations,
    String detailedReport
) {
    
    /**
     * 创建Builder实例
     * @return Builder实例
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * 面板状态报告构建器
     * <p>
     * 使用Builder模式简化复杂对象的创建，提供流畅的API。
     */
    public static final class Builder {
        private int totalPanels = 0;
        private int healthyPanels = 0;
        private int problematicPanels = 0;
        private int missingPanels = 0;
        private int criticalErrors = 0;
        private int warnings = 0;
        private int initializationErrors = 0;
        private int renderingErrors = 0;
        private double averageRenderTime = 0.0;
        private boolean performanceHealthy = true;
        private long renderCount = 0;
        private boolean healthy = true;
        private String summary = "";
        private final List<String> issues = new ArrayList<>();
        private final List<String> recommendations = new ArrayList<>();
        private final StringBuilder detailedReport = new StringBuilder();
        
        /**
         * 设置总面板数量
         */
        public Builder setTotalPanels(int totalPanels) {
            this.totalPanels = totalPanels;
            return this;
        }
        
        /**
         * 设置健康面板数量
         */
        public Builder setHealthyPanels(int healthyPanels) {
            this.healthyPanels = healthyPanels;
            return this;
        }
        
        /**
         * 设置有问题面板数量
         */
        public Builder setProblematicPanels(int problematicPanels) {
            this.problematicPanels = problematicPanels;
            return this;
        }
        
        /**
         * 设置缺失面板数量
         */
        public Builder setMissingPanels(int missingPanels) {
            this.missingPanels = missingPanels;
            return this;
        }
        
        /**
         * 设置关键错误数量
         */
        public Builder setCriticalErrors(int criticalErrors) {
            this.criticalErrors = criticalErrors;
            return this;
        }
        
        /**
         * 设置警告数量
         */
        public Builder setWarnings(int warnings) {
            this.warnings = warnings;
            return this;
        }
        
        /**
         * 设置初始化错误数量
         */
        public Builder setInitializationErrors(int initializationErrors) {
            this.initializationErrors = initializationErrors;
            return this;
        }
        
        /**
         * 设置渲染错误数量
         */
        public Builder setRenderingErrors(int renderingErrors) {
            this.renderingErrors = renderingErrors;
            return this;
        }
        
        /**
         * 设置平均渲染时间
         */
        public Builder setAverageRenderTime(double averageRenderTime) {
            this.averageRenderTime = averageRenderTime;
            return this;
        }
        
        /**
         * 设置性能是否健康
         */
        public Builder setPerformanceHealthy(boolean performanceHealthy) {
            this.performanceHealthy = performanceHealthy;
            return this;
        }
        
        /**
         * 设置渲染次数
         */
        public Builder setRenderCount(long renderCount) {
            this.renderCount = renderCount;
            return this;
        }
        
        /**
         * 设置整体是否健康
         */
        public Builder setHealthy(boolean healthy) {
            this.healthy = healthy;
            return this;
        }
        
        /**
         * 设置状态摘要
         */
        public Builder setSummary(String summary) {
            this.summary = Objects.requireNonNull(summary, "Summary cannot be null");
            return this;
        }
        
        /**
         * 添加问题
         */
        public Builder addIssue(String issue) {
            this.issues.add(Objects.requireNonNull(issue, "Issue cannot be null"));
            return this;
        }
        
        /**
         * 添加建议
         */
        public Builder addRecommendation(String recommendation) {
            this.recommendations.add(Objects.requireNonNull(recommendation, "Recommendation cannot be null"));
            return this;
        }
        
        /**
         * 添加详细报告内容
         */
        public Builder addDetailedReport(String content) {
            this.detailedReport.append(Objects.requireNonNull(content, "Content cannot be null"));
            return this;
        }
        
        /**
         * 构建PanelStatusReport实例
         */
        public PanelStatusReport build() {
            return new PanelStatusReport(
                totalPanels,
                healthyPanels,
                problematicPanels,
                missingPanels,
                criticalErrors,
                warnings,
                initializationErrors,
                renderingErrors,
                averageRenderTime,
                performanceHealthy,
                renderCount,
                healthy,
                summary,
                List.copyOf(issues), // 创建不可变副本
                List.copyOf(recommendations), // 创建不可变副本
                detailedReport.toString()
            );
        }
    }
}
