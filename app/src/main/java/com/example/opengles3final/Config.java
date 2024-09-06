package com.example.opengles3final;

import android.content.Context;
import java.io.InputStream;
import java.util.Properties;

public class Config {
    private Properties properties;

    public Config(Context context) {
        properties = new Properties();
        try {
            InputStream rawResource = context.getResources().openRawResource(R.raw.config);
            properties.load(rawResource);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }
}
