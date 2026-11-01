"""把思源黑体（Noto Sans SC 变量字体）压成 Android 能用的静态子集。

## 为什么要子集

中文全量字体一个静态字重约 9MB（6 万多字形）。这个 App 的 APK 现在才 1.28MB，
直接塞进去等于把体积翻十倍。

子集只保留**实际会被渲染的字**：

  - ASCII 可打印字符
  - GB2312 一级汉字（3755 个，覆盖现代汉语 99.5% 以上的用字）
  - 常用标点、全角符号、箭头、圈号
  - 源码里出现过的所有非 ASCII 字符（保证界面文案一个都不缺）

子集之外的生僻字不会变成豆腐块：Android 的 Minikin 对自定义 Typeface
有系统回退链，缺字会落到系统 CJK 字体上（这也是给拉丁字体设自定义
Typeface 之后中文仍然正常显示的原因）。

## 为什么要实例化

下载到的是 `wght` 单轴的变量字体。Android 从 API 26 起支持可变字体，
但 Compose 侧要按字重逐个声明 `variationSettings`，且单个文件仍然带着
全部字重的插值数据（gvar），体积比静态字重之和大得多。

这里先用 `varLib.instancer` 把 `wght` 钉死成具体字重（gvar 被消掉），
再子集。代价是每个字重一个文件，但每个都小。

## 用法

    # 变量字体（17.8MB，只需下一次，可以放在 .fontwork/ 里）
    curl -L -o .fontwork/NotoSansSC-VF.ttf \
      "https://cdn.jsdelivr.net/gh/google/fonts@main/ofl/notosanssc/NotoSansSC%5Bwght%5D.ttf"

    python tools/build_fonts.py .fontwork/NotoSansSC-VF.ttf app/src/main/res/font

输出 `noto_sc_400.ttf` / `_500` / `_700` / `_900`，直接放进 `res/font/`。
思源黑体是 SIL Open Font License 1.1（Copyright 2014-2021 Adobe，保留字体名
'Source'）。OFL 要求分发时附带许可证，所以同时放了
`res/raw/ofl_noto_sans_sc.txt`——**不要删**。
"""

import sys
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

# 生成的字重。挑这四个是因为：
#   400 —— 正文（bodyMedium / bodySmall / bodyLarge）
#   500 —— 次要信息、分区标签、可点击文字（titleMedium / labelSmall / labelMedium）
#   700 —— 标题（headlineSmall / headlineMedium）。设计上原本要 600，
#           但为 600 单独存一份 1.2MB 不划算，CSS 的字体匹配规则会把
#           600 的请求落到 700 上（>500 时向上取），观感差别很小
#   900 —— 大数字（displayMedium / displayLarge）。这一档是这一屏的主角，
#           少它整个字重体系就塌了
#
# 第二个字段是字符集：`full` 是完整子集，`display` 只有大数字那一行会用到的字。
WEIGHTS = [
    (400, "full"),
    (500, "full"),
    (700, "full"),
    (900, "display"),
]

# 900 只会被 displayLarge / displayMedium / displaySmall 用到，
# 而这三个样式只渲染数字、日期、时钟和「天 / 周 / 今天」这几种单位。
#
# 思源黑体的 glyf 占整个文件 92%，一个中文子集字重 1.2MB，
# 把 900 的字符集从 4290 个码位砍到几十个，省下 1.2MB——这是整个流程里
# 唯一一处"砍掉的东西永远不会被渲染到"的优化。
DISPLAY_CHARS = "0123456789−-+.,:·/ 今天周天年月日时分秒已走过还就是"

# 垂直度量。
#
# 思源黑体的 hhea 是 `1160 / -288`，也就是 1.448em。Roboto 是 1.171em。
# 差这 0.28em 不是小数点问题：Compose 在 `lineHeight` 未指定时用字体的
# ascent + descent 当行盒，于是同一个 88sp 的大数字，行盒会从 103sp 变成 127sp，
# 而 `DetailScreen` 里 `GLYPH_BOX = 1.15` 是照着 1.171em 定的——
# 卡片装不下，数字会被裁掉上下两头。
#
# 两个选择：把 GLYPH_BOX 改成 1.448（数字缩小 21%，与"字太小"的诉求相反），
# 或者把字体的行盒压回拉丁字体的量级（排版几何完全不动）。
# 选后者。
#
# 955 / -195 的取法：中日韩表意文字的字面大致在基线以上 0.88em、
# 以下 0.12em。要让这个方块在行盒里**正好居中**，上下余量要相等：
#     0.88 + p = 0.955，0.12 + p = 0.195  →  p = 0.075
# 合计 1.15em，正好等于 GLYPH_BOX。字形不会被裁，位置也不会偏。
#
# 只改 hhea 和 OS/2 的 win 度量；sTypo 那三个数不动——它们是给"排版模式"
# 用的，Android 不读，改了反而会让某些工具算出不一样的行高。
METRIC_ASCENT = 955
METRIC_DESCENT = -195

# 逐段补的标点与符号。顺序不重要，最后都并成一个集合。
EXTRA_RANGES = [
    (0x00A0, 0x00FF),   # Latin-1：· × ° 这些
    (0x2010, 0x203B),   # — – ‘ ’ “ ” … ※
    (0x2190, 0x2193),   # ← ↑ → ↓
    (0x2460, 0x2473),   # ① ~ ⑳
    (0x25A0, 0x25FF),   # ■ □ ▲ ► ● 之类
    (0x3000, 0x303F),   # 中文标点：、。「」『』【】〜
    (0xFF01, 0xFF60),   # 全角 ASCII
    (0xFFE0, 0xFFE6),   # ￥ ￠ ￡ 等
]


def gb2312_level1() -> set[str]:
    """GB2312 一级汉字（3755 个，按拼音排序的那一批）。"""
    chars = set()
    for hi in range(0xB0, 0xD8):
        for lo in range(0xA1, 0xFF):
            try:
                chars.add(bytes([hi, lo]).decode("gb2312"))
            except UnicodeDecodeError:
                pass
    return chars


def source_chars(root: Path) -> set[str]:
    """源码里出现过的所有非 ASCII 字符。

    这一份是**保险**：万一某个文案里用了 GB2312 之外的字符
    （比如「⏳」「℃」或者某个生僻的名字），少了它就会出现豆腐块。
    成本只有几十个字形。
    """
    chars = set()
    for f in root.rglob("*.kt"):
        chars.update(c for c in f.read_text(encoding="utf-8") if ord(c) > 0x7F)
    for f in list(root.rglob("*.xml")):
        try:
            chars.update(c for c in f.read_text(encoding="utf-8") if ord(c) > 0x7F)
        except UnicodeDecodeError:
            pass
    return chars


def build_charset(src_root: Path) -> set[str]:
    chars = {chr(c) for c in range(0x20, 0x7F)}
    chars |= gb2312_level1()
    for lo, hi in EXTRA_RANGES:
        chars |= {chr(c) for c in range(lo, hi + 1)}
    chars |= source_chars(src_root)
    # 控制字符和代理区不能进 cmap
    return {c for c in chars if c.isprintable() or c == " "}


def trim_metrics(font: TTFont) -> None:
    """把行盒压回 1.15em，理由见文件头 [METRIC_ASCENT]。"""
    upm = font["head"].unitsPerEm
    ascent = round(METRIC_ASCENT * upm / 1000)
    descent = round(METRIC_DESCENT * upm / 1000)

    hhea = font["hhea"]
    hhea.ascent = ascent
    hhea.descent = descent
    hhea.lineGap = 0

    os2 = font["OS/2"]
    os2.usWinAscent = ascent
    os2.usWinDescent = -descent


def build(src: Path, out_dir: Path, src_root: Path) -> None:
    full = build_charset(src_root)
    display = {c for c in DISPLAY_CHARS}
    charsets = {"full": full, "display": display}
    print(
        f"字符集：full {len(full)} 个码位（GB2312 一级 + 源码用字 + 标点）"
        f"｜display {len(display)} 个（只有大数字那一行）"
    )

    out_dir.mkdir(parents=True, exist_ok=True)
    total = 0
    for w, kind in WEIGHTS:
        unicodes = sorted({ord(c) for c in charsets[kind]})

        # 第一步：钉死字重。gvar / HVAR 被消掉，体积立刻掉一个量级。
        vf = TTFont(src)
        instantiateVariableFont(vf, {"wght": w}, inplace=True, updateFontNames=True)
        trim_metrics(vf)

        tmp = out_dir / f"_tmp_{w}.ttf"
        vf.save(tmp)
        vf.close()

        # 第二步：按码位子集。
        out = out_dir / f"noto_sc_{w}.ttf"
        opts = subset.Options()
        opts.layout_features = ["*"]      # 必须留 tnum（等宽数字），否则秒表会抖
        opts.name_IDs = ["*"]
        opts.name_legacy = True
        opts.notdef_outline = True        # 留 .notdef 的方框，缺字时看得见
        opts.recalc_bounds = True
        opts.recalc_average_width = True
        opts.drop_tables += ["DSIG"]
        opts.hinting = False              # Skia 不跑 TT 微调，留着白占体积
        opts.desubroutinize = True

        font = subset.load_font(str(tmp), opts)
        subsetter = subset.Subsetter(options=opts)
        subsetter.populate(unicodes=unicodes)
        subsetter.subset(font)
        subset.save_font(font, str(out), opts)
        font.close()
        tmp.unlink()

        kb = out.stat().st_size / 1024
        total += kb
        print(f"  {out.name:20s} {kb:8.0f} KB  ({kind})")

    print(f"合计 {total/1024:.2f} MB / {len(WEIGHTS)} 个字重")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(2)
    build(
        Path(sys.argv[1]),
        Path(sys.argv[2]),
        Path(__file__).resolve().parent.parent / "app" / "src" / "main",
    )
