# -*- coding: utf-8 -*-
"""列表行的**宽度预算**：用真实字体度量算，不靠眼看。

    python tools/row_budget.py

为什么要有这个脚本
------------------
`EventRow` 的右列是"数字 + 天"、下面还有"秒表"和"分类"。这三样右对齐到同一条
竖线，可它们**各自的右边缘并不在同一个位置**——「天」是一个真字符，占掉一份
advance；「今天」那一行不写「天」，于是它的数字会比别的行**右移**整整一格。

这种错位在代码里看不出来（每一行都是"右对齐"，逻辑上完全一致），只有把
字体里的 advance 乘上字号才看得见。所以算，不看。

数字口径
--------
`advance` 全部取自 `res/font/noto_sc_*.ttf` 的 `hmtx`，单位是 1/unitsPerEm。
`letterSpacing` 是 Compose 的 `TextStyle` 值（sp），乘字号前先除以字号得到 em 当量。

`RollingText` 例外：它把每个字符的 `letterSpacing` 清零，改用
`Arrangement.spacedBy(tracking)`——而 `spacedBy` 只在**字符之间**加空隙，
最后一个字符后面**不加**。所以滚动数字的右边缘是准的。
"""

import sys
from pathlib import Path

from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parent.parent
FONT_DIR = ROOT / "app/src/main/res/font"

# ── 令牌（和 Theme.kt / Bits.kt / SettingsStore.kt 对齐，改那边要改这里）──
SCREEN_DP = 360.0          # 录屏是 720px @2x
GUTTER_DP = 16.0           # RowInset
BAR_DP = 3.0               # 事件色竖线
BAR_GAP_DP = 13.0          # 竖线和标题之间
COL_GAP_DP = 12.0          # 左右两列之间
UNIT_START_DP = 3.0        # 「天」前面的 padding
CATEGORY_MAX_DP = 78.0     # 分类的 widthIn(max =)

DISPLAY_SP = 46.0          # displayLarge.fontSize
DISPLAY_TRACK_SP = -1.8    # displayLarge.letterSpacing
TITLE_SP = 18.0            # headlineSmall.fontSize
TITLE_TRACK_SP = -0.2      # headlineSmall.letterSpacing
UNIT_SP = 13.0             # labelMedium.fontSize（「天」用它）
UNIT_TRACK_SP = 0.6        # labelMedium.letterSpacing
CAT_SP = 11.5              # labelSmall.fontSize（分类用它）
CAT_TRACK_SP = 1.6         # labelSmall.letterSpacing
CLOCK_RATIO = 0.30         # ClockLine 的 fontSize = numSize * 0.30

# 行密度：名字、上下留白、数字字号、标题比例
DENSITIES = [
    ("COMPACT", 8.0, 36.0, 0.90),
    ("STANDARD", 13.0, 48.0, 1.00),
    ("LOOSE", 20.0, 60.0, 1.08),
]


def em(font_name, ch):
    """一个字符在某个字重里的 advance，单位 em。"""
    font = TTFont(str(FONT_DIR / font_name))
    upm = font["head"].unitsPerEm
    cmap = font.getBestCmap()
    if ord(ch) not in cmap:
        raise KeyError(f"{font_name} 里没有 U+{ord(ch):04X} {ch!r}")
    return font["hmtx"][cmap[ord(ch)]][0] / upm


def text_em(font_name, s, track_sp=0.0, size_sp=1.0):
    """一串字的 advance 合计，单位 em（含 Compose 的 letterSpacing）。

    Compose 的 letterSpacing 是**每个字符后面都加**（包括最后一个），
    所以布局宽度里含有那一段——这正是右对齐时最后那个字的墨迹会提前收住的原因。
    """
    base = sum(em(font_name, c) for c in s)
    return base + (track_sp / size_sp) * len(s)


def main():
    print("字体度量（advance / em）")
    for f, chars in [
        ("noto_sc_900.ttf", "0123456789"),
        ("noto_sc_500.ttf", "天生活"),
        ("noto_sc_700.ttf", "0123456789天"),
    ]:
        vals = {c: em(f, c) for c in chars}
        print(f"  {f}")
        for c, v in vals.items():
            print(f"    {c!r}  {v:.4f}")

    unit_em = text_em("noto_sc_500.ttf", "天", UNIT_TRACK_SP, UNIT_SP)
    cat_em = text_em("noto_sc_500.ttf", "生活", CAT_TRACK_SP, CAT_SP)
    print()
    print(f"「天」的盒宽 = {unit_em:.4f} em × {UNIT_SP}sp = {unit_em * UNIT_SP:.2f} dp")
    print(f"「生活」盒宽 = {cat_em:.4f} em × {CAT_SP}sp = {cat_em * CAT_SP:.2f} dp")

    content = SCREEN_DP - GUTTER_DP * 2
    after_bar = content - BAR_DP - BAR_GAP_DP
    columns = after_bar - COL_GAP_DP
    print()
    print(f"行内容宽 = {SCREEN_DP} − 2×{GUTTER_DP} = {content} dp")
    print(f"去掉竖线 = {content} − {BAR_DP} − {BAR_GAP_DP} = {after_bar} dp")
    print(f"两列可用 = {after_bar} − {COL_GAP_DP} = {columns} dp")

    print()
    print("每一档密度下的右列宽度与左列余量")
    for name, pad, num_sp, tscale in DENSITIES:
        title_sp = TITLE_SP * tscale
        unit_w = unit_em * UNIT_SP + UNIT_START_DP
        clock_sp = num_sp * CLOCK_RATIO
        print(f"\n  {name}  数字 {num_sp:.0f}sp  标题 {title_sp:.2f}sp  秒表 {clock_sp:.2f}sp")
        print(f"    秒表 / 标题 = {clock_sp / title_sp:.2f}"
              f"{'  ← 和标题同号，层级塌了' if clock_sp >= title_sp * 0.97 else ''}")
        for digits in (1, 2, 3, 4, 5):
            s = "8" * digits
            num_w = text_em("noto_sc_900.ttf", s, DISPLAY_TRACK_SP, num_sp) * num_sp
            right = num_w + unit_w
            right = max(right, CATEGORY_MAX_DP, cat_em * CAT_SP)
            left = columns - right
            title_chars = left / (em("noto_sc_700.ttf", "天") * title_sp
                                  + TITLE_TRACK_SP)
            flag = "  ← 左列 < 50%" if left < columns * 0.5 else ""
            print(f"    {digits} 位  数字 {num_w:6.2f}  右列 {right:6.2f}"
                  f"  左列 {left:6.2f} ({left / columns * 100:4.1f}%)"
                  f"  标题约 {title_chars:4.1f} 字{flag}")

    # ── 结论一：「今天」那一行的数字右边缘错位 ──
    print()
    print("「今天」行的错位（0.58.1 之前）")
    unit_w = unit_em * UNIT_SP + UNIT_START_DP
    print(f"  有「天」的行：数字右边缘 = 列右边缘 − {unit_w:.2f} dp")
    print(f"  无「天」的行：数字右边缘 = 列右边缘 − 0")
    print(f"  错位 = {unit_w:.2f} dp")
    assert unit_w > 2.0, "「天」那一格应当大于 2dp，否则这条结论不成立"

    # ── 结论二：秒表封顶之后，每一档都明显低于标题 ──
    print()
    print("秒表字号（0.30 × 数字，封顶 labelLarge 14sp）")
    clock_cap = 14.0
    bad = []
    for name, pad, num_sp, tscale in DENSITIES:
        title_sp = TITLE_SP * tscale
        raw = num_sp * CLOCK_RATIO
        capped = min(raw, clock_cap)
        ratio = capped / title_sp
        mark = "" if ratio < 0.85 else "  ← 追平标题"
        if ratio >= 0.85:
            bad.append(name)
        print(f"  {name:9s} 数字 {num_sp:4.0f}  0.30 → {raw:5.2f}  封顶后 {capped:5.2f}"
              f"  标题 {title_sp:5.2f}  比值 {ratio:.2f}{mark}")
    assert not bad, f"这些档位的秒表追平了标题：{bad}"

    print()
    print("全部断言通过")


if __name__ == "__main__":
    sys.exit(main())
