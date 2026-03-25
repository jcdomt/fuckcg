# 运动记录详情功能 - 快速参考

## 🚀 快速开始

### 用户使用流程
```
1. 打开"运动记录" tab
2. 看到运动记录列表（从近到远排序）
3. 点击任意记录卡片
4. 进入详情页面查看完整信息
5. 点击"← 返回"或浏览器后退返回列表
```

## 📁 新增文件清单

| 文件 | 说明 | 行数 |
|------|------|------|
| `record-detail.html` | 详情页面 | 450+ |
| `record-detail.js` | 详情模块 | 100+ |
| `RECORD_DETAIL_README.md` | 功能说明 | 本文件 |

## 🔧 修改的文件清单

| 文件 | 修改说明 |
|------|---------|
| `work.html` | 添加 script 标签引入 record-detail.js |
| `app.js` | renderRecords() 添加点击事件处理 |
| `style.css` | 添加详情页面样式 + 列表卡片悬停效果 |

## 💾 数据流

### 列表 → 详情
```javascript
// 用户点击列表卡片
card.addEventListener('click', function() {
    window.RecordDetailModule.openDetail(item);
});

// 模块处理
RecordDetailModule.openDetail = function(record) {
    sessionStorage.setItem('currentRecord', JSON.stringify(record));
    window.location.href = 'record-detail.html';
};
```

### 详情 → 列表
```javascript
// 用户点击返回按钮
backButton.addEventListener('click', function() {
    sessionStorage.removeItem('currentRecord');
    window.history.back();
});
```

## 🎨 UI 布局

### 列表卡片（可点击）
- 顶部：时间 + 状态标签
- 中间：5 个关键数据（距离、时长、速度、配速、步数）
- 底部：提示信息
- 效果：悬停时有蓝光 + 上浮动画

### 详情页面（4 个卡片）
1. **时间信息** - 开始/结束/时长
2. **运动数据** - 距离/速度/配速/步数/卡路里
3. **审核状态** - 有效性/状态代码/提示
4. **路线信息** - 路线名称/类型/ID

## 🔑 关键方法

### RecordDetailModule
```javascript
// 打开详情页面
RecordDetailModule.openDetail(record)

// 关闭详情页面
RecordDetailModule.closeDetail()

// 格式化记录
RecordDetailModule.formatRecord(record)

// 获取状态文字
RecordDetailModule.getStatusText(record)  // "有效" / "待审核" / "无效"

// 获取状态样式类
RecordDetailModule.getStatusClass(record)  // "valid" / "pending" / "invalid"

// 生成摘要
RecordDetailModule.getSummary(record)  // "03-25 13:07 ~ 13:19 | 2km | 有效"
```

## 🎯 状态颜色

| 状态 | 颜色 | 条件 |
|------|------|------|
| 有效 | 🟢 绿色 | isValid === '1' |
| 待审核 | 🟡 黄色 | checkStatus === '1' |
| 无效 | 🔴 红色 | 其他 |

## 📊 数据结构

```javascript
// 前端接收的记录对象
{
    recordId: "3517474",
    beginTime: "2026-03-25 13:26:12",
    endTime: "2026-03-25 13:36:41",
    activeTime: "00:10:28",
    distance: "2.08",
    calorie: "111.7",
    avgSpeed: "11.9",
    avgPace: "05'03''",
    stepCount: "2894",
    tip: "提交成功，待审核",
    isValid: "1",          // "1" = 有效，"-1" = 无效
    checkStatus: "0",      // "0" = 已审核，"1" = 待审核，"-2" = 其他
    planRouteName: "校内定向线路",
    subType: "1"
}
```

## 🔌 Java 端接口

### work.java
```java
// 获取运动记录（已实现）
public static String getSportRecordsJson(Context context, String queryJson)

// 标准化单条记录（已实现）
private static JSONObject normalizeRecord(JSONObject raw)

// 排序记录（已实现）
private static void sortRecordsByTime(JSONArray items)
```

## 🧪 测试清单

### 功能测试
- [ ] 点击记录能打开详情页面
- [ ] 详情页面正确显示 4 个卡片
- [ ] 返回按钮能返回列表
- [ ] 浏览器后退正常工作

### 样式测试
- [ ] 列表卡片悬停有反馈
- [ ] 详情页面在手机上能正常显示
- [ ] 状态标签颜色正确

### 数据测试
- [ ] 记录按时间从近到远排序
- [ ] 所有字段正确显示（包括空值）

## 🚨 常见问题

### Q: 详情页面打开后数据空白
A: 检查 sessionStorage 是否被清空，确保 record-detail.html 与 work.html 在同一目录

### Q: 返回按钮不工作
A: 确保有历史记录，可以手动输入 JavaScript 测试：`window.history.back()`

### Q: 样式不生效
A: 检查 style.css 是否正确引入，确保 CSS 变量已定义

## 📦 部署检查

```bash
# ✅ 编译无错误
.\gradlew.bat :app:compileDebugJavaWithJavac

# ✅ 所有文件都在
ls app/src/main/assets/web/
# 应该看到：
# - record-detail.html ✓
# - record-detail.js ✓
# - work.html (已修改) ✓
# - app.js (已修改) ✓
# - style.css (已修改) ✓

# ✅ 安装到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk

# ✅ 测试：打开"运动记录"tab，点击任意记录
```

## 🎓 学习资源

1. **sessionStorage** - 理解页面间数据传递
2. **CORS/跨域** - 不涉及（本地文件）
3. **History API** - 浏览器前进后退机制
4. **CSS Grid** - 卡片布局
5. **字符串格式化** - 时间和数字显示

## 📞 联系方式

遇到问题？查看这些文档：
- `RECORD_DETAIL_README.md` - 完整功能说明
- `RECORD_DETAIL_IMPLEMENTATION.md` - 实现细节
- `docs/` 目录 - 其他文档

---

**版本**: 1.0  
**完成日期**: 2026-03-25  
**状态**: ✅ 生产就绪

