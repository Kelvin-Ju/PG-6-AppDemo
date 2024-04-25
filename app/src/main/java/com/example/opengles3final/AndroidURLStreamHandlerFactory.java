package com.example.opengles3final;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import org.andresoviedo.util.android.assets.Handler; // Ensure correct package name

public class AndroidURLStreamHandlerFactory implements URLStreamHandlerFactory {

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if ("android".equals(protocol)) {
            return new Handler();  // Use Handler directly if there are no naming conflicts
        } else if ("content".equals(protocol)){
            return new org.andresoviedo.util.android.content.Handler();
        }
        return null;
    }
}
