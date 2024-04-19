package com.example.opengles3final;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.core.app.ComponentActivity;
import androidx.fragment.app.Fragment;

import org.andresoviedo.android_3d_model_engine.camera.CameraController;
import org.andresoviedo.android_3d_model_engine.controller.TouchController;
import org.andresoviedo.android_3d_model_engine.model.Camera;
import org.andresoviedo.android_3d_model_engine.model.Constants;
import org.andresoviedo.android_3d_model_engine.model.Object3DData;
import org.andresoviedo.android_3d_model_engine.model.Projection;
import org.andresoviedo.android_3d_model_engine.services.LoadListener;
import org.andresoviedo.android_3d_model_engine.services.SceneLoader;
import org.andresoviedo.android_3d_model_engine.services.wavefront.WavefrontLoaderTask;
import org.andresoviedo.android_3d_model_engine.view.ModelSurfaceView;
import org.andresoviedo.util.android.ContentUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.Arrays;

public class SecondFragment extends Fragment {

    private ModelSurfaceView modelSurfaceView;
    private SceneLoader sceneLoader;
    private FrameLayout layout;

    private ModelSurfaceView glView;
    private SceneLoader scene;
    private CameraController cameraController;
    private TouchController touchController;
    private Camera camera;
    private WavefrontLoaderTask task;
    private WavefrontLoaderTask task2;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.i("SecondFragment", "Creating Scene...");
        Activity activity = getActivity();
        scene = new SceneLoader(activity);

        camera = new Camera(Constants.UNIT);
        camera.setProjection(Projection.ISOMETRIC);
        scene.setCamera(camera);

        cameraController = new CameraController(camera);
        touchController = new TouchController(activity);
        touchController.addListener(cameraController);

        glView = new ModelSurfaceView(activity, Constants.COLOR_GRAY, scene);
        glView.addListener(cameraController);
        glView.setProjection(Projection.PERSPECTIVE);
        scene.toggleWireframe();

        // Set touch listener to handle touch events on the GLSurfaceView
        glView.setOnTouchListener((v, event) -> touchController.onMotionEvent(event));

        loadModel();

        return glView;
    }

    private void loadModel() {
        URI modelUri = URI.create("android://com.example.opengles3final/assets/a1_0_hrn_mid_mesh.obj");
        new WavefrontLoaderTask (getActivity(), modelUri, new LoadListener() {
            @Override
            public void onStart() {
                ContentUtils.setThreadActivity(getActivity());
            }
            @Override
            public void onProgress(String progress) {}
            @Override
            public void onLoadError(Exception ex) {
                Log.e("SecondFragment", "Model load error: " + ex.getMessage(), ex);
            }
            @Override
            public void onLoad(Object3DData data) {
                Log.i("SecondFragment", "Adding object...");
                data.setScale(new float[]{20f, 20f, 20f});
                data.setColor(Constants.COLOR_RED);
                data.setLocation(new float[]{0, -0.25f, 0});
                scene.addObject(data);
            }
            @Override
            public void onLoadComplete() {}
        }).execute();
    }

    // No need for onTouchEvent here






    @Override
    public void onResume() {
        super.onResume();
        if (modelSurfaceView != null) {
            modelSurfaceView.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (modelSurfaceView != null) {
            modelSurfaceView.onPause();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Properly manage the lifecycle by removing the ModelSurfaceView from its parent
        if (modelSurfaceView != null && layout != null) {
            layout.removeView(modelSurfaceView);
        }
    }
}
