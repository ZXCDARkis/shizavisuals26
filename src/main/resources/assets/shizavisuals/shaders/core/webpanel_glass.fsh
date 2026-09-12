#version 330 core

in vec2 FragCoord;
in vec2 TexCoord;
in vec4 VertColor;

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

layout(std140) uniform WebPanelGlass {
    vec2  Size;
    float Smoothness;
    float ContentAlpha;
    vec4  Radius;
    vec2  ScreenSize;
    float Opacity;
    float TintAlpha;
    vec4  TintColor;
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
    float mask = 1.0 - smoothstep(1.0 - Smoothness, 1.0, distance);
    if (mask <= 0.0) {
        discard;
    }

    vec3 backdrop = texture(Sampler0, gl_FragCoord.xy / ScreenSize).rgb;
    vec3 glass = mix(backdrop, TintColor.rgb, TintAlpha);

    // Изнанка окна — только матовое стекло (размытая подложка + тон), без
    // страницы: сзади сайт читался зеркально и выглядел мусором.
    float content = gl_FrontFacing ? ContentAlpha : 0.0;

    vec4 page = texture(Sampler1, TexCoord);
    vec3 rgb = mix(glass, page.rgb, clamp(page.a * content, 0.0, 1.0));

    float alpha = mask * Opacity * VertColor.a;
    if (alpha <= 0.001) {
        discard;
    }
    OutColor = vec4(rgb, alpha);
}
