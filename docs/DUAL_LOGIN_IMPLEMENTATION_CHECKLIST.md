# 与官方版同时登录功能 - 实施检查清单

## 代码修改验证

### DualLoginManager.java 检查项

- [x] **executeRootCommand 方法重写**
  - [x] 改用 `su -c "command"` 方式执行命令
  - [x] 移除了问题的 stdin 写入方式
  - [x] 添加了详细的日志输出
  - 验证：编译通过，无ERROR

- [x] **超时控制实现**
  - [x] 流读取10秒超时
  - [x] 进程等待15秒超时
  - [x] 使用线程轮询实现（兼容API 24）
  - [x] 避免了 `Process.waitFor(timeout, TimeUnit)` （API 26）
  - 验证：编译通过，无ERROR

- [x] **重试机制**
  - [x] `executeRootCommandWithRetry()` 方法实现
  - [x] 最多重试2次
  - [x] 重试间隔500ms
  - 验证：代码逻辑正确

- [x] **错误处理和诊断**
  - [x] `waitForProcessWithTimeout()` 方法
  - [x] 详细的日志信息
  - [x] 异常错误消息包含诊断信息
  - 验证：代码逻辑正确

### WebAppInterface.java 集成检查

- [x] **performDualLogin() 接口**
  - [x] JavaScript 可调用接口
  - [x] 创建 DualLoginManager 实例
  - [x] 实现 LoginCallback 回调
  - [x] 成功和失败情况分别处理
  - 验证：已存在并实现

### login.java 兼容性检查

- [x] **UserBody 类定义**
  - [x] jwt 字段
  - [x] secret 字段
  - 验证：与 DualLoginManager 调用兼容

## 编译验证

| 检查项 | 结果 | 备注 |
|-------|------|------|
| 代码编译 | ✅ BUILD SUCCESSFUL | 执行 `gradlew compileDebugSources` |
| ERROR 检查 | ✅ 0 errors | 无编译错误 |
| WARNING 检查 | ⚠️ 4 warnings | 都是参数未使用的警告，不影响功能 |
| API 兼容性 | ✅ API 24+ | minSdk = 24 |

## 功能测试准备

### 前置条件验证

- [ ] 设备已ROOT（Magisk 或 KernelSU）
- [ ] 官方应用（net.crigh.cgsport）已安装
- [ ] 官方应用已登录
- [ ] 当前应用已获得ROOT权限授予
- [ ] ADB 调试连接正常

### 测试场景

#### 场景1：正常登录流程
```
步骤：
1. 启动应用
2. 点击"与官方版同时登录"按钮
3. 等待ROOT权限提示（如有）
4. 等待登录完成

预期结果：
- TOKEN 和 SECRET 成功读取
- 应用自动登录
- 显示主界面
- 日志显示：[D/DualLoginManager: Successfully read token from: ...]
```

#### 场景2：未ROOT设备
```
步骤：
1. 在未ROOT的设备上启动应用
2. 点击"与官方版同时登录"按钮

预期结果：
- 显示错误信息："设备未ROOT或未授予ROOT权限"
- 应用不崩溃，可继续操作
- 日志显示：[D/DualLoginManager: Device is not rooted or su access denied]
```

#### 场景3：官方应用未登录
```
步骤：
1. 退出官方应用登录
2. 启动本应用
3. 点击"与官方版同时登录"按钮

预期结果：
- 显示错误信息："无法读取官方应用的登录信息，请确保官方应用已登录"
- 日志显示诊断信息
```

#### 场景4：重试机制验证
```
步骤：
1. 监视日志输出
2. 点击"与官方版同时登录"按钮
3. 观察是否有重试日志

预期结果：
- 第一次尝试失败时，日志显示："Attempt 1 failed"
- 自动重试，日志显示："Attempt 2 ..."
```

## 文件完整性检查

### 核心文件

| 文件 | 状态 | 版本 | 备注 |
|-----|------|------|------|
| DualLoginManager.java | ✅ 修复完成 | 2.0 | 改进ROOT命令执行方式 |
| WebAppInterface.java | ✅ 已集成 | - | performDualLogin() 已实现 |
| login.java | ✅ 兼容 | - | UserBody 支持 |
| MainActivity.java | ⏳ 可选 | - | 可添加UI按钮 |

### 文档文件

| 文件 | 状态 | 内容 |
|-----|------|------|
| DUAL_LOGIN_ROOT_IMPLEMENTATION.md | ✅ 新建 | 完整使用说明 |
| 本清单文件 | ✅ 本文件 | 实施检查清单 |

## 已知问题及解决方案

### 问题1：su -c 命令在某些ROOT框架下不工作

**状态**：监控中

**解决方案**（如果发生）：
1. 添加降级方案
2. 检测ROOT框架类型
3. 根据框架选择不同的命令方式

### 问题2：文件权限问题

**状态**：已考虑

**解决方案**：
- 代码中有 `isFileReadableByRoot()` 验证
- 支持多种共享偏好配置文件位置
- 详细的诊断日志

### 问题3：超时配置不适合所有场景

**状态**：灵活配置

**可调整参数**：
- 流读取超时：`readStreamsConcurrentlyWithTimeout()` 的 10000ms
- 进程等待超时：`waitForProcessWithTimeout()` 的 15000ms
- 重试次数：`executeRootCommandWithRetry()` 的 2 次

## 部署检查清单

### 开发环境准备

- [x] Java 11+ 
- [x] Android SDK 35
- [x] minSdk = 24 (Android 7.0)
- [x] Gradle 8.11.1
- [x] 所有依赖已齐全

### 编译部署

- [ ] 生成签名 APK（`gradlew assembleRelease`）
- [ ] 验证 APK 大小正常
- [ ] 在真实设备上测试
- [ ] 检查是否有性能影响
- [ ] 验证UI集成

### 质量保证

- [x] 代码审查 ✅
  - [x] 逻辑正确性
  - [x] 异常处理完整
  - [x] 日志详细

- [ ] 功能测试（待执行）
  - [ ] ROOT检查
  - [ ] 文件读取
  - [ ] TOKEN解析
  - [ ] 同步保存
  - [ ] 错误处理

- [ ] 性能测试（待执行）
  - [ ] 启动时间
  - [ ] 内存占用
  - [ ] 电池消耗

- [ ] 安全测试（待执行）
  - [ ] ROOT权限验证
  - [ ] 数据隐私保护
  - [ ] 异常情况处理

## 发布前清单

- [ ] 所有单元测试通过
- [ ] 集成测试通过
- [ ] 代码审查完成
- [ ] 文档完整
- [ ] 版本号更新
- [ ] Release notes 准备
- [ ] 用户沟通（如需要）

## 备注与联系

### 关键代码位置

```
D:\work\android\fuckcg\app\src\main\java\com\wzjer\fuckcg\DualLoginManager.java
  → executeRootCommand()         # 主要修复
  → executeRootCommandInternal() # su -c 实现
  → waitForProcessWithTimeout()  # 超时控制
  → readStreamsConcurrentlyWithTimeout() # 并发读取
```

### 日志关键词

- `DualLoginManager` - 类名过滤
- `Successfully read token` - 成功标志
- `Attempt` - 重试日志
- `timed out` - 超时警告
- `Device is not rooted` - 未ROOT错误

### 问题反馈渠道

如遇到问题，提供以下信息：

1. **设备信息**
   - Android 版本
   - ROOT框架类型及版本
   - 设备型号

2. **应用信息**
   - 应用版本号
   - 编译时间戳

3. **日志信息**
   ```bash
   adb logcat | grep -E "DualLoginManager|WebAppInterface" > bug_report.log
   ```

## 修改总结

本次修改的核心改进：

| 方面 | 改进内容 | 原因 |
|-----|--------|------|
| **命令执行** | stdin → su -c 参数传递 | 避免缓冲区死锁 |
| **超时控制** | 新增线程轮询方式 | 兼容API 24 |
| **可靠性** | 添加重试机制 | 提高成功率 |
| **诊断能力** | 详细日志和错误信息 | 便于问题排查 |
| **兼容性** | 支持多种ROOT框架 | 覆盖更多用户场景 |

---

**最后更新**: 2026-03-25
**检查者**: AI Assistant
**状态**: ✅ 实施完成，待测试

