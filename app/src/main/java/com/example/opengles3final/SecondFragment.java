package com.example.opengles3final;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
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
//import org.andresoviedo.util.android.ContentUtils;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SecondFragment extends Fragment {

    private static final String ARG_SUBJECT_ID = "subject_id";
    private static final String SERVER_HOST = ServerConfig.SERVER_HOST;
    private static final int SERVER_PORT = ServerConfig.SERVER_PORT;
    private static final String SERVER_USERNAME = ServerConfig.SERVER_USERNAME;
    private static final String SERVER_PASSWORD = ServerConfig.SERVER_PASSWORD;

    private ModelSurfaceView glView;
    private SceneLoader scene;
    private TouchController touchController;
    private CameraController cameraController;
    private String subjectId;

    public static SecondFragment newInstance(String subjectId) {
        SecondFragment fragment = new SecondFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SUBJECT_ID, subjectId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            subjectId = getArguments().getString(ARG_SUBJECT_ID);
        }
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
        new FetchModelFilesTask(activity).execute(subjectId);

        return glView;
    }

    private class FetchModelFilesTask extends AsyncTask<String, Void, List<String>> {

        private WeakReference<Activity> weakActivity;

        FetchModelFilesTask(Activity activity) {
            this.weakActivity = new WeakReference<>(activity);
        }

        @Override
        protected List<String> doInBackground(String... params) {
            String subjectId = params[0];
            List<String> filePaths = new ArrayList<>();
            JSch jsch = new JSch();
            Session session = null;
            ChannelSftp channelSftp = null;

            try {
                session = jsch.getSession(SERVER_USERNAME, SERVER_HOST, SERVER_PORT);
                session.setPassword(SERVER_PASSWORD);
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect();

                channelSftp = (ChannelSftp) session.openChannel("sftp");
                channelSftp.connect();

                String remotePath = ServerConfig.SERVER_USER_DIRECTORY + "/" + subjectId + "/Output/3DMM";
                channelSftp.cd(remotePath);

                List<ChannelSftp.LsEntry> files = channelSftp.ls(".");
                for (ChannelSftp.LsEntry entry : files) {
                    if (!entry.getAttrs().isDir()) {
                        Activity activity = weakActivity.get();
                        if (activity != null) {
                            String remoteFilePath = remotePath + "/" + entry.getFilename();
                            String localFilePath = activity.getExternalFilesDir(null) + "/" + entry.getFilename();
                            channelSftp.get(remoteFilePath, localFilePath);
                            filePaths.add(localFilePath);

                            // Check if it's an obj file and process it
                            if (localFilePath.endsWith(".obj")) {
                                processObjFile(localFilePath);
                            }

                            // Check if it's an mtl file and process it
                            if (localFilePath.endsWith(".mtl")) {
                                processMtlFile(localFilePath);
                            }
                        } else {
                            Log.e("FetchModelFilesTask", "Activity is null, stopping task");
                            return null;
                        }
                    }
                }
            } catch (JSchException | SftpException e) {
                e.printStackTrace();
            } finally {
                if (channelSftp != null && channelSftp.isConnected()) {
                    channelSftp.disconnect();
                }
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
            }

            return filePaths;
        }

        @Override
        protected void onPostExecute(List<String> filePaths) {
            if (filePaths != null && !filePaths.isEmpty()) {
                for (String filePath : filePaths) {
                    if (filePath.endsWith(".obj")) {
                        loadModel(filePath);
                    } else if (filePath.endsWith(".mtl")) {
                        // load materials if needed
                    } else if (filePath.endsWith(".jpg")) {
                        // load textures if needed
                    }
                }
            }
        }

        // Method to process and modify the .obj file
        private void processObjFile(String filePath) {
            try {
                File inputFile = new File(filePath);
                File tempFile = new File(filePath + "_processed.obj");

                BufferedReader reader = new BufferedReader(new FileReader(inputFile));
                BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));

                String line;
                boolean addedUseMtl = false;

                // Read the file line by line
                while ((line = reader.readLine()) != null) {
                    // Step 1: Modify the 'mtllib' line by removing './'
                    if (line.startsWith("mtllib")) {
                        line = line.replace("./", "");
                    }

                    // Step 2: Add 'usemtl material_0' right before the first 'f' (face) line
                    if (!addedUseMtl && line.startsWith("f ")) {
                        writer.write("usemtl material_0\n");
                        addedUseMtl = true;
                    }

                    // Write the current line (modified or not) to the temp file
                    writer.write(line + "\n");
                }

                writer.close();
                reader.close();

                // Replace the original file with the modified file
                if (inputFile.delete()) {
                    tempFile.renameTo(inputFile);
                } else {
                    Log.e("FetchModelFilesTask", "Error: Could not replace the original .obj file.");
                }

            } catch (IOException e) {
                Log.e("FetchModelFilesTask", "Error processing obj file: " + filePath, e);
            }
        }

        // Method to process and modify the .mtl file
        // Method to process and modify the .mtl file
        private void processMtlFile(String filePath) {
            try {
                File inputFile = new File(filePath);
                File tempFile = new File(filePath + "_processed.mtl");

                // Log the file path for debugging purposes
                Log.d("FetchModelFilesTask", "Processing .mtl file at path: " + filePath);

                BufferedReader reader = new BufferedReader(new FileReader(inputFile));
                BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));

                String line;
                boolean kaLineModified = false; // To track if the Ka line was modified

                // Read the file line by line
                while ((line = reader.readLine()) != null) {
                    // Step 1: Modify the 'Ka' line if it exists
                    if (line.startsWith("Ka")) {
                        line = "Ka 0.300000 0.300000 0.300000";
                        kaLineModified = true;
                        Log.d("FetchModelFilesTask", "Modified Ka line: " + line); // Log the modified Ka line
                    }

                    // Write the current line (modified or not) to the temp file
                    writer.write(line + "\n");
                }

                // If Ka line was not found, log it for further debugging
                if (!kaLineModified) {
                    Log.w("FetchModelFilesTask", "Warning: 'Ka' line not found in .mtl file: " + filePath);
                }

                writer.close();
                reader.close();

                // Replace the original file with the modified file
                if (inputFile.delete()) {
                    if (!tempFile.renameTo(inputFile)) {
                        Log.e("FetchModelFilesTask", "Error: Could not rename the temporary .mtl file to original.");
                    }
                } else {
                    Log.e("FetchModelFilesTask", "Error: Could not delete the original .mtl file.");
                }

            } catch (IOException e) {
                Log.e("FetchModelFilesTask", "Error processing .mtl file: " + filePath, e);
            }
        }

    }




    private void loadModel(String modelFilePath) {
        File file = new File(modelFilePath);
        URI uri = file.toURI();
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
                loadMaterials(data.getMeshData(), subjectId);
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

    private void loadMaterials(MeshData meshData, String subjectId) {
        if (meshData.getMaterialFile() == null) return;

        Log.i("WavefrontLoader", "Parsing materials...");
        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp channelSftp = null;
        InputStream mtlInputStream = null;

        try {
            // Establish SFTP session
            session = jsch.getSession(SERVER_USERNAME, SERVER_HOST, SERVER_PORT);
            session.setPassword(SERVER_PASSWORD);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect();

            // Path on the server where the .mtl file is located
            String remoteMtlFilePath = ServerConfig.SERVER_USER_DIRECTORY + "/" + subjectId + "/Output/3DMM/" + meshData.getMaterialFile();
            Log.d("SecondFragment", "Fetching .mtl from server: " + remoteMtlFilePath);

            // Fetch the .mtl file from the server as an InputStream
            mtlInputStream = channelSftp.get(remoteMtlFilePath);

            // Parse the material file
            final WavefrontMaterialsParser materialsParser = new WavefrontMaterialsParser();
            final Materials materials = materialsParser.parse(meshData.getMaterialFile(), mtlInputStream);

            if (materials.size() > 0) {
                for (Element element : meshData.getElements()) {
                    final String elementMaterialId = element.getMaterialId();
                    if (elementMaterialId != null && materials.contains(elementMaterialId)) {
                        final Material elementMaterial = materials.get(elementMaterialId);
                        element.setMaterial(elementMaterial);

                        if (elementMaterial.getTextureFile() != null) {
                            // Fetch texture directly from the server
                            String textureFileName = elementMaterial.getTextureFile();

                            // Pass the material, subjectId, and textureFileName to loadTextureFromServer
                            loadTextureFromServer(elementMaterial, subjectId, textureFileName);
                        }
                    }
                }
            }

        } catch (JSchException | SftpException ex) {
            Log.e("SecondFragment", "Error loading .mtl file from server: " + meshData.getMaterialFile(), ex);
        } finally {
            try {
                if (mtlInputStream != null) mtlInputStream.close();
            } catch (IOException e) {
                Log.e("SecondFragment", "Error closing mtlInputStream", e);
            }

            if (channelSftp != null && channelSftp.isConnected()) {
                channelSftp.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }




    private void loadTextureFromServer(Material material, String subjectId, String textureFileName) {
        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp channelSftp = null;
        try {
            session = jsch.getSession(SERVER_USERNAME, SERVER_HOST, SERVER_PORT);
            session.setPassword(SERVER_PASSWORD);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect();

            // Path on the server where the texture is located
            String remoteFilePath = ServerConfig.SERVER_USER_DIRECTORY + "/" + subjectId + "/Output/3DMM/" + textureFileName;

            // Fetch the file from the server as an input stream
            InputStream textureStream = channelSftp.get(remoteFilePath);

            // Decode the input stream into a Bitmap
            Bitmap textureBitmap = BitmapFactory.decodeStream(textureStream);

            if (textureBitmap == null) {
                Log.e("SecondFragment", "Failed to decode texture bitmap from server: " + remoteFilePath);
                return;
            }

            // Optionally, compress the bitmap into byte array format
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            textureBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            byte[] textureData = stream.toByteArray();
            material.setTextureData(textureData);
            Log.d("SecondFragment", "Texture loaded successfully from server for material.");

            textureStream.close();
            channelSftp.disconnect();
            session.disconnect();

        } catch (JSchException | SftpException | IOException e) {
            Log.e("SecondFragment", "Error loading texture from server: " + textureFileName, e);
        } finally {
            if (channelSftp != null && channelSftp.isConnected()) {
                channelSftp.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }




    private void loadTexture(Material material, String texturePath) {
        try {
            Log.d("SecondFragment", "Loading texture from path: " + texturePath);

            // Use FileInputStream to read the texture from external storage
            InputStream textureStream = new FileInputStream(texturePath);
            Bitmap textureBitmap = BitmapFactory.decodeStream(textureStream);

            File textureFile = new File(texturePath);
            if (!textureFile.exists()) {
                Log.e("SecondFragment", "Texture file does not exist: " + texturePath);
                return;
            }


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