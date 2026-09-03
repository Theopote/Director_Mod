package com.director.bridge;

/** Render-state bridge for Director preview actor per-bone scale. */
public interface DirectorActorBoneScaleHolder {
    void director$setBoneScale(String boneId, float x, float y, float z);
    float director$getBoneScaleX(String boneId);
    float director$getBoneScaleY(String boneId);
    float director$getBoneScaleZ(String boneId);
}
