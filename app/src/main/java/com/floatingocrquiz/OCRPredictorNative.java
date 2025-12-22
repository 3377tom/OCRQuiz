package com.floatingocrquiz;

import android.graphics.Bitmap;
import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class OCRPredictorNative {

    private static final AtomicBoolean isSOLoaded = new AtomicBoolean();

    public static boolean loadLibrary() {
        if (!isSOLoaded.get() && isSOLoaded.compareAndSet(false, true)) {
            try {
                System.loadLibrary("Native");
                return true;
            } catch (Throwable e) {
                Log.e("OCRPredictorNative", "Load libNative.so failed, please check it exists in apk file: " + e.getMessage());
                return false;
            }
        }
        return true;
    }

    private Config config;

    private long nativePointer = 0;

    public OCRPredictorNative(Config config) {
        this.config = config;
        boolean libraryLoaded = loadLibrary();
        if (libraryLoaded) {
            nativePointer = init(config.detModelFilename, config.recModelFilename, config.clsModelFilename, config.useOpencl,
                    config.cpuThreadNum, config.cpuPower);
            Log.i("OCRPredictorNative", "load success " + nativePointer);
        } else {
            nativePointer = 0;
            Log.e("OCRPredictorNative", "Cannot initialize OCRPredictorNative because libNative.so is not loaded");
        }
    }


    public ArrayList<OcrResultModel> runImage(Bitmap originalImage, int max_size_len, int run_det, int run_cls, int run_rec) {
        Log.i("OCRPredictorNative", "begin to run image ");
        if (nativePointer == 0) {
            Log.e("OCRPredictorNative", "Cannot run image because native pointer is 0 (libNative.so may be missing)");
            return new ArrayList<>();
        }
        float[] rawResults = forward(nativePointer, originalImage, max_size_len, run_det, run_cls, run_rec);
        ArrayList<OcrResultModel> results = postprocess(rawResults);
        return results;
    }

    public static class Config {
        public int useOpencl;
        public int cpuThreadNum;
        public String cpuPower;
        public String detModelFilename;
        public String recModelFilename;
        public String clsModelFilename;

    }

    public void destroy() {
        if (nativePointer != 0) {
            release(nativePointer);
            nativePointer = 0;
        }
    }

    protected native long init(String detModelPath, String recModelPath, String clsModelPath, int useOpencl, int threadNum, String cpuMode);

    protected native float[] forward(long pointer, Bitmap originalImage,int max_size_len, int run_det, int run_cls, int run_rec);

    protected native void release(long pointer);

    private ArrayList<OcrResultModel> postprocess(float[] raw) {
        ArrayList<OcrResultModel> results = new ArrayList<OcrResultModel>();
        int begin = 0;

        while (begin < raw.length) {
            int point_num = Math.round(raw[begin]);
            int word_num = Math.round(raw[begin + 1]);
            OcrResultModel res = parse(raw, begin + 2, point_num, word_num);
            begin += 2 + 1 + point_num * 2 + word_num + 2;
            results.add(res);
        }

        return results;
    }

    private OcrResultModel parse(float[] raw, int begin, int pointNum, int wordNum) {
        int current = begin;
        OcrResultModel res = new OcrResultModel();
        res.setConfidence(raw[current]);
        current++;
        for (int i = 0; i < pointNum; i++) {
            res.addPoints(Math.round(raw[current + i * 2]), Math.round(raw[current + i * 2 + 1]));
        }
        current += (pointNum * 2);
        for (int i = 0; i < wordNum; i++) {
            int index = Math.round(raw[current + i]);
            res.addWordIndex(index);
        }
        current += wordNum;
        res.setClsIdx(raw[current]);
        res.setClsConfidence(raw[current + 1]);
        Log.i("OCRPredictorNative", "word finished " + wordNum);
        return res;
    }


}