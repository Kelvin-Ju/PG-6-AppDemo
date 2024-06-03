package com.example.opengles3final;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.fragment.app.Fragment;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NerfFragment extends Fragment {

    private static final String TAG = "NerfFragment";

    private ImageView imageView;
    private List<ImageData> imagesData = new ArrayList<>();
    private Set<Integer> viewedIndices = new HashSet<>(); // Set to track viewed images
    private int currentIndex = 0;
    private int imageViewsCount = 0; // Counter for how many times images have been viewed
    private float downX, downY;
    private int touchSlop = 180;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_nerf, container, false);
        imageView = view.findViewById(R.id.imageView);

        try {
            loadImagesData();
            if (!imagesData.isEmpty()) {
                setImageFromAssetsFile(imagesData.get(currentIndex).filePath);
                viewedIndices.add(currentIndex); // Add the initial image to the viewed set
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error loading image data", e);
        }

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

    private void loadImagesData() throws IOException, JSONException {
        InputStream is = getActivity().getAssets().open("transforms_train.json");
        int size = is.available();
        byte[] buffer = new byte[size];
        is.read(buffer);
        is.close();
        String json = new String(buffer, "UTF-8");
        JSONObject root = new JSONObject(json);
        JSONArray jsonArray = root.getJSONArray("frames");
        Log.d(TAG, "Total images in JSON: " + jsonArray.length());

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            double[] rotationDegree = {
                    jsonObject.getJSONArray("rotation_degree").getDouble(0),
                    jsonObject.getJSONArray("rotation_degree").getDouble(1)
            };
            String filePath = "nerf1/" + jsonObject.getString("file_path").replace("train/", "");
            imagesData.add(new ImageData(filePath, rotationDegree));
        }
        Log.d(TAG, "Total images loaded: " + imagesData.size());
    }

    private void setImageFromAssetsFile(String filePath) {
        Log.d(TAG, "Loading image from: " + filePath);
        try (InputStream is = getActivity().getAssets().open(filePath)) {
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                imageViewsCount++;
                Log.d(TAG, "Image loaded successfully. Total views: " + imageViewsCount);
                Log.d(TAG, "Current image path: " + filePath);
            } else {
                Log.d(TAG, "BitmapFactory returned a null bitmap.");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error loading image from " + filePath, e);
        }
    }

    private void moveInDirection(String direction) {
        Log.d(TAG, "Moving in direction: " + direction);
        double bestFit = Double.MAX_VALUE;
        int bestIndex = -1;

        for (int i = 0; i < imagesData.size(); i++) {
            if (viewedIndices.contains(i)) continue; // Skip already viewed images
            double score = calculateFitScore(imagesData.get(i).rotationDegree, direction);
            if (score < bestFit) {
                bestFit = score;
                bestIndex = i;
            }
        }

        if (bestIndex != -1) {
            currentIndex = bestIndex;
            setImageFromAssetsFile(imagesData.get(currentIndex).filePath);
            viewedIndices.add(currentIndex); // Add to the viewed set
            Log.d(TAG, "Moved to new image at index: " + currentIndex);
        } else {
            Log.d(TAG, "No suitable new image found or same image selected.");
        }
    }

    private double calculateFitScore(double[] rotationDegree, String direction) {
        double score = Double.MAX_VALUE;
        switch (direction) {
            case "left":
                if (rotationDegree[1] < 0) score = -rotationDegree[1];
                break;
            case "right":
                if (rotationDegree[1] > 0) score = rotationDegree[1];
                break;
            case "up":
                if (rotationDegree[0] < 0) score = -rotationDegree[0];
                break;
            case "down":
                if (rotationDegree[0] > 0) score = rotationDegree[0];
                break;
        }
        return score;
    }

    static class ImageData {
        String filePath;
        double[] rotationDegree;

        ImageData(String filePath, double[] rotationDegree) {
            this.filePath = filePath;
            this.rotationDegree = rotationDegree;
        }
    }
}
