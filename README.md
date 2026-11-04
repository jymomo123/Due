# 如期 · Due

![CI](https://github.com/jymomo123/Due/actions/workflows/ci.yml/badge.svg) ![License](https://img.shields.io/badge/license-Apache--2.0-blue) ![minSdk](https://img.shields.io/badge/minSdk-26-3ddc84)

一个倒数日应用，纯离线免费。

## 功能

- 倒数 / 计时「含/不含 起始日」切换显示 天 时:分:秒
- 重复规则：不重复 / 每天 / 每周 / 每月 / 每年。
- 提醒
- 桌面小组件
- 数据导出


## 数据

`/data/data/com.offline.due/files/events.json`，明文显示

## 更新日志

完整记录看 [CHANGELOG.md](CHANGELOG.md)

0.58.19

有时候整个界面突然点不动。查下来是返回闸门 backBusy 卡成了真：手势没收到终点，闸门又把入口拦住，后面那段自愈代码根本没机会跑。现在手指一落下就认为系统返回手势已经结束，另外加了 400ms 一道兜底。这样症状不会一直挂着，哪场手势漏了终点还没抓到

0.58.18

纯色背景下渐隐带会透出一条淡粉。那层错拿了主题渐变当背景，用户自己调的蓝底是另一套值。现在统一从 pageBaseColor() 取色。

0.58.17

「深浅」滑块拖半天没反应——底色只认 0.84~1.0 和 0.06~0.18 这两小段，滑块却跑满 0..1，等于 84% 的行程是空的。加了 backdropValueBand()，把看得见的那截换成有区别的那截，值还是同一个值。顺带把 swipePage 里写成提前返回的 enabled 挪进 pointerInput 的 key，原来那样会让 Compose 重建节点、手势状态丢掉。

0.58.14 / 0.58.11 / 0.58.10

背景垫底统一到 drawBackdropBase。连续点动画时卡片卡死，是 busy 锁罩住了可能永不返回的 events.collect。详情页返回首页闪一下亮，则是同一张半透明的画被画了两遍，换成 previewSamePage 之后好了。


## 许可证

[Apache-2.0](LICENSE)。随包发的思源黑体是子集化过的，那几个 .ttf 按 SIL OFL 1.1 单独走，不被本项目的 Apache-2.0 覆盖。
