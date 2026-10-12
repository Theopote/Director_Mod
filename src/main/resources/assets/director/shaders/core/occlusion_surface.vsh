#version 330
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
uniform sampler2D Sampler2;
out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 tileCoord;
flat out vec2 spriteOrigin;
flat out vec2 spriteSize;
void main() {
    vec3 pos = Position + ModelOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = Color * texture(Sampler2, clamp((UV2 / 256.0) + 0.5 / 16.0,
                                                vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
    tileCoord = UV0;
    spriteOrigin = vec2(UV1);
    spriteSize = exp2(round(Normal.xy * 127.0));
}
