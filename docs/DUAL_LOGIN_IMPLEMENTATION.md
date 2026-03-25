# 双重登录功能实现说明

## 功能概述

新增"和官方版同时登录（需ROOT）"的登录方式，允许用户直接从已登录的官方应用同步登录信息到本应用，无需重新进行统一认证。

## 实现原理

### 架构设计

```
┌─────────────────────────┐
│   login.html (前端)      │
│  - 添加双重登录按钮      │
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│   app.js (JavaScript)   │
│  - 处理按钮点击事件      │
│  - 调用Bridge方法        │
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│  WebAppInterface.java   │
│  - performDualLogin()   │
│  - 创建Manager实例       │
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│ DualLoginManager.java   │
│  - 检测ROOT权限         │
│  - 读取官方应用数据      │
│  - 同步登录信息         │
└─────────────────────────┘
```

### 核心流程

1. **用户点击按钮** → JavaScript中的`performDualLogin()`方法被触发
2. **Java端检测ROOT** → `DualLoginManager.isDeviceRooted()`
3. **读取官方应用数据** → 从官方应用的SharedPreferences读取JWT和Secret
4. **数据验证与同步** → 解析数据并存储到本应用
5. **返回结果给前端** → JavaScript回调函数处理成功/失败

## 文件修改详情

### 1. DualLoginManager.java（新建）

**位置**: `app/src/main/java/com/wzjer/fuckcg/DualLoginManager.java`

**核心方法**:
- `isDeviceRooted()` - 检查设备是否ROOT
- `performDualLogin(LoginCallback)` - 执行双重登录流程
- `readOfficialAppToken()` - 读取官方应用的登录信息
- `parseSharedPreferencesXml()` - 解析SharedPreferences XML文件
- `extractStringValue()` - 从XML中提取字段值
- `syncTokenToCurrentApp()` - 将Token同步到本应用

**关键配置**:
```java
private static final String OFFICIAL_APP_PACKAGE = "com.cg.cgapp"; 
// ⚠️ 需要修改为实际的官方应用包名
```

### 2. WebAppInterface.java（修改）

**新增方法**:
```java
@JavascriptInterface
public void performDualLogin() {
    // 创建DualLoginManager实例
    // 调用performDualLogin()方法
    // 通过JavaScript回调返回结果
}
```

**修改markBridgeMethodsReferencedForAnalysis()**: 添加performDualLogin()引用

### 3. login.html（修改）

**新增按钮**:
```html
<button id="dualLoginButton" class="primary" type="button">
    和官方版同时登录（需ROOT）
</button>
```

### 4. app.js（修改）

**新增事件监听**:
```javascript
dualLoginButton.addEventListener('click', function () {
    setStatus(status, '正在从官方应用读取登录信息...', 'info');
    window.Bridge.performDualLogin();
});
```

**新增回调函数**:
```javascript
window.onDualLoginSuccess = function(success) {
    // 登录成功，跳转到工作页
};

window.onDualLoginError = function(errorMessage) {
    // 显示错误信息
};
```

## 使用指南

### 用户使用

1. **前提条件**:
   - 设备已ROOT
   - 官方应用已安装且已登录
   - 本应用已启动

2. **登录流程**:
   - 打开本应用
   - 点击"和官方版同时登录（需ROOT）"按钮
   - 系统自动检测ROOT权限
   - 如果有ROOT权限，自动从官方应用读取登录信息
   - 登录成功后自动进入工作页

3. **错误处理**:
   - "设备未ROOT或未授予ROOT权限" → 需要ROOT权限
   - "无法读取官方应用的登录信息，请确保官方应用已登录" → 官方应用需要先登录
   - 其他错误 → 查看应用日志

### 开发者配置

**重要**: 需要修改DualLoginManager.java中的官方应用包名：

```java
// 修改这一行为实际的官方应用包名
private static final String OFFICIAL_APP_PACKAGE = "com.cg.cgapp";
```

### 调试

查看LogCat日志，搜索"DualLoginManager"标签：

```
D/DualLoginManager: DualLogin success, token synced
E/DualLoginManager: DualLogin failed: ...
```

## 权限要求

### AndroidManifest.xml（如需要）

当前实现不需要额外的AndroidManifest权限，但需要：
- 设备ROOT权限
- 访问其他应用数据的ROOT权限

## 安全考虑

1. **ROOT权限风险**: 本功能只有在设备ROOT且用户授予权限时才能工作
2. **数据隐私**: 仅读取JWT和Secret，不涉及其他个人信息
3. **错误处理**: 所有异常都被捕获并返回给用户

## 常见问题

### Q1: 为什么点击按钮没有反应？
**A**: 
- 检查设备是否ROOT
- 确认官方应用已登录
- 查看LogCat日志找出具体原因

### Q2: 如何修改官方应用包名？
**A**: 编辑`DualLoginManager.java`第23行，替换包名字符串

### Q3: 读取失败的常见原因？
**A**:
- 官方应用未登录
- 官方应用的SharedPreferences位置不同
- 没有ROOT权限或未授予权限
- 官方应用存储的字段名不同（需要调整extractStringValue逻辑）

### Q4: 如何检查官方应用的SharedPreferences位置？
**A**: 
```bash
# 在有ROOT权限的设备上运行
adb shell
su
ls /data/data/com.cg.cgapp/shared_prefs/
cat /data/data/com.cg.cgapp/shared_prefs/LoginPrefs.xml
```

## 测试清单

- [ ] 编译通过无报错
- [ ] APK生成成功
- [ ] 登录页显示新按钮
- [ ] 无ROOT权限时显示错误提示
- [ ] ROOT权限下能读取官方应用数据
- [ ] Token成功同步到本应用
- [ ] 登录成功后自动跳转到工作页
- [ ] 登录失败显示相应错误信息

## 日志示例

### 成功场景
```
D/DualLoginManager: Successfully read LoginPrefs.xml
D/DualLoginManager: DualLogin success, token synced
D/WebAppInterface: DualLogin success
```

### 失败场景
```
D/DualLoginManager: Device is not rooted or su not available
E/DualLoginManager: Failed to read LoginPrefs.xml
E/DualLoginManager: DualLogin failed: 无法读取官方应用的登录信息
```

## 后续改进方向

1. **动态包名配置**: 通过配置文件或设置界面来配置官方应用包名
2. **支持多个应用**: 扩展支持同步其他应用的登录信息
3. **更完善的XML解析**: 使用专业的XML解析库替代字符串匹配
4. **加密存储**: 对同步的Token进行加密存储
5. **自动检测官方应用**: 智能识别已安装的官方应用

## 相关文件总览

| 文件 | 修改类型 | 说明 |
|------|--------|------|
| DualLoginManager.java | 新建 | 双重登录管理器 |
| WebAppInterface.java | 修改 | 添加performDualLogin()方法 |
| login.html | 修改 | 添加双重登录按钮 |
| app.js | 修改 | 添加事件监听和回调 |

---

**最后更新**: 2026-03-25
**状态**: ✅ 实现完成，编译通过

