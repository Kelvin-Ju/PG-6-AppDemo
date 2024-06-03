package com.example.opengles3final;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class OverlayRenderer {
    private Context context;
    private int programId;
    private int vertexBufferId;
    private int texCoordBufferId;
    private int textureId;

    public OverlayRenderer(Context context) {
        this.context = context;
    }

    public void initialize() {
        Log.d("OverlayRenderer", "Initializing OverlayRenderer");
        loadShaders();
        setupBuffers();
    }

    private void loadShaders() {
        String vertexShaderCode = loadShaderCodeFromResource(context, R.raw.overlay_vertex_shader);
        String fragmentShaderCode = loadShaderCodeFromResource(context, R.raw.overlay_fragment_shader);
        programId = createShaderProgram(vertexShaderCode, fragmentShaderCode);
        Log.d("OverlayRenderer", "Shaders loaded. Program ID: " + programId);
    }

    private String loadShaderCodeFromResource(Context context, int resourceId) {
        StringBuilder shaderCode = new StringBuilder();
        try (InputStream inputStream = context.getResources().openRawResource(resourceId);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                shaderCode.append(line).append("\n");
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not read shader code:", e);
        }
        return shaderCode.toString();
    }

    private int compileShader(int type, String shaderCode) {
        int shaderId = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shaderId, shaderCode);
        GLES20.glCompileShader(shaderId);

        final int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shaderId, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0) {
            Log.e("OverlayRenderer", "Error compiling shader: " + GLES20.glGetShaderInfoLog(shaderId));
            GLES20.glDeleteShader(shaderId);
            return 0;
        }
        Log.d("OverlayRenderer", "Shader compiled successfully. Shader ID: " + shaderId);
        return shaderId;
    }

    public void renderOverlay() {
        Log.d("OverlayRenderer", "Rendering overlay...");
        if (programId == 0) {
            Log.e("OpenGL", "Invalid program ID");
            return;
        }

        GLES20.glUseProgram(programId);

        // Bind vertex buffer
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId);
        int posAttrib = GLES20.glGetAttribLocation(programId, "position");
        GLES20.glEnableVertexAttribArray(posAttrib);
        GLES20.glVertexAttribPointer(posAttrib, 2, GLES20.GL_FLOAT, false, 0, 0);

        // Bind texture coordinate buffer
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, texCoordBufferId);
        int texAttrib = GLES20.glGetAttribLocation(programId, "texCoord");
        GLES20.glEnableVertexAttribArray(texAttrib);
        GLES20.glVertexAttribPointer(texAttrib, 2, GLES20.GL_FLOAT, false, 0, 0);

        // Bind the texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programId, "u_Texture"), 0);

        // Draw the quad
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        // Disable attribute arrays
        GLES20.glDisableVertexAttribArray(posAttrib);
        GLES20.glDisableVertexAttribArray(texAttrib);

        // Unbind the program
        GLES20.glUseProgram(0);
    }

    private int createShaderProgram(String vertexShaderCode, String fragmentShaderCode) {
        int vertexShaderId = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShaderId = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        int programId = GLES20.glCreateProgram();
        GLES20.glAttachShader(programId, vertexShaderId);
        GLES20.glAttachShader(programId, fragmentShaderId);
        GLES20.glLinkProgram(programId);

        final int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            Log.e("OverlayRenderer", "Error linking program: " + GLES20.glGetProgramInfoLog(programId));
            GLES20.glDeleteProgram(programId);
            return 0;
        }
        Log.d("OverlayRenderer", "Program linked successfully. Program ID: " + programId);
        return programId;
    }

    public void setupBuffers() {
        // Define the positions and texture coordinates
        float[] quadVertices = {
                -1.0f,  1.0f,  // top left
                -1.0f, -1.0f,  // bottom left
                1.0f,  1.0f,  // top right
                1.0f, -1.0f   // bottom right
        };
        float[] quadTexCoords = {
                0.0f, 1.0f,  // top left
                0.0f, 0.0f,  // bottom left
                1.0f, 1.0f,  // top right
                1.0f, 0.0f   // bottom right
        };
        vertexBufferId = createBuffer(quadVertices);
        texCoordBufferId = createBuffer(quadTexCoords);
        Log.d("OverlayRenderer", "Buffers setup successfully. Vertex Buffer ID: " + vertexBufferId + ", Texture Coord Buffer ID: " + texCoordBufferId);
    }

    private int createBuffer(float[] bufferData) {
        final int[] buffers = new int[1];
        ByteBuffer bb = ByteBuffer.allocateDirect(bufferData.length * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(bufferData);
        fb.position(0);

        GLES20.glGenBuffers(1, buffers, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, bufferData.length * 4, fb, GLES20.GL_STATIC_DRAW);

        return buffers[0];
    }
}
