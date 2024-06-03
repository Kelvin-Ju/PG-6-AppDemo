precision mediump float;
varying vec2 v_TexCoord;
uniform sampler2D u_Texture;

void main() {
    vec4 texColor = texture2D(u_Texture, v_TexCoord);
    gl_FragColor = vec4(texColor.rgb, texColor.a);
}
