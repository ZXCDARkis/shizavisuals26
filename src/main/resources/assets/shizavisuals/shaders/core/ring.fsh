#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <shizavisuals:ui_common.glsl>
#moj_import <shizavisuals:ui_fragment.glsl>

in vec2 localPos;
in vec4 baseColor;
flat in vec2 halfSize;
flat in vec4 arcColor;
flat in float radius;
flat in float thickness;
flat in float startAngle;
flat in float sweepAngle;
flat in float blendDegrees;

out vec4 fragColor;

void main() {
    float ringRadius = max(radius, 0.5);
    float ringThickness = max(thickness, 0.15);

    // Radial band coverage; evaluated unconditionally so derivatives stay defined.
    float bandDist = abs(length(localPos) - ringRadius) - ringThickness * 0.5;
    float bandCoverage = ui_coverage(bandDist);

    float sweep = sweepAngle;
    float window = 1.0;
    if (sweep < 360.0) {
        float angle = degrees(atan(localPos.y, localPos.x));
        angle = mod(angle - startAngle, 360.0);
        float blend = max(blendDegrees, 0.001);
        window = min(clamp(angle / blend, 0.0, 1.0), clamp((sweep - angle) / blend, 0.0, 1.0));
    }

    vec4 arc = arcColor * ColorModulator * window;
    vec4 base = baseColor * ColorModulator;
    float baseAlpha = base.a * bandCoverage;
    float arcAlpha = arc.a * bandCoverage;

    vec4 composed = ui_compositePremul(
        vec4(base.rgb * baseAlpha, baseAlpha),
        vec4(arc.rgb * arcAlpha, arcAlpha)
    );
    if (composed.a <= 0.001) {
        discard;
    }
    fragColor = ui_unpremultiply(composed);
}
