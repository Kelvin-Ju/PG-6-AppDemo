package com.example.opengles3final;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.andresoviedo.android_3d_model_engine.camera.CameraController;
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
//import org.andresoviedo.android_3d_model_engine.view.ModelSurfaceView;
import org.andresoviedo.util.android.ContentUtils;
import org.andresoviedo.util.io.IOUtils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;

public class SecondFragment extends Fragment {

    private ModelSurfaceView glView;
    private SceneLoader scene;
    private TouchController touchController;
    private CameraController cameraController;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.i("SecondFragment", "Creating Scene...");
        Activity activity = getActivity();

        // Initialize the scene loader and camera setup
        scene = new SceneLoader(activity);
        Camera camera = new Camera(Constants.UNIT);
        camera.setProjection(Projection.ISOMETRIC);
        scene.setCamera(camera);

        // Initialize controllers
        touchController = new TouchController(activity);
        cameraController = new CameraController(camera);
        touchController.addListener(cameraController);

        // Define white color as background (RGBA)
        float[] whiteColor = {0.0f, 0.0f, 0.0f, 1.0f};
        glView = new ModelSurfaceView(activity, whiteColor, scene);
        //glView.setProjection(Projection.PERSPECTIVE);
        glView.addListener(cameraController);

        // Set touch listener to handle touch events on the GLSurfaceView
        glView.setOnTouchListener((v, event) -> touchController.onMotionEvent(event));
        checkAssets(); // Add this line to check assets right after initializing the scene.

        setupSceneEnvironment();

        // Load model into the scene
        loadModel();

        return glView;
    }

    private void checkAssets() {
        try {
            Activity activity = getActivity(); // Get the activity instance
            if (activity != null) {
                String[] assets = activity.getAssets().list("models");
                if (assets != null && assets.length > 0) {
                    Log.d("SecondFragment", "Available models: " + Arrays.toString(assets));
                    // If assets are listed, provide them to ContentUtils
                    ContentUtils.provideAssets(activity);
                } else {
                    Log.e("SecondFragment", "No assets found in the 'models' directory.");
                }
            } else {
                Log.e("SecondFragment", "Activity is null. Cannot list assets.");
            }
        } catch (IOException e) {
            Log.e("SecondFragment", "Failed to list assets", e);
        }
    }



    private void loadModel() {
        //URI modelUri = URI.create("android://com.example.opengles3final/assets/models/007_30D_F_0_hrn_mid_mesh.obj");
        //URI modelUri = URI.create("android://com.example.opengles3final/assets/models/penguin.obj");
        URI modelUri = URI.create("android://com.example.opengles3final/assets/models/006_30D_F_0_hrn_mid_mesh.obj");


        Log.d("SecondFragment", "Model URI: " + modelUri.toString()); // Check if the URI looks correct

        new WavefrontLoaderTask(getActivity(), modelUri, new LoadListener() {
            @Override
            public void onStart() {
                ContentUtils.setThreadActivity(getActivity());
                logDocumentsProvided(); // Log documents provided to ensure registration

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
                stopAnimationIfNeeded();

            }

            @Override
            public void onLoadComplete() {}
        }).execute();
    }

    private void stopAnimationIfNeeded() {
        if (scene.isDoAnimation()) {
            scene.toggleAnimation();  // Ensure animations are stopped
        }
    }

    private String readInputStream(InputStream inputStream) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line).append("\n");
        }
        return stringBuilder.toString();
    }

    private void setupSceneEnvironment() {
        // Ensure static lighting
        if (scene.isRotatingLight()) {
            scene.toggleLighting();  // This assumes one toggle switches from rotating to static
        }
        if (!scene.isDrawLighting()) {
            scene.toggleLighting();  // Ensures lighting is turned on if it was off
        }

    }


    private void loadMaterials(MeshData meshData) {
        // process materials
        if (meshData.getMaterialFile() == null) return;

        // log event
        Log.i("WavefrontLoader", "--------------------------------------------------");
        Log.i("WavefrontLoader", "Parsing materials... ");
        Log.i("WavefrontLoader", "--------------------------------------------------");

        Log.i("WavefrontLoader", "Material file content:");

        try {
            // get materials stream
            final InputStream inputStream = ContentUtils.getInputStream(meshData.getMaterialFile());

            // parse materials
            final WavefrontMaterialsParser materialsParser = new WavefrontMaterialsParser();
            final Materials materials = materialsParser.parse(meshData.getMaterialFile(), inputStream);

            // Access materials via reflection
            try {
                Field materialsField = Materials.class.getDeclaredField("materials");
                materialsField.setAccessible(true); // Make the field accessible
                Map<String, Material> materialsMap = (Map<String, Material>) materialsField.get(materials);

                // Iterate over materials and log each one
                for (Map.Entry<String, Material> entry : materialsMap.entrySet()) {
                    String materialId = entry.getKey();
                    Material material = entry.getValue();
                    // Log or process each material
                    Log.i("WavefrontLoader", "Material ID: " + materialId);
                    Log.i("WavefrontLoader", "Material: " + material.toString());
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace(); // Handle the exception appropriately
            }

            if (materials.size() > 0) {
                for (Element element : meshData.getElements()) {
                    final String elementMaterialId = element.getMaterialId();
                    Log.e("SecondFragment", "testing1" + element.getId() + ", Material ID: " + element.getMaterialId());

                    if (elementMaterialId != null && materials.contains(elementMaterialId)) {
                        final Material elementMaterial = materials.get(elementMaterialId);
                        Log.e("SecondFragment", "testing2");


                        element.setMaterial(elementMaterial);

                        // Load and bind texture file
                        if (elementMaterial.getTextureFile() != null) {
                            String texturePath = "models/" + elementMaterial.getTextureFile();
                            Log.e("SecondFragment", "testing3");

                            try {
                                Log.d("SecondFragment", "Loading texture: " + texturePath);
                                InputStream textureStream = getActivity().getAssets().open(texturePath);
                                Bitmap textureBitmap = BitmapFactory.decodeStream(textureStream);

                                // Check if textureBitmap is null
                                if (textureBitmap == null) {
                                    Log.e("SecondFragment", "Failed to decode texture bitmap for texture: " + texturePath);
                                    continue;  // Skip to the next element if textureBitmap is null
                                }

                                // Log successful bitmap decoding
                                Log.d("SecondFragment", "Texture bitmap decoded successfully for texture: " + texturePath);

                                // Convert Bitmap to byte array
                                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                                textureBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                                byte[] textureData = stream.toByteArray();

                                // Log the length of the texture data
                                Log.d("SecondFragment", "Texture data length: " + textureData.length);

                                // Set the texture data
                                elementMaterial.setTextureData(textureData);

                                // Log successful texture loading for the element
                                Log.d("SecondFragment", "Texture loaded successfully for element: " + element.getId());
                            } catch (IOException ex) {
                                Log.e("SecondFragment", "Error loading texture file: " + texturePath, ex);
                            }
                        }

                    } else {
                        Log.e("SecondFragment", "Error lolz");
                    }
                }
            }
        } catch (IOException ex) {
            Log.e("SecondFragment", "Error loading materials", ex);
        }
    }




    private void logDocumentsProvided() {
        try {
            Field field = ContentUtils.class.getDeclaredField("documentsProvided");
            field.setAccessible(true); // Make the field accessible if it is private
            Map<String, Uri> documents = (Map<String, Uri>) field.get(null); // Get the static field without an instance
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
