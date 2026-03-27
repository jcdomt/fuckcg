package com.wzjer.fuckcg;

import android.content.Context;
import android.net.Uri;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import net.crigh.api.encrypt.ChingoEncrypt;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class http {
    private static final String BASE_URL = "https://ggtypt.njtech.edu.cn/cgapp-server";


    static String executeSignedRequest(String url, String sign, String timestamp, String v1, String imei, String ua, String cli, String cgAuthorization, RequestBody body, String signSource) {
        OkHttpClient client = new OkHttpClient();

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json;charset=UTF-8")
                .addHeader("cgAuthorization", safeHeaderValue(cgAuthorization))
                .addHeader("client", safeHeaderValue(cli))
                .addHeader("sign", safeHeaderValue(sign))
                .addHeader("timestamp", safeHeaderValue(timestamp))
                .addHeader("v1", safeHeaderValue(v1))
                .addHeader("imei", safeHeaderValue(imei))
                .addHeader("User-Agent", safeHeaderValue(ua));

        if (body != null) {
            requestBuilder.post(body);
        }

        Request request = requestBuilder.build();

        StringBuilder reqLog = new StringBuilder();
        reqLog.append("\n========== HTTP REQUEST ==========\n");
        reqLog.append("signSource: ").append(signSource == null ? "" : signSource).append("\n");
        reqLog.append("sign: ").append(sign == null ? "" : sign).append("\n");
        reqLog.append("timestamp: ").append(timestamp == null ? "" : timestamp).append("\n");
        reqLog.append(request.method()).append(" ").append(request.url()).append("\n");
        okhttp3.Headers reqHeaders = request.headers();
        for (int i = 0; i < reqHeaders.size(); i++) {
            reqLog.append(reqHeaders.name(i)).append(": ").append(reqHeaders.value(i)).append("\n");
        }
        if (request.body() != null) {
            try {
                okio.Buffer buffer = new okio.Buffer();
                request.body().writeTo(buffer);
                reqLog.append("\n").append(buffer.readUtf8()).append("\n");
            } catch (Exception ignored) {
            }
        }
        reqLog.append("==================================");
        Log.d("HTTP_RAW", reqLog.toString());

        try (Response response = client.newCall(request).execute()) {
            String bodyStr = response.body() == null ? "" : response.body().string();

            StringBuilder respLog = new StringBuilder();
            respLog.append("\n========== HTTP RESPONSE ==========\n");
            respLog.append("signSource: ").append(signSource == null ? "" : signSource).append("\n");
            respLog.append("sign: ").append(sign == null ? "" : sign).append("\n");
            respLog.append("timestamp: ").append(timestamp == null ? "" : timestamp).append("\n");
            respLog.append(response.code()).append(" ").append(response.message()).append("\n");
            okhttp3.Headers respHeaders = response.headers();
            for (int i = 0; i < respHeaders.size(); i++) {
                respLog.append(respHeaders.name(i)).append(": ").append(respHeaders.value(i)).append("\n");
            }
            respLog.append("\n").append(bodyStr).append("\n");
            respLog.append("===================================");
            Log.d("HTTP_RAW", respLog.toString());

            if (response.isSuccessful()) {
                if (bodyStr.isEmpty()) {
                    return "{\"error\":\"empty response body\"}";
                }
                return bodyStr;
            }
            return "{\"error\":\"http code: " + response.code() + "\"}";
        } catch (IOException e) {
            Log.e("http", "request failed", e);
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String get(String url, JSONObject data, String jwt, String secret) {
        return get(null, url, data, jwt, secret, null);
    }

    public static String get(Context context, String url, JSONObject data, String jwt, String secret, String studentIdOverride) {
        if (url == null || url.trim().isEmpty()) {
            return "{\"error\":\"empty url\"}";
        }
        if (jwt == null || jwt.trim().isEmpty()) {
            return "{\"error\":\"empty jwt\"}";
        }
        if (secret == null || secret.trim().isEmpty()) {
            return "{\"error\":\"empty secret\"}";
        }

        Uri.Builder builder = Uri.parse(url).buildUpon();
        if (data != null) {
            Iterator<String> keys = data.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = data.opt(key);
                builder.appendQueryParameter(key, value == null ? "" : String.valueOf(value));
            }
        }

        String paramUrl = builder.build().toString();
        String signSourceUrl = extractSignPath(paramUrl);
        Log.d("http", "get signSourceUrl: " + signSourceUrl);

        encryptResult r = encrypt(signSourceUrl, secret);
        if (r == null) {
            Log.e("http", "encrypt failed, abort get");
            return "{\"error\":\"encrypt failed, abort get\"}";
        }

        String studentId = safeStudentId(studentIdOverride);
        if (studentId.isEmpty() && data != null) {
            studentId = safeStudentId(data.optString("xh", ""));
        }

        String imei = generateImei(context, studentId, jwt);
        String v1 = md5(String.valueOf(System.currentTimeMillis()));
        String ua = generateUserAgent();
        String clientJson = "{\"root\":0,\"heap\":1,\"t\":" + System.currentTimeMillis() + "}";
        String aesKey = fixedAesKey(secret);
        if (aesKey == null) {
            return "{\"error\":\"invalid secret\"}";
        }
        String client = aes(clientJson, aesKey);
        String fullUrl = BASE_URL + signSourceUrl;

        String rs = executeSignedRequest(fullUrl, r.sign, r.timestamp, v1, imei, ua, client, jwt, null, signSourceUrl);
        Log.d("DEBUG", "Response: " + rs);
        return rs;
    }

    private static String extractSignPath(String url) {
        if (url == null) {
            return "";
        }
        int appIndex = url.indexOf("/app");
        if (appIndex >= 0) {
            return url.substring(appIndex);
        }
        int apiIndex = url.indexOf("/api");
        if (apiIndex >= 0) {
            return url.substring(apiIndex);
        }
        return url;
    }

    private static String decodeUtf8(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String buildPostSignSource(String path, TreeMap<String, String> params) {
        if (path == null) {
            path = "";
        }
        if (params == null || params.isEmpty()) {
            return path;
        }

        StringBuilder sb = new StringBuilder(path);
        sb.append(path.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            first = false;
            sb.append(entry.getKey()).append('=').append(entry.getValue() == null ? "" : entry.getValue());
        }
        return sb.toString();
    }

    public static String post(String url, JSONObject data, String jwt, String secret) {
        return post(null, url, data, jwt, secret, null);
    }

    public static String post(Context context, String url, JSONObject data, String jwt, String secret, String studentIdOverride) {
        if (url == null || url.trim().isEmpty()) {
            return "{\"error\":\"empty url\"}";
        }
        if (jwt == null || jwt.trim().isEmpty()) {
            return "{\"error\":\"empty jwt\"}";
        }
        if (secret == null || secret.trim().isEmpty()) {
            return "{\"error\":\"empty secret\"}";
        }

        FormBody.Builder bodyBuilder = new FormBody.Builder(StandardCharsets.UTF_8);
        TreeMap<String, String> sortedSignParams = new TreeMap<>();

        if (data != null) {
            Iterator<String> keys = data.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = data.opt(key);
                String stringValue = value == null ? "" : String.valueOf(value);
                bodyBuilder.add(key, stringValue);
                sortedSignParams.put(key, decodeUtf8(stringValue));
            }
        }

        String signPath = extractSignPath(url);
        String signSourceUrl = buildPostSignSource(signPath, sortedSignParams);
        Log.d("http", "post signSourceUrl: " + signSourceUrl);

        encryptResult r = encrypt(signSourceUrl, secret);
        if (r == null) {
            Log.e("http", "encrypt failed, abort post");
            return "{\"error\":\"encrypt failed, abort post\"}";
        }

        String studentId = safeStudentId(studentIdOverride);
        if (studentId.isEmpty() && data != null) {
            studentId = safeStudentId(data.optString("xh", ""));
        }

        String imei = generateImei(context, studentId, jwt);
        String v1 = md5(String.valueOf(System.currentTimeMillis()));
        String ua = generateUserAgent();
        String clientJson = "{\"root\":0,\"heap\":1,\"t\":" + System.currentTimeMillis() + "}";
        String aesKey = fixedAesKey(secret);
        if (aesKey == null) {
            return "{\"error\":\"invalid secret\"}";
        }
        String client = aes(clientJson, aesKey);

        String fullUrl = BASE_URL + signPath;
        RequestBody body = bodyBuilder.build();
        String rs = executeSignedRequest(fullUrl, r.sign, r.timestamp, v1, imei, ua, client, jwt, body, signSourceUrl);
        Log.d("DEBUG", "Response: " + rs);
        return rs;
    }

    private static class encryptResult {
        public String sign;
        public String timestamp;
    }

    private static encryptResult encrypt(String url, String secret) {
        if (url == null || url.trim().isEmpty() || secret == null || secret.trim().isEmpty()) {
            Log.e("http", "encrypt input invalid, url or secret is empty");
            return null;
        }
        if (!ChingoEncrypt.isLibraryLoaded()) {
            Log.e("http", "Native library not loaded, cannot compute signature");
            return null;
        }

        try {
            String r = ChingoEncrypt.cgapiEnrypt(secret, url);
            String[] split = r.split("\\|");
            if (split.length <= 3) {
                Log.e("http", "encrypt result invalid: " + r);
                return null;
            }
            String sign1 = split[3];
            String time = split[1];

            Log.d("HTTP_SIGN", "source=" + url + ", sign=" + sign1 + ", timestamp=" + time);

            encryptResult result = new encryptResult();
            result.sign = sign1;
            result.timestamp = time;
            return result;
        } catch (Exception e) {
            Log.e("http", "Error computing signature: " + e.getMessage(), e);
            return null;
        }
    }

    private static String fixedAesKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(key);
        while (sb.length() < 16) {
            sb.append('0');
        }
        return sb.substring(0, 16);
    }

    private static String safeHeaderValue(String value) {
        return value == null ? "" : value;
    }

    private static String md5(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] array = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(array.length * 2);
            for (byte b : array) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e("http", "MD5 algorithm not available", e);
            return "";
        }
    }

    private static String aes(String data, String key) {
        try {
            StringBuilder sb = new StringBuilder(key);
            while (sb.length() < 16) {
                sb.append('0');
            }
            byte[] keyBytes = sb.toString().substring(0, 16).getBytes(StandardCharsets.UTF_8);

            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"));
            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e("http", "aes encrypt failed", e);
            return null;
        }
    }

    public static String postSportsData(String url, String jsonData, android.content.Context context) {
        try {
            login.UserBody userBody = login.loadUserBody(context);
            if (userBody == null || userBody.jwt == null || userBody.secret == null) {
                return "{\"error\":\"Authentication not available, please login first\"}";
            }

            JSONObject dataObj = new JSONObject();
            String studentId = "";
            try {
                String aesKey = fixedAesKey(userBody.secret);
                if (aesKey == null) {
                    return "{\"error\":\"invalid secret\"}";
                }
                studentId = extractStudentIdFromSportsJson(jsonData);
                dataObj.put("jsonsports", aes(jsonData, aesKey));
            } catch (JSONException e) {
                Log.e("http", "Failed to construct data object: " + e.getMessage(), e);
                return "{\"error\":\"Failed to construct data object: " + e.getMessage() + "\"}";
            }

            String responseText = post(context, url, dataObj, userBody.jwt, userBody.secret, studentId);
            if (responseText.trim().isEmpty()) {
                return "{\"error\":\"empty response\"}";
            }

            try {
                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("endpoint", url);
                result.put("response", new JSONObject(responseText));
                return result.toString();
            } catch (Exception ignored) {
                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("endpoint", url);
                result.put("responseText", responseText);
                return result.toString();
            }
        } catch (Exception e) {
            Log.e("http", "postSportsData failed: " + e.getMessage(), e);
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private static String generateImei() {
        return generateImei(null, null, null);
    }

    private static String generateImei(Context context, String studentId, String jwt) {
        String androidId = getAndroidId(context);
        String officialLikeImei = buildOfficialLikeImei(androidId);

        String normalizedStudentId = safeStudentId(studentId);
        String sessionSeed = resolveLoginCycleSeed(context, jwt);

        // Bind to account + login cycle while staying deterministic for that cycle.
        String seed = officialLikeImei + "|" + normalizedStudentId + "|" + sessionSeed;
        String digest = md5(seed);
        if (digest != null && digest.length() >= 32) {
            String uuidLike = digest.substring(0, 8) + "-"
                    + digest.substring(8, 12) + "-"
                    + digest.substring(12, 16) + "-"
                    + digest.substring(16, 20) + "-"
                    + digest.substring(20, 32);
            if (!uuidLike.isEmpty()) {
                return uuidLike;
            }
        }

        if (officialLikeImei != null && !officialLikeImei.isEmpty()) {
            return officialLikeImei;
        }
        return "ffffffff-cce7-2bea-cce7-2bea00000000";
    }

    private static String getAndroidId(Context context) {
        if (context == null) {
            return "";
        }
        try {
            String value = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            return value == null ? "" : value;
        } catch (Exception e) {
            Log.w("http", "Failed to read android_id", e);
            return "";
        }
    }

    private static String buildOfficialLikeImei(String androidId) {
        if (androidId == null || androidId.trim().isEmpty()) {
            return "";
        }

        // Centered on the official mechanism: UUID(hash(android_id), hash(android_id)<<32).
        int hash = androidId.hashCode();
        long msb = hash;
        long lsb = ((long) hash) << 32;
        return new java.util.UUID(msb, lsb).toString();
    }

    private static String resolveLoginCycleSeed(Context context, String jwt) {
        if (context != null) {
            try {
                String jsessionId = new Modules.LoginPrefs(context).getSavedJSessionId();
                if (jsessionId != null && !jsessionId.trim().isEmpty()) {
                    return jsessionId.trim();
                }
            } catch (Exception ignored) {
            }
        }
        return (jwt == null || jwt.trim().isEmpty()) ? "no-session" : jwt.trim();
    }

    private static String safeStudentId(String studentId) {
        return studentId == null ? "" : studentId.trim();
    }

    private static String extractStudentIdFromSportsJson(String jsonData) {
        if (jsonData == null || jsonData.trim().isEmpty()) {
            return "";
        }
        try {
            JSONObject payload = new JSONObject(jsonData);
            return safeStudentId(payload.optString("xh", ""));
        } catch (Exception e) {
            Log.w("http", "extractStudentIdFromSportsJson failed", e);
            return "";
        }
    }

    private static String randomFrom(String[] values) {
        if (values == null || values.length == 0) {
            return "";
        }
        int idx = ThreadLocalRandom.current().nextInt(values.length);
        return values[idx];
    }

    private static String randomBuildId() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        char c1 = (char) ('A' + r.nextInt(26));
        char c2 = (char) ('A' + r.nextInt(26));
        int d1 = r.nextInt(10);
        char c3 = (char) ('A' + r.nextInt(26));
        int yymmdd = 240000 + r.nextInt(300000);
        int patch = 100 + r.nextInt(900);
        int branch = 1 + r.nextInt(4);
        return "" + c1 + c2 + d1 + c3 + "." + yymmdd + "." + patch + ".A" + branch;
    }

    private static boolean isSafeHeaderAscii(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 32 || c > 126) {
                return false;
            }
        }
        return true;
    }
    private static final String UA_FALLBACK = "cgapp/2.9.6 (Linux; Android 16; Xiaomi/BP2A.250605.031.A3)";
    private static final String[] UA_APP_VERSIONS = {
            "2.9.5", "2.9.6"
    };
    private static final String[] UA_ANDROID_VERSIONS = {
            "12", "13", "14", "15", "16"
    };
    private static final String[] UA_BRANDS = {
            "Xiaomi", "Redmi", "OnePlus", "OPPO", "vivo", "HUAWEI", "samsung"
    };
    private static String generateUserAgent() {
        String appVersion = randomFrom(UA_APP_VERSIONS);
        String androidVersion = randomFrom(UA_ANDROID_VERSIONS);
        String brand = randomFrom(UA_BRANDS);
        String buildId = randomBuildId();

        String ua = "cgapp/" + appVersion + " (Linux; Android " + androidVersion + "; " + brand + "/" + buildId + ")";
        if (!isSafeHeaderAscii(ua) || ua.length() > 128) {
            return UA_FALLBACK;
        }
        return ua;
    }
}
