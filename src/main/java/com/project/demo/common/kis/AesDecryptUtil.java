package com.project.demo.common.kis;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AesDecryptUtil {

    public static String decrypt(String encryptedData, String key, String iv) throws Exception {
        byte[] decodedKey = key.getBytes(StandardCharsets.UTF_8);
        byte[] decodedIv = iv.getBytes(StandardCharsets.UTF_8);

        SecretKeySpec secretKeySpec = new SecretKeySpec(decodedKey, "AES");
        IvParameterSpec ivParameterSpec = new IvParameterSpec(decodedIv);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

        byte[] decodedBytes = Base64.getDecoder().decode(encryptedData);
        byte[] decrypted = cipher.doFinal(decodedBytes);

        return new String(decrypted, StandardCharsets.UTF_8);
    }
}

