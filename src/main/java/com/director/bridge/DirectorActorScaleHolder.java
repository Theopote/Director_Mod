package com.director.bridge;

/** Render-state bridge for Director preview actor non-uniform root scale. */
public interface DirectorActorScaleHolder {
    void director$setActorScale(float x, float y, float z);
    float director$getActorScaleX();
    float director$getActorScaleY();
    float director$getActorScaleZ();
}
