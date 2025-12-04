package com.floatingocrquiz;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OCRHelper {

    private static final String TAG = "OCRHelper";
    private static final String API_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic";
    
    private OkHttpClient client;
    private String accessToken;
    
    public OCRHelper(Context context) {
        // 初始化OkHttpClient
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
        
        // 从配置中获取AccessToken
        // 实际使用时，应该从安全的地方获取，这里仅作为示例
        accessToken = "your_baidu_ocr_access_token";
    }
    
    /**
     * 设置百度OCR的AccessToken
     * @param token AccessToken
     */
    public void setAccessToken(String token) {
        this.accessToken = token;
    }
    
    /**
     * 识别图片中的文字
     * @param bitmap 图片
     * @return 识别结果
     */
    public String recognizeText(Bitmap bitmap) {
        try {
            // 将Bitmap转换为Base64编码
            String imageBase64 = bitmapToBase64(bitmap);
            
            // 构建请求参数
            String params = "image=" + Base64.encodeToString(imageBase64.getBytes(), Base64.URL_SAFE)
                    + "&language_type=CHN_ENG";
            
            // 构建请求
            Request request = new Request.Builder()
                    .url(API_URL + "?access_token=" + accessToken)
                    .post(RequestBody.create(params, MediaType.parse("application/x-www-form-urlencoded")))
                    .build();
            
            // 同步执行请求
            Response response = client.newCall(request).execute();
            
            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                return parseOcrResponse(responseBody);
            } else {
                Log.e(TAG, "OCR请求失败: " + response.code() + " " + response.message());
                return "";
            }
        } catch (Exception e) {
            Log.e(TAG, "OCR识别失败: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * 异步识别图片中的文字
     * @param bitmap 图片
     * @param callback 回调
     */
    public void recognizeTextAsync(Bitmap bitmap, final OcrCallback callback) {
        try {
            // 将Bitmap转换为Base64编码
            String imageBase64 = bitmapToBase64(bitmap);
            
            // 构建请求参数
            String params = "image=" + Base64.encodeToString(imageBase64.getBytes(), Base64.URL_SAFE)
                    + "&language_type=CHN_ENG";
            
            // 构建请求
            Request request = new Request.Builder()
                    .url(API_URL + "?access_token=" + accessToken)
                    .post(RequestBody.create(params, MediaType.parse("application/x-www-form-urlencoded")))
                    .build();
            
            // 异步执行请求
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "OCR请求失败: " + e.getMessage());
                    callback.onOcrComplete("");
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        String result = parseOcrResponse(responseBody);
                        callback.onOcrComplete(result);
                    } else {
                        Log.e(TAG, "OCR请求失败: " + response.code() + " " + response.message());
                        callback.onOcrComplete("");
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "OCR识别失败: " + e.getMessage());
            callback.onOcrComplete("");
        }
    }
    
    /**
     * 将Bitmap转换为Base64编码
     * @param bitmap 图片
     * @return Base64编码的字符串
     */
    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] bytes = baos.toByteArray();
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }
    
    /**
     * 解析OCR响应
     * @param response 响应字符串
     * @return 识别的文字
     */
    private String parseOcrResponse(String response) {
        try {
            JSONObject jsonObject = new JSONObject(response);
            JSONArray wordsResult = jsonObject.getJSONArray("words_result");
            
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < wordsResult.length(); i++) {
                JSONObject wordObject = wordsResult.getJSONObject(i);
                String word = wordObject.getString("words");
                sb.append(word).append("\n");
            }
            
            return sb.toString().trim();
        } catch (JSONException e) {
            Log.e(TAG, "解析OCR响应失败: " + e.getMessage());
            return "";
        }
    }
    
    /**
     * OCR回调接口
     */
    public interface OcrCallback {
        void onOcrComplete(String result);
    }
}