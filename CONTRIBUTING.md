# 参与开发

先说清楚这是个什么工程，能省掉很多来回。

**如期（Due）是一个纯离线的倒数日 App**，单 Activity，Jetpack Compose，没有网络、没有账号、没有遥测。它的特点是「界面很讲究」——整套视觉建立在一层淡蓝玻璃上，动效、配色、留白都有一份硬性约定，改一处往往要连带改几处。

代价是：**这些约定编译器一条都不管**。所以这个仓库里躺着六个 Python 脚本，用来看守编译器看不见的东西。

---

## 一、环境

| 需要 | 版本 | 说明 |
|---|---|---|
| JDK | **17** | `compileOptions` 和 `kotlinOptions` 都写死 17，AGP 8.7.3 也要求 ≥17 |
| Android SDK | **platform 35 / build-tools 35.0.0** | `compileSdk = 35` |
| Gradle | 8.9 | 用仓库里的 `./gradlew`，别用系统装的 |
| Python | 3.8+ | 只给六个自检脚本用，不参与构建 |

`minSdk = 26`（Android 8.0）。选 26 是因为 `java.time` 从这一版起进了系统，不需要 core library desugaring。

### 配置 SDK 路径

二选一：

```bash
# 方式一：环境变量（CI 用这个）
export ANDROID_HOME=/path/to/android-sdk

# 方式二：仓库根建 local.properties（本机开发用，已被 .gitignore 排除）
echo 'sdk.dir=/path/to/android-sdk' > local.properties
```

`local.properties` **不要提交**。它里面是本机绝对路径，换台机器就没用了。

---

## 二、编译

```bash
./gradlew assembleDebug      # 调试包
./gradlew assembleRelease    # 发布包（跑 R8 + 资源压缩，慢一些）
```

### 关于签名

发布签名在 `keystore.properties` 和 `app/*.jks` 里，**两个都不在仓库中**（`keystore.properties` 存明文口令，`.jks` 是私钥本身；一旦推上去等于把发布签名送人，而且无法撤销——改签名意味着老用户无法覆盖安装）。

没有这两个文件时，`app/build.gradle.kts` 里的 `hasReleaseKey` 为 `false`，**release 会退回 debug 签名**。所以你克隆下来就能编、能装、能改，只是不能发版。

如果你编出来的包在装了正式版的手机上装不上，提示「应用未安装」，原因就是这个——签名不同，Android 拒绝覆盖。

---

## 三、六项自检

在**仓库根目录**跑（它们都用 `Path(__file__).parent` 定位 `app/src/main/java`，挪进 `tools/` 就找不到源码了）：

```bash
python audit.py          # 静态符号审计，42 遍规则
python parser_check.py   # 自然语言日期解析，56 项
python math_check.py     # 倒计时数学不变量，9 组
python remind_check.py   # 提醒调度对拍，1712 项断言
python stress_check.py   # 压力/崩溃面扫描，46 个文件
python morph_check.py    # 卡片 ⇄ 列表行变换，10 组
```

全部通过时，每个脚本打印 `问题 0 条`，**并且退出码为 0**。

### 改检查脚本时的规矩

动规则之前请先看这一条，它是这个仓库里踩得最多的坑：

> **报错先怀疑检查器。**

「0 问题」有两种可能：真的没问题，或者规则根本没跑到。后者更常见——路径写成了相对当前工作目录而不是相对源码根、白名单漏了包名前缀、正则写宽了导致满屏假报然后被人工忽略。所以：

- **新增规则必须双向验证**：塞一条假问题进去确认它报；把规则删掉确认之前的报错归零；再确认不该报的地方确实没报。
- **正则里的 `\s` 会匹配换行**。`^\s*import` 会把下一行代码一起吃掉，于是满屏假报——这个 bug 真的发生过，改成 `^[ \t]*` 才对。
- **行号要用剥注释后的文本算，但剥的时候必须保留换行数**，否则报出来的行号全是错的（曾经报 `Glass.kt:425`，实际在 665 行）。

### 一个已经踩过的坑：假绿灯

0.58.19 之前，六个脚本里只有 `stress_check.py` 和 `remind_check.py` 带退出码，另外四个无论查出多少问题都 `exit 0`。

后果是：终端里人眼能看见「问题 4 条」，但 CI 只认退出码，于是流水线**永远是绿的**。一个不会失败的检查，比没有检查更危险——它让人以为这条线守住了。

CI 里因此加了一步，直接 `grep` 脚本源码里有没有 `sys.exit(` / `raise SystemExit(`。看起来笨，但它防的是「有人重构时顺手把退出码删了」。

---

## 四、目录

```
.
├── app/src/main/java/com/offline/due/
│   ├── ui/            界面层（Compose）
│   ├── data/          存储、备份、设置
│   ├── domain/        纯逻辑：日期数学、重复规则、农历
│   ├── remind/        提醒调度与闹钟
│   └── net/           全工程唯一的联网点（校时，只提示不改数据）
├── app/src/main/res/  资源（字体是子集化的，见下）
├── docs/              开发纪事与技术备忘
├── tools/             构建与预览脚本
└── *.py               六项自检 —— 必须在根目录
```

### 字体

`res/font/noto_sc_*` 是**思源黑体 / Noto Sans SC 的子集**，由 `tools/build_fonts.py` 生成，只保留界面实际用到的字形。900 字重只压了 34 个字形，专门给标题用。

字体按 **SIL Open Font License 1.1** 分发，全文在 `res/raw/ofl_noto_sans_sc.txt`。子集化属于「修改」，而 OFL 规定修改版不得占用保留字体名——这里的族名是 `Noto Sans SC`，保留名 `Source` 只出现在版权与商标字段里，没有占用。

**改字体相关代码时注意**：`Typography(...)` 必须写全十五个样式，漏一个就会静默落回 `FontFamily.Default`；字号有两条来源（`MaterialTheme.typography` 和 `displaySp()`），改要一起改。

---

## 五、代码约定

这些不是风格偏好，是踩出来的。完整版规则很长，这里只列最容易被 PR 破坏的几条。

### 颜色

- **颜色只有一个来源**，不许写死带色相的字面量。`GlassTokens.scrim` 是派生属性。
- **整套色相一起转**：新增色相时所有同族颜色一起转，明度/饱和度不动，并重算对比度（`tools/dark_contrast.py`）。
- **对比度要算，不要看**。次要墨色压在凹槽上可能只剩 2.89:1，肉眼看不出来。

### 尺寸与动效

- **圆角只许 `Radius`，动画只许 `Motion`，留白只许三档**（`CardInset` / `Gutter` / `RowInset`）。
- **字号不许写字面量**，走排版体系。
- 投影由 `GlassPanel` 内部给（`weight² × 7dp`，深色不给），外面别再叠 `Modifier.shadow`。

### 弹窗

- 自绘弹窗必须走 `GlassDialog`；`close { … }` 里**必须**调 `onDismiss()`，否则返回键会失灵。
- 弹窗里「一圈框」只能用 `GlassInset`。

### 状态保存

- `rememberSaveable` 救转屏，**不救「离开再回来」**。判断依据是：这个状态会不会从组合里消失。
- `AnimatedContent` 换页会销毁旧页，需要 `SaveableStateProvider(key)`。
- **新增路由要同时改四处**：`when(route)`、`routeDepth`、`backTarget`、预览层 `when`。漏一处的表现是「返回键点了没反应」。

### 返回手势

这是全工程最容易改坏的一块，`BackHandler` 全工程只允许出现在三处（`BackSwipe.kt` / `Bits.kt` / `EditScreen.kt`）。

原因：`OnBackPressedCallback` 不是 `OnBackAnimationCallback`——它只把 `onBack` 给**最后一个**注册者，不是广播。页内自己拦一道，外层就会被抬起来却等不到回调，`backLiftP` 停在半路，表现是「点哪儿都没反应」。看到「所有位置都这样」，先查这条。

### 数据

- `CountdownEvent` 加字段必须同时改 `toJson` / `fromJson` 并给默认值（用 `optXxx(名字, 默认值)`，不要靠 `SCHEMA_VERSION`）。
- `Settings` 加字段必须同时在读盘构造里赋值。

---

## 六、提交信息

用中文写，一句话说清楚「改了什么 + 为什么」，不强制前缀。比如：

```
修详情页返回卡在半路：busy 闩罩住了 events.collect

collect 可能永不返回，于是 busy 永远为真，返回键彻底失灵且不自愈。
把闩的作用域收窄到补演那 180ms，并给 onCancel 和清闩都加上代际。
```

改到界面观感的，请附一张前后对比图（`tools/make_*_preview.py` 能生成）。

---

## 七、发版

**没有自动发布的工作流，这是有意的。**

发布包必须用正式密钥签名，而密钥不能进仓库。把它放进 GitHub Secrets 再让 CI 签名，技术上可行，但意味着任何能改 workflow 的人都能签出一个包——风险不对等。所以发版在本地做：

```bash
# 1. 升版本号：app/build.gradle.kts 里的 versionCode / versionName
# 2. 更新 CHANGELOG.md
# 3. 构建
./gradlew assembleRelease
# 4. 验证签名
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
# 5. 手动上传到 GitHub Releases
```

版本号规则见 `CHANGELOG.md` 末尾。简单说：`versionCode` 每次发版 +1 且**永不回退**（Android 靠它判断能不能覆盖安装）；`versionName` 用 `0.xx.y-beta`，进入正式版时去掉 `-beta`。

---

## 八、许可证

提交代码即表示同意以 **Apache License 2.0** 授权（见 `LICENSE`）。

分发衍生作品时必须一并传递 `NOTICE` 的内容——这是 Apache-2.0 第 4(d) 条的要求，不是可选项。
