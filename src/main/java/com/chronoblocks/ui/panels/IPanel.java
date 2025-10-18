package com.chronoblocks.ui.panels;

/**
 * IPanel - 面板接口
 * <p>
 * 定义了所有UI面板必须实现的基本功能，提供统一的面板管理接口。
 * 支持面板的可见性控制、生命周期管理和渲染功能。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public interface IPanel {
    
    /**
     * 获取面板名称
     * @return 面板的唯一标识名称
     */
    String getPanelName();
    
    /**
     * 获取面板在ImGui中的窗口名称。
     * 这个名称必须是唯一的，用于ImGui.begin()和DockBuilder。
     * @return 窗口名称
     */
    String getWindowName();
    
    /**
     * 渲染面板内容
     * 每帧调用一次，负责绘制面板的UI元素
     */
    void render();
    
    /**
     * 检查面板是否可见
     * @return true如果面板应该被渲染，false否则
     */
    default boolean isVisible() {
        return true;
    }
    
    /**
     * 设置面板可见性
     * @param visible 是否可见
     */
    default void setVisible(boolean visible) {
        // 默认实现为空，子类可以重写以支持动态可见性控制
    }
    
    /**
     * 初始化面板
     * 在面板首次使用前调用，用于设置初始状态
     */
    default void initialize() {
        // 默认实现为空
    }
    
    /**
     * 清理面板资源
     * 在面板不再需要时调用，用于释放资源
     */
    default void dispose() {
        // 默认实现为空
    }
    
    /**
     * 检查面板是否需要更新
     * 用于性能优化，避免不必要的渲染
     * @return true如果面板需要重新渲染
     */
    default boolean needsUpdate() {
        return true;
    }
    
    /**
     * 获取面板优先级
     * 用于控制面板的渲染顺序，数值越小优先级越高
     * @return 面板优先级
     */
    default int getPriority() {
        return 100;
    }
    
    // ========== 输入事件处理方法 ==========
    
    /**
     * 处理键盘按键事件
     * @param keyCode 按键代码
     * @param scanCode 扫描码
     * @param modifiers 修饰键（Ctrl、Shift、Alt等）
     * @return true如果事件被处理，false如果需要传递给其他处理器
     */
    default boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }
    
    /**
     * 处理鼠标点击事件
     * @param mouseX 鼠标X坐标
     * @param mouseY 鼠标Y坐标
     * @param button 鼠标按钮（0=左键，1=右键，2=中键）
     * @return true如果事件被处理，false如果需要传递给其他处理器
     */
    default boolean handleMouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }
    
    /**
     * 处理鼠标拖拽事件
     * @param mouseX 鼠标X坐标
     * @param mouseY 鼠标Y坐标
     * @param button 鼠标按钮
     * @param deltaX X方向移动距离
     * @param deltaY Y方向移动距离
     * @return true如果事件被处理，false如果需要传递给其他处理器
     */
    default boolean handleMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }
    
    /**
     * 处理鼠标滚轮事件
     * @param mouseX 鼠标X坐标
     * @param mouseY 鼠标Y坐标
     * @param horizontalAmount 水平滚动量
     * @param verticalAmount 垂直滚动量
     * @return true如果事件被处理，false如果需要传递给其他处理器
     */
    default boolean handleMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return false;
    }
    
    /**
     * 处理字符输入事件（用于文本输入）
     * @param codepoint Unicode字符码点
     * @return true如果事件被处理，false如果需要传递给其他处理器
     */
    default boolean handleCharTyped(char codepoint, int modifiers) {
        return false;
    }
    
    /**
     * 检查面板是否应该接收输入事件
     * 用于性能优化，避免不必要的输入处理
     * @return true如果面板应该接收输入事件
     */
    default boolean shouldReceiveInput() {
        return isVisible();
    }
}