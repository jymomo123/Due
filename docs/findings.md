# 技术备忘

写代码时踩出来的结论。按领域分组，每条一句话，理由写在括号里。

## 天数口径

- 倒数用 `between(today, target)`，目标日当天是 0，不是 1。
- "含起始日"是把起点当天算第 1 天，即 `between + 1`。开关只影响显示，不影响排序。
- 过了的一次性倒数折成计时。判据是 `anchor.isBefore(today)`，不是 `days < 0`——
  锚点就是今天的那一条（`raw == 0`）不能折，否则"今天"会变成"已过 0 天"。
- 这个判据有三个读者：`countdownInfo`、`precise`、`rollPastToElapsed`。
  改一处就会两数矛盾（卡片写"还有 3 天"，精确读数写"2 天 23 小时"）。
- 折成计时的结果只在 `init()` 里落盘，不在渲染路径上写。

## 重复与提醒

- 两根轴不能混：`RepeatRule` 是"这件事多久发生一次"，`RemindRule` 是"提醒多久响一次"。
- 倒数往回数、计时往后数，两者的 `nextTriggerMillis` 走不同分支。
- 计时的 `ONCE` 在调度时折成 `DAILY`，只折算不写回——写回会让用户的设置被悄悄改掉。
- "要不要重排闹钟"只有一个判断点：Receiver 无条件调 `scheduleOne`，
  null 判断只放在 `nextTriggerMillis` 里。两个地方都判断就会漏排。
- "每周几/每月几号"填 0 表示跟随目标日。界面上要解成具体值显示，但必须和调度算同一个数。
- 2 月没有 31 号时夹到月末，3 月要回到 31 号——不能把夹过的值写回规则。
- 兜底原则：宁可多响一次，也不静默丢掉提醒。

## 颜色

- 颜色只有一个来源。不许写死带色相的字面量，`GlassTokens.scrim` 是派生属性。
- 整套色相一起转：新增色相时所有同族颜色同转，明度/饱和度不动，然后重算对比度。
- 对比度要算，不要看。次要墨色压在凹槽上只剩 2.89:1，肉眼分辨不出来。
- 反差色是 `contrast` + `onContrast` 一对，明度按 4.5:1 算出来。不要用色相转 180°。
- 一屏只许有一块反差色。

## 玻璃

- 玻璃 alpha 是"厚度"不是"颜色"：一律乘 `0.7 + weight×0.5`，
  不要用 `copy(alpha = 0.95f)` 覆盖。
- 投影由 `GlassPanel` 内部给（`weight² × 7dp`，深色不给），外面别再叠 `Modifier.shadow`。
- 浮起用 `GlassPanel`，刻入用 `GlassInset`。弹窗里"一圈框"只能用 `GlassInset`。
- 同一张画不许同时画两遍。玻璃填充是半透明的，叠两遍会更亮，调 alpha 修不好。

## 背景

- 先问"哪一份"：`settings.bgColor`/`bgStyle` 是用户设的（权威），
  `g.gradient` 是令牌渐变（只在 ICE 下等于背景），`g.scrim` 是兜底。
- 画法只有 `drawBackdropBase` 一处——照片装不进 `Brush`，只能在这一处分支。
- 纯色模式下不许叠极光。帘幕的颜色取自全局色相，跟用户选的颜色无关，
  叠上去就变成"背景不是我设的那个颜色"。

## 字体

- `res/font/noto_sc_*` 是思源黑体的子集，由 `tools/build_fonts.py` 生成，只保留界面用到的字形。
- 900 字重只压了 34 个字形，专给标题。
- `Typography(...)` 必须写全十五个样式，漏一个会静默落回 `FontFamily.Default`。
- 字号有两条来源：`MaterialTheme.typography` 和 `displaySp()`，改要一起改。
- `Text(lineHeight = Unspecified)` 等于没写，只能 `style = typo.x.copy(lineHeight = …)`。

## 返回手势

全工程最容易改坏的一块。`BackHandler` 只允许出现在三处：`BackSwipe.kt`、`Bits.kt`、`EditScreen.kt`。

- `BackHandler` 是 `OnBackPressedCallback`，不是 `OnBackAnimationCallback`。
  进度和 `onBack` 都只给最后一个注册者，不是广播。
- 页内自己拦一道，外层就会被抬起来却等不到回调，`backLiftP` 停在半路，
  表现是"点哪儿都没反应"。看到"所有位置都这样"，先查这条。
- 锁的作用域等于它要挡的那一段。`busy` 曾经罩住 `events.collect`，
  而 collect 可能永不返回，于是 `busy` 永久为真，返回键失灵且不自愈。
- 布尔闩不许当交互闸门。闸门要写"现在在发生什么"（手指按着、收尾在跑），
  不能写"曾经发生过什么"。
- 自愈不许排在闸门后面：手指一落就先复位，否则进不去就永远不自愈。
- 返回方向跟 `BackEventCompat.swipeEdge` 走，左划往右退、右划往左退。
  API 33 恒为 `EDGE_LEFT`，那里也没有进度。

## 页面与预览层

- 页面自己是透明的（底色只在根上画一次），所以任何 alpha 都会露出后面那层。
- 交叉淡入是 `a+(1−a)b`，两层都半透明必然露底；一层恒 1 则内容重叠。调曲线解决不了。
- 手势期间页面自带一层底，写在 `graphicsLayer{}` 里面，跟着缩、被同一道圆角裁。
- 预览层是"上一页的画"：吞触摸、和真页面对齐、只改 alpha 且只增不减。
- `previewA` 只许一个写者。整页浮起归 `pageDragEnd`，详情页归 `onCover`，不能两边都写。
- 动画被取消等于状态永久卡住。靠"归位写成状态" + `try/finally` + 代际 + `landed` 兜底。

## 卡片与列表行

- 是"换人"不是"压扁"：透明度只增不减，接力点是"后来那个已经能画出来"。
- `noMorph()` 写回什么，`morphTo()` 在 `p=1` 就必须等于什么。
- 列表行两列各贴一头（左 `Top`、右 `Bottom`），行高不变，变的是左列可用高度，
  标题才敢写 `maxLines = 2`。
- 秒表在右列数字正下方，只取 `clock()`。抬 0.195em 才叫"贴着"。
- "天"那一格写不写都要占住位置，否则"今天"会右移 16.60dp。
- "右对齐"不等于右边缘一条线，只在每行算满时等价，只能算不能猜。

## 状态保存

- `rememberSaveable` 救转屏，不救"离开再回来"。判据是：这个状态会不会从组合里消失。
- `AnimatedContent` 换页会销毁旧页，需要 `SaveableStateProvider(key)`，
  且 key 不能放会被回写的量。
- 页内换内容时 `rememberScrollState()` 必须提到分支外。
- 新增路由要同时改四处：`when(route)`、`routeDepth`、`backTarget`、预览层 `when`。漏一处就是返回失灵。

## 数据

- 事件存在 `filesDir/events.json`，设置存在 `MODE_PRIVATE` 的 SharedPreferences，都是明文。
- 写盘用原子替换 + `.bak` 回退。`EventStore.persist` 是有意同步的。
- `CountdownEvent` 加字段必须同时改 `toJson`/`fromJson` 并给默认值，
  用 `optXxx(名字, 默认值)`，不要靠 `SCHEMA_VERSION` 兜底。
- `Settings` 加字段必须同时在读盘构造里赋值，否则读出来是默认值，看起来像"设置没保存"。
- 导入按 id 去重。不做"按标题+日期合并"，那会误判。

## 资源压缩

- `isShrinkResources = true` 会删掉没被代码引用的资源。
- 给人看、给程序读的资源（协议全文之类）必须写进 `res/raw/keep.xml`。
- 打包后按类型和大小找——release 会改名，`res/raw/x.txt` 会变成 `res/yg.txt`。
- 这类问题只在 release 里暴露，debug 不压缩永远编得过。

## 无障碍

- `pressable` 是唯一的按压入口，`role` 在那里定一次（`clickable` 的 role 默认是 null）。
- 开关要补 `Role.Switch` + `stateDescription`；滑块要补 `progressBarRangeInfo` + `setProgress`；
  纯图形按钮要补 `contentDescription`。
- 只写语义树是零像素风险的；需要改尺寸才"合规"的一律不做。

## 节气与农历

- 二十四节气和节日纯本地算，不查表、不联网，算法在 `domain/Almanac.kt`。
- 农历没做。要做得引一份几十 KB 的表，而它只影响节日名称。

## 小组件

- 刷新靠 `AlarmManager` 定时触发 `ACTION_APPWIDGET_UPDATE`，不是 WorkManager。
- 另外监听 `TIME_SET` / `TIMEZONE_CHANGED` / `DATE_CHANGED` 三个受保护广播：
  改系统时间、换时区、跨过午夜都必须立刻重算，否则第二天早上看到的还是昨天的数字。
- 秒级刷新用 `Chronometer`，因为 `ACTION_TIME_TICK`（每分钟一次）不能静态注册。

## 构建

- JDK 17，`compileSdk`/`targetSdk` 35，`minSdk` 26（`java.time` 从这一版起进系统，不需要 desugaring）。
- 签名信息在 `keystore.properties`，不入库。文件缺失时 `hasReleaseKey` 为 false，
  release 退回 debug 签名，照样能编能装，只是不能发版。
- 六项自检脚本必须留在仓库根目录——它们用 `Path(__file__).parent` 定位源码。
