#version 330 core

// Kill-effect "portal" billboard.
// Ported to ShizaVisuals core pipeline (Blaze3D RenderPipeline):
//   - only Projection uniform is bound by the engine
//   - effect params come from KillEffectUniforms:
//       params = (uTime, uProgress, uIntensity, uSize), effectColor = uColor (rgba)

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
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0, a = 0.5, freq = 1.0;
    for (int k = 0; k < 4; k++) {
        v += a * smoothNoise2D(p * freq);
        freq *= 2.1;
        a *= 0.48;
    }
    return v;
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    float uTime = params.x;
    float uProgress = params.y;
    float uIntensity = params.z;
    vec4 uColor = effectColor;

    vec2 center = vec2(0.5);
    vec2 uv = texCoord;
    vec2 dir = uv - center;
    float dist = length(dir);
    float angle = atan(dir.y, dir.x);

    float progress = uProgress;
    float time = uTime;

    // Lifecycle envelope
    float appear = smoothstep(0.0, 0.10, progress);
    float vanish = 1.0 - smoothstep(0.70, 1.0, progress);
    vanish = vanish * vanish * (3.0 - 2.0 * vanish);

    float radius = 0.05 + 0.55 * progress;
    radius *= (0.9 + 0.1 * sin(time * 2.5));
    radius *= (0.15 + 0.85 * vanish);
    radius = max(radius, 0.001);

    // 3D sphere
    float phi = dist * 3.14159;
    float theta = angle;
    float sx = sin(phi) * cos(theta);
    float sy = sin(phi) * sin(theta);
    float sz = cos(phi);
    float rotAngle = time * 0.6;
    float cx = sx * cos(rotAngle) - sz * sin(rotAngle);
    float cz = sx * sin(rotAngle) + sz * cos(rotAngle);
    sx = cx; sz = cz;

    float sphereMask = 1.0 - smoothstep(0.0, radius, dist);

    // 1. Sphere grid
    float grid = 0.0;
    float lat = sin(phi * 8.0 + time * 0.2) * 0.5 + 0.5;
    float lon = sin(theta * 6.0 + time * 0.4) * 0.5 + 0.5;
    grid = (lat > 0.97 || lon > 0.97) ? 1.0 : 0.0;
    grid *= sphereMask * (0.6 + 0.4 * sz);
    grid *= 1.5;

    // 2. Rings (4)
    float rings = 0.0;
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float radiusRing = 0.25 + fi * 0.15 + 0.05 * sin(time * 0.4 + fi * 1.1);
        float ringDist = length(dir);
        float ringWidth = 0.01 + 0.01 * (1.0 - progress);
        float ring = 1.0 - smoothstep(radiusRing - ringWidth, radiusRing + ringWidth, ringDist);
        ring *= (1.0 - abs(ringDist - radiusRing) * 4.0);
        ring *= (0.7 + 0.3 * sin(time * 1.0 + fi * 1.3));
        ring *= sphereMask * (0.5 + 0.5 * sz);
        rings += ring * 0.8;
    }
    rings *= 1.8;

    // 3. Lightning
    float lightning = 0.0;
    for (int i = 0; i < 8; i++) {
        float seed = float(i) * 1.73 + 0.5;
        float a = hash21(vec2(seed, 0.0)) * 6.2832;
        float len = 0.3 + 0.7 * hash21(vec2(seed, 1.0));
        float life = fract(time * 0.8 + seed * 0.5);
        float bright = 1.0 - smoothstep(0.1, 0.9, life);
        float rad = radius * (0.2 + 0.8 * len);
        vec2 pos = vec2(0.5 + rad * cos(a + time * 0.4), 0.5 + rad * sin(a + time * 0.4));
        vec2 delta = uv - pos;
        vec2 dirToPoint = normalize(pos - center + vec2(1e-5));
        vec2 perp = vec2(-dirToPoint.y, dirToPoint.x);
        float proj = dot(delta, dirToPoint);
        float perpDist = abs(dot(delta, perp));
        float line = 1.0 - smoothstep(0.0, 0.02, perpDist);
        line *= smoothstep(0.0, 0.1, proj) * smoothstep(1.0, 0.85, proj / (rad + 0.001));
        lightning += line * bright * 1.2;
    }
    lightning *= sphereMask * (0.5 + 0.5 * sz);
    lightning *= 1.5;

    // 4. Core
    float coreDist = length(dir) / (0.03 + 0.02 * progress);
    float core = exp(-coreDist * coreDist) * 2.5;
    core *= (1.0 - progress * 0.2);
    core *= (0.6 + 0.4 * sz);

    // 5. Particles (60)
    float particles = 0.0;
    for (int i = 0; i < 60; i++) {
        float seed = float(i) * 1.37;
        float a = hash21(vec2(seed, 0.0)) * 6.2832;
        float r = radius * (0.1 + 0.9 * hash21(vec2(seed, 1.0)));
        float life = fract(time * 0.5 + seed * 0.3);
        float bright = 1.0 - smoothstep(0.1, 0.9, life);
        float angleOff = time * (0.4 + 0.6 * hash21(vec2(seed, 2.0)));
        float phiP = (r / radius) * 3.14159;
        float thetaP = a + angleOff;
        float sxP = sin(phiP) * cos(thetaP);
        float syP = sin(phiP) * sin(thetaP);
        float szP = cos(phiP);
        vec2 pos = center + vec2(sxP, syP) * radius * 0.5;
        float szFactor = 0.5 + 0.5 * szP;
        float pr = 0.005 + 0.025 * hash21(vec2(seed, 3.0));
        float d = length(uv - pos);
        float p = exp(-d * d / (2.0 * pr * pr));
        particles += p * bright * 0.8 * szFactor;
    }
    particles *= (1.0 - progress * 0.5);
    particles *= 1.8;

    // 6. Glow + pseudo bloom
    float glow = exp(-dist * 5.0) * 0.6 * (1.0 + 0.3 * sin(time * 2.5));
    glow *= (1.0 - progress * 0.3);
    glow *= (0.6 + 0.4 * sz);
    float bloom = exp(-dist * 3.0) * 0.3;

    // 7. Shockwave ring
    float ring2 = 0.0;
    float r2 = radius * 1.7 * progress;
    ring2 = 1.0 - smoothstep(r2 - 0.03, r2 + 0.03, dist);
    ring2 *= 0.5 * (1.0 - progress) * sin(progress * 3.14159);
    ring2 *= 1.5;

    // Palette
    vec3 baseColor = uColor.rgb;
    vec3 gridColor = mix(baseColor, vec3(0.9, 0.95, 1.0), 0.4);
    vec3 ringColor = mix(baseColor, vec3(1.0, 0.9, 0.5), 0.5);
    vec3 lightningColor = vec3(0.8, 0.9, 1.0);
    vec3 coreColor = vec3(1.0, 0.98, 0.9);
    vec3 particleColor = vec3(1.0, 0.85, 0.5);
    vec3 glowColor = mix(baseColor, vec3(0.6, 0.8, 1.0), 0.5);

    float finalIntensity = grid + rings + lightning + core + particles + glow + bloom + ring2;
    vec3 finalColor = gridColor * grid * 1.5
                    + ringColor * rings * 1.2
                    + lightningColor * lightning * 1.0
                    + coreColor * core * 2.5
                    + particleColor * particles * 0.9
                    + glowColor * glow * 0.6
                    + vec3(0.8, 0.9, 1.0) * bloom * 0.5
                    + baseColor * ring2 * 0.6;
    finalColor *= uIntensity * 1.2;

    float finalAlpha = clamp(finalIntensity * 1.6, 0.0, 1.0) * vertexColor.a;
    float vignette = 1.0 - dist * 0.5;
    finalAlpha *= mix(vignette, 1.0, 0.5);
    finalAlpha *= appear * vanish;
    // Soft quad borders: the billboard must never read as a cut-out picture
    // when it touches the screen edge.
    vec2 borderDist = min(texCoord, 1.0 - texCoord);
    finalAlpha *= smoothstep(0.0, 0.08, min(borderDist.x, borderDist.y));

    if (finalAlpha < 0.01) discard;
    fragColor = vec4(clamp(finalColor, 0.0, 1.0), finalAlpha);
}
