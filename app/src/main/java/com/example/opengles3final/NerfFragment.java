package com.example.opengles3final;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.Semaphore;

public class NerfFragment extends Fragment {

    private static final String TAG = "NerfFragment";
    private static final String SERVER_HOST = ServerConfig.SERVER_HOST;
    private static final int SERVER_PORT = ServerConfig.SERVER_PORT;
    private static final String SERVER_USERNAME = ServerConfig.SERVER_USERNAME;
    private static final String SERVER_PASSWORD = ServerConfig.SERVER_PASSWORD;

    private ImageView imageView;
    private List<ImageData> imagesData = new ArrayList<>();
    private Map<String, Bitmap> imageCache = new HashMap<>();
    private Stack<Integer> backStackRight = new Stack<>();
    private Stack<Integer> backStackLeft = new Stack<>();
    private Stack<Integer> backStackUp = new Stack<>();
    private Stack<Integer> backStackDown = new Stack<>();
    private Set<Integer> viewedIndices = new HashSet<>();
    private int currentIndex = 0;
    private float downX, downY;
    private int touchSlop = 25;

    private double currentPitch = 0.0;
    private double currentYaw = 0.0;

    private static final double BASE_PITCH_TOLERANCE = 0.01;
    private static final double BASE_YAW_TOLERANCE = 0.01;
    private double pitchTolerance = BASE_PITCH_TOLERANCE;
    private double yawTolerance = BASE_YAW_TOLERANCE;

    private String subjectID;
    private static final int MAX_CONCURRENT_TASKS = 5; // Adjust as needed
    private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT_TASKS);
    private ProgressDialog progressDialog;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_nerf, container, false);
        imageView = view.findViewById(R.id.imageView);

        if (getArguments() != null) {
            subjectID = getArguments().getString("subject_id");
        }

        progressDialog = new ProgressDialog(getContext());
        progressDialog.setTitle("Downloading Images");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.setCancelable(false);
        progressDialog.show();

        new FetchDataFromServerTask().execute(subjectID);
        imageView.setOnTouchListener(new View.OnTouchListener() {
            private float lastX, lastY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                float x = event.getX();
                float y = event.getY();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = x;
                        downY = y;
                        lastX = x;
                        lastY = y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float deltaX = x - lastX;
                        float deltaY = y - lastY;
                        if (Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop) {
                            int steps = (int) (Math.max(Math.abs(deltaX), Math.abs(deltaY)) / touchSlop);
                            for (int i = 0; i < steps; i++) {
                                if (Math.abs(deltaX) > Math.abs(deltaY)) {
                                    if (deltaX > 0) {
                                        moveInDirection("right");
                                    } else {
                                        moveInDirection("left");
                                    }
                                } else {
                                    if (deltaY > 0) {
                                        moveInDirection("down");
                                    } else {
                                        moveInDirection("up");
                                    }
                                }
                            }
                            lastX = x;
                            lastY = y;
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                }
                return false;
            }
        });

        return view;
    }

    private class FetchDataFromServerTask extends AsyncTask<String, Integer, List<ImageData>> {
        @Override
        protected List<ImageData> doInBackground(String... params) {
            String subjectId = params[0];
            List<ImageData> imagesData = new ArrayList<>();
            JSch jsch = new JSch();
            Session session = null;
            ChannelSftp channelSftp = null;

            try {
                session = jsch.getSession(SERVER_USERNAME, SERVER_HOST, SERVER_PORT);
                session.setPassword(SERVER_PASSWORD);
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect();
                Log.d(TAG, "Connected to server");

                channelSftp = (ChannelSftp) session.openChannel("sftp");
                channelSftp.connect();
                Log.d(TAG, "SFTP channel connected");

                String remoteDirPath = ServerConfig.SERVER_USER_DIRECTORY + "/" + subjectId + "/Output/Tensor";
                channelSftp.cd(remoteDirPath);
                Log.d(TAG, "Changed directory to: " + remoteDirPath);

                String remoteFilePath = remoteDirPath + "/transform_matrix.json";
                InputStream inputStream = channelSftp.get(remoteFilePath);

                if (inputStream == null) {
                    Log.e(TAG, "InputStream is null, file not found: " + remoteFilePath);
                    return imagesData;
                }

                long fileSize = channelSftp.lstat(remoteFilePath).getSize();
                Log.d(TAG, "Size of file: " + fileSize);

                if (fileSize == 0) {
                    Log.e(TAG, "File is empty: " + remoteFilePath);
                    return imagesData;
                }

                StringBuilder stringBuilder = new StringBuilder();
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    stringBuilder.append(new String(buffer, 0, bytesRead));
                }
                inputStream.close();

                String json = stringBuilder.toString();

                JSONObject root = new JSONObject(json);
                JSONArray jsonArray = root.getJSONArray("frames");

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    JSONArray transformMatrix = jsonObject.getJSONArray("transform_matrix");
                    double yaw = transformMatrix.getJSONArray(0).getDouble(2);
                    double pitch = transformMatrix.getJSONArray(2).getDouble(0);
                    String filePath = jsonObject.getString("file_path").replace("images/", ""); // Remove the "images/" part
                    imagesData.add(new ImageData(filePath, yaw, pitch));
                }

            } catch (JSchException | SftpException | IOException | JSONException e) {
                Log.e(TAG, "Error during SFTP operation", e);
            } finally {
                if (channelSftp != null && channelSftp.isConnected()) {
                    channelSftp.disconnect();
                }
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
            }

            return imagesData;
        }

        @Override
        protected void onPostExecute(List<ImageData> imagesData) {
            if (imagesData != null && !imagesData.isEmpty()) {
                NerfFragment.this.imagesData = imagesData;
                new DownloadImagesTask().execute(imagesData.toArray(new ImageData[0]));
            } else {
                progressDialog.dismiss();
                showAlert("Not Available Yet");
                Log.e(TAG, "Failed to load data from server");
            }
        }

        private void showAlert(String message) {
            new AlertDialog.Builder(getContext())
                    .setTitle("Information")
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    private class DownloadImagesTask extends AsyncTask<ImageData, Integer, Void> {
        private int totalImages;
        private int downloadedImages = 0;

        @Override
        protected void onPreExecute() {
            totalImages = imagesData.size();
            progressDialog.setMax(totalImages);
        }

        @Override
        protected Void doInBackground(ImageData... imagesData) {
            File directory = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), subjectID);
            if (!directory.exists()) {
                directory.mkdirs();
            }

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

                for (int i = 0; i < imagesData.length; i++) {
                    String remoteFilePath = ServerConfig.SERVER_USER_DIRECTORY + "/" + subjectID + "/Output/Tensor/" + imagesData[i].filePath;
                    File localFile = new File(directory, imagesData[i].filePath);

                    if (!localFile.exists()) {
                        try (InputStream inputStream = channelSftp.get(remoteFilePath);
                             FileOutputStream outputStream = new FileOutputStream(localFile)) {
                            byte[] buffer = new byte[1024];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                            }
                        } catch (IOException | SftpException e) {
                            Log.e(TAG, "Error downloading image: " + imagesData[i].filePath, e);
                        }
                    } else {
                        Log.d(TAG, "Image already exists: " + imagesData[i].filePath);
                    }

                    downloadedImages++;
                    publishProgress(downloadedImages, totalImages); // Update progress with current and total count
                }

            } catch (JSchException e) {
                Log.e(TAG, "Error during SFTP operation", e);
            } finally {
                if (channelSftp != null && channelSftp.isConnected()) {
                    channelSftp.disconnect();
                }
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            int current = values[0];
            int total = values[1];
            progressDialog.setProgress(current);
            progressDialog.setMessage(current + " out of " + total + " images downloaded");
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            progressDialog.dismiss();
            loadImageFromLocal(imagesData.get(currentIndex).filePath);
            prefetchNextImages(currentIndex);
            viewedIndices.add(currentIndex);
            currentPitch = imagesData.get(currentIndex).pitch;
            currentYaw = imagesData.get(currentIndex).yaw;
        }
    }

    private synchronized void loadImageFromLocal(String filePath) {
        Bitmap cachedBitmap = imageCache.get(filePath);
        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap);
        } else {
            File directory = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), subjectID);
            File localFile = new File(directory, filePath);
            Bitmap bitmap = BitmapFactory.decodeFile(localFile.getAbsolutePath());
            if (bitmap != null) {
                imageCache.put(filePath, bitmap);
                imageView.setImageBitmap(bitmap);
            } else {
                Log.e(TAG, "Failed to load image from local storage: " + filePath);
            }
        }
    }

    private void prefetchNextImages(int currentIndex) {
        int nextIndex = currentIndex + 1;
        int prevIndex = currentIndex - 1;

        if (nextIndex < imagesData.size() && !imageCache.containsKey(imagesData.get(nextIndex).filePath)) {
            new PrefetchImageTask().execute(imagesData.get(nextIndex).filePath);
        }

        if (prevIndex >= 0 && !imageCache.containsKey(imagesData.get(prevIndex).filePath)) {
            new PrefetchImageTask().execute(imagesData.get(prevIndex).filePath);
        }
    }

    private class PrefetchImageTask extends AsyncTask<String, Void, Bitmap> {
        private String filePath;

        @Override
        protected void onPreExecute() {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Log.e(TAG, "Semaphore acquire interrupted", e);
            }
        }

        @Override
        protected Bitmap doInBackground(String... params) {
            filePath = params[0];
            File directory = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), subjectID);
            File localFile = new File(directory, filePath);
            Bitmap bitmap = BitmapFactory.decodeFile(localFile.getAbsolutePath());

            if (bitmap != null) {
                imageCache.put(filePath, bitmap);
            }

            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            semaphore.release();
        }
    }


    private synchronized void moveInDirection(String direction) {
        double bestFit = Double.MAX_VALUE;
        int bestIndex = -1;
        double incrementFactor = 1.1;

        Stack<Integer> directionStack = getDirectionStack(direction);
        if (directionStack != null && !directionStack.isEmpty()) {
            bestIndex = directionStack.pop();
        } else {
            pushCurrentIndexToOppositeStack(direction);

            while (bestIndex == -1) {
                for (int i = 0; i < imagesData.size(); i++) {
                    if (viewedIndices.contains(i)) continue;
                    double score = calculateFitScore(imagesData.get(i), direction);
                    if (score < bestFit) {
                        bestFit = score;
                        bestIndex = i;
                    }
                }

                if (bestIndex == -1) {
                    pitchTolerance *= incrementFactor;
                    yawTolerance *= incrementFactor;

                    if (pitchTolerance > 1.0 || yawTolerance > 1.0) {
                        bestIndex = currentIndex;
                        break;
                    }
                }
            }
        }

        if (bestIndex == -1) {
            return;
        }

        currentIndex = bestIndex;
        loadImageFromLocal(imagesData.get(currentIndex).filePath);
        Log.d(TAG, "Displaying image: " + imagesData.get(currentIndex).filePath);
        viewedIndices.add(currentIndex);
        currentPitch = imagesData.get(currentIndex).pitch;
        currentYaw = imagesData.get(currentIndex).yaw;
    }

    private Stack<Integer> getDirectionStack(String direction) {
        switch (direction) {
            case "left":
                return backStackLeft;
            case "right":
                return backStackRight;
            case "up":
                return backStackUp;
            case "down":
                return backStackDown;
            default:
                return null;
        }
    }

    private void pushCurrentIndexToOppositeStack(String direction) {
        switch (direction) {
            case "left":
                backStackRight.push(currentIndex);
                break;
            case "right":
                backStackLeft.push(currentIndex);
                break;
            case "up":
                backStackDown.push(currentIndex);
                break;
            case "down":
                backStackUp.push(currentIndex);
                break;
        }
    }

    private double calculateFitScore(ImageData imageData, String direction) {
        double score = Double.MAX_VALUE;
        double yaw = imageData.yaw;
        double pitch = imageData.pitch;

        // Calculate yaw and pitch in degrees for comparison
        double yawDegrees = Math.toDegrees(yaw);
        double pitchDegrees = Math.toDegrees(pitch);

        switch (direction) {
            case "left":
                if (yaw < currentYaw && Math.abs(yawDegrees) > Math.abs(pitchDegrees)) {
                    score = Math.abs(currentYaw - yaw);
                    if (Math.abs(pitchDegrees - currentPitch) > pitchTolerance) {
                        score += 1000;
                    }
                } else {
                    score = Double.MAX_VALUE;
                }
                break;
            case "right":
                if (yaw > currentYaw && Math.abs(yawDegrees) > Math.abs(pitchDegrees)) {
                    score = Math.abs(currentYaw - yaw);
                    if (Math.abs(pitchDegrees - currentPitch) > pitchTolerance) {
                        score += 1000;
                    }
                } else {
                    score = Double.MAX_VALUE;
                }
                break;
            case "up":
                if (pitch > currentPitch && Math.abs(pitchDegrees) > Math.abs(yawDegrees)) {
                    score = Math.abs(currentPitch - pitch);
                    if (Math.abs(yawDegrees - currentYaw) > yawTolerance) {
                        score += 1000;
                    }
                } else {
                    score = Double.MAX_VALUE;
                }
                break;
            case "down":
                if (pitch < currentPitch && Math.abs(pitchDegrees) > Math.abs(yawDegrees)) {
                    score = Math.abs(currentPitch - pitch);
                    if (Math.abs(yawDegrees - currentYaw) > yawTolerance) {
                        score += 1000;
                    }
                } else {
                    score = Double.MAX_VALUE;
                }
                break;
        }

        return score;
    }

}
