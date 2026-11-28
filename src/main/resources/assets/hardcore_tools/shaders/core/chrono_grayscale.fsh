#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 src = texture(Sampler0, texCoord0);
    float luma = dot(src.rgb, vec3(0.299, 0.587, 0.114));
    float contrasted = clamp((luma - 0.5) * 1.14 + 0.5, 0.0, 1.0);
    vec3 silverGray = vec3(contrasted) * vec3(0.93, 0.95, 0.99);
    fragColor = vec4(silverGray, vertexColor.a) * ColorModulator;
}
