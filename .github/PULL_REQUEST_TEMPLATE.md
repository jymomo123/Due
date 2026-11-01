<!--
  提交 PR 之前请把下面几项过一遍。

  这不是流程表演。这个工程没有真机测试环境，绝大多数保证来自静态检查，
  所以「我本地跑过了」这句话必须能对上具体的东西。
-->

## 这个 PR 做了什么

<!-- 一两句话。如果是修 bug，说清楚原来的行为是什么。 -->

## 关联 issue

<!-- 比如 Closes #12。没有就写「无」。 -->

## 六项自检

在仓库根目录依次跑（它们必须留在根目录，用 `Path(__file__).parent` 找源码）：

```bash
python audit.py          # 静态符号审计，42 遍规则
python parser_check.py   # 自然语言日期解析，56 项
python math_check.py     # 倒计时数学不变量，9 组
python remind_check.py   # 提醒调度对拍，1712 项断言
python stress_check.py   # 压力/崩溃面扫描，46 个文件
python morph_check.py    # 卡片⇄列表行变换，10 组
```

- [ ] 六个全过（输出 `问题 0 条`，且退出码为 0）

> 退出码是 0.58.19 才补上的。在那之前有四个脚本无论查出多少问题都 `exit 0`，
> CI 会永远显示绿色。如果你看到某个脚本不打印汇总就退出，说明它被改坏了。

## 红线

- [ ] **没有引入带色相的字面量。** 颜色只有一个来源，`GlassTokens.scrim` 是派生属性。
- [ ] **圆角只用了 `Radius`，动画只用了 `Motion`，留白只用了三档**（`CardInset` / `Gutter` / `RowInset`）。
- [ ] **字号没有写死数字**（走 `MaterialTheme.typography` 或 `displaySp()`）。
- [ ] **弹窗走了 `GlassDialog`**，且 `close { … }` 里调了 `onDismiss()`。
- [ ] **新增路由同时改了四处**：`when(route)`、`routeDepth`、`backTarget`、预览层 `when`。
- [ ] **没有新增 `BackHandler`。** 全工程只允许三处（`BackSwipe.kt` / `Bits.kt` / `EditScreen.kt`）。
- [ ] **`CountdownEvent` 加字段时同时改了 `toJson` / `fromJson` 并给了默认值。**
- [ ] **新增的「给人看、给程序读」的资源写进了 `res/raw/keep.xml`。**（release 会压缩资源，debug 不会——只编 debug 是验不出来的。）

## 改了检查脚本吗

<!--
  如果这个 PR 动了某个自检脚本的规则，请回答两个问题：
  1. 塞一条假问题进去，它会不会报？（反向验证）
  2. 把规则删掉，之前的假报会不会归零？
  只做第一项不够——规则写宽了会漏报，写窄了会满屏假报，两个方向都要试。
-->

## 验证到哪一步了

**请如实勾选。** 这台开发机上既没有模拟器也没有真机，所以「编译通过」和
「真的能用」之间还有很长的距离，写清楚比写得漂亮重要。

- [ ] 编译通过（`./gradlew assembleDebug`）
- [ ] release 也能编（`./gradlew assembleRelease`，会跑 R8 + 资源压缩）
- [ ] 六项自检全过
- [ ] **在真机 / 模拟器上实际点过**（如果是，请说明机型和系统版本）

<!-- 没在真机上验证过的改动，请在下面写清楚哪一部分没有验证、风险在哪。 -->
