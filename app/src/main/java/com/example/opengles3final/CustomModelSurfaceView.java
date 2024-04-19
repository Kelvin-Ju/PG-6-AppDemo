package com.example.opengles3final;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.Toast;

import org.andresoviedo.android_3d_model_engine.services.SceneLoader;
import org.andresoviedo.android_3d_model_engine.view.ModelSurfaceView;

public class CustomModelSurfaceView extends ModelSurfaceView {

    public CustomModelSurfaceView(Context context, AttributeSet attrs) {
        super((Activity) context, new float[]{0.5f, 0.5f, 0.5f, 1.0f}, new SceneLoader((Activity) context));
        // Initialize with default settings
        init();
    }

    public CustomModelSurfaceView(Context context, AttributeSet attrs, int defStyle) {
        super((Activity) context, new float[]{0.5f, 0.5f, 0.5f, 1.0f}, new SceneLoader((Activity) context));
        // Initialize with default settings
        init();
    }

    private void init() {
        // Additional initialization steps can be performed here if necessary
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }
}
