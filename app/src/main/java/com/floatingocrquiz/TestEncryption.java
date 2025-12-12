package com.floatingocrquiz;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class TestEncryption {

    private static final String TAG = "TestEncryption";
    private static final String ORIGINAL_LICENSE_FILE = "original-license.license";
    private static final String ENCRYPTED_TEST_FILE = "test-encrypted.license";
    private static final String DECRYPTED_TEST_FILE = "test-decrypted.license";

    /**
     * 保存原始license文件到本地
     * @param context 上下文
     * @return 是否保存成功
     */
    public static boolean saveOriginalLicense(Context context) {
        try {
            // 从assets读取原始license文件
            AssetManager assetManager = context.getAssets();
            InputStream is = assetManager.open("aip-ocr.license");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int readBytes;
            while ((readBytes = is.read(buffer)) != -1) {
                baos.write(buffer, 0, readBytes);
            }
            byte[] originalData = baos.toByteArray();
            is.close();
            baos.close();

            // 保存到本地文件
            File originalFile = new File(context.getCacheDir(), ORIGINAL_LICENSE_FILE);
            FileOutputStream fos = new FileOutputStream(originalFile);
            fos.write(originalData);
            fos.close();

            Log.i(TAG, "原始license文件已保存到本地: " + originalFile.getAbsolutePath());
            Log.i(TAG, "原始license文件大小: " + originalData.length + "字节");
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "保存原始license文件失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 测试加密解密功能是否能正确还原license文件
     * @param context 上下文
     * @return 是否测试通过
     */
    public static boolean testEncryptionDecryption(Context context) {
        try {
            // 读取原始license文件
            File originalFile = new File(context.getCacheDir(), ORIGINAL_LICENSE_FILE);
            if (!originalFile.exists()) {
                Log.e(TAG, "原始license文件不存在，正在保存...");
                if (!saveOriginalLicense(context)) {
                    return false;
                }
            }

            InputStream is = new FileInputStream(originalFile);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int readBytes;
            while ((readBytes = is.read(buffer)) != -1) {
                baos.write(buffer, 0, readBytes);
            }
            byte[] originalData = baos.toByteArray();
            is.close();
            baos.close();

            Log.i(TAG, "读取原始license文件成功，大小: " + originalData.length + "字节");

            // 加密原始数据
            Log.i(TAG, "开始加密原始数据...");
            byte[] encryptedData = EncryptionUtils.encryptBytes(originalData);
            if (encryptedData == null) {
                Log.e(TAG, "加密失败");
                return false;
            }
            Log.i(TAG, "加密成功，加密后大小: " + encryptedData.length + "字节");

            // 保存加密数据到文件（可选，用于调试）
            File encryptedFile = new File(context.getCacheDir(), ENCRYPTED_TEST_FILE);
            FileOutputStream fos = new FileOutputStream(encryptedFile);
            fos.write(encryptedData);
            fos.close();
            Log.i(TAG, "加密数据已保存到: " + encryptedFile.getAbsolutePath());

            // 解密数据
            Log.i(TAG, "开始解密数据...");
            byte[] decryptedData = EncryptionUtils.decryptBytes(encryptedData);
            if (decryptedData == null) {
                Log.e(TAG, "解密失败");
                return false;
            }
            Log.i(TAG, "解密成功，解密后大小: " + decryptedData.length + "字节");

            // 保存解密数据到文件（可选，用于调试）
            File decryptedFile = new File(context.getCacheDir(), DECRYPTED_TEST_FILE);
            fos = new FileOutputStream(decryptedFile);
            fos.write(decryptedData);
            fos.close();
            Log.i(TAG, "解密数据已保存到: " + decryptedFile.getAbsolutePath());

            // 验证解密后的数据是否与原始数据一致
            return verifyDataMatch(originalData, decryptedData);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "测试加密解密功能失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 验证两个字节数组是否完全一致
     * @param original 原始数据
     * @param decrypted 解密后的数据
     * @return 是否一致
     */
    private static boolean verifyDataMatch(byte[] original, byte[] decrypted) {
        if (original.length != decrypted.length) {
            Log.e(TAG, "数据长度不一致: 原始长度=" + original.length + ", 解密后长度=" + decrypted.length);
            return false;
        }

        for (int i = 0; i < original.length; i++) {
            if (original[i] != decrypted[i]) {
                Log.e(TAG, "数据内容不一致，在索引 " + i + " 处: 原始值=" + original[i] + ", 解密后值=" + decrypted[i]);
                return false;
            }
        }

        Log.i(TAG, "✅ 加密解密测试通过！解密后的数据与原始数据完全一致");
        return true;
    }

    /**
     * 验证AES密钥和IV的长度是否正确
     * @return 是否验证通过
     */
    public static boolean verifyAESParameters() {
        try {
            byte[] keyBytes = EncryptionUtils.AES_KEY.getBytes("UTF-8");
            byte[] ivBytes = EncryptionUtils.AES_IV.getBytes("UTF-8");

            Log.i(TAG, "AES密钥长度: " + keyBytes.length + "字节");
            Log.i(TAG, "AES IV长度: " + ivBytes.length + "字节");

            boolean keyValid = keyBytes.length == 16;
            boolean ivValid = ivBytes.length == 16;

            if (keyValid && ivValid) {
                Log.i(TAG, "✅ AES密钥和IV长度验证通过！");
                return true;
            } else {
                Log.e(TAG, "❌ AES密钥和IV长度验证失败！");
                if (!keyValid) {
                    Log.e(TAG, "   密钥长度应为16字节，实际为: " + keyBytes.length + "字节");
                }
                if (!ivValid) {
                    Log.e(TAG, "   IV长度应为16字节，实际为: " + ivBytes.length + "字节");
                }
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "验证AES参数失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 运行所有测试
     * @param context 上下文
     * @return 是否所有测试通过
     */
    public static boolean runAllTests(Context context) {
        Log.i(TAG, "开始运行所有加密解密测试...");

        boolean paramValid = verifyAESParameters();
        boolean saveSuccess = saveOriginalLicense(context);
        boolean testSuccess = testEncryptionDecryption(context);

        Log.i(TAG, "\n=== 测试结果汇总 ===");
        Log.i(TAG, "AES参数验证: " + (paramValid ? "✅ 通过" : "❌ 失败"));
        Log.i(TAG, "原始license保存: " + (saveSuccess ? "✅ 通过" : "❌ 失败"));
        Log.i(TAG, "加密解密测试: " + (testSuccess ? "✅ 通过" : "❌ 失败"));
        Log.i(TAG, "===================");

        return paramValid && saveSuccess && testSuccess;
    }
}