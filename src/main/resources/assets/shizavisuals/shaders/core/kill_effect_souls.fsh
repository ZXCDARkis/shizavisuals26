#version 330 core

// Kill-effect "souls": glowing wisps rising from the corpse + rising soul orbs.
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

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    float freq = 1.0;
    for (int k = 0; k < 4; k++) {
        v += a * smoothNoise2D(p * freq);
        freq *= 2.05;
        a *= 0.5;
    }
    return v;
}

void main() {
    float uTime = params.x;
    float uProgress = params.y;
    float uIntensity = params.z;
    vec4 uColor = effectColor;

    float x = texCoord.x * 2.0 - 1.0;
    float y = texCoord.y;
    float prog = clamp(uProgress, 0.0, 1.0);

    // Souls only occupy the lower 2/3, rising as the effect plays
    float rise = 0.15 + prog * 0.85;
    float body = (1.0 - smoothstep(rise - 0.25, rise, y)) * smoothstep(0.0, 0.06, y);
    if (body <= 0.002) discard;

    float width = mix(0.55, 1.0, y);
    float side = 1.0 - smoothstep(0.2, 1.0, abs(x) / max(width, 1.0e-4));
    float mask = body * side;

    // Rising wisp streaks, scrolling upward with fbm wobble
    float wobble = fbm(vec2(texCoord.x * 5.0, y * 3.0 - uTime * 1.6)) - 0.5;
    float streaks = fbm(vec2(texCoord.x * 9.0 + wobble * 2.0, y * 4.0 - uTime * 2.4));
    streaks = smoothstep(0.35, 0.85, streaks) * mask;

    // Soul orbs floating up, swaying side to side
    float orbs = 0.0;
    for (int i = 0; i < 16; i++) {
        float seed = float(i) * 1.93 + 0.7;
        float speed = 0.25 + 0.5 * hash21(vec2(seed, 4.0));
        float hgt = fract(hash21(vec2(seed, 1.0)) + uTime * speed * 0.35);
        if (hgt > rise) continue;
        float sway = sin(uTime * (1.0 + hash21(vec2(seed, 2.0))) + seed * 6.28) * 0.12 * hgt;
        float px = (hash21(vec2(seed, 8.0)) * 2.0 - 1.0) * 0.4 + sway;
        float sz = 0.015 + 0.030 * hash21(vec2(seed, 6.0));
        vec2 d = vec2(x - px, (y - hgt * 0.9) * 1.6);
        float fade = (1.0 - hgt) * smoothstep(0.0, 0.1, hgt);
        orbs += exp(-dot(d, d) / (sz * sz)) * fade;
    }
    orbs *= 1.2;

    // Cold ground glow where the soul leaves the body
    float grave = exp(-y * 9.0) * exp(-x * x * 6.0) * (1.0 - prog * 0.6);

    vec3 base = uColor.rgb;
    vec3 col = mix(base, vec3(0.75, 0.88, 1.0), 0.45) * streaks * 1.1
             + vec3(0.92, 0.97, 1.0) * orbs * 1.4
             + base * grave * 1.2;

    float alpha = clamp(streaks * 0.8 + orbs + grave * 0.7, 0.0, 1.0);
    alpha *= vertexColor.a;

    float fadeIn = smoothstep(0.0, 0.10, prog);
    float fadeOut = 1.0 - smoothstep(0.55, 1.0, prog);
    alpha *= fadeIn * fadeOut * uIntensity;
    // Soft quad borders so the billboard never cuts hard at the screen edge.
    vec2 borderDist = min(texCoord, 1.0 - texCoord);
    alpha *= smoothstep(0.0, 0.08, min(borderDist.x, borderDist.y));

    if (alpha < 0.01) discard;
    fragColor = vec4(clamp(col * uIntensity, 0.0, 1.0), alpha);
}
