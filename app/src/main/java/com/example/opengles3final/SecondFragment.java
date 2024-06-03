package com.example.opengles3final;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.andresoviedo.android_3d_model_engine.controller.TouchController;
import org.andresoviedo.android_3d_model_engine.model.Camera;
import org.andresoviedo.android_3d_model_engine.model.Constants;
import org.andresoviedo.android_3d_model_engine.model.Element;
import org.andresoviedo.android_3d_model_engine.model.Material;
import org.andresoviedo.android_3d_model_engine.model.Materials;
import org.andresoviedo.android_3d_model_engine.model.Object3DData;
import org.andresoviedo.android_3d_model_engine.model.Projection;
import org.andresoviedo.android_3d_model_engine.services.LoadListener;
import org.andresoviedo.android_3d_model_engine.services.SceneLoader;
import org.andresoviedo.android_3d_model_engine.services.collada.entities.MeshData;
import org.andresoviedo.android_3d_model_engine.services.wavefront.WavefrontLoaderTask;
import org.andresoviedo.util.android.ContentUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.Map;

public class SecondFragment extends Fragment {

    private static final String ARG_MODEL_URI = "model_uri";

    private ModelSurfaceView glView;
    private SceneLoader scene;
    private TouchController touchController;
    private CameraController cameraController;

    public static SecondFragment newInstance(String modelUri) {
        SecondFragment fragment = new SecondFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MODEL_URI, modelUri);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.i("SecondFragment", "Creating Scene...");
        Activity activity = getActivity();

        // Initialize the scene loader and camera setup
        scene = new SceneLoader(activity);
        Camera camera = new Camera(30);
        camera.setProjection(Projection.ISOMETRIC);
        scene.setCamera(camera);

        // Initialize controllers
        touchController = new TouchController(activity);
        cameraController = new CameraController(camera);
        touchController.addListener(cameraController);

        // Define white color as background (RGBA)
        float[] whiteColor = {0.0f, 0.0f, 0.0f, 1.0f};
        glView = new ModelSurfaceView(activity, whiteColor, scene);
        glView.addListener(cameraController);

        // Set touch listener to handle touch events on the GLSurfaceView
        glView.setOnTouchListener((v, event) -> touchController.onMotionEvent(event));

        setupSceneEnvironment();

        // Ensure assets are provided before loading the model
        ContentUtils.provideAssets(activity);

        // Load model into the scene
        if (getArguments() != null) {
            String modelUri = getArguments().getString(ARG_MODEL_URI);
            loadModel(modelUri);
        }

        return glView;
    }

    private void loadModel(String modelUri) {
        URI uri = URI.create(modelUri);
        Log.d("SecondFragment", "Model URI: " + uri.toString());

        new WavefrontLoaderTask(getActivity(), uri, new LoadListener() {
            @Override
            public void onStart() {
                ContentUtils.setThreadActivity(getActivity());
                logDocumentsProvided();
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
                data.setColor(Constants.COLOR_WHITE);
                data.setLocation(new float[]{0, -0.25f, 0});
                scene.addObject(data);
                loadMaterials(data.getMeshData());
            }

            @Override
            public void onLoadComplete() {}
        }).execute();
    }

    private void setupSceneEnvironment() {
        if (scene.isRotatingLight()) {
            scene.toggleLighting();
            scene.toggleLighting();
        }
    }

    private void loadMaterials(MeshData meshData) {
        // process materials
        if (meshData.getMaterialFile() == null) return;

        Log.i("WavefrontLoader", "--------------------------------------------------");
        Log.i("WavefrontLoader", "Parsing materials... ");
        Log.i("WavefrontLoader", "--------------------------------------------------");

        try {
            final InputStream inputStream = ContentUtils.getInputStream(meshData.getMaterialFile());
            Log.d("SecondFragment", "Material file path: " + meshData.getMaterialFile());
            final WavefrontMaterialsParser materialsParser = new WavefrontMaterialsParser();
            final Materials materials = materialsParser.parse(meshData.getMaterialFile(), inputStream);

            try {
                Field materialsField = Materials.class.getDeclaredField("materials");
                materialsField.setAccessible(true);
                Map<String, Material> materialsMap = (Map<String, Material>) materialsField.get(materials);

                for (Map.Entry<String, Material> entry : materialsMap.entrySet()) {
                    String materialId = entry.getKey();
                    Material material = entry.getValue();
                    Log.i("WavefrontLoader", "Material ID: " + materialId);
                    Log.i("WavefrontLoader", "Material: " + material.toString());
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }

            if (materials.size() > 0) {
                for (Element element : meshData.getElements()) {
                    final String elementMaterialId = element.getMaterialId();
                    Log.i("SecondFragment", "Element Material ID: " + element.getMaterialId());

                    if (elementMaterialId != null && materials.contains(elementMaterialId)) {
                        final Material elementMaterial = materials.get(elementMaterialId);
                        element.setMaterial(elementMaterial);

                        if (elementMaterial.getTextureFile() != null) {
                            String texturePath = "models/" + elementMaterial.getTextureFile();
                            loadTexture(elementMaterial, texturePath);

                            // Additional texture linking for specific material file
                            if (meshData.getMaterialFile().toString().endsWith("0003_0_hrn_mid_mesh.mtl")) {
                                String additionalTexturePath = texturePath.replace(".jpg", "F.jpg");
                                loadTexture(elementMaterial, additionalTexturePath);
                            }
                        }
                    }
                }
            }
        } catch (IOException ex) {
            Log.e("SecondFragment", "Error loading materials", ex);
        }
    }

    private void loadTexture(Material material, String texturePath) {
        try {
            Log.d("SecondFragment", "Loading texture: " + texturePath);
            InputStream textureStream = getActivity().getAssets().open(texturePath);
            Bitmap textureBitmap = BitmapFactory.decodeStream(textureStream);

            if (textureBitmap == null) {
                Log.e("SecondFragment", "Failed to decode texture bitmap for texture: " + texturePath);
                return;
            }

            Log.d("SecondFragment", "Texture bitmap decoded successfully for texture: " + texturePath);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            textureBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byte[] textureData = stream.toByteArray();
            Log.d("SecondFragment", "Texture data length: " + textureData.length);
            material.setTextureData(textureData);
            Log.d("SecondFragment", "Texture loaded successfully for material.");
        } catch (IOException ex) {
            Log.e("SecondFragment", "Error loading texture file: " + texturePath, ex);
        }
    }

    private void logDocumentsProvided() {
        try {
            Field field = ContentUtils.class.getDeclaredField("documentsProvided");
            field.setAccessible(true);
            Map<String, Uri> documents = (Map<String, Uri>) field.get(null);
            if (documents != null) {
                Log.d("SecondFragment", "Registered documents: " + documents.keySet());
            } else {
                Log.d("SecondFragment", "No documents are registered.");
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e("SecondFragment", "Failed to access documentsProvided due to: ", e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (glView != null) {
            glView.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (glView != null) {
            glView.onPause();
        }
    }
}