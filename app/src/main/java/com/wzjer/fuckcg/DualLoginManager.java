package com.wzjer.fuckcg;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 同时登录管理器
 * 用于实现与官方应用同时登录的功能（需要ROOT权限）
 */
public class DualLoginManager {
    private static final String TAG = "DualLoginManager";

    private static final String OFFICIAL_APP_PACKAGE = "net.crigh.cgsport";

    private static final String[] COMMON_PREF_FILES = {
            "HEADER.xml",
            "login.xml",
            "auth.xml",
            "login_prefs.xml",
            "auth_prefs.xml",
            "LoginPrefs.xml"
    };

    private final Context context;

    public DualLoginManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean isDeviceRooted() {
        return checkRootAccess();
    }

    public void performDualLogin(LoginCallback callback) {
        if (!isDeviceRooted()) {
            callback.onError("设备未ROOT或未授予ROOT权限");
            return;
        }

        try {
            String tokenJson = readOfficialAppToken();
            if (!tokenJson.isEmpty()) {
                login.UserBody userBody = parseTokenJson(tokenJson);
                if (userBody != null && userBody.jwt != null && !userBody.jwt.isEmpty()) {
                    syncTokenToCurrentApp(userBody);
                    Log.d(TAG, "DualLogin success, token synced");
                    callback.onSuccess(userBody);
                } else {
                    callback.onError("无法解析官方应用的登录信息");
                }
            } else {
                callback.onError("无法读取官方应用的登录信息，请确保官方应用已登录");
            }
        } catch (Exception e) {
            Log.e(TAG, "DualLogin failed", e);
            callback.onError("同时登录失败: " + e.getMessage());
        }
    }

    private String readOfficialAppToken() throws Exception {
        if (!isOfficialPackageInstalled()) {
            throw new IOException("官方应用未安装");
        }

        List<String> sharedPrefsDirs = buildSharedPrefsDirs();
        if (sharedPrefsDirs.isEmpty()) {
            throw new IOException("未发现官方应用数据目录");
        }

        for (String sharedPrefsDir : sharedPrefsDirs) {
            Log.d(TAG, "Trying directory: " + sharedPrefsDir);
            List<String> existingXmlFiles = listExistingSharedPrefXmlFiles(sharedPrefsDir);

            LinkedHashSet<String> candidateFiles = new LinkedHashSet<>();
            if (!existingXmlFiles.isEmpty()) {
                for (String preferred : COMMON_PREF_FILES) {
                    if (existingXmlFiles.contains(preferred)) {
                        candidateFiles.add(preferred);
                    }
                }
                // 兜底：把目录里其他 xml 也尝试一遍
                candidateFiles.addAll(existingXmlFiles);
            } else {
                // 目录扫描可能受 shell/glob/命名空间影响，回退到固定文件名直读
                Log.d(TAG, "No xml files listed under: " + sharedPrefsDir + ", fallback to COMMON_PREF_FILES");
                for (String preferred : COMMON_PREF_FILES) {
                    candidateFiles.add(preferred);
                }
            }

            for (String prefFile : candidateFiles) {
                String fullPath = sharedPrefsDir + "/" + prefFile;
                try {
                    Log.d(TAG, "Attempting to read: " + fullPath);
                    // 直接拼接命令，不使用 shellQuote()
                    String command = "cat \"" + fullPath + "\"";
                    String xmlContent = executeRootCommand(command);

                    if (xmlContent.trim().isEmpty()) {
                        continue;
                    }

                    String parsed = parseSharedPreferencesXml(xmlContent);
                    if (parsed != null && !parsed.isEmpty()) {
                        Log.d(TAG, "Successfully parsed token from: " + fullPath);
                        return parsed;
                    }
                } catch (IOException e) {
                    Log.d(TAG, "Error reading " + fullPath + ": " + e.getMessage());
                }
            }
        }

        throw new IOException("未找到可用TOKEN/SECRET");
    }

    private List<String> listExistingSharedPrefXmlFiles(String sharedPrefsDir) {
        List<String> result = new ArrayList<>();
        try {
            // 无文件时也返回 0，避免把“目录里没有 xml”当成错误
            String command = "for f in \"" + sharedPrefsDir + "\"/*.xml; do [ -e \"$f\" ] || continue; basename \"$f\"; done";
            String output = executeRootCommand(command);
            for (String line : output.split("\\r?\\n")) {
                String name = line.trim();
                if (!name.isEmpty() && !result.contains(name)) {
                    result.add(name);
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Failed to list xml files in " + sharedPrefsDir + ": " + e.getMessage());
        }
        return result;
    }

    private boolean isOfficialPackageInstalled() {
        try {
            String result = executeRootCommand("pm path \"" + OFFICIAL_APP_PACKAGE + "\"");
            return result.contains("package:");
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressLint("SdCardPath")
    private List<String> buildSharedPrefsDirs() {
        LinkedHashSet<String> dirs = new LinkedHashSet<>();

        // 常见路径
        dirs.add("/data/data/" + OFFICIAL_APP_PACKAGE + "/shared_prefs");
        dirs.add("/data/user/0/" + OFFICIAL_APP_PACKAGE + "/shared_prefs");
        dirs.add("/data/user_de/0/" + OFFICIAL_APP_PACKAGE + "/shared_prefs");

        // 某些环境下可见的数据镜像路径
        dirs.add("/data_mirror/data_ce/null/0/" + OFFICIAL_APP_PACKAGE + "/shared_prefs");

        for (String userId : listAppUserIds()) {
            dirs.add("/data/user/" + userId + "/" + OFFICIAL_APP_PACKAGE + "/shared_prefs");
            dirs.add("/data/user_de/" + userId + "/" + OFFICIAL_APP_PACKAGE + "/shared_prefs");
        }
        return new ArrayList<>(dirs);
    }

    private List<String> listAppUserIds() {
        List<String> userIds = new ArrayList<>();
        try {
            String command = "for d in /data/user/*; do if [ -d \"$d/" + OFFICIAL_APP_PACKAGE
                    + "\" ]; then basename \"$d\"; fi; done";
            String output = executeRootCommand(command);
            for (String line : output.split("\\r?\\n")) {
                String userId = line.trim();
                if (!userId.isEmpty() && !userIds.contains(userId)) {
                    userIds.add(userId);
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Failed to list app user ids: " + e.getMessage());
        }
        return userIds;
    }

    private String parseSharedPreferencesXml(String xmlContent) {
        try {
            String jwt = extractStringValue(xmlContent, "TOKEN");
            if (jwt == null || jwt.isEmpty()) {
                jwt = extractStringValue(xmlContent, "jwt");
            }

            String secret = extractStringValue(xmlContent, "SECRET");
            if (secret == null || secret.isEmpty()) {
                secret = extractStringValue(xmlContent, "secret");
            }

            if (jwt != null && !jwt.isEmpty()) {
                JSONObject json = new JSONObject();
                json.put("jwt", jwt);
                if (secret != null && !secret.isEmpty()) {
                    json.put("secret", secret);
                }
                return json.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse SharedPreferences XML", e);
        }
        return null;
    }

    private String extractStringValue(String xml, String key) {
        try {
            int startIndex = xml.indexOf("name=\"" + key + "\"");
            if (startIndex == -1) {
                return null;
            }

            int valueStart = xml.indexOf(">", startIndex);
            if (valueStart == -1) {
                int attrStart = xml.indexOf("value=\"", startIndex);
                if (attrStart != -1) {
                    int attrEnd = xml.indexOf("\"", attrStart + 7);
                    if (attrEnd != -1) {
                        return xml.substring(attrStart + 7, attrEnd);
                    }
                }
                return null;
            }

            int valueEnd = xml.indexOf("<", valueStart);
            if (valueEnd == -1) {
                return null;
            }

            return xml.substring(valueStart + 1, valueEnd).trim();
        } catch (Exception e) {
            return null;
        }
    }

    private login.UserBody parseTokenJson(String tokenJson) {
        try {
            JSONObject json = new JSONObject(tokenJson);
            login.UserBody userBody = new login.UserBody();
            userBody.jwt = json.optString("jwt", "");
            userBody.secret = json.optString("secret", "");
            return (userBody.jwt != null && !userBody.jwt.isEmpty()) ? userBody : null;
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse token JSON", e);
            return null;
        }
    }

    private void syncTokenToCurrentApp(login.UserBody userBody) {
        if (userBody == null || userBody.jwt == null) {
            return;
        }
        try {
            Modules.LoginPrefs prefs = new Modules.LoginPrefs(context);
            prefs.saveUserBody(userBody);
            Log.d(TAG, "UserBody synced to current app");
        } catch (Exception e) {
            Log.e(TAG, "Failed to sync token", e);
        }
    }

    private String executeRootCommand(String command) throws IOException, InterruptedException {
        return executeRootCommandWithRetry(command, 2);
    }

    private String executeRootCommandWithRetry(String command, int maxRetries)
            throws IOException, InterruptedException {
        IOException lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return executeRootCommandInternal(command);
            } catch (IOException e) {
                lastException = e;
                String msg = e.getMessage() == null ? "" : e.getMessage();
                // 文件不存在是确定性结果，不重试，直接返回上层处理
                if (msg.contains("No such file or directory")) {
                    throw e;
                }
                Log.w(TAG, "Attempt " + attempt + " failed: " + msg);
                if (attempt < maxRetries) {
                    Thread.sleep(500);
                }
            }
        }
        throw lastException != null ? lastException :
            new IOException("Failed to execute root command");
    }

    private String executeRootCommandInternal(String command) throws IOException, InterruptedException {
        Log.d(TAG, "executeRootCommandInternal: original command = " + command);

        Process process = Runtime.getRuntime().exec("su");
        java.io.DataOutputStream os = new java.io.DataOutputStream(process.getOutputStream());
        final String marker = "__CG_RC__";

        // 在同一个 su 会话中执行命令并输出命令返回码
        os.writeBytes(command + " 2>&1\n");
        os.writeBytes("echo " + marker + "$?\n");
        os.writeBytes("exit\n");
        os.flush();
        os.close();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        int commandExitCode = Integer.MIN_VALUE;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith(marker)) {
                String codeText = line.substring(marker.length()).trim();
                try {
                    commandExitCode = Integer.parseInt(codeText);
                } catch (NumberFormatException e) {
                    commandExitCode = -1;
                }
                break;
            }
            output.append(line).append("\n");
        }
        reader.close();

        int suExitCode = process.waitFor();
        String result = output.toString().trim();

        Log.d(TAG, "executeRootCommand: suExit=" + suExitCode
                + " cmdExit=" + commandExitCode
                + " stdout_len=" + result.length());

        if (commandExitCode != 0) {
            throw new IOException("su command failed. cmdExit=" + commandExitCode
                    + ", suExit=" + suExitCode
                    + (result.isEmpty() ? "" : ", output=" + result));
        }

        return result;
    }

    private String shellQuote(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                   .replace("$", "\\$")
                   .replace("`", "\\`")
                   .replace("\"", "\\\"");
    }

    private boolean checkRootAccess() {
        try {
            String idResult = executeRootCommand("id");
            return idResult.contains("uid=0");
        } catch (Exception e) {
            Log.d(TAG, "Device is not rooted: " + e.getMessage());
            return false;
        }
    }

    public interface LoginCallback {
        void onSuccess(login.UserBody userBody);
        void onError(String errorMessage);
    }
}

