package com.example.opengles3final;

import android.app.Activity;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Toast;

import org.andresoviedo.android_3d_model_engine.controller.TouchEvent;
import org.andresoviedo.android_3d_model_engine.model.Projection;
import org.andresoviedo.android_3d_model_engine.services.SceneLoader;
import org.andresoviedo.util.android.AndroidUtils;
import org.andresoviedo.util.event.EventListener;
import org.andresoviedo.util.math.Quaternion;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class ModelSurfaceView extends GLSurfaceView implements EventListener {
    private final ModelRenderer mRenderer;
    private final List<EventListener> listeners = new ArrayList<>();
    private OverlayRenderer overlayRenderer;
    private float lastX, lastY;
    private static final float MAX_MOVEMENT = 50.0f; // Maximum pixels user can drag before stopping

    public ModelSurfaceView(Activity parent, float[] backgroundColor, SceneLoader scene){
        super(parent);
        try {
            Log.i("ModelSurfaceView", "Loading [OpenGL 2] ModelSurfaceView...");
            setEGLContextClientVersion(2);
            mRenderer = new ModelRenderer(parent, this, backgroundColor, scene);
            mRenderer.addListener(this);
            setRenderer(new Renderer() {
                @Override
                public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                    Log.d("ModelSurfaceView", "onSurfaceCreated");
                    if (overlayRenderer != null) {
                        Log.d("ModelSurfaceView", "Initializing OverlayRenderer");
                        overlayRenderer.initialize();
                    }
                    mRenderer.onSurfaceCreated(gl, config);
                }

                @Override
                public void onSurfaceChanged(GL10 gl, int width, int height) {
                    Log.d("ModelSurfaceView", "onSurfaceChanged");
                    mRenderer.onSurfaceChanged(gl, width, height);
                }

                @Override
                public void onDrawFrame(GL10 gl) {
                    // Clear buffers
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
                    Log.d("ModelSurfaceView", "Buffers cleared");

                    // Enable depth test
                    GLES20.glEnable(GLES20.GL_DEPTH_TEST);
                    Log.d("ModelSurfaceView", "Depth test enabled");

                    // Render the 3D model
                    mRenderer.onDrawFrame(gl);
                    Log.d("ModelSurfaceView", "3D model rendered");

                    // Check for errors after model rendering
                    checkGLError("ModelSurfaceView - After 3D model rendering");

                    // Disable depth test for overlay rendering
                    GLES20.glDisable(GLES20.GL_DEPTH_TEST);
                    Log.d("ModelSurfaceView", "Depth test disabled for overlay");

                    // Render the overlay
                    if (overlayRenderer != null) {
                        overlayRenderer.renderOverlay();
                        Log.d("ModelSurfaceView", "Overlay rendered");
                    }

                    // Check for errors after overlay rendering
                    checkGLError("ModelSurfaceView - After overlay rendering");
                }
            });
            setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        } catch (Exception e) {
            Log.e("ModelActivity", e.getMessage(), e);
            Toast.makeText(parent, "Error loading shaders:\n" + e.getMessage(), Toast.LENGTH_LONG).show();
            throw new RuntimeException(e);
        }
    }

    public void setOverlayRenderer(OverlayRenderer overlayRenderer) {
        this.overlayRenderer = overlayRenderer;
        if (overlayRenderer != null) {
            Log.d("ModelSurfaceView", "OverlayRenderer set and initialized");
            queueEvent(() -> overlayRenderer.initialize());
        }
    }

    private void checkGLError(String tag) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(tag, "glError " + error);
        }
    }

    public void addListener(EventListener listener){
        listeners.add(listener);
    }

    public float[] getProjectionMatrix() {
        return mRenderer.getProjectionMatrix();
    }

    public float[] getViewMatrix() {
        return mRenderer.getViewMatrix();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastX = x;
                lastY = y;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = x - lastX;
                float dy = y - lastY;
                dx = Math.max(-MAX_MOVEMENT, Math.min(MAX_MOVEMENT, dx));
                dy = Math.max(-MAX_MOVEMENT, Math.min(MAX_MOVEMENT, dy));
                if (Math.abs(dx) > 0 || Math.abs(dy) > 0) {
                    mRenderer.handleTouchDrag(dx, dy);
                    lastX = x;
                    lastY = y;
                }
                break;
        }
        return true;
    }

    @Override
    public boolean onEvent(EventObject event) {
        if (event instanceof TouchEvent && ((TouchEvent) event).getAction() == TouchEvent.Action.PINCH) {
            mRenderer.addZoom(-mRenderer.getZoom() * ((TouchEvent) event).getZoom() / 100f);
        } else {
            AndroidUtils.fireEvent(listeners, event);
        }
        return true;
    }

    public void toggleProjection() {
        Log.i("ModelSurfaceView", "Toggling projection...");
        mRenderer.toggleProjection();
        Toast.makeText(getContext(), "Projection: " + mRenderer.getProjection(), Toast.LENGTH_SHORT).show();
    }

    public void setProjection(Projection projection) {
        mRenderer.setProjection(projection);
    }

    public Projection getProjection() {
        return mRenderer.getProjection();
    }

    public void toggleLights() {
        Log.i("ModelSurfaceView", "Toggling lights...");
        mRenderer.toggleLights();
    }

    public void toggleWireframe() {
        Log.i("ModelSurfaceView", "Toggling wireframe...");
        mRenderer.toggleWireframe();
    }

    public void toggleTextures() {
        Log.i("ModelSurfaceView", "Toggling textures...");
        mRenderer.toggleTextures();
    }

    public void toggleColors() {
        Log.i("ModelSurfaceView", "Toggling colors...");
        mRenderer.toggleColors();
    }

    public void toggleAnimation() {
        Log.i("ModelSurfaceView", "Toggling animation...");
        mRenderer.toggleAnimation();
    }

    public boolean isLightsEnabled() {
        return mRenderer.isLightsEnabled();
    }

    public void setOrientation(Quaternion orientation) {
        mRenderer.setOrientation(orientation);
    }
}
