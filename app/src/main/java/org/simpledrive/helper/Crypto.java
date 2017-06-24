package org.simpledrive.helper;

import android.util.Base64;

import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class Crypto {

    private static SecretKeySpec generateKey(String password, String salt) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes("UTF-8"), 2048, 256); // AES-256
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            byte[] key = f.generateSecret(spec).getEncoded();
            return new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String randomString(int length) {
        String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        SecureRandom rnd = new SecureRandom();

        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        }

        return sb.toString();
    }

    private static byte[] randomBytes(int length) {
        SecureRandom rnd = new SecureRandom();
        byte[] b = new byte[length];
        rnd.nextBytes(b);
        return b;
    }

    public static String encrypt(String value, String secret) {
        try {
            // Generate IV
            int iv_size = 16;
            byte[] iv = randomBytes(iv_size);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // Generate Salt
            int salt_size = 16;
            String salt = randomString(salt_size);

            // Generate Key
            SecretKeySpec skeySpec = generateKey(secret, salt);

            // Init cipher
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, ivSpec);

            // Encrypt
            byte[] encrypted = cipher.doFinal(value.getBytes());
            String encryptedString = Base64.encodeToString(encrypted, Base64.DEFAULT);

            // Encode
            String forReturn = encryptedString + ":" + Base64.encodeToString(iv, Base64.DEFAULT) + ":" + salt;
            return Base64.encodeToString(forReturn.getBytes(), Base64.DEFAULT);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return "";
    }

    public static String decrypt(String encrypted64, String secret) {
        try {
            // Decode
            byte[] data = Base64.decode(encrypted64.getBytes("UTF-8"), Base64.DEFAULT);
            String encrypted = new String(data, "UTF-8");

            String[] separated = encrypted.split(":");

            // Extract payload
            String payloadString = separated[0];
            byte[] payload = Base64.decode(payloadString, Base64.DEFAULT);

            // Extract IV
            String ivString = separated[1];
            byte[] iv = Base64.decode(ivString.getBytes("UTF-8"), Base64.DEFAULT);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // Extract Salt
            String salt = separated[2];

            // Generate key
            SecretKeySpec skeySpec = generateKey(secret, salt);

            // Init Cipher
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivSpec);

            // Decrypt
            byte[] decrypted = cipher.doFinal(payload);

            return new String(decrypted, "UTF-8");
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return "";
    }
}