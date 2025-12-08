package com.floatingocrquiz;

import android.app.Application;
import android.media.projection.MediaProjection;

public class OCRApplication extends Application {

    private static final String TAG = "OCRApplication";
    private static OCRApplication instance;
    private MediaProjection mediaProjection;
    private boolean isFirstCapture = true;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static synchronized OCRApplication getInstance() {
        return instance;
    }

    public MediaProjection getMediaProjection() {
        return mediaProjection;
    }

    public void setMediaProjection(MediaProjection mediaProjection) {
        this.mediaProjection = mediaProjection;
    }

    public void releaseMediaProjection() {
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    public boolean isFirstCapture() {
        return isFirstCapture;
    }

    public void setFirstCapture(boolean firstCapture) {
        isFirstCapture = firstCapture;
    }
}
