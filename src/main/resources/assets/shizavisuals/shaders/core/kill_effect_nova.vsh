#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

layout(std140) uniform Projection {
    mat4 ProjMat;
};

out vec2 texCoord;
out vec4 vertexColor;

void main() {
    texCoord = UV0;
    vertexColor = Color;
    gl_Position = ProjMat * vec4(Position, 1.0);
}
