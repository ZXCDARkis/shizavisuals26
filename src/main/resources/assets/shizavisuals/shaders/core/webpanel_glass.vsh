#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

layout(std140) uniform Projection {
    mat4 ProjMat;
};

out vec2 FragCoord;
out vec2 TexCoord;
out vec4 VertColor;

void main() {
    gl_Position = ProjMat * vec4(Position, 1.0);

    // Изогнутое стекло режется на вертикальные полосы, поэтому нормированная
    // координата панели берётся из UV, а не из vertexID: у второй и следующих
    // полос vertexID снова даёт 0..3, и скругление углов считалось бы для
    // каждой полосы отдельно.
    FragCoord = UV0;
    TexCoord = UV0;
    VertColor = Color;
}
