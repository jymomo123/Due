# 如期 · Due

![CI](https://github.com/jymomo123/Due/actions/workflows/ci.yml/badge.svg)

![License](https://img.shields.io/badge/license-Apache--2.0-blue)



![minSdk](https://img.shields.io/badge/minSdk-26-3ddc84)



![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7f52ff)



![Compose](https://img.shields.io/badge/Compose-1.7.5-4285f4)

一个倒数应用。**不是**原版「倒数日」的破解或修改版，而是按它的核心交互重新写的原生实现：  
事件列表 + 天数计算 + 重复规则 + 提醒 + 小组件。

> **关于名字**  
> 「如期」取自「如期而至」。  
> 「如期」的视角是到来 —— 那件事还在路上，它会在它该在的那天到。  
> 同一个数字，两种心情。这也决定了界面的语气：没有红色告警、没有「仅剩」、没有催迫感。

- 语言/UI：Kotlin + Jetpack Compose（Material 3）
- 最低系统：Android 8.0（API 26）
- 包名：`com.offline.due`
- 依赖：无网络库、无数据库、无序列化框架、无统计 SDK

### 安装

直接下载 [**发布**](https://github.com/jymomo123/Due/releases) 里最新的 `.apk`。

没有上架任何应用商店。装之前建议核对签名——官方发布包的证书是：

```
CN=Due, OU=Offline, O=Due Studio
```

```bash
apksigner verify --print-certs 如期-x.y.z.apk
```

签名对不上的包不是官方发布的，请别装。想自己编一个？见[第二节](#二怎么跑起来)。

---

## 一、联网

这里有一条线，画得很清楚：

**核心功能，一个字节的网络都不需要。**  
所有事件、天数、提醒、小组件、备份，全部在本机完成。断网、飞行模式、  
把 SIM 卡拔了，功能一样不缺，也不会少任何一个字。

**只有一件事，而且它坏掉也不影响任何功能。**

| 唯一联网点     | 做什么                             | 失败时             |
| --------- | ------------------------------- | --------------- |
| 设置 → 网络校时 | 发一个 `HEAD` 请求，只读服务器响应头里的 `Date` | 显示「无法连接（不影响使用）」 |

它不发送任何用户数据、不带 cookie、不请求任何业务接口、不上传事件。  
更关键的是：**测出来的偏差只用于提示，不会去改你的任何一条倒数**。  
如果悄悄用网络时间覆盖本机时间，用户会遇到"倒数怎么自己变了"这种无法解释的行为——  
那才是真正"影响了大体功能"。

顺带说一句：二十四节气和节日是**纯本地算**的，不查表、不联网，算法在下一节。

对比原版（逆向结论）：

|         | 原版                              | 本实现              |
| ------- | ------------------------------- | ---------------- |
| 账号体系    | 有登录 / 云同步                       | 无                |
| 数据落地    | CLDB 自研数据层 + 云页面引擎 CLDP         | 单个 `events.json` |
| 资源加密    | deflate + AES/CBC/PKCS5 信封      | 无（全部明文可查）        |
| 内购      | 有（`Iap_default_list.json` 加密配置） | 无                |
| 第三方 SDK | 统计 / 推送 / 崩溃                    | 无                |
| 联网      | 业务强依赖                           | 仅一个可选的校时按钮       |

---

## 二、设计：冰川glass

上一版是纯黑白的纸与墨。这一版换成一整块冰：极淡的蓝、半透明的面、受光的边。

三条硬规则，决定了它不像那种廉价的毛玻璃：

1. **底色不是纯白，是带灰度的冰蓝。** 纯白上的半透明白会发灰、发脏。
2. **分隔线是亮的，不是暗的。** 玻璃的边缘受光，所以发丝线用白色高光而不是灰线。  
   这是最容易做错、也最容易一眼看出差别的地方 —— 暗线在淡蓝玻璃上像一道裂缝。
3. **只用一个色相。** 全部颜色都在 H≈212° 上，靠明度和透明度分层。  
   唯一的高饱和出现在事件自己的颜色上，且只占几个平方毫米。

界面有**厚度**，靠的是"一浮一刻"：

- `GlassPanel` 浮在冰面上（顶栏、底栏、弹窗、主角卡片、日历、年历）
- `GlassInset` 刻进冰面里（列表分组、设置分组）
- 两者的内阴影方向相反：浮起的上亮下暗，凹陷的上暗下亮。  
  这一点点方向差异，就是"凸起"与"凹陷"的全部区别。

全部做成"浮起的卡片"是最廉价的毛玻璃，因为那等于把一张纸换成一块塑料。

### 关于"模糊"

Compose 的 `Modifier.blur` 需要 **API 31**，而 minSdk 是 26。

所以真正被模糊的不是卡片（卡片只是半透明），而是背景里的三团冷光。  
毛玻璃的感知本质是"半透明 + 边缘高光"，模糊只是加分项——没有它，材质依然成立。  
三团光的位置刻意不对称：对称会立刻显得像模板。

### 两个字阶细节

- 大数字用 `FontWeight.Black` + 负字距，像仪表读数
- 秒级读数用 `fontFeatureSettings = "tnum"`（等宽数字），否则每秒变化时整行会左右抖

---

## 三、功能

### 核心

- **两种读法**：**倒数**（有目标日，向下逼近 0，到点转"已过"）/  
  **计时**（有起始日，从 0 向上累加，永不为负）。  
  两者的差别不在界面，而在"时间是敌人还是朋友"——倒数在等，计时在走。
- **含起始日**：把第一天算作第 1 天。**只作用于"已经过"的读数，绝不作用于"还剩"**——  
  还剩 0 天就是今天，加一就变成明天了。
- **显示秒**：大数字下面再加一行 `天 时:分:秒`。只在开了这个开关的事件上启动 1Hz 时钟，  
  且 App 一退到后台就停（`repeatOnLifecycle(RESUMED)`），不会在你看不见的时候每秒醒一次。
- **自定义颜色**：8 个低饱和预设色。刻意压低饱和度——它们要落在淡蓝玻璃上，  
  高饱和的荧光色会让整块玻璃显脏。
- **置顶**：置顶的事件永远排在列表最前面，行首多一个小圆点。
- **重复规则**：不重复 / 每天 / 每周 / 每月 / 每年。锚点日期永不被改写，所以  
  "每月 31 号"在 2 月会自动落到 28/29 号，且不会因为某次跳过而漂移。
- **提醒**：指定日期 + 指定时分，用 `AlarmManager.setExactAndAllowWhileIdle` 而不是  
  WorkManager（后者会被批处理推迟几十分钟）；没有精确闹钟权限时自动降级为窗口闹钟。
- **重启不丢提醒**：`BootReceiver` 收 `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` 后补排。
- **备份**：JSON 全量导出/导入（合并去重）、ICS 导出（可导入系统日历）。

### 详情页就是设置页

点进一条事件，数字下面直接就是设置——模式、日期、结束日、重复、含起始日、  
显示秒、置顶、颜色、提醒、时间胶囊，全部**就地可改，改完立刻落库**。  
不需要再点一次"编辑"才能动一个开关，也不会因为返回而丢失。

「编辑」只负责内容：标题、备注、胶囊里的话、删除。

### 节气与节日（纯本地算法）

二十四节气不是查表来的——它就&#x662F;**"太阳视黄经达到 15° 整数倍的瞬间"**。  
用 Meeus《Astronomical Algorithms》第 25 章的太阳视黄经近似式，  
再用牛顿迭代反解出对应的儒略日。精度约 ±15 分钟，足以定到某一天。

这套实现在移植前先在 `tools/solar_proto.py` 里跑通，并与 2026 年公认的  
24 个节气日期逐条对账（**24/24 全对**）。踩过的坑写在那份脚本的注释里。

首页头部因此多一行：`今天 霜降 · 距立冬 13 天`。

### 另外三个刻意加的东西

**1. 一句话建事件（`domain/NaturalDateParser.kt`）**

在首页输入框写 `下周五 交房租`，边打边在下面预览，按回车直接落库。  
支持 `明天` / `3天后` / `下周五` / `下下周一` / `10月25日` / `每月15号` /  
`每年10月25` / `2026-10-25` / `下个月5号` / `6个月后` / `2月29日`。

两个设计约束：**宁可解析不出来，也不猜错日期**；**只有"绝对日期"才套用关键词推断**  
（`10月25日 妈妈生日` 会推断成每年，但 `下周五 交房租` 不会推断成每月）。

**2. 时间胶囊**

给未来的自己写一段话，设定日子之前正文是封存的，到期才解锁。  
倒计时从"看数字"变成"等一封信"。

**3. 年度时间地图（`ui/YearMapScreen.kt`）**

12 行 × 31 格的全年点阵，一格一天。不按列表看、也不按日历看 ——  
一眼能看出"今年哪几个月是空的、哪段挤成一团"。这是日历给不了的尺度感。

---

## 四、桌面小组件

按 [Android 官方 App Widgets 文档](https://developer.android.com/develop/ui/views/appwidgets) 实现，  
有几条约束必须守住：

- **`updatePeriodMillis` 最小 30 分钟**，低于此值系统会静默改成 30 分钟。  
  所以「显示秒」绝不可能靠它实现。
- 真正让秒走起来的是布局里的 **`Chronometer`**——它在 `RemoteViews` 的白名单里，  
  由系统自己每秒刷新，App 不需要醒着，**也不耗电**。
- `RemoteViews` 只支持有限的 View 类型，不能放 Compose、不能放自定义 View。  
  所以小组件全部用 `LinearLayout` / `TextView` / `ImageView` / `Chronometer` 拼。
- 额外监听 `ACTION_TIME_CHANGED` / `ACTION_TIMEZONE_CHANGED` / `ACTION_DATE_CHANGED`：  
  用户改系统时间、换时区、跨过午夜，天数必须立刻重算，否则第二天早上看到的还是昨天的数字。  
  （`ACTION_TIME_TICK` 每分钟一次，不能在清单里静态注册——那也正是用 `Chronometer` 的原因。）
- `res/xml-v31/widget_info.xml` 单独提供 `targetCellWidth` / `targetCellHeight` /  
  `previewLayout`，让 Android 12+ 按"格子数"摆放，并在选择器里显示真实布局。

小组件本身也跟随深浅色主题：`setBackgroundResource` 切换冰面/夜冰两套 drawable。

---

## 五、数据

单个文件：`/data/data/com.offline.due/files/events.json`

```json
{
  "app": "due",
  "version": 2,
  "exportedAt": 1761300000000,
  "events": [
    {
      "id": "uuid",
      "title": "在一起",
      "anchor": "2024-03-14",
      "mode": "ELAPSED",
      "endDate": null,
      "includeStartDay": true,
      "showSeconds": true,
      "repeat": "NONE",
      "category": "纪念日",
      "color": 4283076583,
      "pinned": true,
      "note": "",
      "capsule": false,
      "capsuleText": "",
      "remind": false,
      "remindHour": 9,
      "remindMinute": 0,
      "createdAt": 1761300000000
    }
  ]
}
```

刻意不用 Room：几百条数据一次全量读写完全够用，换来的是**备份文件就是数据文件**，  
不用写迁移脚本，也不用担心哪天 ORM 升级把数据读坏。

v1 的旧文件仍可读：缺 `mode` 的按 `COUNTDOWN` 处理，缺 `endDate` 的按"无限累计"处理。

---

## 六、目录

仓库根：

```
如期-Due/
├── app/                     Android 模块（src/ 是全部源码）
├── docs/                    开发文档
│   ├── progress.md          开发纪事
│   └── findings.md          技术备忘
├── tools/                   预览图生成、字体子集、节气对拍等脚本
├── dist/                    安装包（在 .gitignore 里，不进仓库；发版走 Releases）
├── audit.py 等六个          自检脚本，**必须在根目录**——它们用 __file__.parent 定位源码
└── gradlew / gradle/wrapper/   Gradle 8.9 wrapper，无需预装 Gradle
```

源码：

```
app/src/main/java/com/offline/due/
├── CountdownApp.kt          Application：初始化仓库、建通知渠道、冷启动补排闹钟
├── MainActivity.kt          Activity + 手写路由 + 边到边
├── data/
│   ├── CountdownEvent.kt    数据模型 + EventMode + RepeatRule + JSON 编解码
│   ├── EventStore.kt        单文件仓库（StateFlow）
│   ├── SettingsStore.kt     SharedPreferences 封装
│   └── Backup.kt            JSON / ICS 导出
├── domain/
│   ├── CountdownMath.kt     双模式读数、精确到秒、下一次发生日、进度、排序
│   ├── Almanac.kt           二十四节气（天文算法）+ 节日，纯本地
│   └── NaturalDateParser.kt 一句话解析
├── net/
│   └── TimeSync.kt          全 App 唯一联网点：只读 Date 头，只提示不改数
├── notify/
│   ├── Notifications.kt     两个渠道（提醒 / 常驻）
│   └── Reminders.kt         AlarmManager 调度 + 两个 Receiver
├── ui/
│   ├── HomeScreen.kt        冰面 + 一块大玻璃（列表）+ 两块亮玻璃（顶栏/底栏）
│   ├── DetailScreen.kt      大数字 + 就地可改的全部设置
│   ├── EditScreen.kt        标题 / 备注 / 胶囊 / 删除
│   ├── CalendarScreen.kt    只有数字的月历
│   ├── YearMapScreen.kt     12×31 点阵年历
│   ├── SettingsScreen.kt    分组凹槽 + 网络校时
│   ├── RememberToday.kt     跨天刷新 + 秒级时钟
│   ├── components/
│   │   ├── Glass.kt         冰面 / 浮起玻璃 / 凹槽 / 发丝线
│   │   ├── Bits.kt          分区标签 / 设置行 / 分段选择 / 色点 / 弹窗
│   │   ├── DatePicker.kt    日期与时间选择
│   │   └── EventRow.kt      列表里的一行事件
│   └── theme/Theme.kt       冰川玻璃配色 + 六级字阶
└── widget/
    └── TodayWidgetProvider.kt  桌面小组件（Chronometer 自走秒）
```

---

## 七、没有编译器时怎么验证

这些脚本不参与打包，纯 Python，用来在没有真机的情况下守住逻辑和约定：

```bash
python audit.py          # 42 遍跨文件检查：符号对账、设计令牌、无障碍、导入…
python parser_check.py   # 56 条自然语言用例，逐条比对 标题/日期/重复规则
python math_check.py     # 9 组不变量对拍：双模式读数、下一次发生日、进度
python remind_check.py   # 1712 项断言：AlarmManager 调度与重复规则
python stress_check.py   # 46 个文件的边界扫描
python morph_check.py    # 10 组 container transform 对拍
python tools/solar_proto.py   # 24 节气与已知日期对账
```

当前结果：全部 **0 问题**。

`audit.py` 会抓三类编译错误：import 了不存在的声明、调用了类型里没有的成员、  
具名实参在声明里找不到对应形参。

这一轮它自己也被修好了两个 bug，值得记下来：

1. **import 检查用的是改名前的旧包名**（`com.offline.countdown`），  
   所以从项目改名那天起，这条检查就一直在静默失效——等于什么都没查。  
   现在改成引用 `PKG` 常量，127 条自有 import 全部纳入检查。
2. **不认识 `CompositionLocal` 的 `.current`**，把每一处 `LocalGlass.current` 都报成错误。  
   现在 `staticCompositionLocalOf` 声明的 val 会自动登记 `current` / `provides`。

修完之后还做了一次**反向验证**：故意塞一个不存在的 import，确认脚本能抓到，再删掉。  
一个不会报错的检查脚本，比没有检查脚本更危险。

它抓不到类型不匹配和 Compose API 版本差异，所以 Compose 的实验性 API  
（`DatePicker` / `TimePicker`）逐个确认过都带 `@OptIn(ExperimentalMaterial3Api::class)`。

---

## 八、已知限制

- **没在真机上跑过**：能编译（`compileDebugKotlin` 零警告），逻辑和约定有六个脚本守着，  
  但这台机器既没有模拟器也没有真机，所以**手感、动画、兼容性都没验证过**。  
  首次在 Android Studio 里同步时如果报错，多半是 SDK 版本或依赖版本要跟着你的 AS 版本微调。
- **不含农历节日**：春节、中秋、端午、七夕、重阳都没做。那需要完整的农历换算  
  （朔望月 + 无中气置闰），本轮没做——**宁可缺，不要错**。  
  公历节日和二十四节气是全的。
- **提醒精度**：Android 12+ 需要用户手动授予「闹钟和提醒」权限，否则只能拿到窗口闹钟，  
  可能晚几分钟。这是系统限制，不是实现问题。
- **小组件的秒**：靠 `Chronometer` 由系统自走，所以显示的是"不足一天的零头"，  
  配合上面的大数字读作「365 天 + 05:12:33」。不是物理上连续的总时长，  
  因为日历日和时间长度本来就不可能严格对齐。
- **导入按 id 去重**：从别的设备导入同一件事会得到两条（UUID 不同）。  
  做"按标题+日期合并"会有误判风险，所以没做。
- **跨天不自动刷新列表**：`rememberToday()` 会在每次回到前台时重新取日期，  
  但 App 一直开着跨过午夜的情况需要切一次后台再回来。
- **未做**：多语言（只有中英资源裁剪）、数据加密（明文 JSON，方便你直接改）。

---

## 久、许可证

本项目以 **Apache License 2.0** 发布，全文见 [`LICENSE`](LICENSE)。

**你可以**：商用、修改、再分发，包括把衍生品闭源分发。

**你需要**：

1. 保留版权声明；
2. 随分发物传递 [`NOTICE`](NOTICE) 的内容（Apache-2.0 第 4(d) 条）；
3. 在改动过的文件里说明你改了什么（第 4(b) 条）——加一行注释即可。

选这个而不是 MIT，唯一的实质差别是**署名有传递义务**：MIT 只要求你  
"保留 LICENSE 文件"，有人把代码抠进自己的项目、在源码角落留一行版权  
就算合规；Apache-2.0 要求 `NOTICE` 一路传下去，进入下游产品的文档。  
附带还多一条专利授权——贡献者把相关专利许可给你，你不用怕用了之后被反手告。

### 第三方组件


| 组件 | 协议 | 说明 |
|---|---|---|
| AndroidX / Jetpack Compose / Material 3 | Apache-2.0 | 与本项目协议一致 |
| 思源黑体（Noto Sans SC） | **SIL Open Font License 1.1** | 单独适用，不被本项目的 Apache-2.0 覆盖 |

字体这一项容易被忽略：`app/src/main/res/font/` 下的四个 `.ttf` 是**子集化后**的
思源黑体，OFL 全文随包分发在 `app/src/main/res/raw/ofl_noto_sans_sc.txt`。
OFL 允许修改与再分发，但要求修改版不得占用保留字体名——本工程内字体的族名
仍是 `Noto Sans SC`，`Source` 只出现在版权与商标字段，未占用保留名。

> 如果你要再分发本项目，**字体那部分按 OFL 走，不要按 Apache-2.0 走**。

---

## 十、参与

想改代码，先看 [`CONTRIBUTING.md`](CONTRIBUTING.md)——里面写了环境怎么配、
六项自检怎么跑，以及几条最容易被 PR 破坏的约定（颜色只有一个来源、
`BackHandler` 全工程只许三处、`AnimatedContent` 换页会销毁旧页……）。
这些约定编译器一条都不管，所以它们才需要写下来。

| 想做什么 | 去哪 |
|---|---|
| 报 bug | [开 issue](https://github.com/jymomo123/Due/issues/new?template=bug_report.yml)（**能录屏就录屏**，这个工程的 bug 大多要动起来才看得见） |
| 提想法 | [开 issue](https://github.com/jymomo123/Due/issues/new?template=feature_request.yml) |
| 报告安全漏洞 | [私密报告](https://github.com/jymomo123/Due/security/advisories/new)，**别开公开 issue**，详见 [`SECURITY.md`](SECURITY.md) |
| 看开发过程 | [`docs/`](docs/)，怎么一步步做成现在这样，以及踩过的坑 |

### 关于「已知限制」

第九节列的那些限制（跨天不自动刷新、导入按 id 去重、没有数据加密）都是**有意的取舍**，
不是待办。改动之前请先看那一节的理由，以及 [`docs/findings.md`](docs/findings.md)
里对应的推导过程。
