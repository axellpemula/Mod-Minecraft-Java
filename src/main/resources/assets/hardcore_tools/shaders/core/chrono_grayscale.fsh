#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 src = texture(Sampler0, texCoord0);
    float luma = dot(src.rgb, vec3(0.2126, 0.7152, 0.0722));
    float lin = clamp((luma - 0.5) * 1.22 + 0.5, 0.0, 1.0);
    float curve = mix(lin, smoothstep(0.02, 0.98, lin), 0.35);
    vec3 shadowTint = vec3(0.86, 0.90, 0.98);
    vec3 highlightTint = vec3(0.98, 0.99, 1.00);
    vec3 silverGray = vec3(curve) * mix(shadowTint, highlightTint, curve);
    fragColor = vec4(silverGray, vertexColor.a) * ColorModulator;
}
