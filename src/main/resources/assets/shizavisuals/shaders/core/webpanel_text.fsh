#version 330 core

in vec2 TexCoord;
in vec4 VertColor;

uniform sampler2D Sampler0;

layout(std140) uniform WebPanelText {
    float PxRange;
    float Weight;
};

out vec4 OutColor;

float median3(vec3 v) {
    return max(min(v.r, v.g), min(max(v.r, v.g), v.b));
}

float screenPxRange(vec2 texCoord, vec2 atlasSize) {
    vec2 unitRange = vec2(PxRange) / atlasSize;
    vec2 screenTexSize = vec2(1.0) / max(fwidth(texCoord), vec2(0.0000001));
    return max(0.5 * dot(unitRange, screenTexSize), 1.0);
}

void main() {
    vec4 atlas = texture(Sampler0, TexCoord);
    float range = screenPxRange(TexCoord, vec2(textureSize(Sampler0, 0)));

    // MTSDF: rgb-медиана держит острыми углы, альфа — честный SDF.
    float sdCrisp = median3(atlas.rgb) - 0.5;
    float sdRound = atlas.a - 0.5;
    float sd = mix(sdRound, sdCrisp, clamp(range - 1.0, 0.0, 1.0));

    sd += Weight;

    float alpha = clamp(sd * range + 0.5, 0.0, 1.0);
    if (alpha <= 0.004) {
        discard;
    }
    OutColor = vec4(VertColor.rgb, VertColor.a * alpha);
}
