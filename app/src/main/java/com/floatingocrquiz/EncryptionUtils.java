package com.floatingocrquiz;

import android.util.Base64;
import android.util.Log;

import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionUtils {
    private static final String TAG = "EncryptionUtils";
    
    // AES密钥和IV（16字节）
    public static final String AES_KEY = "your_aes_key_here";
    public static final String AES_IV = "your_aes_iv_here";
    
    private static final String AES_TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String AES_ALGORITHM = "AES";
    
    /**
     * 加密字节数组
     * @param original 原始字节数组
     * @return 加密后的字节数组，失败返回null
     */
    public static byte[] encryptBytes(byte[] original) {
        try {
            // 准备密钥和IV
            SecretKeySpec keySpec = new SecretKeySpec(AES_KEY.getBytes("UTF-8"), AES_ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(AES_IV.getBytes("UTF-8"));
            
            // 初始化Cipher
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            
            // 执行加密
            return cipher.doFinal(original);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "加密失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 解密字节数组
     * @param encrypted 加密后的字节数组
     * @return 解密后的字节数组，失败返回null
     */
    public static byte[] decryptBytes(byte[] encrypted) {
        try {
            // 准备密钥和IV
            SecretKeySpec keySpec = new SecretKeySpec(AES_KEY.getBytes("UTF-8"), AES_ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(AES_IV.getBytes("UTF-8"));
            
            // 初始化Cipher
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            
            // 执行解密
            return cipher.doFinal(encrypted);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "解密失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 生成随机的AES密钥（16字节）
     * @return 随机密钥
     */
    public static String generateRandomKey() {
        byte[] key = new byte[16];
        new SecureRandom().nextBytes(key);
        return Base64.encodeToString(key, Base64.NO_WRAP);
    }
    
    /**
     * 生成随机的AES IV（16字节）
     * @return 随机IV
     */
    public static String generateRandomIV() {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        return Base64.encodeToString(iv, Base64.NO_WRAP);
    }
}