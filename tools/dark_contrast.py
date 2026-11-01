# -*- coding: utf-8 -*-
"""深色主题下「凹槽 / 滑块 / 墨色」的合成与对比度对拍。

纯计算，不读代码——数字写死在这里，是为了让改动前后可以并排放在一张表里。
改令牌的话，这里的常量要跟着改，否则算出来的是另一个 App 的对比度。

用法：python tools/dark_contrast.py
"""


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def over(fg, a, bg):
    """fg 以 a 的不透明度压在不透明的 bg 上。"""
    return tuple(round(f * a + b * (1 - a)) for f, b in zip(fg, bg))


def lin(c):
    c = c / 255
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def lum(rgb):
    r, g, b = (lin(x) for x in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def ratio(a, b):
    la, lb = lum(a), lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def show(rgb):
    return "#%02X%02X%02X" % rgb


BASE = hex_rgb("#101E2C")     # DarkGlass.gradient 中间那一档
INK = hex_rgb("#E8F1FA")      # 正文
INK_MID = hex_rgb("#93AAC4")  # 次要
INK_SOFT = hex_rgb("#6E88A5")  # 最淡
WHITE = hex_rgb("#FFFFFF")

rows = []


def row(name, fg, bg, note=""):
    rows.append((name, show(bg), show(fg), ratio(fg, bg), note))


# ── SegmentedChoice：滑块 ──────────────────────────────────────
old_track = over(INK, 0.18, BASE)
new_track = over(INK, 0.14, BASE)

old_thumb = over(WHITE, 0.95, old_track)          # copy(alpha = 0.95f) 覆盖
new_thumb = over(WHITE, 0.15 * 1.125, new_track)  # 跟令牌走

row("滑块·选中字（旧）", INK, old_thumb, "白底白字")
row("滑块·选中字（新）", INK, new_thumb, "薄玻璃")
row("凹槽·未选中字（旧）", INK_MID, old_track, "卡在 4.5 边上")
row("凹槽·未选中字（新）", INK_MID, new_track, "")

# ── SlidingSegments：GlassInset 凹槽 ──────────────────────────
old_inset = over(INK, 0.16, BASE)
new_inset = over(INK, 0.14, BASE)

row("月/年凹槽·未选中（旧）", INK_SOFT, old_inset, "低于 3:1")
row("月/年凹槽·未选中（新）", INK_MID, new_inset, "")

# ── 参照 ──────────────────────────────────────────────────────
row("页面底色·正文", INK, BASE, "令牌标称 14.7")
row("页面底色·次要", INK_MID, BASE, "令牌标称 7.1")
row("页面底色·最淡", INK_SOFT, BASE, "令牌标称 4.8")

print(f"{'':30s} {'背景':>9s} {'文字':>9s} {'对比度':>7s}  备注")
print("-" * 78)
for name, bg, fg, r, note in rows:
    flag = "  " if r >= 4.5 else ("! " if r >= 3 else "XX")
    print(f"{flag}{name:28s} {bg:>9s} {fg:>9s} {r:6.2f}  {note}")

print()
print("滑块 vs 凹槽的亮度比：")
print("  旧 %.2f×" % (lum(old_thumb) / lum(old_track)))
print("  新 %.2f×" % (lum(new_thumb) / lum(new_track)))
