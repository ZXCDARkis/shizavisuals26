#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D SceneSampler;

layout(std140) uniform TntShockwaveUniforms {
    vec4 Screen;  // xy = framebuffer size in pixels, zw unused
    vec4 Ring;    // xy = origin in pixels, z = radius in pixels, w = thickness in pixels
    vec4 Params;  // x = strength, yzw unused
};

// The ring is measured in framebuffer pixels rather than uv so it stays
// circular on non-square windows; gl_FragCoord already shares uv's origin
// (bottom-left), so no flip is needed here.
void main() {
    vec2 frag = gl_FragCoord.xy;
    vec2 delta = frag - Ring.xy;
    float dist = length(delta);
    vec2 dir = dist > 0.001 ? delta / dist : vec2(0.0);

    float ring = exp(-pow((dist - Ring.z) / max(Ring.w, 1.0), 2.0));

    vec2 offset = dir * ring * Params.x / max(Screen.xy, vec2(1.0));
    vec3 col = texture(SceneSampler, clamp(uv + offset, 0.0, 1.0)).rgb;
    col += ring * 0.08 * vec3(1.0, 0.96, 0.88);

    finalColor = vec4(col, 1.0);
}