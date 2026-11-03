#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <shizavisuals:ui_common.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
in float LineWidth;

out vec2 localPos;
out vec4 baseColor;
flat out vec2 halfSize;
flat out vec4 arcColor;
flat out float radius;
flat out float thickness;
flat out float startAngle;
flat out float sweepAngle;
flat out float blendDegrees;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position.xy, 0.0, 1.0);

    // UV0 is centered on the ring bounds (same contract as rect).
    localPos = UV0;
    baseColor = Color;
    halfSize = vec2(UV1) / UI_SIZE_SCALE * 0.5;

    // Position.z carries the border path: thickness px | arc alpha.
    vec3 packedZ = ui_unpackZ(Position.z);
    thickness = packedZ.y;
    arcColor = vec4(Normal * 0.5 + 0.5, packedZ.z);

    // dual12: hi = ring radius (1/16 px), lo = angular blend (1/16 deg, capped 255).
    vec2 dual = ui_unpackDual12(LineWidth);
    radius = dual.x;
    blendDegrees = dual.y;

    // Angles travel as signed 1/64 deg in the two 16-bit slots.
    startAngle = float(UV2.x) / 64.0;
    sweepAngle = float(UV2.y) / 64.0;
}
