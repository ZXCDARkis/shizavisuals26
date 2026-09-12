#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

layout(std140) uniform Projection {
    mat4 ProjMat;
};

out vec4 VertColor;
out vec2 FragCoord;

void main() {
    gl_Position = ProjMat * vec4(Position, 1.0);
    VertColor = Color;

    // Угловая координата (0..1) идёт явным атрибутом: меш рисуется как
    // TRIANGLES (по 6 вершин на квад), и gl_VertexID%4 для него не имеет смысла.
    FragCoord = UV0;
}
