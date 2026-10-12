#version 330
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 tileCoord;
flat in vec2 spriteOrigin;
flat in vec2 spriteSize;
out vec4 fragColor;
void main() {
    vec2 atlasSize = vec2(textureSize(Sampler0, 0));
    // Repeat inside this sprite, never across neighboring atlas entries. Derivatives precede fract.
    vec2 pixel = clamp(fract(tileCoord) * spriteSize, vec2(0.5), spriteSize - vec2(0.5));
    vec2 uv = (spriteOrigin + pixel) / atlasSize;
    vec2 dx = dFdx(tileCoord) * spriteSize / atlasSize;
    vec2 dy = dFdy(tileCoord) * spriteSize / atlasSize;
    vec4 color = textureGrad(Sampler0, uv, dx, dy) * vertexColor * ColorModulator;
    if (color.a < 0.01) discard;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
