package com.amarullz.androidtv.animetvjmto.utils;

import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.zip.InflaterInputStream;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class GotakuCrypto {
    private static final String TAG = "GotakuCrypto";

    public static final byte[] API_SEED_V8 = new byte[] {
        (byte) 169, (byte) 97, (byte) 13, (byte) 229, (byte) 137, (byte) 106, (byte) 199, (byte) 91,
        (byte) 124, (byte) 53, (byte) 130, (byte) 103, (byte) 235, (byte) 216, (byte) 204, (byte) 41,
        (byte) 161, (byte) 207, (byte) 4, (byte) 157, (byte) 109, (byte) 106, (byte) 130, (byte) 5,
        (byte) 57, (byte) 176, (byte) 155, (byte) 55, (byte) 18, (byte) 148, (byte) 80, (byte) 128
    };

    public static final byte[] API_SEED_V10 = new byte[] {
        (byte) 30, (byte) 99, (byte) 206, (byte) 236, (byte) 176, (byte) 26, (byte) 228, (byte) 147,
        (byte) 55, (byte) 35, (byte) 152, (byte) 123, (byte) 6, (byte) 17, (byte) 126, (byte) 179,
        (byte) 40, (byte) 181, (byte) 197, (byte) 210, (byte) 194, (byte) 90, (byte) 232, (byte) 35,
        (byte) 247, (byte) 20, (byte) 130, (byte) 43, (byte) 71, (byte) 117, (byte) 123, (byte) 82
    };

    public static final byte[] DEFAULT_API_SEED = API_SEED_V10;

    public static final String DEFAULT_API_VERSION = "10";
    public static final String DEFAULT_HKDF_INFO = "api-seal|v1";

    public static final byte[] MANIFEST_SEED = hexToBytes("458e5403c40a73332706df406193dd6b040a2ea97a6bb59b52b294a61006e6ef");

    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    public static String b64UrlEncode(byte[] data) {
        return Base64.encodeToString(data, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP).trim();
    }

    public static byte[] b64UrlDecode(String str) {
        if (str == null) return null;
        try {
            return Base64.decode(str.trim(), Base64.URL_SAFE | Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] newNonce() {
        byte[] nonce = new byte[16];
        new SecureRandom().nextBytes(nonce);
        return nonce;
    }

    public static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            Log.e(TAG, "hmacSha256 failed", e);
            return null;
        }
    }

    public static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int length) {
        if (salt == null || salt.length == 0) {
            salt = new byte[32];
        }
        byte[] prk = hmacSha256(salt, ikm);
        if (prk == null) return null;

        ByteArrayOutputStream okm = new ByteArrayOutputStream();
        byte[] t = new byte[0];
        int iterations = (int) Math.ceil((double) length / 32.0);

        for (int i = 1; i <= iterations; i++) {
            ByteArrayOutputStream stepInput = new ByteArrayOutputStream();
            stepInput.write(t, 0, t.length);
            if (info != null && info.length > 0) {
                stepInput.write(info, 0, info.length);
            }
            stepInput.write((byte) i);
            t = hmacSha256(prk, stepInput.toByteArray());
            if (t == null) return null;
            okm.write(t, 0, t.length);
        }

        byte[] all = okm.toByteArray();
        return Arrays.copyOfRange(all, 0, length);
    }

    public static byte[] gcmDecrypt(byte[] key, byte[] iv, byte[] ciphertextAndTag) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return cipher.doFinal(ciphertextAndTag);
        } catch (Exception e) {
            Log.w(TAG, "gcmDecrypt failed: " + e.getMessage());
            return null;
        }
    }

    public static byte[] gcmEncrypt(byte[] key, byte[] iv, byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            Log.w(TAG, "gcmEncrypt failed: " + e.getMessage());
            return null;
        }
    }

    public static String inflate(byte[] compressed) {
        try {
            InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(compressed));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int read;
            while ((read = iis.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.w(TAG, "inflate failed: " + e.getMessage());
            return null;
        }
    }

    public static String decryptApi(byte[] body, String apiVersion, byte[] apiSeed, String hkdfInfo) {
        if (body == null || body.length <= 29) return null;
        int versionInt = 10;
        try {
            versionInt = (apiVersion != null && !apiVersion.isEmpty()) ? Integer.parseInt(apiVersion) : 10;
        } catch (Exception ignored) {}
        byte versionByte = (byte) (versionInt & 0xFF);

        byte[] blob = body;
        if (body[0] != versionByte) {
            blob = b64UrlDecode(new String(body, StandardCharsets.UTF_8));
            if (blob == null || blob.length <= 29) return null;
            if (blob[0] == 10 || blob[0] == 8) {
                versionByte = blob[0];
            } else if (blob[0] != versionByte) {
                return null;
            }
        } else {
            versionByte = blob[0];
        }

        byte[] seed = apiSeed;
        if (seed == null || seed == DEFAULT_API_SEED || seed == API_SEED_V10 || seed == API_SEED_V8) {
            seed = (versionByte == 8) ? API_SEED_V8 : API_SEED_V10;
        }

        byte[] iv = Arrays.copyOfRange(blob, 1, 13);
        byte[] ct = Arrays.copyOfRange(blob, 13, blob.length);
        byte[] key = hkdfSha256(seed, new byte[0], hkdfInfo.getBytes(StandardCharsets.UTF_8), 32);
        if (key == null) return null;

        byte[] compressed = gcmDecrypt(key, iv, ct);
        if (compressed == null) return null;

        return inflate(compressed);
    }

    public static String encryptManifest(String stamp, byte[] nonce) {
        String payload = "{\"nonce\":\"" + b64UrlEncode(nonce) + "\",\"stamp\":\"" + stamp + "\"}";
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        byte[] ct = gcmEncrypt(MANIFEST_SEED, iv, payload.getBytes(StandardCharsets.UTF_8));
        if (ct == null) return null;

        byte[] combined = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ct, 0, combined, iv.length, ct.length);
        return b64UrlEncode(combined);
    }

    public static String decryptManifest(String body, byte[] nonce) {
        byte[] blob = b64UrlDecode(body);
        if (blob == null || blob.length < 28) return null;

        byte[] iv = Arrays.copyOfRange(blob, 0, 12);
        byte[] ct = Arrays.copyOfRange(blob, 12, blob.length);

        byte[] key = hkdfSha256(MANIFEST_SEED, nonce, "manifest-resp|v1".getBytes(StandardCharsets.UTF_8), 32);
        if (key == null) return null;

        byte[] plain = gcmDecrypt(key, iv, ct);
        if (plain == null) return null;

        return new String(plain, StandardCharsets.UTF_8);
    }

    public static String getUrlId(String url, boolean fullPath) {
        try {
            int qIdx = url.indexOf('?');
            String clean = (qIdx != -1) ? url.substring(0, qIdx) : url;
            int schemeIdx = clean.indexOf("://");
            String pathOnly = (schemeIdx != -1) ? clean.substring(schemeIdx + 3) : clean;
            int firstSlash = pathOnly.indexOf('/');
            String path = (firstSlash != -1) ? pathOnly.substring(firstSlash) : "";

            String[] rawSegments = path.split("/");
            java.util.List<String> segments = new java.util.ArrayList<>();
            for (String s : rawSegments) {
                if (!s.isEmpty()) segments.add(s);
            }
            int drop = fullPath ? 0 : 2;
            StringBuilder sb = new StringBuilder();
            for (int i = drop; i < segments.size(); i++) {
                if (sb.length() > 0) sb.append('/');
                sb.append(segments.get(i));
            }
            String res = sb.toString();
            int dot = res.lastIndexOf('.');
            return (dot != -1) ? res.substring(0, dot) : res;
        } catch (Exception e) {
            return "";
        }
    }

    public static byte[] segmentCounter(String url, boolean fullPath) {
        try {
            String urlId = getUrlId(url, fullPath);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(urlId.getBytes(StandardCharsets.UTF_8));
            byte[] counter = new byte[16];
            System.arraycopy(hash, 0, counter, 0, 8);
            return counter;
        } catch (Exception e) {
            Log.e(TAG, "segmentCounter failed", e);
            return new byte[16];
        }
    }

    public static byte[] decryptPrefix(byte[] data, String url, byte[] key, boolean fullPath, int encryptedLength) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(segmentCounter(url, fullPath)));
            int n = Math.min(data.length, encryptedLength);
            byte[] ks = cipher.doFinal(new byte[n]);
            byte[] out = new byte[data.length];
            for (int i = 0; i < data.length; i++) {
                out[i] = (i < n) ? (byte) (data[i] ^ ks[i]) : data[i];
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    public static Cipher createSegmentCipher(byte[] keySeed, String url, boolean fullPath, long rangeStart) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            byte[] iv = segmentCounter(url, fullPath);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keySeed, "AES"), new IvParameterSpec(iv));

            if (rangeStart > 0) {
                byte[] scratch = new byte[4096];
                long remaining = rangeStart;
                while (remaining > 0) {
                    int n = (int) Math.min(remaining, scratch.length);
                    cipher.update(scratch, 0, n);
                    remaining -= n;
                }
            }
            return cipher;
        } catch (Exception e) {
            Log.e(TAG, "createSegmentCipher failed", e);
            return null;
        }
    }

    public static boolean looksLikeMedia(byte[] bytes) {
        if (bytes == null || bytes.length < 188) return false;
        if ((bytes[0] & 0xFF) == 0x47) {
            for (int i = 188; i < bytes.length; i += 188) {
                if ((bytes[i] & 0xFF) != 0x47) return false;
            }
            return true;
        }
        if (bytes.length >= 8) {
            String magic = new String(bytes, 4, 4, StandardCharsets.ISO_8859_1);
            return "ftyp".equals(magic) || "styp".equals(magic) || "moof".equals(magic);
        }
        return false;
    }
}
