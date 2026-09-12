#version 330 core

in vec2 uv;
in vec4 tint;
out vec4 finalColor;

uniform sampler2D EspSpriteSampler;

layout(std140) uniform EspSpriteUniforms {
    vec4 params; // x = brightness multiplier
};

void main() {
    vec4 tex = texture(EspSpriteSampler, uv);
    vec3 col = tex.rgb * tint.rgb * params.x;
    float a = tex.a * tint.a;
    if (a <= 0.004) {
        discard;
    }
    finalColor = vec4(col, a);
}
