package com.wzjer.fuckcg;

import android.content.Context;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONObject;

/**
 * JavaScript Bridge for WebView
 * Provides methods that can be called from JavaScript
 */
@SuppressWarnings("unused")
public class WebAppInterface {
    private final Context mContext;
    private final WebView webView;

    public WebAppInterface(Context context, WebView webView) {
        mContext = context.getApplicationContext();
        this.webView = webView;
        markBridgeMethodsReferencedForAnalysis();
    }

    private void markBridgeMethodsReferencedForAnalysis() {
        // These methods are invoked reflectively by WebView JavaScript bridge.
        if (System.currentTimeMillis() < 0) {
            isLogin();
            setLogin(false);
            isJwtValid();
            getAuthState();
            getCookies();
            getJSessionId();
            startOAuthPage();
            performDualLogin();
            logout();
            buildUploadJsonSports("", "");
            requestSportId("", "");
            submitSportsData("{}");
            getSportRecords("{}");
            getSportRecordsAsync("{}", "req_0");
            applyCredentials("{}");
        }
    }

    /**
     * Check if user is logged in
     * Can be called from JavaScript as: Bridge.isLogin()
     * @return true if logged in, false otherwise
     */
    //noinspection unused
    @SuppressWarnings("unused")
    @JavascriptInterface
    public boolean isLogin() {
        boolean isLoggedIn = login.hasValidSavedLogin(mContext);
        android.util.Log.d("WebAppInterface", "isLogin() called, returning: " + isLoggedIn);
        return isLoggedIn;
    }

    /**
     * Set login status
     * Can be called from JavaScript as: Bridge.setLogin(true)
     * @param isLoggedIn login status
     */
    //noinspection unused
    @SuppressWarnings("unused")
    @JavascriptInterface
    public void setLogin(boolean isLoggedIn) {
        Modules.LoginPrefs prefs = new Modules.LoginPrefs(mContext);
        if (isLoggedIn) {
            prefs.setLoggedIn(true);
        } else {
            prefs.clearAuth();
        }
        android.util.Log.d("WebAppInterface", "setLogin() called with: " + isLoggedIn);
    }

    //noinspection unused
    @SuppressWarnings("unused")
    @JavascriptInterface
    public boolean isJwtValid() {
        return login.hasValidSavedLogin(mContext);
    }

    //noinspection unused
    @SuppressWarnings("unused")
    @JavascriptInterface
    public String getAuthState() {
        try {
            Modules.LoginPrefs prefs = new Modules.LoginPrefs(mContext);
            login.UserBody userBody = prefs.loadUserBody();
            boolean jwtValid = login.hasValidSavedLogin(mContext);

            JSONObject json = new JSONObject();
            json.put("isLoggedIn", jwtValid);
            json.put("jwtValid", jwtValid);
            json.put("jsessionId", nullToEmpty(prefs.getSavedJSessionId()));
            json.put("jwt", userBody != null ? nullToEmpty(userBody.jwt) : "");
            json.put("secret", userBody != null ? nullToEmpty(userBody.secret) : "");
            json.put("hasCredentials", userBody != null && userBody.jwt != null && userBody.secret != null);
            return json.toString();
        } catch (Exception e) {
            android.util.Log.e("WebAppInterface", "getAuthState() failed", e);
            return "{\"isLoggedIn\":false,\"jwtValid\":false,\"jwt\":\"\",\"secret\":\"\",\"hasCredentials\":false}";
        }
    }

    /**
     * Get saved cookies
     * Can be called from JavaScript as: Bridge.getCookies()
     * @return saved cookies string or empty string if none
     */
    //noinspection unused
    @SuppressWarnings("unused")
    @JavascriptInterface
    public String getCookies() {
        Modules.LoginPrefs prefs = new Modules.LoginPrefs(mContext);
        String cookies = prefs.getSavedCookies();
        android.util.Log.d("WebAppInterface", "getCookies() called, returning: " + cookies);
        return cookies;
    }

    /**
     * Get JSESSIONID
     * Can be called from JavaScript as: Bridge.getJSessionId()
     * @return JSESSIONID value or empty string if none
     */
    //noinspection unused
    @SuppressWarnings("unused")
    @JavascriptInterface
    public String getJSessionId() {
        Modules.LoginPrefs prefs = new Modules.LoginPrefs(mContext);
        String jsessionId = prefs.getSavedJSessionId();
        android.util.Log.d("WebAppInterface", "getJSessionId() called, returning: " + jsessionId);
        return jsessionId == null ? "" : jsessionId;
    }

    /**
     * Start OAuth login page
     * Can be called from JavaScript as: Bridge.startOAuthPage()
     * @return current saved JSESSIONID, or empty string if not available yet
     */
    //noinspection unused
    @SuppressWarnings("unused")
    @JavascriptInterface
    public String startOAuthPage() {
        android.util.Log.d("WebAppInterface", "startOAuthPage() called from JavaScript");
        String jsessionId = login.startOAuthPage();
        return jsessionId == null ? "" : jsessionId;
    }

    /**
     * Perform dual login with official app (requires ROOT)
     * Can be called from JavaScript as: Bridge.performDualLogin()
     * This will try to read login info from the official app and sync to current app
     */
    //noinspection unused
    @SuppressWarnings("unused")
    @JavascriptInterface
    public void performDualLogin() {
        android.util.Log.d("WebAppInterface", "performDualLogin() called from JavaScript");

        DualLoginManager manager = new DualLoginManager(mContext);
        manager.performDualLogin(new DualLoginManager.LoginCallback() {
            @Override
            public void onSuccess(login.UserBody userBody) {
                android.util.Log.d("WebAppInterface", "DualLogin success");
                if (webView != null) {
                    webView.post(() -> {
                        if (webView != null) {
                            webView.evaluateJavascript(
                                "javascript:window.onDualLoginSuccess && window.onDualLoginSuccess(true)",
                                null
                            );
                        }
                    });
                }
            }

            @Override
            public void onError(String errorMessage) {
                android.util.Log.e("WebAppInterface", "DualLogin error: " + errorMessage);
                if (webView != null) {
                    webView.post(() -> {
                        if (webView != null) {
                            String escapedError = errorMessage.replace("'", "\\'").replace("\"", "\\\"");
                            webView.evaluateJavascript(
                                "javascript:window.onDualLoginError && window.onDualLoginError('" + escapedError + "')",
                                null
                            );
                        }
                    });
                }
            }
        });
    }

    //noinspection unused
    @SuppressWarnings("unused")
    @JavascriptInterface
    public void logout() {
        login.clearSavedAuth(mContext);
        if (webView != null) {
            webView.post(() -> webView.loadUrl("file:///android_asset/web/login.html"));
        }
    }

    //noinspection unused
    @SuppressWarnings("unused")
    @JavascriptInterface
    public String buildUploadJsonSports(String studentId, String studentName) {
        return work.buildUploadJsonSportsJson(mContext, studentId, studentName, 0L);
    }

    //noinspection unused
    @SuppressWarnings("unused")
    @JavascriptInterface
    public String buildUploadJsonSports(String studentId, String studentName, String fetchedAtMsStr) {
        long fetchedAtMs = 0L;
        try {
            fetchedAtMs = Long.parseLong(fetchedAtMsStr == null ? "0" : fetchedAtMsStr.trim());
        } catch (NumberFormatException ignored) {}
        return work.buildUploadJsonSportsJson(mContext, studentId, studentName, fetchedAtMs);
    }

    //noinspection unused
    @SuppressWarnings("unused")
    @JavascriptInterface
    public String requestSportId(String studentId, String studentName) {
        return work.requestSportIdJson(mContext, studentId, studentName);
    }

    //noinspection unused
    @SuppressWarnings("unused")
    @JavascriptInterface
    public String submitSportsData(String jsonData) {
        try {
            return http.postSportsData("/api/l/v7/savesports", jsonData, mContext);
        } catch (Exception e) {
            android.util.Log.e("WebAppInterface", "submitSportsData failed", e);
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    //noinspection unused
    @SuppressWarnings("unused")
    @JavascriptInterface
    public String getSportRecords(String queryJson) {
        return work.getSportRecordsJson(mContext, queryJson);
    }

    @JavascriptInterface
    public void getSportRecordsAsync(String queryJson, String requestId) {
        final String safeRequestId = requestId == null ? "" : requestId;
        new Thread(() -> {
            String result = work.getSportRecordsJson(mContext, queryJson);
            if (webView == null) {
                return;
            }
            String script = "window.onSportRecordsResult && window.onSportRecordsResult(" +
                    JSONObject.quote(safeRequestId) + "," + JSONObject.quote(result) + ")";
            webView.post(() -> webView.evaluateJavascript(script, null));
        }).start();
    }

    //noinspection unused
    @SuppressWarnings("unused")
    @JavascriptInterface
    public String applyCredentials(String jsonText) {
        try {
            JSONObject json = new JSONObject(jsonText == null ? "{}" : jsonText);
            String jwt = json.optString("jwt", "").trim();
            String secret = json.optString("secret", "").trim();

            if (jwt.isEmpty() || secret.isEmpty()) {
                return "{\"error\":\"jwt 和 secret 不能为空\"}";
            }
            if (Modules.LoginParser.isJwtExpired(jwt)) {
                return "{\"error\":\"jwt 已过期或无效\"}";
            }

            login.UserBody userBody = new login.UserBody();
            userBody.jwt = jwt;
            userBody.secret = secret;

            Modules.LoginPrefs prefs = new Modules.LoginPrefs(mContext);
            prefs.clearAuth();
            prefs.saveUserBody(userBody);

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("jwtValid", true);
            result.put("message", "凭据已更新");
            return result.toString();
        } catch (Exception e) {
            android.util.Log.e("WebAppInterface", "applyCredentials() failed", e);
            return "{\"error\":\"applyCredentials() 处理失败\"}";
        }
    }

    private String nullToEmpty(String str) {
        return str == null ? "" : str;
    }
}
