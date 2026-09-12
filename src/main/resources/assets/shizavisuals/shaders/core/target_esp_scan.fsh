#version 330 core

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec3 scanColor;
in vec3 secondScanColor;
in float scanHeight;
in float scanPhase;
in float appear;
in float glowStrength;
in float horizontalPosition;
in float scanDirection;
in vec3 modelNormal;

out vec4 fragColor;

void main() {
    vec4 skin = texture(Sampler0, texCoord0);
    if (skin.a < 0.1) {
        discard;
    }

    const float TWO_PI = 6.28318530718;
    float gentleBend = sin(horizontalPosition * TWO_PI * 0.82 + scanPhase * 3.2) * 0.015;
    gentleBend += sin(horizontalPosition * TWO_PI * 1.73 - scanPhase * 1.6) * 0.006;
    gentleBend += sin(horizontalPosition * TWO_PI * 3.35 + scanPhase * 1.1) * 0.0035;
    gentleBend += sin(horizontalPosition * TWO_PI * 6.20 - scanPhase * 2.3) * 0.0018;

    float signedDistance = (scanHeight + gentleBend - scanPhase) * scanDirection;
    float distanceToLine = abs(signedDistance);
    float edgeSmoothing = clamp(fwidth(signedDistance) * 1.15, 0.001, 0.006);
    float core = 1.0 - smoothstep(
            max(0.0, 0.004 - edgeSmoothing),
            0.015 + edgeSmoothing,
            distanceToLine);
    float innerGlow = 1.0 - smoothstep(0.014, 0.037 + edgeSmoothing, distanceToLine);
    float outerGlow = 1.0 - smoothstep(0.034, 0.075 + edgeSmoothing, distanceToLine);
    float behind = 1.0 - step(0.0, signedDistance);
    float middleTravel = sin(clamp(scanPhase, 0.0, 1.0) * 3.14159265359);
    float trailLength = mix(0.060, 0.40, smoothstep(0.0, 0.78, middleTravel));
    float trail = behind * (1.0 - smoothstep(0.012, trailLength, distanceToLine));
    float brightTrailStart = behind
            * (1.0 - smoothstep(0.018, max(0.035, trailLength * 0.48), distanceToLine));

    // Horizontal cube caps made the old effect look like rectangular rings.
    float sideMask = 1.0 - smoothstep(0.65, 0.90, abs(modelNormal.y));
    float surfaceMask = 0.02 + sideMask * 0.98;
    float opacity = skin.a * appear
            * clamp(core
                    + glowStrength * (innerGlow * 0.26 + outerGlow * 0.055)
                    + trail * 0.46
                    + brightTrailStart * 0.15 * glowStrength,
                    0.0,
                    1.0)
            * surfaceMask;
    if (opacity < 0.003) {
        discard;
    }

    float colorFlow = 0.5 + 0.5 * sin(horizontalPosition * TWO_PI * 1.05 - scanPhase * 5.2);
    colorFlow = smoothstep(0.0, 1.0, colorFlow);
    vec3 flowingColor = mix(scanColor, secondScanColor, colorFlow);

    vec3 whiteCore = vec3(0.82, 0.96, 1.0);
    vec3 color = mix(flowingColor * 0.68, whiteCore, core * 0.90);
    color *= 0.85 + core * 0.5 * glowStrength;
    fragColor = vec4(color * ColorModulator.rgb, opacity * ColorModulator.a);
}
