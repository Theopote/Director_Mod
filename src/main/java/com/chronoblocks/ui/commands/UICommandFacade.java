package com.chronoblocks.ui.commands;

/**
 * UI 命令门面接口 - 将用户意图翻译成系统命令
 * 
 * <p>职责：
 * <ul>
 *   <li>提供统一的命令级 API，供 Menu / Toolbar / Shortcut 共用</li>
 *   <li>将用户意图翻译成系统操作，而不是直接操作系统状态</li>
 *   <li>保持 MenuBarPanel 等 UI 组件的"薄"，只负责转发用户意图</li>
 * </ul>
 * 
 * <p>设计原则：
 * <ul>
 *   <li>按功能域分组，每个子接口负责一个领域</li>
 *   <li>每个方法代表一个明确的"用户意图"（如 play(), pause(), stop()）</li>
 *   <li>不暴露内部实现细节（如 PlaybackController、TimelineModel 等）</li>
 *   <li>支持未来扩展（快捷键、自动化、录制宏等）</li>
 * </ul>
 * 
 * <p>使用示例：
 * <pre>{@code
 * ui.commands().playback().play();
 * ui.commands().camera().createCameraPath(CameraPathType.DOLLY);
 * ui.commands().layout().togglePanel(PanelId.TIMELINE);
 * }</pre>
 * 
 * @author ChronoBlocks Team
 * @version 2.0 - 重构为分组结构
 */
public interface UICommandFacade {
    
    /**
     * 获取项目命令接口
     */
    ProjectCommands project();
    
    /**
     * 获取时间线命令接口
     */
    TimelineCommands timeline();
    
    /**
     * 获取播放命令接口
     */
    PlaybackCommands playback();
    
    /**
     * 获取摄像机命令接口
     */
    CameraCommands camera();
    
    /**
     * 获取视图命令接口
     */
    ViewCommands view();
    
    /**
     * 获取布局命令接口
     */
    LayoutCommands layout();
    
    /**
     * 获取帮助命令接口
     */
    HelpCommands help();
    
    /**
     * 获取工具命令接口
     */
    ToolCommands tools();

    /**
     * 获取容器命令接口（删除选中容器等，供 Shortcut 与菜单调用）
     */
    ContainerCommands container();
}
