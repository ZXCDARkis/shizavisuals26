#version 330 core

// Velocity-reprojection motion blur, ported from the MotionReBlur reference:
// each pixel is reprojected into the previous frame via inverse view-projection,
// then blurred along its screen-space velocity with jittered gamma-correct taps.

in vec2 uv;
out vec4 finalColor;

uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform MotionBlurUniforms {
    mat4 mvInverse;
    mat4 projInverse;
    mat4 prevModelView;
    mat4 prevProjection;
    vec3 cameraPos;
    vec3 prevCameraPos;
    vec2 viewRes;
    float blendFactor;
    float inverseSamples;
    float handDepthThreshold;
    int motionBlurSamples;
    int halfSamples;
    int blurAlgorithm;
};

vec3 reproject(vec3 screenPos) {
    vec3 ndc = screenPos * 2.0 - 1.0;
    vec4 viewPos4 = projInverse * vec4(ndc, 1.0);
    vec3 viewPos = viewPos4.xyz / viewPos4.w;

    vec3 worldPos = (mvInverse * vec4(viewPos, 1.0)).xyz + (cameraPos - prevCameraPos);
    vec4 prevProj = prevProjection * (prevModelView * vec4(worldPos, 1.0));

    return (prevProj.xyz / prevProj.w) * 0.5 + 0.5;
}

vec2 clampLength(vec2 velocity) {
    // Раньше потолок был 0.4 (40% экрана): при прыжке/спринте весь
    // экран мазался в кашу. Теперь максимум ~5% экрана, вертикаль
    // давим дополнительно — прыжок больше не даёт простыню.
    velocity.y *= 0.65;
    float lenSq = dot(velocity, velocity);
    float maxLen = 0.055;
    return (lenSq > maxLen * maxLen) ? velocity * (maxLen * inversesqrt(lenSq)) : velocity;
}

float noise(vec2 pos) {
    return fract(52.9829189 * fract(0.06711056 * pos.x + 0.00583715 * pos.y));
}

void main() {
    ivec2 texel = ivec2(gl_FragCoord.xy);

    float depth = texelFetch(DepthSampler, texel, 0).x;

    // NOTE: this engine uses reversed-Z (sky depth is ~0.0, near geometry ~1.0),
    // so "near stays sharp" means skipping HIGH depth, opposite of the reference mod.
    if (depth > handDepthThreshold) {
        finalColor = texture(SceneSampler, uv);
        return;
    }

    vec2 velocity = uv - reproject(vec3(uv, depth)).xy;
    velocity = clampLength(velocity);

    // Игнор микродрожания камеры (дыхание, покачивание при ходьбе):
    // меньше ~0.3px — блура нет вообще.
    vec2 velocityPx = velocity * viewRes;
    float speedPx = length(velocityPx);
    if (speedPx < 0.3) {
        finalColor = texture(SceneSampler, uv);
        return;
    }
    // Мягкое нарастание: до 2px блур выходит из нуля постепенно,
    // а не щёлкает на полную.
    float ramp = clamp((speedPx - 0.3) / 1.7, 0.0, 1.0);
    ramp *= ramp * (3.0 - 2.0 * ramp);

    vec2 totalOffset = blendFactor * velocity * ramp;
    // Финальная страховка: даже на максимуме силы смещение не больше
    // ~3.5% экрана (~35px в 1080p).
    float offsetLen = length(totalOffset);
    float maxOffset = 0.035;
    if (offsetLen > maxOffset) {
        totalOffset *= maxOffset / offsetLen;
    }
    vec2 baseStep = totalOffset * inverseSamples;

    vec3 colorSum = vec3(0.0);
    vec2 seed = uv * viewRes;

    bool centerBlur = blurAlgorithm != 0;
    for (int i = 0; i < motionBlurSamples; ++i) {
        float fi = float(i);

        float jitter = noise(seed + vec2(fi, fi * 1.4));
        float offsetCentered = fi - float(halfSamples);
        float sampleIndex = centerBlur ? offsetCentered : fi;
        float sampleOffset = sampleIndex + jitter;

        vec2 pos = uv + sampleOffset * baseStep;
        vec3 c = texture(SceneSampler, pos).rgb;

        colorSum += c * c;
    }
    finalColor = vec4(sqrt(colorSum * inverseSamples), 1.0);
}
