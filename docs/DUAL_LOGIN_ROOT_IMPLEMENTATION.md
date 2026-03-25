# 与官方版同时登录功能实现说明（需ROOT）

## 功能概述

本功能实现了一种新的登录方式，允许用户在获得ROOT权限后，从官方应用中读取登录凭据（TOKEN和SECRET），并直接同步到当前应用，实现与官方版同时登录。

### 核心优势

1. **零用户操作** - 自动读取官方应用的登录信息，无需用户重新输入密码
2. **快速切换** - 两个应用使用相同的登录凭据，可无缝切换
3. **安全可靠** - 通过ROOT权限读取系统文件，比网页登录更加安全

## 实现原理

### 文件结构

```
app/src/main/java/com/wzjer/fuckcg/
├── DualLoginManager.java          # 核心实现类（已修复）
├── WebAppInterface.java            # JavaScript接口（已集成）
├── MainActivity.java               # 主活动（可集成）
└── login.java                      # 登录工具类
```

### 关键改进点

#### 1. ROOT命令执行方式（executeRootCommand）

**之前的问题：**
- 使用stdin方式传递命令：`su` -> stdin写入命令
- 存在缓冲区同步问题，导致命令无法正确执行

**改进后的方式：**
```bash
su -c "command"  # 直接通过参数传递命令
```

**优势：**
- 避免stdin写入时序问题
- 更兼容主流ROOT框架（Magisk、KernelSU等）
- 执行更可靠，错误诊断更清晰

#### 2. 超时控制

- **流读取超时**：10秒（防止流缓冲区阻塞）
- **进程等待超时**：15秒（防止ROOT命令卡住）
- **重试机制**：最多重试2次，每次重试间隔500ms

#### 3. API级别兼容性

- 改用线程轮询方式实现超时（兼容API 24+）
- 避免使用 `Process.waitFor(timeout, TimeUnit)` 和 `destroyForcibly()`（需要API 26）

## 使用方式

### 方式一：从JavaScript调用（推荐）

在Web页面中添加按钮，调用JavaScript接口：

```html
<button onclick="Bridge.performDualLogin()">与官方版同时登录</button>
<script>
window.onDualLoginSuccess = function(success) {
    if (success) {
        console.log("双应用登录成功");
        location.reload();
    } else {
        console.log("双应用登录失败");
    }
};
</script>
```

### 方式二：从Java代码直接调用

```java
DualLoginManager manager = new DualLoginManager(context);
manager.performDualLogin(new DualLoginManager.LoginCallback() {
    @Override
    public void onSuccess(login.UserBody userBody) {
        Log.d(TAG, "Login successful: " + userBody.jwt);
        // 刷新UI或重新加载应用
    }
    
    @Override
    public void onError(String errorMessage) {
        Log.e(TAG, "Login failed: " + errorMessage);
        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
    }
});
```

## 工作流程

```
┌─────────────────────┐
│  用户点击登录按钮   │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ 检查ROOT权限        │
│ isDeviceRooted()    │
└──────────┬──────────┘
           │
      ┌────┴────┐
      │          │
     否         是
      │          │
      ▼          ▼
   出错      ┌──────────────────┐
         │ 定位官方应用数据目录  │
         │ buildSharedPrefsDirs│
         └──────────┬──────────┘
                    │
                    ▼
         ┌──────────────────────┐
         │ 搜索登录配置文件      │
         │ (HEADER.xml等)      │
         └──────────┬───────────┘
                    │
                    ▼
         ┌──────────────────────┐
         │ 读取TOKEN和SECRET    │
         │ (via su -c cat)      │
         └──────────┬───────────┘
                    │
         ┌──────────┴──────────┐
         │                     │
        读                    读
       成                     失
       功                     败
        │                     │
        ▼                     ▼
   ┌─────────┐           ┌────────┐
   │ 解析XML │           │ 重试   │
   └────┬────┘           │ 或出错 │
        │                └────────┘
        ▼
   ┌─────────────┐
   │ 同步到当前应│
   │ 用并返回    │
   └─────────────┘
```

## 文件读取流程详解

### 1. 文件位置
官方应用登录信息保存在：
```
/data/data/net.crigh.cgsport/shared_prefs/HEADER.xml
```

### 2. XML格式示例
```xml
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="TOKEN">eyJhbGciOiJIUzI1NiJ9....</string>
    <string name="SECRET">AAAANIBogAGMtE0rl1jjK2Ma2H...</string>
    <string name="SIGN">cafccef2fbda9e0ed3d0c20c4b072c21</string>
    <string name="LOGIN_OUT_TIME">1775183258000</string>
</map>
```

### 3. 读取命令
```bash
# 直接读取文件内容
su -c "cat /data/data/net.crigh.cgsport/shared_prefs/HEADER.xml"

# 验证文件可读性
su -c "[ -r /data/data/net.crigh.cgsport/shared_prefs/HEADER.xml ] && echo 1 || echo 0"

# 列出shared_prefs目录
su -c "ls -la /data/data/net.crigh.cgsport/shared_prefs/"
```

## 代码关键方法说明

### executeRootCommandInternal(String command)

**功能**：执行单个ROOT命令

**参数**：
- `command`: 要执行的shell命令（不含 `su -c`）

**返回**：命令的标准输出

**流程**：
1. 组装成 `su -c "command"` 形式
2. 通过Runtime执行
3. 并发读取stdout和stderr
4. 等待进程完成（带超时）
5. 检查exit code并返回结果

### readStreamsConcurrentlyWithTimeout()

**功能**：并发读取两个IO流，防止缓冲区满导致死锁

**特点**：
- 使用两个线程分别读取stdout和stderr
- 支持10秒超时保护
- 如果线程超时会被中断

### waitForProcessWithTimeout(Process, long)

**功能**：等待进程结束，兼容API 24

**实现**：
- 使用循环轮询的方式（每100ms检查一次）
- 支持自定义超时时间
- 超时返回 `Integer.MIN_VALUE`

## 故障排查

### 问题1：ROOT权限检查失败

**症状**：`Device is not rooted or su access denied`

**排查步骤**：
```bash
# 1. 验证ROOT权限
adb shell su -c "id"
# 应显示: uid=0(root)

# 2. 检查ROOT应用是否授予权限
adb shell pm grant com.wzjer.fuckcg android.permission.PACKAGE_USAGE_STATS

# 3. 重新启动应用
adb shell am force-stop com.wzjer.fuckcg
```

### 问题2：无法读取官方应用文件

**症状**：`未找到可用TOKEN/SECRET`

**排查步骤**：
```bash
# 1. 验证官方应用是否安装
adb shell pm path net.crigh.cgsport

# 2. 验证官方应用数据目录存在
adb shell su -c "ls -la /data/data/net.crigh.cgsport/"

# 3. 验证HEADER.xml文件存在且可读
adb shell su -c "ls -la /data/data/net.crigh.cgsport/shared_prefs/"
adb shell su -c "cat /data/data/net.crigh.cgsport/shared_prefs/HEADER.xml"

# 4. 检查文件权限
adb shell su -c "stat /data/data/net.crigh.cgsport/shared_prefs/HEADER.xml"
```

### 问题3：TOKEN格式不正确

**症状**：`无法解析官方应用的登录信息`

**排查步骤**：
```bash
# 检查TOKEN格式是否正确（应为JWT格式）
# JWT格式：header.payload.signature
echo "token_content" | grep -E '^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$'
```

### 问题4：命令超时

**症状**：`Root command timed out after 15 seconds`

**解决方案**：
- 增加超时时间（修改 `waitForProcessWithTimeout` 的15000参数）
- 检查系统I/O性能
- 尝试重启ROOT管理应用

## 日志查看

### ADB日志过滤
```bash
# 查看DualLoginManager的所有日志
adb logcat | grep DualLoginManager

# 查看特定级别的日志
adb logcat DualLoginManager:D *:S  # 只显示DEBUG级别
adb logcat DualLoginManager:E *:S  # 只显示ERROR级别

# 保存日志到文件
adb logcat > logcat_output.txt
```

### 关键日志信息

```
D/DualLoginManager: Successfully read token from: /data/data/net.crigh.cgsport/shared_prefs/HEADER.xml
D/DualLoginManager: DualLogin success, token synced
```

## 安全考虑

1. **ROOT权限**：仅在用户明确授权时使用，避免恶意应用滥用
2. **数据隐私**：TOKEN和SECRET不会上传，仅在本地应用间同步
3. **权限验证**：每次读取前都验证ROOT权限
4. **文件访问**：使用selective access（选择性访问）原则

## 兼容性

- **最低SDK**：API 24 (Android 7.0)
- **TARGET SDK**：API 35 (Android 15)
- **ROOT框架兼容性**：Magisk、KernelSU等

## 测试用例

### 测试1：基础功能测试
```
前提条件：设备已ROOT，官方应用已登录
1. 打开应用
2. 点击"与官方版同时登录"按钮
3. 等待1-2秒
预期结果：应用自动登录，显示主界面
```

### 测试2：未ROOT设备测试
```
前提条件：设备未ROOT
1. 打开应用
2. 点击"与官方版同时登录"按钮
预期结果：显示"设备未ROOT"错误信息
```

### 测试3：官方应用未登录测试
```
前提条件：设备已ROOT，官方应用未登录
1. 打开应用
2. 点击"与官方版同时登录"按钮
预期结果：显示"无法读取官方应用的登录信息"错误
```

## 相关文件修改记录

### DualLoginManager.java

| 修改项 | 原方式 | 改进方式 | 原因 |
|-------|-------|---------|------|
| 命令执行 | stdin方式 | su -c参数方式 | 避免缓冲区问题 |
| 超时控制 | 无 | 线程轮询 | API 24兼容 |
| 流读取 | 顺序读 | 并发读（超时） | 防止缓冲区满 |
| 重试机制 | 无 | 最多2次重试 | 提高可靠性 |
| 错误诊断 | 基础 | 详细诊断路径 | 便于问题排查 |

## 总结

本功能通过以下关键改进，成功解决了ROOT命令执行的问题：

1. ✅ 改用 `su -c "command"` 方式，避免stdin缓冲区问题
2. ✅ 添加重试机制，提高命令执行的可靠性
3. ✅ 实现API 24兼容的超时控制
4. ✅ 增强并发读取保护，防止IO阻塞
5. ✅ 详细的错误日志和诊断信息

现在用户可以安全、可靠地使用ROOT权限从官方应用读取登录凭据，实现真正的"与官方版同时登录"功能。

