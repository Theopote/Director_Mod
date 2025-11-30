package com.chronoblocks.core.visual;

/**
 * 方块渲染模式
 * 
 * <p>定义方块在渲染时的行为意图，用于控制透明度、深度写入等渲染参数。
 * 
 * <p>设计目标：
 * <ul>
 *   <li>将"渲染意图"从渲染逻辑中分离</li>
 *   <li>时间线和容器可见性系统只需设置 mode，无需直接操作 RenderSystem</li>
 *   <li>Renderer 根据 mode 自动选择合适的 RenderLayer 和深度策略</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public enum BlockRenderMode {
    
    /**
     * 实体方块：alpha=1，写入深度，用于"最终成品"
     * 
     * <p>用途：
     * <ul>
     *   <li>已经完成建造的方块</li>
     *   <li>当前正在播放的片段方块（主角）</li>
     * </ul>
     */
    SOLID,
    
    /**
     * 半透明预览，写入深度：看得到，但会遮挡后面的东西
     * 
     * <p>用途：
     * <ul>
     *   <li>已经建好的，但需要弱化的片段</li>
     *   <li>略微退后一点，但仍然有存在感</li>
     * </ul>
     */
    GHOST_WITH_DEPTH,
    
    /**
     * 半透明预览，不写深度：不会遮挡后面的东西
     * 
     * <p>用途：
     * <ul>
     *   <li>为了给后面的片段腾视线的遮挡块</li>
     *   <li>看得见轮廓，但不会挡住后面的动画</li>
     * </ul>
     */
    GHOST_NO_DEPTH,
    
    /**
     * 完全隐藏（不渲染），但逻辑仍存在
     * 
     * <p>用途：
     * <ul>
     *   <li>片段还没开始播放</li>
     *   <li>用户手动隐藏的容器</li>
     * </ul>
     */
    HIDDEN
}

