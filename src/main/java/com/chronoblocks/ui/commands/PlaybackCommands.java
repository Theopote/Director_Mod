package com.chronoblocks.ui.commands;

/**
 * 播放命令接口 - 时间流动控制
 * 
 * <p>职责：管理播放状态、播放范围、播放速度、导航等
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public interface PlaybackCommands {
    
    /**
     * 播放
     */
    void play();
    
    /**
     * 暂停
     */
    void pause();
    
    /**
     * 停止
     */
    void stop();
    
    /**
     * 切换播放/暂停
     */
    void togglePlayPause();
    
    /**
     * 设置入点
     */
    void setInPoint();
    
    /**
     * 设置出点
     */
    void setOutPoint();
    
    /**
     * 清除入出点
     */
    void clearInOut();
    
    /**
     * 设置播放速度
     * @param speed 播放速度（0.25x, 0.5x, 1.0x, 2.0x, 3.0x 等）
     */
    void setPlaybackSpeed(float speed);
    
    /**
     * 切换循环播放
     */
    void toggleLoop();
    
    /**
     * 跳转到开始
     */
    void goToStart();
    
    /**
     * 跳转到结束
     */
    void goToEnd();
    
    // ==================== 状态查询方法（只读接口） ====================
    
    /**
     * 查询是否正在播放
     * @return true 如果正在播放，false 如果已暂停或停止
     */
    boolean isPlaying();
    
    /**
     * 查询是否开启循环播放
     * @return true 如果开启循环，false 如果未开启
     */
    boolean isLooping();
    
    /**
     * 获取当前播放速度
     * @return 播放速度（如 1.0f 表示正常速度）
     */
    float getPlaybackSpeed();
    
    /**
     * 获取当前播放时间
     * @return 当前时间（秒）
     */
    float getCurrentTime();
    
    /**
     * 获取总时长
     * @return 总时长（秒）
     */
    float getTotalDuration();
}
