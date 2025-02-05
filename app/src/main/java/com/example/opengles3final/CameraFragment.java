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
import android.os.Build;
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
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
import java.util.Vector;

import android.hardware.camera2.CameraCaptureSession.CaptureCallback;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;


@RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
public class CameraFragment extends Fragment{

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



    private String subjectID; // Default ID in case none is provided



    private static final String SERVER_HOST = ServerConfig.SERVER_HOST;
    private static final int SERVER_PORT = ServerConfig.SERVER_PORT;
    private static final String SERVER_USERNAME = ServerConfig.SERVER_USERNAME;
    private static final String SERVER_PASSWORD = ServerConfig.SERVER_PASSWORD;
    private static final String INPUT_VIDEO_FOLDER = "Input/Video";
    private static final String INPUT_IMAGE_FOLDER = "Input/Images";




    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_camera, container, false);
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
            deleteRemoteSubjectDirectory(subjectID);

            if (isRecordingVideo) {
                stopRecordingVideo();
            } else {
                startRecordingVideo();
            }
            toggleButton();
        });

        Button toggleFaceOutlineButton = view.findViewById(R.id.removeFaceOutlineButton);
        faceFrameOverlay = view.findViewById(R.id.face_frame_overlay); // Make sure you have this line if you haven't defined faceFrameOverlay already

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
    private void deleteRemoteSubjectDirectory(String subjectID) {
        Thread thread = new Thread(() -> {
            JSch jsch = new JSch();
            Session session = null;
            ChannelSftp channelSftp = null;
            try {
                session = jsch.getSession(SERVER_USERNAME, SERVER_HOST, SERVER_PORT);
                session.setPassword(SERVER_PASSWORD);
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect();
                Log.d(TAG, "Session connected.");

                channelSftp = (ChannelSftp) session.openChannel("sftp");
                channelSftp.connect();
                Log.d(TAG, "SFTP channel opened and connected.");

                // Check if the subject directory exists
                String remoteSubjectDirectory = ServerConfig.SERVER_USER_DIRECTORY+ "/" + subjectID;
                if (directoryExists(channelSftp, remoteSubjectDirectory)) {
                    // Clear the contents of the Input and Output directories
                    clearRemoteDirectory(channelSftp, remoteSubjectDirectory + "/Input");
                    clearRemoteDirectory(channelSftp, remoteSubjectDirectory + "/Output");

                    // Clear the main subject directory
                    clearRemoteDirectory(channelSftp, remoteSubjectDirectory);

                    // Delete the main subject directory
                    channelSftp.rmdir(remoteSubjectDirectory);
                    Log.d(TAG, "Directory deleted: " + remoteSubjectDirectory);
                } else {
                    Log.d(TAG, "Directory does not exist: " + remoteSubjectDirectory);
                }

            } catch (JSchException | SftpException e) {
                Log.e(TAG, "Failed to delete directory: " + e.getMessage(), e);
            } finally {
                if (channelSftp != null && channelSftp.isConnected()) {
                    channelSftp.disconnect();
                    Log.d(TAG, "SFTP channel disconnected.");
                }
                if (session != null && session.isConnected()) {
                    session.disconnect();
                    Log.d(TAG, "Session disconnected.");
                }
            }
        });
        thread.start();
    }

    private boolean directoryExists(ChannelSftp channelSftp, String path) {
        try {
            SftpATTRS attrs = channelSftp.lstat(path);
            return attrs.isDir();
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return false;
            } else {
                Log.e(TAG, "Failed to check directory existence: " + e.getMessage(), e);
                return false;
            }
        }
    }

    private void clearRemoteDirectory(ChannelSftp channelSftp, String directoryPath) throws SftpException {
        try {
            channelSftp.cd(directoryPath);
            Vector<ChannelSftp.LsEntry> files = channelSftp.ls(".");
            for (ChannelSftp.LsEntry entry : files) {
                String fileName = entry.getFilename();
                if (!fileName.equals(".") && !fileName.equals("..")) {
                    String fullPath = directoryPath + "/" + fileName;
                    if (entry.getAttrs().isDir()) {
                        clearRemoteDirectory(channelSftp, fullPath);
                        channelSftp.rmdir(fullPath);
                    } else {
                        channelSftp.rm(fullPath);
                    }
                }
            }
        } catch (SftpException e) {
            if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                throw e;
            }
        }
    }






    private void uploadFileToServer(String localFilePath, String remoteFolderPath) {
        Thread thread = new Thread(() -> {
            JSch jsch = new JSch();
            Session session = null;
            ChannelSftp channelSftp = null;
            try {
                session = jsch.getSession(SERVER_USERNAME, SERVER_HOST, SERVER_PORT);
                session.setPassword(SERVER_PASSWORD);
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect();
                Log.d(TAG, "Session connected.");

                channelSftp = (ChannelSftp) session.openChannel("sftp");
                channelSftp.connect();
                Log.d(TAG, "SFTP channel opened and connected.");

                // Ensure directories are created
                createRemoteDirectoryIfNotExists(channelSftp, subjectID);
                createRemoteDirectoryIfNotExists(channelSftp, subjectID + "/Input");
                createRemoteDirectoryIfNotExists(channelSftp, subjectID + "/Input/Images");
                createRemoteDirectoryIfNotExists(channelSftp, subjectID + "/Input/Video");
                createRemoteDirectoryIfNotExists(channelSftp, subjectID + "/Output");
                createRemoteDirectoryIfNotExists(channelSftp, subjectID + "/Output/3DMM");
                createRemoteDirectoryIfNotExists(channelSftp, subjectID + "/Output/Tensor");

                String remoteFilePath = remoteFolderPath + "/" + new File(localFilePath).getName();
                channelSftp.put(localFilePath, remoteFilePath);

                Log.d(TAG, "File uploaded to server: " + remoteFilePath);

                // Log successful upload
                Log.i(TAG, "Successfully uploaded file: " + localFilePath + " to " + remoteFilePath);
            } catch (JSchException e) {
                Log.e(TAG, "Failed to upload file to server: " + e.getMessage(), e);
            } catch (SftpException e) {
                Log.e(TAG, "SFTP Exception during file upload: " + e.getMessage(), e);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error during file upload: " + e.getMessage(), e);
            } finally {
                if (channelSftp != null && channelSftp.isConnected()) {
                    channelSftp.disconnect();
                    Log.d(TAG, "SFTP channel disconnected.");
                }
                if (session != null && session.isConnected()) {
                    session.disconnect();
                    Log.d(TAG, "Session disconnected.");
                }
            }
        });
        thread.start();
    }







    private void createRemoteDirectoryIfNotExists(ChannelSftp channelSftp, String directoryPath) {
        try {
            channelSftp.cd(directoryPath);
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                try {
                    channelSftp.mkdir(directoryPath);
                    Log.d(TAG, "Created directory: " + directoryPath);
                } catch (SftpException se) {
                    Log.e(TAG, "Failed to create directory: " + directoryPath, se);
                }
            } else {
                Log.e(TAG, "Failed to change directory to: " + directoryPath, e);
            }
        }
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
                // Save the image with the suffix
                saveImage(bytes, "M"); // Use "M" as a default or initial suffix
            } catch (Exception e) {
                Log.e(TAG, "Error processing image", e);
            }
        }
    };

    private void saveImage(byte[] bytes, String suffix) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = subjectID + "_30D_" + suffix + ".jpg"; // Added time stamp for uniqueness

        // Decode the byte array to a Bitmap
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        // Rotate the bitmap to vertical (portrait) orientation
        bitmap = rotateBitmap(bitmap, 270); // 90 degrees for portrait

        // Save the rotated image
        File imageFile = new File(getActivity().getExternalFilesDir(null), imageFileName);
        try (FileOutputStream fos = new FileOutputStream(imageFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.getFD().sync();
            Log.d(TAG, "Image saved: " + imageFile.getAbsolutePath());
            uploadFileToServer(imageFile.getAbsolutePath(), INPUT_IMAGE_FOLDER);
        } catch (IOException e) {
            Log.e(TAG, "Failed to save image", e);
        }
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }







    // Inside your method where you configure the visibility and positions of your views
    private void configureLinePositions() {
        // Assuming horizontalLine is already initialized
        float newYPosition = horizontalLine.getTranslationY() + 150; // Adjust this value as needed
        horizontalLine.setTranslationY(newYPosition);

    }


    private void showToast(String message) {
        getActivity().runOnUiThread(() -> Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show());
    }




    private void resetAndShowCrosshairs() {
        // Reset positions to initial
        // Reset any transformations if applied
        horizontalLine.setRotation(0);
        verticalLine.setRotation(0);

        // Make lines visible
        horizontalLine.setVisibility(View.VISIBLE);
        verticalLine.setVisibility(View.VISIBLE);

        // Explicitly start the animation sequence
        animateHorizontalLine(); // Assuming this starts your desired animation sequence
    }


    private void startRecordingVideo() {
        if (!hasPermissionsGranted()) {
            showToast("Waiting for permissions...");
            return; // Exit method and wait for permissions callback
        }

        if (cameraDevice == null || !cameraPreviewTextureView.isAvailable() || previewSize == null) {
            showToast("Camera not ready for recording.");
            return;
        }
        try {
            closePreviewSession(); // Ensure any previous session is closed
            setUpMediaRecorder();

            SurfaceTexture texture = cameraPreviewTextureView.getSurfaceTexture();
            assert texture != null;
            Surface previewSurface = new Surface(texture);
            Surface recordSurface = mediaRecorder.getSurface();

            imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.JPEG, 3);
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
                        // Reset and show crosshairs for new recording
                        resetAndShowCrosshairs();
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






    private void saveBitmapToGallery(Bitmap bitmap, String fileName) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "YourAppFolder");

        Uri uri = getActivity().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        try (OutputStream out = getActivity().getContentResolver().openOutputStream(uri)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            Log.e(TAG, "Failed to save bitmap to gallery", e);
        }
    }

    private void capturePhoto(final String fileNameSuffix) {
        if (null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }

        if (!isRecordingVideo) {
            Log.d(TAG, "Recording has stopped, skipping screenshot.");
            return;
        }

        try {
            // Setup for a still image capture
            final CaptureRequest.Builder captureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());

            // Orientation
            int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            // Listener for the capture result
            CameraCaptureSession.CaptureCallback captureCallback =
                    new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                       @NonNull CaptureRequest request,
                                                       @NonNull TotalCaptureResult result) {
                            Log.d(TAG, "Photo captured: " + fileNameSuffix);
                            // Set the suffix to be used when saving the image
                            imageReader.setOnImageAvailableListener(
                                    reader -> {
                                        Image image = null;
                                        try {
                                            image = reader.acquireNextImage();
                                            if (image != null) {
                                                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                                                byte[] bytes = new byte[buffer.remaining()];
                                                buffer.get(bytes);
                                                saveImage(bytes, fileNameSuffix);
                                                image.close();
                                            }
                                        } catch (Exception e) {
                                            Log.e(TAG, "Error processing image", e);
                                            if (image != null) {
                                                image.close();
                                            }
                                        }
                                    },
                                    backgroundHandler);
                        }
                    };

            previewSession.capture(captureBuilder.build(), captureCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "capturePhoto exception", e);
        }
    }



    private int getOrientation(int rotation) {
        // Convert rotation in degrees (Surface.ROTATION_...) into degrees (0, 90, 270, 360)
        // This method depends on your device orientation and the camera sensor orientation
        return 0; // Placeholder: calculate the correct orientation
    }



    private void animateVerticalLine() {
        float translationXRight = 180; // Adjust this value based on where you want to move the line

        // Capture the middle photo first (ensure completion before moving)
        capturePhotoWithSync("M", () -> {
            // Move the vertical line to the right
            verticalLine.animate()
                    .translationX(translationXRight)
                    .setDuration(4000) // Duration in milliseconds
                    .withEndAction(() -> {
                        // Capture the photo at the rightmost position
                        capturePhotoWithSync("R", () -> {
                            // Move the vertical line back to the middle
                            verticalLine.animate()
                                    .translationX(0)
                                    .setDuration(4000)
                                    .withEndAction(() -> {
                                        // Recapture the middle photo to ensure correct labeling
                                        capturePhotoWithSync("M", () -> {
                                            // Move the vertical line to the leftmost position
                                            float translationXLeft = -180;
                                            verticalLine.animate()
                                                    .translationX(translationXLeft)
                                                    .setDuration(4000)
                                                    .withEndAction(() -> {
                                                        // Capture the leftmost position
                                                        capturePhotoWithSync("L", () -> {
                                                            // Move the vertical line back to the center and stop recording
                                                            verticalLine.animate()
                                                                    .translationX(0)
                                                                    .setDuration(4000)
                                                                    .withEndAction(() -> {
                                                                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                                                            stopRecordingVideo();
                                                                        }, 1000); // Delay of 1 second before stopping
                                                                    })
                                                                    .start();
                                                        });
                                                    })
                                                    .start();
                                        });
                                    })
                                    .start();
                        });
                    })
                    .start();
        });
    }

    // New helper method to ensure photo capture completes before continuing
    private void capturePhotoWithSync(String label, Runnable onCaptureComplete) {
        // Set the suffix before capturing
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireNextImage();
                if (image != null) {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    saveImage(bytes, label);  // Save with the proper label
                    image.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing image", e);
                if (image != null) {
                    image.close();
                }
            }
        }, backgroundHandler);

        // Capture the photo
        capturePhoto(label);

        // Delay the next step to ensure the capture is complete
        new Handler(Looper.getMainLooper()).postDelayed(onCaptureComplete, 1000); // 1 second delay
    }






    private void animateHorizontalLine() {
        // Initialize starting position based on configureLinePositions logic
        final float startPosition = horizontalLine.getTranslationY(); // Capture the starting position

        // Step 1: Calculate the end position for moving down by adding 250 units
        final float endPositionDown = startPosition - 150;

        // Step 2: Animate moving down
        horizontalLine.animate()
                .translationY(endPositionDown)
                .setDuration(4000) // Duration for moving down
                .withEndAction(() -> {
                    // Step 3: After moving down, animate moving back up to the start position
                    horizontalLine.animate()
                            .translationY(startPosition) // Move back up to the start position
                            .setDuration(4000) // Duration for moving up
                            .withEndAction(() -> {
                                // Step 4: Animate moving up from the start position
                                final float endPositionUp = startPosition + 150; // Calculate end position for moving up
                                horizontalLine.animate()
                                        .translationY(endPositionUp)
                                        .setDuration(4000) // Duration for moving up
                                        .withEndAction(() -> {
                                            // Step 5: After moving up, animate back to the configured line position
                                            // Ensure we animate back to the initial configuration position
                                            final float configuredPosition = startPosition; // Assuming configureLinePositions sets it back to this
                                            horizontalLine.animate()
                                                    .translationY(configuredPosition)
                                                    .setDuration(4000) // Animate back to configured position
                                                    .withEndAction(this::animateVerticalLine)
                                                    .start();
                                        })
                                        .start();
                            })
                            .start();
                })
                .start();
    }











    private void animateHorizontalLineUpAndBack() {
        final float startPositionY = horizontalLine.getTranslationY();
        final float moveDistance = 150;

        horizontalLine.animate()
                .translationY(startPositionY - moveDistance)
                .setDuration(4000)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        horizontalLine.animate()
                                .translationY(startPositionY)
                                .setDuration(4000)
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        // After moving up and back, start the down and back animation
                                        animateHorizontalLineDownAndBack();
                                    }
                                })
                                .start();
                    }
                })
                .start();
    }
    private void animateHorizontalLineDownAndBack() {
        // Capture the horizontal line's start position
        final float startPositionY = horizontalLine.getTranslationY();

        // Define the distance to move down
        final float moveDistance = 150; // Adjust this value as needed

        // Animate moving down
        horizontalLine.animate()
                .translationY(startPositionY + moveDistance) // Move down by moveDistance
                .setDuration(4000) // Duration for moving down
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        // After moving down, animate back to the original position
                        horizontalLine.animate()
                                .translationY(startPositionY) // Move back to the start position
                                .setDuration(4000) // Duration for moving back
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        // After moving up and back, start the down and back animation
                                        animateVerticalLineUpAndBack();
                                    }
                                })
                                .start();
                    }
                })
                .start();
    }

    private void animateVerticalLineUpAndBack() {
        final float startPositionY = verticalLine.getTranslationY();
        final float moveDistance = 150;

        verticalLine.animate()
                .translationY(startPositionY - moveDistance) // Move vertical line up by moveDistance
                .setDuration(4000)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        verticalLine.animate()
                                .translationY(startPositionY) // Move vertical line back to the start position
                                .setDuration(4000)
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        // After moving up and back, start the down and back animation for vertical line
                                        animateVerticalLineDownAndBack();
                                    }
                                })
                                .start();
                    }
                })
                .start();
    }

    private void animateVerticalLineDownAndBack() {
        final float startPositionY = verticalLine.getTranslationY();
        final float moveDistance = 150;

        verticalLine.animate()
                .translationY(startPositionY + moveDistance) // Move vertical line down by moveDistance
                .setDuration(4000)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        verticalLine.animate()
                                .translationY(startPositionY) // Move vertical line back to the start position
                                .setDuration(4000)
                                .start();
                    }
                })
                .start();
    }





    private Point getScreenSize() {
        // Check if the Fragment is currently attached to an activity
        if (getActivity() != null) {
            Display display = getActivity().getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            return size;
        } else {
            // Return a default size or handle this case as needed
            return new Point(0, 0); // You may want to handle this case differently
        }
    }

















// Call this method at the end of the animation where the horizontal line returns to the center














    private void switchCamera() {
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        isFrontCamera = !isFrontCamera; // Toggle the camera flag
        if(isFrontCamera) {
            showLinesWithDelay(); // Only schedule showing lines when switching to the front camera
        } else {
            hideLines(); // Immediately hide lines when switching to the back camera
        }
        openCamera();
    }



    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            configureTransform(width, height);
            //previewSize = new Size(1920, 1080); // Adjust as needed

            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            // Configure your camera preview size
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

// Inside your CameraFragment class

    // Method to show the face frame overlay
    private void showFaceFrame() {
        getActivity().runOnUiThread(() -> faceFrameOverlay.setVisibility(View.VISIBLE));

        // Initialize the Timer
        faceFrameTimer = new Timer();


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




    private void openCamera() {
        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null) {
                    if (isFrontCamera && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        currentCameraId = cameraId;
                        // Since it's front camera, ensure face frame and lines are managed here
                        showFaceFrame(); // Assuming you want to show this for the front camera
                        showLinesWithDelay(); // Show lines with delay for front camera
                        break;
                    } else if (!isFrontCamera && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        currentCameraId = cameraId;
                        hideLines(); // Ensure lines are hidden for back camera
                        break;
                    }
                }
            }
            if (currentCameraId == null) {
                // No suitable camera found, handle accordingly
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
                    // Process the image here (for example, convert it to Bitmap, run face detection)
                    Log.d(TAG, "Image available!");

                    // Remember to close the image once you're done with it
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
            if (getActivity() == null || !isFrontCamera) return; // Don't show lines if the fragment is detached or the current camera is not the front camera.

            if (horizontalLine != null && verticalLine != null) {
                horizontalLine.setVisibility(View.VISIBLE);
                verticalLine.setVisibility(View.VISIBLE);
            }
        }, 5000); // Delay in milliseconds
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

    private void setUpMediaRecorder() throws IOException {
        // Create a new video file in the external files directory
        videoFile = new File(getActivity().getExternalFilesDir(null), subjectID + "_30D.mp4");

        final Activity activity = getActivity();
        if (activity == null) {
            return; // If the Fragment is not attached to an Activity, exit the method
        }

        // Configure the MediaRecorder's source for video and audio
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);

        // Set the output format and encoding parameters. These settings are for 1080p video.
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(videoFile.getAbsolutePath());
        mediaRecorder.setVideoEncodingBitRate(10000000); // Bit rate
        mediaRecorder.setVideoFrameRate(30); // Frames per second
        // Note: You may need to adjust the video size according to your camera's capabilities
        mediaRecorder.setVideoSize(1920, 1080); // Resolution: 1080p
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264); // Video encoder
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); // Audio encoder

        // Configure the MediaRecorder's orientation and start preview
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                mediaRecorder.setOrientationHint(90);
                break;
            case Surface.ROTATION_90:
                mediaRecorder.setOrientationHint(0);
                break;
            case Surface.ROTATION_180:
                mediaRecorder.setOrientationHint(270);
                break;
            case Surface.ROTATION_270:
                mediaRecorder.setOrientationHint(180);
                break;
        }

        int orientationHint = 90;
        if (isFrontCamera) {
            // Adjust these hints based on testing with your specific device and front camera
            switch (rotation) {
                case Surface.ROTATION_0:
                    orientationHint = 270; // Adjust for front camera
                    break;
                case Surface.ROTATION_90:
                    orientationHint = 180; // Adjust for front camera
                    break;
                case Surface.ROTATION_180:
                    orientationHint = 90; // Adjust for front camera
                    break;
                case Surface.ROTATION_270:
                    orientationHint = 0; // Adjust for front camera
                    break;
            }
        } else {
            // Your existing switch case for the back camera
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


        // Prepare the MediaRecorder to begin capturing and encoding data
        mediaRecorder.prepare();
    }





    public void stopRecordingVideo() {
        if (!isRecordingVideo) {
            return; // Early return if we're not recording
        }

        getActivity().runOnUiThread(() -> {
            // Stop background thread before stopping the video
            stopBackgroundThread();

            try {
                mediaRecorder.stop();
            } catch (RuntimeException stopException) {
                // Handle any runtime exceptions from stopping the recording too soon
                Log.e(TAG, "Error stopping media recorder: " + stopException.getMessage());
            } finally {
                mediaRecorder.reset();
                mediaRecorder.release();
                mediaRecorder = null;  // Set to null for proper reinitialization
            }

            isRecordingVideo = false;
            toggleButton(); // Update the button state

            // Ensure the video is uploaded to the server
            uploadFileToServer(videoFile.getAbsolutePath(), INPUT_VIDEO_FOLDER); // Upload to the "Input/Video" folder

            // Close and reset the ImageReader after each recording session
            if (imageReader != null) {
                imageReader.close();
                imageReader = null; // Ensure it's set to null for reinitialization later
            }

            // Properly close the camera resources
            closeCamera();

            // Restart background thread for next session
            startBackgroundThread();

            // Restart preview and reset the ImageReader for the next recording
            startPreview();
            initializeImageReader(); // Ensure ImageReader is ready for the next recording

            // Hide lines
            if (horizontalLine != null && verticalLine != null) {
                horizontalLine.setVisibility(View.GONE);
                verticalLine.setVisibility(View.GONE);
            }
        });
    }

    // Ensure the ImageReader is properly initialized after stopping
    private void initializeImageReader() {
        imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.JPEG, 3);
        imageReader.setOnImageAvailableListener(imageAvailableListener, backgroundHandler);
    }

    // Background thread management
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
    public void onPause() {

        if (faceFrameTimer != null) {
            faceFrameTimer.cancel();
            faceFrameTimer = null; // Reset the Timer reference
        }


        if (isRecordingVideo) {
            stopRecordingVideo(); // Safely stop the recording if the user navigates away
        }

        closeCamera();
        stopBackgroundThread();
        super.onPause();
        WindowManager.LayoutParams layoutParams = getActivity().getWindow().getAttributes();
        layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getActivity().getWindow().setAttributes(layoutParams);
    }

    private void closeCamera() {
        if (previewSession != null) {
            previewSession.close();
            previewSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
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
            // This accounts for the case where the device rotation might be 0 (normal landscape) or 180 (reverse landscape)
            float scale = Math.max((float) viewWidth / previewSize.getWidth(), (float) viewHeight / previewSize.getHeight());
            matrix.postScale(scale, scale, centerX, centerY);
        }

        cameraPreviewTextureView.setTransform(matrix);
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


        // Ensure permissions are granted
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



}