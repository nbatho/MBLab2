package com.example.mblab2.dal;

import android.util.Base64;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Salted password storage and verification, compatible with minSdk 24. */
final class PasswordHasher {
    private static final int PASSWORD_ITERATIONS = 120_000;

    private PasswordHasher() { }

    static String hashPassword(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = derivePassword(password, salt, PASSWORD_ITERATIONS);
        return "pbkdf2-sha1$" + PASSWORD_ITERATIONS + "$"
                + Base64.encodeToString(salt, Base64.NO_WRAP) + "$"
                + Base64.encodeToString(hash, Base64.NO_WRAP);
    }

    static boolean verifyPassword(String password, String stored) {
        if (stored == null) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !"pbkdf2-sha1".equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations != PASSWORD_ITERATIONS) {
                return false;
            }
            byte[] salt = Base64.decode(parts[2], Base64.NO_WRAP);
            byte[] expected = Base64.decode(parts[3], Base64.NO_WRAP);
            return salt.length == 16 && expected.length == 32
                    && MessageDigest.isEqual(expected, derivePassword(password, salt, iterations));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static byte[] derivePassword(String password, byte[] salt, int iterations) {
        // HmacSHA1 PBKDF2 is available on minSdk 24; SHA256 PBKDF2 needs newer Android.
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Không thể xử lý mật khẩu", ex);
        } finally {
            spec.clearPassword();
        }
    }
}
