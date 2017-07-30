package org.simpledrive.helper;

import android.util.Base64;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class Crypto {
    private static int blockSize = 16; // Bytes
    private static int keySize = 256; // Bit - AES-256

    private static String sign(byte[] str, SecretKeySpec key) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            sha256_HMAC.init(key);
            return Util.bytesToHex(sha256_HMAC.doFinal(str));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
        }

        return null;
    }

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

    private static String base64_url_encode(byte[] str) {
        return Base64.encodeToString(str, Base64.NO_WRAP).replace("+", "-").replace("/", "_");
    }

    private static byte[] base64_url_decode(String str) throws UnsupportedEncodingException {
        return Base64.decode(str.replace("-", "+").replace("_", "/").getBytes("UTF-8"), Base64.NO_WRAP);
    }

    public static String encryptString(String value, String secret) {
        return encryptString(value, secret, false);
    }

    public static String encryptString(String value, String secret, boolean sign) {
        return encrypt(value.getBytes(), secret, sign);
    }

    public static String encryptFile(String path, String secret) {
        return encryptFile(path, secret, false, false);
    }

    public static String encryptFile(String path, String secret, boolean sign, boolean encryptFilename) {
        // Read file
        File file = new File(path);
        byte[] content = Util.readFromStorage(path);

        // Encrypt file
        String enc = encrypt(content, secret, sign);

        // Determine destination
        String filename = (encryptFilename) ? encryptString(file.getName(), secret) + ".enc" : file.getName() + ".enc";
        String destination = file.getParent() + "/" + filename;

        // Write file
        return (Util.writeTextToStorage(destination, enc)) ? destination : "";
    }

    private static String encrypt(byte[] value, String secret, boolean sign) {
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
            byte[] ciphertext = cipher.doFinal(value);

            // Concatenate (iv + salt + ciphertext)
            byte[] concat = new byte[iv.length + salt.length + ciphertext.length];
            System.arraycopy(iv, 0, concat, 0, iv.length);
            System.arraycopy(salt, 0, concat, iv.length, salt.length);
            System.arraycopy(ciphertext, 0, concat, iv.length + salt.length, ciphertext.length);

            // Encode
            String concat64 = base64_url_encode(concat);

            // Sign
            if (sign) {
                concat64 = concat64 + ":" + sign(concat64.getBytes("UTF-8"), key);
            }

            return concat64;

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }

    public static String decryptString(String encrypted64, String secret) {
        return Util.byteToString(decrypt(encrypted64, secret));
    }

    public static String decryptFile(String path, String secret, boolean filenameEncrypted) {
        // Read file
        File file = new File(path);
        String content = Util.readTextFromStorage(path);

        // Decrypt
        byte[] dec = decrypt(content, secret);

        // Determine destination path
        String filename = (path.endsWith(".enc")) ? file.getName().substring(0, file.getName().length() - 4) : file.getName();
        filename = (filenameEncrypted) ? decryptString(filename, secret) : filename;
        String destination = file.getParent() + "/" + filename;

        // Write file
        return (Util.writeToStorage(destination, dec)) ? destination : "";
    }

    private static byte[] decrypt(String encrypted64, String secret) {
        try {
            // Separate payload from potential hmac
            String[] separated = encrypted64.trim().split(":");

            // Extract HMAC
            String hmac = (separated.length > 1) ? separated[1] : "";

            // Decode
            byte[] data = base64_url_decode(separated[0]);

            // Extract IV
            byte[] iv = Arrays.copyOfRange(data, 0, blockSize);

            // Extract salt
            byte[] salt = Arrays.copyOfRange(data, blockSize, blockSize * 2);

            // Extract payload
            byte[] ciphertext= Arrays.copyOfRange(data, blockSize * 2, data.length);

            // Generate key
            SecretKeySpec key = generateKey(secret, salt);

            if (!hmac.equals("") && !hmac.equals(sign(separated[0].getBytes("UTF-8"), key))) {
                return null;
            }

            // Init Cipher
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));

            // Decrypt
            return cipher.doFinal(ciphertext);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return null;
    }
}