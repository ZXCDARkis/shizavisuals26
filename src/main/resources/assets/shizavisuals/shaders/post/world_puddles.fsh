#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D SceneSampler;
uniform sampler2D DepthSampler;

layout (std140) uniform WorldPuddlesUniforms {
    mat4 Proj;
    mat4 InvProj;
    mat4 View;
    mat4 InvView;
    vec4 CameraPos;
    vec4 PuddleParams;
    vec4 PuddleSettings;

};

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
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
    float v = 0.0;
    float a = 0.5;
    mat2 rot = mat2(0.8, -0.6, 0.6, 0.8);
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = rot * p * 2.05 + vec2(12.5);
        a *= 0.5;
    }
    return v;
}


vec2 getWaveNormal(vec2 pos, float time, float speed) {
    float t = time * speed * 2.2;
    float w1 = sin(pos.x * 4.0 + t) * cos(pos.y * 4.0 + t * 0.8);
    float w2 = sin(pos.x * 8.0 - t * 1.3) * sin(pos.y * 8.0 + t);
    float dx = (cos(pos.x * 4.0 + t) * cos(pos.y * 4.0 + t * 0.8) * 4.0 + cos(pos.x * 8.0 - t * 1.3) * 8.0);
    float dy = (-sin(pos.x * 4.0 + t) * sin(pos.y * 4.0 + t * 0.8) * 4.0 + cos(pos.y * 8.0 + t) * 8.0);
    return vec2(dx, dy) * 0.0035;
}

vec3 getViewPosition(vec2 texCoord, float rawDepth) {
    vec4 clip = vec4(texCoord * 2.0 - 1.0, rawDepth, 1.0);
    vec4 viewH = InvProj * clip;
    return viewH.xyz / max(abs(viewH.w), 0.000001);
}

vec4 traceScreenSpaceReflection(vec3 rayOrigin, vec3 rayDir, float maxDistance, int maxSteps) {
    vec3 stepVec = rayDir * (maxDistance / float(maxSteps));
    vec3 currentPos = rayOrigin + stepVec * 0.6;

    for (int i = 0; i < maxSteps; i++) {
        currentPos += stepVec;

        vec4 projPoint = Proj * vec4(currentPos, 1.0);
        if (projPoint.w <= 0.0) break;

        vec2 sampleUV = (projPoint.xy / projPoint.w) * 0.5 + 0.5;

        if (sampleUV.x < 0.002 || sampleUV.x > 0.998 || sampleUV.y < 0.002 || sampleUV.y > 0.998) {
            break;
        }

        float sceneRawDepth = texture(DepthSampler, sampleUV).r;
        if (sceneRawDepth <= 0.00001) continue;

        vec3 sceneViewPos = getViewPosition(sampleUV, sceneRawDepth);
        float depthDiff = sceneViewPos.z - currentPos.z;

        if (depthDiff >= -0.06 && depthDiff < (0.45 + float(i) * 0.04)) {
            vec2 edgeFactor = smoothstep(vec2(0.0), vec2(0.15), sampleUV) *
                              smoothstep(vec2(0.0), vec2(0.15), 1.0 - sampleUV);
            float screenFade = edgeFactor.x * edgeFactor.y;

            vec4 color = texture(SceneSampler, sampleUV);
            return vec4(color.rgb, screenFade);
        }
    }

    return vec4(0.0);
}

void main() {
    vec4 baseScene = texture(SceneSampler, uv);
    float depth = texture(DepthSampler, uv).r;

    if (depth <= 0.00001 || depth >= 0.99999) {
        finalColor = baseScene;
        return;
    }

    vec3 viewPos = getViewPosition(uv, depth);
    vec3 worldPos = CameraPos.xyz + (InvView * vec4(viewPos, 1.0)).xyz;

    vec3 dPdx = dFdx(viewPos);
    vec3 dPdy = dFdy(viewPos);
    vec3 viewNormal = normalize(cross(dPdx, dPdy));
    if (viewNormal.z > 0.0) viewNormal = -viewNormal;

    vec3 worldNormal = normalize((InvView * vec4(viewNormal, 0.0)).xyz);
    if (worldNormal.y < 0.0) worldNormal = -worldNormal;

    float flatness = smoothstep(0.65, 0.82, worldNormal.y);
    if (flatness <= 0.0) {
        finalColor = baseScene;
        return;
    }

    float scale = max(0.05, PuddleSettings.x * 0.4);
    vec2 pCoord = worldPos.xz * scale;
    float n = fbm(pCoord);

    float coverage = clamp(1.0 - PuddleParams.y, 0.05, 0.95);
    float puddleMask = smoothstep(coverage - 0.08, coverage + 0.08, n) * flatness;

    if (puddleMask <= 0.001) {
        finalColor = baseScene;
        return;
    }

    float dist = length(viewPos);
    float distFade = clamp(1.0 - (dist - 48.0) / 64.0, 0.0, 1.0);
    puddleMask *= distFade;

    if (puddleMask <= 0.001) {
        finalColor = baseScene;
        return;
    }

    vec2 ripples = getWaveNormal(worldPos.xz, PuddleParams.x, PuddleParams.z) * PuddleSettings.y;

    vec3 perturbedWorldNormal = normalize(vec3(ripples.x, 1.0, ripples.y));
    vec3 perturbedViewNormal = normalize((View * vec4(perturbedWorldNormal, 0.0)).xyz);

    vec3 viewDir = normalize(viewPos);
    float NdotV = clamp(dot(perturbedViewNormal, -viewDir), 0.0, 1.0);

    float F0 = 0.08;
    float fresnel = F0 + (1.0 - F0) * pow(1.0 - NdotV, 3.5);
    fresnel = clamp(fresnel * PuddleParams.w, 0.0, 1.0);

    vec3 reflectRay = normalize(reflect(viewDir, perturbedViewNormal));
    vec4 ssrHit = vec4(0.0);
    if (reflectRay.z < -0.05) {
        ssrHit = traceScreenSpaceReflection(viewPos, reflectRay, 28.0, 32);
    }

    vec3 wetFloor = pow(baseScene.rgb, vec3(1.15)) * 0.58;

    vec2 refractUv = uv + ripples * (1.2 + 0.8 * NdotV);
    refractUv = clamp(refractUv, 0.001, 0.999);
    vec3 underWaterScene = texture(SceneSampler, refractUv).rgb * 0.75;
    wetFloor = mix(wetFloor, underWaterScene, 0.45);

    vec3 finalReflection = ssrHit.a > 0.0 ? ssrHit.rgb : baseScene.rgb * 1.15;
    float reflectionMix = fresnel * (ssrHit.a > 0.0 ? ssrHit.a : (0.25 + (1.0 - NdotV) * 0.75));

    float wetSpecular = pow(clamp(dot(perturbedWorldNormal, vec3(0.0, 1.0, 0.0)), 0.0, 1.0), 32.0) * 0.12 * PuddleSettings.y;

    vec3 puddleColor = mix(wetFloor, finalReflection, clamp(reflectionMix, 0.0, 1.0)) + vec3(wetSpecular);

    finalColor = vec4(mix(baseScene.rgb, puddleColor, puddleMask), baseScene.a);
}