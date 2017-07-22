package org.simpledrive.helper;

import android.util.Base64;

import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class Crypto {
    private static int blockSize = 16; // Bytes
    private static int keySize = 256; // Bit - AES-256

    private static SecretKeySpec generateKey(String password, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 2048, keySize);
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
            byte[] iv = randomBytes(blockSize);

            // Generate Salt
            byte[] salt = randomBytes(blockSize);

            // Generate Key
            SecretKeySpec key = generateKey(secret, salt);

            // Init cipher
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));

            // Encrypt
            byte[] ciphertext = cipher.doFinal(value.getBytes());

            // Concatenate (iv + salt + ciphertext)
            byte[] concat = new byte[iv.length + salt.length + ciphertext.length];
            System.arraycopy(iv, 0, concat, 0, iv.length);
            System.arraycopy(salt, 0, concat, iv.length, salt.length);
            System.arraycopy(ciphertext, 0, concat, iv.length + salt.length, ciphertext.length);

            // Encode
            return Base64.encodeToString(concat, Base64.NO_WRAP);

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return "";
    }

    public static String decrypt(String encrypted64, String secret) {
        try {
            // Decode
            byte[] data = Base64.decode(encrypted64.getBytes("UTF-8"), Base64.NO_WRAP);

            // Extract IV
            byte[] iv = Arrays.copyOfRange(data, 0, blockSize);

            // Extract salt
            byte[] salt = Arrays.copyOfRange(data, blockSize, blockSize * 2);

            // Extract payload
            byte[] ciphertext= Arrays.copyOfRange(data, blockSize * 2, data.length);

            // Generate key
            SecretKeySpec key = generateKey(secret, salt);

            // Init Cipher
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));

            // Decrypt
            byte[] decrypted = cipher.doFinal(ciphertext);

            return new String(decrypted, "UTF-8");
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return "";
    }
}