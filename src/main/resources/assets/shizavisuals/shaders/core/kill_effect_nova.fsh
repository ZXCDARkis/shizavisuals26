#version 330 core

// Kill-effect "nova": shockwave ring + rotating rays + core flash + flying embers.
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

void main() {
    float uTime = params.x;
    float uProgress = params.y;
    float uIntensity = params.z;
    vec4 uColor = effectColor;

    vec2 uv = texCoord - vec2(0.5);
    float dist = length(uv);
    float ang = atan(uv.y, uv.x);
    float prog = clamp(uProgress, 0.0, 1.0);

    float appear = smoothstep(0.0, 0.08, prog);
    float vanish = 1.0 - smoothstep(0.55, 1.0, prog);

    // Expanding shockwave ring (gaussian band, widens as it fades)
    float rr = prog * 0.65;
    float w = 0.025 + 0.06 * prog;
    float q = (dist - rr) / max(w, 1.0e-4);
    float ring = exp(-q * q);

    // Rotating god-rays, strongest right after the burst
    float rays = 0.5 + 0.5 * sin(ang * 12.0 + uTime * 6.0 + sin(ang * 5.0 - uTime * 3.0) * 1.5);
    rays = rays * rays * rays * exp(-dist * 4.5) * (1.0 - prog);

    // White-hot core flash, collapses fast
    float coreDen = 0.015 + 0.25 * prog * prog;
    float core = exp(-dist * dist / (coreDen * coreDen)) * (1.0 - prog * 0.75) * 2.0;

    // Embers flying outward, each with its own speed/size
    float emb = 0.0;
    for (int i = 0; i < 24; i++) {
        float seed = float(i) * 1.71 + 0.3;
        float a = hash21(vec2(seed, 7.0)) * 6.2832;
        float speed = 0.35 + 0.65 * hash21(vec2(seed, 3.0));
        float d = prog * speed * 0.7;
        vec2 p = vec2(cos(a + uTime * 0.5), sin(a + uTime * 0.5)) * d;
        float sz = 0.008 + 0.020 * hash21(vec2(seed, 5.0));
        float dd = length(uv - p);
        float fade = (1.0 - smoothstep(0.0, 0.9, prog + (1.0 - speed) * 0.35)) * (0.4 + 0.6 * speed);
        emb += exp(-dd * dd / (sz * sz)) * fade;
    }
    emb *= 0.9;

    vec3 base = uColor.rgb;
    vec3 col = base * ring * 1.6
             + mix(base, vec3(1.0), 0.6) * rays * 1.2
             + vec3(1.0, 0.97, 0.90) * core
             + mix(base, vec3(1.0, 0.80, 0.40), 0.5) * emb;

    float alpha = clamp(ring + rays * 0.7 + core * 0.8 + emb * 0.6, 0.0, 1.0);
    alpha *= vertexColor.a * appear * vanish;
    // Soft quad borders so the billboard never cuts hard at the screen edge.
    vec2 borderDist = min(texCoord, 1.0 - texCoord);
    alpha *= smoothstep(0.0, 0.08, min(borderDist.x, borderDist.y));

    if (alpha < 0.01) discard;
    fragColor = vec4(clamp(col * uIntensity, 0.0, 1.0), alpha);
}
