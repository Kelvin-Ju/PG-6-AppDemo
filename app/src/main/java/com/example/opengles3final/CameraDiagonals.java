package com.example.opengles3final;

import static android.service.controls.ControlsProviderService.TAG;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix; // Add this import
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;


import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SizeF;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class CameraDiagonals extends Fragment{

    private TextureView cameraPreviewTextureView;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder previewRequestBuilder;
    private CameraCaptureSession previewSession;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    // Add for recording video
    private boolean isRecordingVideo = false;
    private MediaRecorder mediaRecorder;
    private File videoFile;
    private String currentCameraId;
    private boolean isFrontCamera = false; // Flag to track which camera is currently in use
    private Button recordButton; // Moved to class level
    private Size previewSize;
    private Timer timer;
    private ImageView faceFrameOverlay;

    private ImageView arrowImageView;

    private View horizontalLine, verticalLine;
    private Timer faceFrameTimer;

    private ImageReader imageReader;


    private float initialHorizontalY;
    private float initialVerticalX;
    private String subjectID; // Default ID in case none is provided
    private static final String SERVER_HOST = ServerConfig.SERVER_HOST;
    private static final int SERVER_PORT = ServerConfig.SERVER_PORT;
    private static final String SERVER_USERNAME = ServerConfig.SERVER_USERNAME;
    private static final String SERVER_PASSWORD = ServerConfig.SERVER_PASSWORD;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_cameradiagonals, container, false);
        cameraPreviewTextureView = view.findViewById(R.id.camera_preview);
        cameraPreviewTextureView.setSurfaceTextureListener(surfaceTextureListener);

        recordButton = view.findViewById(R.id.startRecordingButton);
        // Initially disable the record button
        recordButton.setEnabled(false);

        if (getArguments() != null) {
            subjectID = getArguments().getString("SUBJECT_ID", "defaultID");
        }

        Log.d(TAG, "Subject ID: " + subjectID);

        // Enable the record button after a 5-second delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (getActivity() == null) return; // Check if Fragment is attached to a context
            recordButton.setEnabled(true);
        }, 5000); // Delay in milliseconds

        recordButton.setOnClickListener(v -> {
            if (!recordButton.isEnabled()) {
                showToast("You can't perform this action yet.");
                return;
            }
            if (isRecordingVideo) {
                stopRecordingVideo();
            } else {
                startRecordingVideo();
                animateDiagonalLines();
            }
            toggleButton();
        });

        Button toggleFaceOutlineButton = view.findViewById(R.id.removeFaceOutlineButton);
        faceFrameOverlay = view.findViewById(R.id.face_frame_overlay);

        // Initially set to hide after 5 seconds
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            toggleFaceOutlineButton.setVisibility(View.VISIBLE);
        }, 5000);

        toggleFaceOutlineButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check the current visibility of the face outline
                if (faceFrameOverlay.getVisibility() == View.VISIBLE) {
                    faceFrameOverlay.setVisibility(View.GONE); // Hide the face outline
                    toggleFaceOutlineButton.setText(getString(R.string.show_face_outline)); // Change button text to "Show Face Outline"
                } else {
                    faceFrameOverlay.setVisibility(View.VISIBLE); // Show the face outline
                    toggleFaceOutlineButton.setText(getString(R.string.hide_face)); // Change button text to "Hide Face Outline"
                }
            }
        });

        Button switchCameraButton = view.findViewById(R.id.switchCameraButton);
        faceFrameOverlay = view.findViewById(R.id.face_frame_overlay);

        horizontalLine = view.findViewById(R.id.horizontal_line);
        verticalLine = view.findViewById(R.id.vertical_line);



        configureLinePositions();
        switchCameraButton.setOnClickListener(v -> switchCamera());

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if(getActivity() == null) return; // Check if Fragment is attached to a context
            horizontalLine.setVisibility(View.VISIBLE);
            verticalLine.setVisibility(View.VISIBLE);
        }, 5000); // Delay in milliseconds

        return view;
    }

    private final ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            // Get the next image from the reader
            try (Image image = reader.acquireNextImage()) {
                // Process the image or save it here
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                saveImageToServer(bytes, System.currentTimeMillis() + "");
            } catch (Exception e) {
                Log.e(TAG, "Error processing image", e);
            }
        }
    };

    private void saveImageToServer(byte[] bytes, String fileNameSuffix) {
        new Thread(() -> {
            String remoteDirPath = ServerConfig.SERVER_USER_DIRECTORY + "/" + subjectID + "/Output/Images/";
            String filePath = remoteDirPath + subjectID + "_" + fileNameSuffix + ".jpg";
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

                createDirectoryStructure(channelSftp, ServerConfig.SERVER_USER_DIRECTORY + "/" + subjectID);

                channelSftp.cd(remoteDirPath);
                InputStream inputStream = new ByteArrayInputStream(bytes);
                channelSftp.put(inputStream, filePath);

                inputStream.close();
                Log.d(TAG, "Image uploaded to server: " + filePath);
            } catch (JSchException | SftpException | IOException e) {
                Log.e(TAG, "Failed to upload image to server", e);
            } finally {
                if (channelSftp != null && channelSftp.isConnected()) {
                    channelSftp.disconnect();
                }
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
            }
        }).start();
    }

    private void saveVideoToServer(String localFilePath, String remoteFileName) {
        new Thread(() -> {
            JSch jsch = new JSch();
            Session session = null;
            ChannelSftp channelSftp = null;
            try {
                File videoFile = new File(localFilePath);
                if (!videoFile.exists()) {
                    Log.e(TAG, "Video file does not exist at path: " + localFilePath);
                    return;
                }

                session = jsch.getSession(SERVER_USERNAME, SERVER_HOST, SERVER_PORT);
                session.setPassword(SERVER_PASSWORD);
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect();
                Log.d(TAG, "Connected to server");

                channelSftp = (ChannelSftp) session.openChannel("sftp");
                channelSftp.connect();
                Log.d(TAG, "SFTP channel connected");

                String remoteDirPath = ServerConfig.SERVER_USER_DIRECTORY + "/" + subjectID + "/Input/Video/";

                // Create the directory structure if it does not exist
                createDirectoryStructure(channelSftp, ServerConfig.SERVER_USER_DIRECTORY + "/" + subjectID);

                String remoteFilePath = remoteDirPath + remoteFileName;
                Log.d(TAG, "Uploading video file to server path: " + remoteFilePath);

                channelSftp.put(localFilePath, remoteFilePath);
                Log.d(TAG, "Video uploaded to server: " + remoteFilePath);

            } catch (JSchException | SftpException e) {
                Log.e(TAG, "Failed to upload video to server", e);
            } finally {
                if (channelSftp != null && channelSftp.isConnected()) {
                    channelSftp.disconnect();
                    Log.d(TAG, "SFTP channel disconnected");
                }
                if (session != null && session.isConnected()) {
                    session.disconnect();
                    Log.d(TAG, "Session disconnected");
                }
            }
        }).start();
    }


    private void createDirectoryStructure(ChannelSftp channelSftp, String baseDir) throws SftpException {
        createDirectoryIfNotExists(channelSftp, baseDir);
        createDirectoryIfNotExists(channelSftp, baseDir + "/Output");
        createDirectoryIfNotExists(channelSftp, baseDir + "/Output/Tensor");
        createDirectoryIfNotExists(channelSftp, baseDir + "/Output/3DMM");
        createDirectoryIfNotExists(channelSftp, baseDir + "/Input");
        createDirectoryIfNotExists(channelSftp, baseDir + "/Input/Video");


    }

    private void createDirectoryIfNotExists(ChannelSftp channelSftp, String path) throws SftpException {
        try {
            SftpATTRS attrs = channelSftp.stat(path);
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                channelSftp.mkdir(path);
                Log.d(TAG, "Directory created: " + path);
            } else {
                throw e;
            }
        }
    }



    private void configureLinePositions() {
        initialHorizontalY = horizontalLine.getTranslationY() + 150; // Store initial Y position for horizontal line
        initialVerticalX = verticalLine.getTranslationX() + 150; // Store initial X position for vertical line
        horizontalLine.setTranslationY(initialHorizontalY);
        verticalLine.setTranslationX(initialVerticalX);
    }

    private void showToast(String message) {
        getActivity().runOnUiThread(() -> Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show());
    }


    private void startRecordingVideo() {
        if (!hasPermissionsGranted()) {
            showToast("Waiting for permissions...");
            return;
        }

        if (cameraDevice == null || !cameraPreviewTextureView.isAvailable() || previewSize == null) {
            showToast("Camera not ready for recording.");
            return;
        }
        try {
            closePreviewSession();
            setUpMediaRecorder();

            SurfaceTexture texture = cameraPreviewTextureView.getSurfaceTexture();
            assert texture != null;
            Surface previewSurface = new Surface(texture);
            Surface recordSurface = mediaRecorder.getSurface();

            imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.JPEG, 2);
            imageReader.setOnImageAvailableListener(imageAvailableListener, backgroundHandler);

            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            previewRequestBuilder.addTarget(previewSurface);
            previewRequestBuilder.addTarget(recordSurface);

            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(previewSurface);
            surfaces.add(recordSurface);
            surfaces.add(imageReader.getSurface());

            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (cameraDevice == null) return;
                    previewSession = session;
                    updatePreview();
                    getActivity().runOnUiThread(() -> {
                        isRecordingVideo = true;
                        mediaRecorder.start();
                        toggleButton();
                        showToast("Recording started");
                    });
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    showToast("Failed to configure camera for recording.");
                }
            }, backgroundHandler);
        } catch (Exception e) {
            showToast("Failed to start recording: " + e.getMessage());
            Log.e(TAG, "startRecordingVideo: ", e);
        }
    }

    public void stopRecordingVideo() {
        if (!isRecordingVideo) {
            return; // Early return if we're not recording
        }

        // UI thread is required for UI updates
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Stop recording and reset MediaRecorder for the next use
                try {
                    // Stop the recording
                    mediaRecorder.stop();
                } catch (RuntimeException stopException) {
                    // Handle the case where stop is called immediately after start causing a RuntimeException
                    // This may happen if there's not enough data for a video file.
                } finally {
                    mediaRecorder.reset(); // Reset the MediaRecorder for future use
                    mediaRecorder.release(); // Release the MediaRecorder
                }

                isRecordingVideo = false; // Update the recording state
                toggleButton(); // Update the button text
                saveVideoToServer(videoFile.getAbsolutePath(), subjectID + "_Diagonal.mp4"); // Save video to server
                startPreview(); // Assuming you have a method to restart the camera preview

                if (horizontalLine != null && verticalLine != null) {
                    horizontalLine.setVisibility(View.GONE);
                    verticalLine.setVisibility(View.GONE);
                }
            }
        });
    }


    private void setUpMediaRecorder() throws IOException {
        videoFile = new File(getActivity().getExternalFilesDir(null), subjectID + "_30D.mp4");

        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);

        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(videoFile.getAbsolutePath());
        mediaRecorder.setVideoEncodingBitRate(10000000);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoSize(1920, 1080);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int orientationHint = 90;
        if (isFrontCamera) {
            switch (rotation) {
                case Surface.ROTATION_0:
                    orientationHint = 270;
                    break;
                case Surface.ROTATION_90:
                    orientationHint = 180;
                    break;
                case Surface.ROTATION_180:
                    orientationHint = 90;
                    break;
                case Surface.ROTATION_270:
                    orientationHint = 0;
                    break;
            }
        } else {
            switch (rotation) {
                case Surface.ROTATION_0:
                    orientationHint = 90;
                    break;
                case Surface.ROTATION_90:
                    orientationHint = 0;
                    break;
                case Surface.ROTATION_180:
                    orientationHint = 270;
                    break;
                case Surface.ROTATION_270:
                    orientationHint = 180;
                    break;
            }
        }
        mediaRecorder.setOrientationHint(orientationHint);
        mediaRecorder.prepare();
    }

    private void animateDiagonalLines() {
        float distanceToMove = 170; // Example distance for the lines to move up and then back

        // Animate horizontal line
        horizontalLine.animate()
                .translationY(initialHorizontalY - distanceToMove)
                .setDuration(4000)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        horizontalLine.animate()
                                .translationY(initialHorizontalY)
                                .setDuration(4000)
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        horizontalLine.animate()
                                                .translationY(initialHorizontalY + distanceToMove)
                                                .setDuration(4000)
                                                .withEndAction(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        horizontalLine.animate()
                                                                .translationY(initialHorizontalY)
                                                                .setDuration(4000)
                                                                .withEndAction(() -> animateVerticalDiagonalLine())
                                                                .start();
                                                    }
                                                })
                                                .start();
                                    }
                                })
                                .start();
                    }
                })
                .start();
    }

    private void animateVerticalDiagonalLine() {
        float distanceToMove = 320; // Example distance for the lines to move up and then back
        Log.d(TAG, "animateVerticalDiagonalLine: Animation started.");

        verticalLine.animate()
                .translationY(initialVerticalX - distanceToMove)
                .setDuration(4000)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "animateVerticalDiagonalLine: Moved up.");

                        verticalLine.animate()
                                .translationY(initialVerticalX - 170)
                                .setDuration(4000)
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        Log.d(TAG, "animateVerticalDiagonalLine: Moved back to start.");

                                        verticalLine.animate()
                                                .translationY(initialVerticalX)
                                                .setDuration(4000)
                                                .withEndAction(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        verticalLine.animate()
                                                                .translationY(initialVerticalX - 170)
                                                                .setDuration(4000)
                                                                .withEndAction(() -> {
                                                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                                                        stopRecordingVideo();
                                                                    }, 1000);
                                                                })
                                                                .start();
                                                    }
                                                })
                                                .start();
                                    }
                                })
                                .start();
                    }
                })
                .start();
    }

    private void switchCamera() {
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        isFrontCamera = !isFrontCamera;
        if(isFrontCamera) {
            showLinesWithDelay();
        } else {
            hideLines();
        }
        openCamera();
    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            configureTransform(width, height);
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
        }
    };

    private void openCamera() {
        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null) {
                    if (isFrontCamera && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        currentCameraId = cameraId;
                        showFaceFrame();
                        showLinesWithDelay();
                        break;
                    } else if (!isFrontCamera && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        currentCameraId = cameraId;
                        hideLines();
                        break;
                    }
                }
            }
            if (currentCameraId == null) {
                return;
            }

            if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.CAMERA}, 200);
                return;
            }

            manager.openCamera(currentCameraId, stateCallback, backgroundHandler);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(currentCameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            previewSize = map.getOutputSizes(SurfaceTexture.class)[0];

            imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(readerListener, backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    Log.d(TAG, "Image available!");

                    image.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing image", e);
                if (image != null) {
                    image.close();
                }
            }
        }
    };

    private void showLinesWithDelay() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (getActivity() == null || !isFrontCamera) return;

            if (horizontalLine != null && verticalLine != null) {
                horizontalLine.setVisibility(View.VISIBLE);
                verticalLine.setVisibility(View.VISIBLE);
            }
        }, 5000);
    }

    private void hideLines() {
        if (horizontalLine != null && verticalLine != null) {
            horizontalLine.setVisibility(View.INVISIBLE);
            verticalLine.setVisibility(View.INVISIBLE);
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "Camera opened.");
            cameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
        }
    };

    private void startPreview() {
        if (cameraDevice == null || !cameraPreviewTextureView.isAvailable()) return;

        try {
            SurfaceTexture texture = cameraPreviewTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(cameraPreviewTextureView.getWidth(), cameraPreviewTextureView.getHeight());
            Surface surface = new Surface(texture);

            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (null == cameraDevice) return;
                    previewSession = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(getActivity(), "Failed", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if (null == cameraDevice) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(previewRequestBuilder);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            backgroundHandler = new Handler(thread.getLooper());

            previewSession.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    private boolean hasPermissionsGranted() {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, 201);
            return false;
        }
        return true;
    }

    private void toggleButton() {
        getActivity().runOnUiThread(() -> {
            if (isRecordingVideo) {
                recordButton.setText("Stop Recording");
            } else {
                recordButton.setText("Start Recording");
            }
        });
    }

    private void closePreviewSession() {
        if (previewSession != null) {
            previewSession.close();
            previewSession = null;
        }
    }

    private void showFaceFrame() {
        getActivity().runOnUiThread(() -> faceFrameOverlay.setVisibility(View.VISIBLE));

        faceFrameTimer = new Timer();
    }


    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == cameraPreviewTextureView || null == previewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / previewSize.getHeight(),
                    (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        } else {
            float scale = Math.max((float) viewWidth / previewSize.getWidth(), (float) viewHeight / previewSize.getHeight());
            matrix.postScale(scale, scale, centerX, centerY);
        }

        cameraPreviewTextureView.setTransform(matrix);
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Activity activity = getActivity();
        if (activity != null) {
            WindowManager.LayoutParams layoutParams = activity.getWindow().getAttributes();
            layoutParams.screenBrightness = 1.0f; // 1.0 for maximum brightness
            activity.getWindow().setAttributes(layoutParams);
        }
        startBackgroundThread();


        if (hasPermissionsGranted()) {
            initializeCameraInfo();
            if (cameraPreviewTextureView.isAvailable()) {
                openCamera();
            } else {
                cameraPreviewTextureView.setSurfaceTextureListener(surfaceTextureListener);
            }
        } else {
            // Request permissions or handle the lack thereof
        }
    }

    private void initializeCameraInfo() {
        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[0]; // Example: Using the primary camera
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

            // FOV calculation here based on characteristics
            float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            SizeF sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            float focalLength = focalLengths[0]; // Assuming the first available focal length
            float fovHorizontal = (float) (2 * Math.atan(sensorSize.getWidth() / (2 * focalLength)));
            float fovDegrees = (float) Math.toDegrees(fovHorizontal);
            Log.d(TAG, "Camera FOV (Horizontal, in degrees): " + fovDegrees); // Log the FOV value

            // DPI calculation
            DisplayMetrics metrics = new DisplayMetrics();
            getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
            int dpi = metrics.densityDpi;
            Log.d(TAG, "Device DPI: " + dpi); // Log the DPI value
            // Store FOV and DPI for later use...

            // Additional logic to use FOV and DPI as needed

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPause() {
        if (faceFrameTimer != null) {
            faceFrameTimer.cancel();
            faceFrameTimer = null;
        }


        if (isRecordingVideo) {
            stopRecordingVideo();
        }

        closeCamera();
        stopBackgroundThread();
        super.onPause();
        WindowManager.LayoutParams layoutParams = getActivity().getWindow().getAttributes();
        layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getActivity().getWindow().setAttributes(layoutParams);
    }

    private void closeCamera() {
        if (null != previewSession) {
            previewSession.close();
            previewSession = null;
        }
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }


}
