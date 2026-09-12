#version 330 core

// Kill-effect "beam": judgement light pillar crashing down + rising energy rings.
// params = (uTime, uProgress, uIntensity, uSize), effectColor = uColor (rgba)

layout(std140) uniform KillEffectUniforms {
    vec4 params;
    vec4 effectColor;
};

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

float hash21(vec2 p) {
    p = fract(p * vec2(443.8975, 397.2973));
    p += dot(p, p + 19.19);
    return fract(p.x * p.y);
}

float smoothNoise2D(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x),
        mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x),
        f.y
    );
}

void main() {
    float uTime = params.x;
    float uProgress = params.y;
    float uIntensity = params.z;
    vec4 uColor = effectColor;

    float x = texCoord.x * 2.0 - 1.0;
    float y = texCoord.y;
    float prog = clamp(uProgress, 0.0, 1.0);

    // Pillar slams down fast, holds, then collapses upward
    float strike = smoothstep(0.0, 0.12, prog);
    float collapse = 1.0 - smoothstep(0.55, 1.0, prog);
    float height = mix(0.15, 1.0, strike) * mix(1.0, 0.25, 1.0 - collapse);
    float body = (1.0 - smoothstep(height - 0.08, height, y)) * smoothstep(0.0, 0.03, y);
    if (body <= 0.002) discard;

    // Flickering pillar width
    float flicker = 0.75 + 0.25 * smoothNoise2D(vec2(uTime * 7.0, 0.0));
    float wCore = 0.10 * flicker;
    float wGlow = 0.30 * flicker;
    float core = exp(-x * x / (wCore * wCore));
    float glow = exp(-x * x / (wGlow * wGlow)) * 0.7;

    // Energy texture scrolling upward inside the pillar
    float scroll = smoothNoise2D(vec2(x * 6.0, y * 8.0 - uTime * 5.0));
    float veins = smoothstep(0.45, 0.9, scroll) * (core + glow * 0.5);

    // Rings of energy racing up the pillar
    float rings = 0.0;
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float hgt = fract(uTime * 0.9 + fi * 0.2 + hash21(vec2(fi, 9.0)) * 0.5);
        if (hgt > height) continue;
        float dd = abs(y - hgt * height);
        rings += exp(-dd * dd / (0.004)) * (1.0 - hgt * 0.5);
    }
    rings *= 0.8;

    // Impact flash at the base + jagged sparks
    float impact = exp(-y * 7.0) * exp(-x * x * 4.0) * (1.0 - prog * 0.5) * 1.5;
    float sparks = 0.0;
    for (int i = 0; i < 12; i++) {
        float seed = float(i) * 2.17 + 1.3;
        float a = (hash21(vec2(seed, 2.0)) - 0.5) * 2.2;
        float len = 0.15 + 0.45 * hash21(vec2(seed, 5.0)) * strike;
        float sx = sin(a) * len;
        float sy = (1.0 - cos(a)) * len * 0.6;
        vec2 d = vec2(x - sx, y - sy);
        float life = fract(uTime * 1.5 + seed);
        sparks += exp(-dot(d, d) / 0.0004) * (1.0 - life) * strike;
    }
    sparks *= 0.8;

    vec3 base = uColor.rgb;
    vec3 col = vec3(1.0, 0.98, 0.92) * core * 1.8
             + mix(base, vec3(1.0), 0.35) * (glow + veins) * 1.2
             + mix(base, vec3(1.0), 0.5) * rings
             + mix(base, vec3(1.0, 0.9, 0.6), 0.4) * (impact + sparks);

    float alpha = clamp(core + glow * 0.7 + veins * 0.5 + rings * 0.6 + impact * 0.8 + sparks * 0.5, 0.0, 1.0);
    alpha *= body * vertexColor.a;

    float fadeIn = smoothstep(0.0, 0.06, prog);
    alpha *= fadeIn * collapse * uIntensity;
    // Soft quad borders so the billboard never cuts hard at the screen edge.
    vec2 borderDist = min(texCoord, 1.0 - texCoord);
    alpha *= smoothstep(0.0, 0.08, min(borderDist.x, borderDist.y));

    if (alpha < 0.01) discard;
    fragColor = vec4(clamp(col * uIntensity, 0.0, 1.0), alpha);
}
