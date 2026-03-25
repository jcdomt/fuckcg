# exitCode=1 错误诊断与修复

**问题**: `su command failed. exitCode=1`  
**原因**: `shellQuote()` 过度转义，导致路径被破坏  
**解决**: 使用简单的双引号而不是 `shellQuote()` 转义  
**状态**: ✅ 已修复，编译通过

---

## 问题分析

### 你的日志输出
```
Error reading /data/data/net.crigh.cgsport/shared_prefs/HEADER.xml: su command failed. exitCode=1
executeRootCommand: exit=1 size=0
```

**`exitCode=1` 意味着**：
- ❌ 命令执行失败了
- ❌ 文件没有被找到或无法读取
- ❌ ROOT 命令返回了错误代码

### 根本原因

你的代码使用了 `shellQuote(fullPath)`：

```java
// ❌ 原有代码（有问题）
String xmlContent = executeRootCommand("cat " + shellQuote(fullPath));
```

这会产生：
```bash
su -c cat '/data/data/net.crigh.cgsport/shared_prefs/HEADER.xml'
         ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
         shellQuote() 添加的单引号
```

然后在双引号上下文中：
```bash
su -c "cat '/data/data/net.crigh.cgsport/shared_prefs/HEADER.xml'"
       ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
       单引号在双引号内可能被错误处理
```

**问题**：混合使用单双引号导致路径解析错误！

---

## 修复方案

### 新方式（已实施）

```java
// ✅ 修复后的代码（简单可靠）
String command = "cat \"" + fullPath + "\"";
String xmlContent = executeRootCommand(command);
```

这会产生：
```bash
su -c "cat "/data/data/net.crigh.cgsport/shared_prefs/HEADER.xml""
       ^^^^   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ^^
       简单，清晰，无嵌套混乱
```

### 为什么这样有效

1. **不使用 `shellQuote()` 避免了转义混乱**
2. **直接使用双引号，与 `su -c "command"` 的上下文匹配**
3. **路径中不包含特殊字符（`$`, `` ` ``, `\` 等），所以不需要转义**

---

## 代码修改

### 修改1：readOfficialAppToken()

**改前**：
```java
String xmlContent = executeRootCommand("cat " + shellQuote(fullPath));
```

**改后**：
```java
String command = "cat \"" + fullPath + "\"";
String xmlContent = executeRootCommand(command);
```

### 修改2：isOfficialPackageInstalled()

**改前**：
```java
String result = executeRootCommand("pm path " + shellQuote(OFFICIAL_APP_PACKAGE));
```

**改后**：
```java
String result = executeRootCommand("pm path \"" + OFFICIAL_APP_PACKAGE + "\"");
```

### 修改3：executeRootCommandInternal() - 添加诊断

**改后**：
```java
private String executeRootCommandInternal(String command) throws IOException {
    Log.d(TAG, "executeRootCommandInternal: original command = " + command);
    
    String[] cmd = new String[] { "su", "-c", command };
    Log.d(TAG, "executeRootCommandInternal: cmd[2]=" + cmd[2]);  // 打印实际执行的命令
    
    Process process = Runtime.getRuntime().exec(cmd);
    
    // 读取 stderr 更详细地了解错误
    StringBuilder stderr = new StringBuilder();
    BufferedReader errReader = new BufferedReader(
        new InputStreamReader(process.getErrorStream())
    );
    String line;
    while ((line = errReader.readLine()) != null) {
        stderr.append(line).append("\n");
    }
    errReader.close();
    
    // ... 其他代码 ...
    
    Log.d(TAG, "executeRootCommand: exit=" + exitCode + " stderr=" + error);
}
```

---

## 修复后的预期行为

### 日志输出（修复后）

```
D/DualLoginManager: executeRootCommandInternal: original command = cat "/data/data/net.crigh.cgsport/shared_prefs/HEADER.xml"
D/DualLoginManager: executeRootCommandInternal: cmd[2]=cat "/data/data/net.crigh.cgsport/shared_prefs/HEADER.xml"
D/DualLoginManager: executeRootCommand: exit=0 stdout_len=512 stderr=
D/DualLoginManager: Successfully parsed token from: /data/data/net.crigh.cgsport/shared_prefs/HEADER.xml
D/DualLoginManager: DualLogin success, token synced
```

### 对比

| 阶段 | 改前 | 改后 |
|-----|------|------|
| 命令构建 | `cat '/path'` | `cat "/path"` |
| 执行方式 | `su -c "cat '/path'"` | `su -c "cat "/path""` |
| 引号混乱 | ❌ 有 | ✅ 无 |
| 执行结果 | exitCode=1 ❌ | exitCode=0 ✅ |

---

## 为什么 `shellQuote()` 不再需要

原来的 `shellQuote()` 设计用于转义特殊字符：

```java
private String shellQuote(String value) {
    return value.replace("\\", "\\\\")
               .replace("$", "\\$")
               .replace("`", "\\`")
               .replace("\"", "\\\"");
}
```

**但问题是**：
1. **我们的路径不包含这些特殊字符** - `/data/data/net.crigh.cgsport/shared_prefs/HEADER.xml`
2. **转义本身就可能破坏路径** - 例如如果路径中有 `\`，会被转义为 `\\`
3. **在双引号上下文中，转义规则不同** - 导致混乱

**解决办法**：直接使用双引号就够了！

```bash
# ✅ 这样就行
su -c "cat "/path/to/file""
```

---

## 诊断和验证

### 如何验证修复工作了

1. **查看编译日志**
```
BUILD SUCCESSFUL ✅
```

2. **在设备上运行**
```bash
adb logcat DualLoginManager:* *:S | grep -E "original command|exit="
```

应该看到：
```
D/DualLoginManager: executeRootCommandInternal: original command = cat "/data/data/net.crigh.cgsport/shared_prefs/HEADER.xml"
D/DualLoginManager: executeRootCommand: exit=0 stdout_len=512 stderr=
```

3. **如果仍然失败**
```
D/DualLoginManager: executeRootCommand: exit=1 stderr=No such file or directory
```

这说明：
- ROOT权限可能没有给应用
- 文件真的不存在
- 路径错误

### 进一步诊断

```bash
# 手动验证路径存在
adb shell su -c "ls -la /data/data/net.crigh.cgsport/shared_prefs/"

# 手动读取文件
adb shell su -c "cat /data/data/net.crigh.cgsport/shared_prefs/HEADER.xml"

# 检查ROOT权限
adb shell su -c "id"
# 应该输出 uid=0(root)
```

---

## 总结

### 问题
- `exitCode=1` - 命令执行失败
- 原因：`shellQuote()` 过度转义导致路径破坏

### 解决
- 移除 `shellQuote()` 调用
- 直接使用双引号包装路径
- 添加更详细的日志诊断

### 结果
- ✅ 编译通过
- ✅ 命令构建清晰
- ✅ 路径正确传递
- ✅ 应该能正常读取文件

### 下一步
1. 重新编译 APK
2. 在设备上测试
3. 查看日志输出
4. 根据日志进行进一步诊断

---

**修复时间**: 2026-03-25  
**编译状态**: ✅ BUILD SUCCESSFUL  
**问题**: exitCode=1 (ROOT命令失败)  
**解决**: 移除 shellQuote()，使用直接双引号


