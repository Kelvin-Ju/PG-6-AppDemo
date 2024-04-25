package com.example.opengles3final;

import android.app.Activity;
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

public class ModelSurfaceView extends GLSurfaceView implements EventListener {
    private final ModelRenderer mRenderer;
    private final List<EventListener> listeners = new ArrayList<>();
    private float lastX, lastY;
    private static final float MAX_MOVEMENT = 50.0f; // Maximum pixels user can drag before stopping

    public ModelSurfaceView(Activity parent, float[] backgroundColor, SceneLoader scene){
        super(parent);
        try {
            Log.i("ModelSurfaceView", "Loading [OpenGL 2] ModelSurfaceView...");
            setEGLContextClientVersion(2);
            mRenderer = new ModelRenderer(parent, this, backgroundColor, scene);
            mRenderer.addListener(this);
            setRenderer(mRenderer);
        } catch (Exception e) {
            Log.e("ModelActivity", e.getMessage(), e);
            Toast.makeText(parent, "Error loading shaders:\n" + e.getMessage(), Toast.LENGTH_LONG).show();
            throw new RuntimeException(e);
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
                // Clamp the movement to the max allowed
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
