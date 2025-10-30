package com.chronoblocks.ui.panels;

import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/**
 * ❓ 帮助面板
 * 
 * 提供ChronoBlocks的使用帮助和快捷键信息：
 * - 快捷键列表
 * - 使用指南
 * - 功能说明
 * - 操作提示
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class HelpPanel extends AbstractPanel {
    
    /** 面板名称常量 */
    public static final String PANEL_NAME = "Help";
    
    /** 帮助内容分类 */
    private final String[] tabs = {"快捷键", "工具", "面板", "动画"};
    
    @Override
    public String getPanelName() {
        return PANEL_NAME;
    }
    
    @Override
    public String getWindowName() {
        return Icons.HELP + " 帮助###HelpPanel";
    }
    
    @Override
    public int getPriority() {
        return 200; // 低优先级，通常最后显示
    }
    
    @Override
    protected int getWindowFlags() {
        return ImGuiWindowFlags.None;
    }
    
    @Override
    protected void doRender() {
        UITheme.title(Icons.HELP + " ChronoBlocks 帮助");
        
        // 标签页
        if (ImGui.beginTabBar("##HelpTabs")) {
            for (int i = 0; i < tabs.length; i++) {
                if (ImGui.beginTabItem(tabs[i])) {
                    renderTabContent(i);
                    ImGui.endTabItem();
                }
            }
            ImGui.endTabBar();
        }
    }
    
    /**
     * 渲染标签页内容
     */
    private void renderTabContent(int tabIndex) {
        switch (tabIndex) {
            case 0 -> renderShortcutsTab();
            case 1 -> renderToolsTab();
            case 2 -> renderPanelsTab();
            case 3 -> renderAnimationTab();
        }
    }
    
    /**
     * 渲染快捷键标签页
     */
    private void renderShortcutsTab() {
        UITheme.label("常用快捷键:", UITheme.DarkTheme.TEXT_SELECTED);
        
        // 选择工具快捷键
        UITheme.label("选择工具:", UITheme.DarkTheme.TEXT);
        UITheme.label("  Ctrl+1 - 点击选择工具", UITheme.DarkTheme.TEXT);
        UITheme.label("  Ctrl+2 - 框选工具", UITheme.DarkTheme.TEXT);
        UITheme.label("  Ctrl+3 - 魔棒工具", UITheme.DarkTheme.TEXT);
        
        ImGui.separator();
        
        // 动画控制快捷键
        UITheme.label("动画控制:", UITheme.DarkTheme.TEXT);
        UITheme.label("  Space - 播放/暂停", UITheme.DarkTheme.TEXT);
        UITheme.label("  Ctrl+Space - 停止", UITheme.DarkTheme.TEXT);
        UITheme.label("  " + Icons.ARROW_LEFT + "/" + Icons.ARROW_RIGHT + " - 上一帧/下一帧", UITheme.DarkTheme.TEXT);
        UITheme.label("  Home/End - 跳转到开始/结束", UITheme.DarkTheme.TEXT);
        
        ImGui.separator();
        
        // 面板快捷键
        UITheme.label("面板控制:", UITheme.DarkTheme.TEXT);
        UITheme.label("  F1 - 显示/隐藏帮助", UITheme.DarkTheme.TEXT);
        UITheme.label("  F2 - 重命名", UITheme.DarkTheme.TEXT);
        UITheme.label("  F11 - 全屏", UITheme.DarkTheme.TEXT);
        UITheme.label("  Ctrl+Tab - 切换面板", UITheme.DarkTheme.TEXT);
        
        ImGui.separator();
        
        // 编辑快捷键
        UITheme.label("编辑操作:", UITheme.DarkTheme.TEXT);
        UITheme.label("  Ctrl+Z - 撤销", UITheme.DarkTheme.TEXT);
        UITheme.label("  Ctrl+Y - 重做", UITheme.DarkTheme.TEXT);
        UITheme.label("  Ctrl+C - 复制", UITheme.DarkTheme.TEXT);
        UITheme.label("  Ctrl+V - 粘贴", UITheme.DarkTheme.TEXT);
        UITheme.label("  Del - 删除", UITheme.DarkTheme.TEXT);
    }
    
    /**
     * 渲染工具标签页
     */
    private void renderToolsTab() {
        UITheme.label("选择工具:", UITheme.DarkTheme.TEXT_SELECTED);
        
        UITheme.label("点击选择工具:", UITheme.DarkTheme.TEXT);
        UITheme.label("  左键点击 - 选择方块", UITheme.DarkTheme.TEXT);
        UITheme.label("  Shift+左键 - 加选", UITheme.DarkTheme.TEXT);
        UITheme.label("  Ctrl+左键 - 减选", UITheme.DarkTheme.TEXT);
        UITheme.label("  右键点击 - 取消选择", UITheme.DarkTheme.TEXT);
        
        ImGui.separator();
        
        UITheme.label("框选工具:", UITheme.DarkTheme.TEXT);
        UITheme.label("  拖拽 - 框选区域", UITheme.DarkTheme.TEXT);
        UITheme.label("  Shift+拖拽 - 加选区域", UITheme.DarkTheme.TEXT);
        UITheme.label("  Ctrl+拖拽 - 减选区域", UITheme.DarkTheme.TEXT);
        UITheme.label("  Alt+拖拽 - 移动选择区域", UITheme.DarkTheme.TEXT);
        
        ImGui.separator();
        
        UITheme.label("魔棒工具:", UITheme.DarkTheme.TEXT);
        UITheme.label("  左键点击 - 选择相同方块", UITheme.DarkTheme.TEXT);
        UITheme.label("  右键点击 - 设置容差", UITheme.DarkTheme.TEXT);
        UITheme.label("  Shift+左键 - 加选相同方块", UITheme.DarkTheme.TEXT);
        UITheme.label("  Ctrl+左键 - 减选相同方块", UITheme.DarkTheme.TEXT);
        
        ImGui.separator();
        
        UITheme.label("工具属性:", UITheme.DarkTheme.TEXT);
        UITheme.label("  画笔半径 - 影响选择范围", UITheme.DarkTheme.TEXT);
        UITheme.label("  吸附设置 - 精确对齐", UITheme.DarkTheme.TEXT);
        UITheme.label("  多选模式 - 连续选择", UITheme.DarkTheme.TEXT);
    }
    
    /**
     * 渲染面板标签页
     */
    private void renderPanelsTab() {
        UITheme.label("面板说明:", UITheme.DarkTheme.TEXT_SELECTED);
        
        UITheme.label("工具栏:", UITheme.DarkTheme.TEXT);
        UITheme.label("  选择工具和操作按钮", UITheme.DarkTheme.TEXT);
        UITheme.label("  状态显示和FPS监控", UITheme.DarkTheme.TEXT);
        UITheme.label("  渲染控制和窗口管理", UITheme.DarkTheme.TEXT);
        
        ImGui.separator();
        
        UITheme.label("时间线面板:", UITheme.DarkTheme.TEXT);
        UITheme.label("  动画轨道管理", UITheme.DarkTheme.TEXT);
        UITheme.label("  关键帧编辑", UITheme.DarkTheme.TEXT);
        UITheme.label("  播放控制", UITheme.DarkTheme.TEXT);
        UITheme.label("  时间轴导航", UITheme.DarkTheme.TEXT);
        
        ImGui.separator();
        
        UITheme.label("属性面板:", UITheme.DarkTheme.TEXT);
        UITheme.label("  对象属性编辑", UITheme.DarkTheme.TEXT);
        UITheme.label("  动画参数设置", UITheme.DarkTheme.TEXT);
        UITheme.label("  材质和纹理配置", UITheme.DarkTheme.TEXT);
        
        ImGui.separator();
        
        UITheme.label("容器面板:", UITheme.DarkTheme.TEXT);
        UITheme.label("  容器管理", UITheme.DarkTheme.TEXT);
        UITheme.label("  容器属性编辑", UITheme.DarkTheme.TEXT);
        UITheme.label("  容器层级管理", UITheme.DarkTheme.TEXT);
        
        ImGui.separator();
        
        UITheme.label("过滤器面板:", UITheme.DarkTheme.TEXT);
        UITheme.label("  方块类型过滤", UITheme.DarkTheme.TEXT);
        UITheme.label("  预设过滤器管理", UITheme.DarkTheme.TEXT);
        UITheme.label("  自定义过滤器创建", UITheme.DarkTheme.TEXT);
        
        ImGui.separator();
        
        UITheme.label("渲染设置面板:", UITheme.DarkTheme.TEXT);
        UITheme.label("  高亮颜色设置", UITheme.DarkTheme.TEXT);
        UITheme.label("  边框线配置", UITheme.DarkTheme.TEXT);
        UITheme.label("  预览效果设置", UITheme.DarkTheme.TEXT);
        UITheme.label("  Debug覆盖层", UITheme.DarkTheme.TEXT);
    }
    
    /**
     * 渲染动画标签页
     */
    private void renderAnimationTab() {
        UITheme.label("动画制作流程:", UITheme.DarkTheme.TEXT_SELECTED);
        
        UITheme.label("1. 选择方块", UITheme.DarkTheme.TEXT);
        UITheme.label("  使用选择工具选择要动画的方块", UITheme.DarkTheme.TEXT);
        UITheme.label("  可以使用过滤器精确选择", UITheme.DarkTheme.TEXT);
        
        ImGui.separator();
        
        UITheme.label("2. 创建容器", UITheme.DarkTheme.TEXT);
        UITheme.label("  将选择的方块转换为容器", UITheme.DarkTheme.TEXT);
        UITheme.label("  设置容器的基本属性", UITheme.DarkTheme.TEXT);
        
        ImGui.separator();
        
        UITheme.label("3. 设置关键帧", UITheme.DarkTheme.TEXT);
        UITheme.label("  在时间线上设置动画关键帧", UITheme.DarkTheme.TEXT);
        UITheme.label("  调整关键帧的时间和属性", UITheme.DarkTheme.TEXT);
        
        ImGui.separator();
        
        UITheme.label("4. 播放动画", UITheme.DarkTheme.TEXT);
        UITheme.label("  使用播放控制预览动画效果", UITheme.DarkTheme.TEXT);
        UITheme.label("  调整播放速度和循环设置", UITheme.DarkTheme.TEXT);
        
        ImGui.separator();
        
        UITheme.label("动画类型:", UITheme.DarkTheme.TEXT_SELECTED);
        UITheme.label("  位置动画 - 移动容器", UITheme.DarkTheme.TEXT);
        UITheme.label("  旋转动画 - 旋转容器", UITheme.DarkTheme.TEXT);
        UITheme.label("  缩放动画 - 缩放容器", UITheme.DarkTheme.TEXT);
        UITheme.label("  透明度动画 - 淡入淡出", UITheme.DarkTheme.TEXT);
        UITheme.label("  颜色动画 - 颜色变化", UITheme.DarkTheme.TEXT);
        
        ImGui.separator();
        
        UITheme.label("高级功能:", UITheme.DarkTheme.TEXT_SELECTED);
        UITheme.label("  动画曲线编辑", UITheme.DarkTheme.TEXT);
        UITheme.label("  动画事件触发", UITheme.DarkTheme.TEXT);
        UITheme.label("  动画预设管理", UITheme.DarkTheme.TEXT);
        UITheme.label("  动画导出功能", UITheme.DarkTheme.TEXT);
    }
}
