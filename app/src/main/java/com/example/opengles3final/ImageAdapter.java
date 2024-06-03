package com.example.opengles3final;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.viewpager2.widget.ViewPager2;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.drawable.Drawable;

import java.io.IOException;
import java.io.InputStream;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {
    private static final int NUM_IMAGES = 15; // From 004999_000 to 004999_014
    private LayoutInflater mInflater;
    private Context mContext;

    ImageAdapter(Context context) {
        this.mContext = context;
        this.mInflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.image_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String imageName = "nerf/004999_" + String.format("%03d", position) + ".png"; // Change to .png
        try {
            InputStream ims = mContext.getAssets().open(imageName);
            Drawable d = Drawable.createFromStream(ims, null);
            holder.imageView.setImageDrawable(d);
            ims.close(); // It's a good practice to close the InputStream
        } catch (IOException e) {
            Log.e("ImageAdapter", "Error opening image " + imageName, e);
        }
    }


    @Override
    public int getItemCount() {
        return NUM_IMAGES;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        ViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView);
        }
    }
}

