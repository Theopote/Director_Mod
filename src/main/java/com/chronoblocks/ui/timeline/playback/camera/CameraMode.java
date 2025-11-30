package com.chronoblocks.ui.timeline.playback.camera;

/**
 * 摄像机运行模式。
 * <p>
 * GAME              - 默认游戏模式，玩家自由控制视角。
 * PREVIEW           - 预览模式，比如点击关键帧时跳过去看一眼。
 * PLAYING           - 时间线播放中，由摄像机轨道全面接管。
 */
public enum CameraMode {
    GAME,           // Player free control
    PREVIEW,        // Jump-to-keyframe preview while editing
    PLAYING;        // Timeline playback drives the camera
    
    public boolean isCinematic() {
        return this == PREVIEW || this == PLAYING;
    }
    
    public boolean isPlaying() {
        return this == PLAYING;
    }
    
    public boolean isGame() {
        return this == GAME;
    }
}
