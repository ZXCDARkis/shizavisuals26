#version 330 core

in vec4 VertColor;
in vec2 FragCoord;

layout(std140) uniform WebPanelSdf {
    vec2 Size;
    float Smoothness;
    vec4 Radius;
};

out vec4 OutColor;

float roundedBoxSDF(vec2 CenterPosition, vec2 Size, vec4 Radius) {
    Radius = min(Radius, vec4(Size.x, Size.y, Size.x, Size.y));
    Radius.xy = (CenterPosition.x > 0.0) ? Radius.xy : Radius.zw;
    Radius.x  = (CenterPosition.y > 0.0) ? Radius.x  : Radius.y;

    vec2 q = abs(CenterPosition) - Size + Radius.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - Radius.x;
}

void main() {
    vec2 center = Size * 0.5;
    float distance = roundedBoxSDF(center - (FragCoord * Size), center - 1.0, Radius);

    float alpha = 1.0 - smoothstep(1.0 - Smoothness, 1.0, distance);
    vec4 finalColor = vec4(VertColor.rgb, VertColor.a * alpha);

    if (finalColor.a <= 0.001) {
        discard;
    }

    OutColor = finalColor;
}
