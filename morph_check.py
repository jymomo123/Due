import math
import sys

GROUPS = {}


def add(group, msg):
    GROUPS.setdefault(group, []).append(msg)


def shell_of(p, o, t):
    l = o[0] + (t[0] - o[0]) * p
    top = o[1] + (t[1] - o[1]) * p
    sx = (o[2] + (t[2] - o[2]) * p) / t[2]
    sy = (o[3] + (t[3] - o[3]) * p) / t[3]
    return l, top, sx, sy


def morph_to(p, o, t, r, dest_left, dest_top, k):
    l, top, sx, sy = shell_of(p, o, t)
    on_left = l + (r[0] - t[0]) * sx
    on_top = top + (r[1] - t[1]) * sy
    dl = r[0] + (dest_left - r[0]) * (1 - p)
    dt = r[1] + (dest_top - r[1]) * (1 - p)
    tx = (dl - on_left) / sx
    ty = (dt - on_top) / sy
    kw = 1.0 + (k - 1.0) * (1.0 - p)
    scx = kw / sx
    scy = kw / sy
    return tx, ty, scx, scy


def screen(p, o, t, r, dest_left, dest_top, k):
    l, top, sx, sy = shell_of(p, o, t)
    tx, ty, scx, scy = morph_to(p, o, t, r, dest_left, dest_top, k)
    left = l + ((r[0] - t[0]) + tx) * sx
    tp = top + ((r[1] - t[1]) + ty) * sy
    w = r[2] * scx * sx
    h = r[3] * scy * sy
    local_top = (r[1] - t[1]) + ty
    local_bottom = local_top + r[3] * scy
    return dict(left=left, top=tp, w=w, h=h,
                local_top=local_top, local_bottom=local_bottom,
                sx=sx, sy=sy, scx=scx, scy=scy)


def net_scale(p, k):
    return 1.0 + (k - 1.0) * (1.0 - p)


EXIT_FROM = 0.67
EXIT_TO = 0.06


def page_exit_alpha(p):
    if p >= EXIT_FROM:
        return 1.0
    if p <= EXIT_TO:
        return 0.0
    t = (EXIT_FROM - p) / (EXIT_FROM - EXIT_TO)
    return 1.0 - t * t * t


def card_chrome_alpha(p):
    return min(1.0, max(0.0, (p - 0.62) / 0.38))


def row_body_alpha(p):
    return min(1.0, max(0.0, (0.10 - p) / 0.08))


def row_settle_scale(p):
    return 1.0 + 0.12 * min(1.0, max(-0.5, p / 0.30))


ROW = (36.0, 2100.0, 1008.0, 216.0)
CARD = (60.0, 400.0, 960.0, 780.0)

ELEMENTS = [
    ("标题",     120.0, 466.0, 500.0,  90.0,  78.0,  72.0),
    ("大数字",   120.0, 560.0, 300.0, 600.0, 570.0, 144.0),
    ("日期",     120.0, 690.0, 420.0,  48.0,  42.0,  36.0),
]

PAD_H = 48.0
BAR_GAP = 48.0


def dest_for(name, r, k):
    if name == "大数字":
        left = ROW[0] + ROW[2] - PAD_H - r[2] * k
        top = ROW[1] + (ROW[3] - r[3] * k) * 0.5
    else:
        left = ROW[0] + PAD_H + BAR_GAP
        top = ROW[1] + 14.0
    return left, top


STEPS = [i / 100.0 for i in range(100, -1, -1)]

for name, left, top, w, h, card_px, row_px in ELEMENTS:
    r = (left, top, w, h)
    k = row_px / card_px
    dl, dt = dest_for(name, r, k)

    s1 = screen(1.0, ROW, CARD, r, dl, dt, k)
    for axis, got, want in (("左", s1["left"], r[0]), ("上", s1["top"], r[1])):
        if abs(got - want) > 0.01:
            add("G1 端点", f"{name} p=1 {axis}：期望 {want:.2f}，实际 {got:.2f}")
    for axis, got, want in (("宽", s1["w"], r[2]), ("高", s1["h"], r[3])):
        if abs(got - want) > 0.01:
            add("G1 端点", f"{name} p=1 {axis}：期望 {want:.2f}（原尺寸），实际 {got:.2f}")

    s0 = screen(0.0, ROW, CARD, r, dl, dt, k)
    for axis, got, want in (("左", s0["left"], dl), ("上", s0["top"], dt)):
        if abs(got - want) > 0.01:
            add("G1 端点", f"{name} p=0 {axis}：期望 {want:.2f}，实际 {got:.2f}")
    for axis, got, want in (("宽", s0["w"], r[2] * k), ("高", s0["h"], r[3] * k)):
        if abs(got - want) > 0.01:
            add("G1 端点", f"{name} p=0 {axis}：期望 {want:.2f}，实际 {got:.2f}")

    for p in STEPS:
        s = screen(p, ROW, CARD, r, dl, dt, k)
        rw = s["w"] / r[2]
        rh = s["h"] / r[3]
        want = net_scale(p, k)
        if abs(rw - want) > 1e-6 or abs(rh - want) > 1e-6:
            add("G2 等比", f"{name} p={p:.2f}：横向 {rw:.4f} / 竖向 {rh:.4f}，"
                f"期望都是 {want:.4f}")
            break

    lo_l, hi_l = min(r[0], dl), max(r[0], dl)
    lo_t, hi_t = min(r[1], dt), max(r[1], dt)
    prev = None
    for p in STEPS:
        s = screen(p, ROW, CARD, r, dl, dt, k)
        if not (lo_l - 0.01 <= s["left"] <= hi_l + 0.01):
            add("G3 路径", f"{name} p={p:.2f}：左 {s['left']:.2f} 越出 [{lo_l:.2f}, {hi_l:.2f}]")
            break
        if not (lo_t - 0.01 <= s["top"] <= hi_t + 0.01):
            add("G3 路径", f"{name} p={p:.2f}：上 {s['top']:.2f} 越出 [{lo_t:.2f}, {hi_t:.2f}]")
            break
        if prev is not None:
            if (s["left"] - prev[0]) * (1 if dl > r[0] else -1) < -0.5:
                add("G3 路径", f"{name} p={p:.2f}：左往回走了 {(s['left'] - prev[0]):.2f}")
                break
            if (s["top"] - prev[1]) * (1 if dt > r[1] else -1) < -0.5:
                add("G3 路径", f"{name} p={p:.2f}：上往回走了 {(s['top'] - prev[1]):.2f}")
                break
        prev = (s["left"], s["top"])

    for p in STEPS:
        s = screen(p, ROW, CARD, r, dl, dt, k)
        if s["local_top"] < -0.5 or s["local_bottom"] > CARD[3] + 0.5:
            a = page_exit_alpha(p)
            if a >= 0.15:
                add("G4 裁剪", f"{name} p={p:.2f}：局部 [{s['local_top']:.0f}, "
                    f"{s['local_bottom']:.0f}] 越出卡片高 {CARD[3]:.0f}，"
                    f"而那时整卡还有 {a:.2f} 的不透明度")
            break

for i in range(0, 101):
    p = i / 100.0
    if page_exit_alpha(p) > 0.2 and row_body_alpha(p) > 0.2:
        add("G5 交接", f"p={p:.2f}：卡片 {page_exit_alpha(p):.2f} / 行 {row_body_alpha(p):.2f} 同时可见")
        break

ZETA = 0.66
OVERSHOOT = math.exp(-math.pi * ZETA / math.sqrt(1 - ZETA * ZETA))
P_MIN = -OVERSHOOT
if not (0.05 <= OVERSHOOT <= 0.08):
    add("G6 回弹", f"过冲 {OVERSHOOT:.3f} 不在 5%~8%：太小看不见，太大像弹球")
if page_exit_alpha(P_MIN) > 0.01:
    add("G6 回弹", f"p={P_MIN:.3f} 时卡片还有 {page_exit_alpha(P_MIN):.2f}，回弹会和飞行叠在一起")
if row_body_alpha(P_MIN) < 0.99:
    add("G6 回弹", f"p={P_MIN:.3f} 时行只有 {row_body_alpha(P_MIN):.2f}，回弹看不见")
sq = row_settle_scale(P_MIN)
if not (0.96 <= sq <= 0.99):
    add("G6 回弹", f"落位缩放在过冲点 {sq:.3f}，期望 0.96~0.99（压一下但不至于缩一圈）")

if abs(row_settle_scale(0.0) - 1.0) > 1e-6:
    add("G7 落位", f"rowSettleScale(0) = {row_settle_scale(0.0):.4f}，必须是 1")
if abs(row_body_alpha(0.0) - 1.0) > 1e-6:
    add("G7 落位", f"rowBodyAlpha(0) = {row_body_alpha(0.0):.4f}，必须是 1")
if page_exit_alpha(0.0) > 1e-6:
    add("G7 落位", f"pageExitAlpha(0) = {page_exit_alpha(0.0):.4f}，必须是 0")

def row_fits(origin_w, target_w):
    return origin_w <= target_w * 1.10

if not row_fits(1008.0, 960.0):
    add("G8 横屏", "竖屏（行 1008 / 卡 960）被判成放不下")
if row_fits(2304.0, 1380.0):
    add("G8 横屏", "横屏（行 2304 / 卡 1380）被判成放得下——那一截会被 verticalScroll 裁掉")

for name, left, top, w, h, card_px, row_px in ELEMENTS:
    r = (left, top, w, h)
    k = row_px / card_px
    dl, dt = dest_for(name, r, k)
    tx, ty, scx, scy = morph_to(1.0, ROW, CARD, r, dl, dt, k)
    if abs(tx) > 1e-3 or abs(ty) > 1e-3:
        add("G9 取消", f"{name} p=1 位移 ({tx:.3f}, {ty:.3f})，noMorph 是 (0, 0)")
    if abs(scx - 1.0) > 1e-6 or abs(scy - 1.0) > 1e-6:
        add("G9 取消", f"{name} p=1 缩放 ({scx:.4f}, {scy:.4f})，noMorph 是 (1, 1)")

EPS = 1e-3
if abs(page_exit_alpha(EXIT_FROM) - 1.0) > 1e-6:
    add("G10 同步", f"p={EXIT_FROM} 处 alpha = {page_exit_alpha(EXIT_FROM):.4f}，必须是 1")
if abs(page_exit_alpha(EXIT_FROM - EPS) - 1.0) > 2e-6:
    add("G10 同步", f"起点导数不为 0：p 只掉了 {EPS}，alpha 已经掉到 "
        f"{page_exit_alpha(EXIT_FROM - EPS):.6f}——会看出折角")
if page_exit_alpha(0.5) < 0.95:
    add("G10 同步", f"走掉一半（p=0.50）只剩 {page_exit_alpha(0.5):.2f}，淡得太快")
if page_exit_alpha(1 / 3) < 0.8:
    add("G10 同步", f"卡片缩到三分之一（p=0.33）只剩 {page_exit_alpha(1 / 3):.2f}，"
        "读起来是「已经变透明」而不是「开始变透明」")
if (EXIT_FROM - EXIT_TO) < 0.5:
    add("G10 同步", f"淡出跨度只有 {EXIT_FROM - EXIT_TO:.2f}，太窄（上一版是 0.24）")
if card_chrome_alpha(1.0) < 0.999:
    add("G10 同步", f"进场到 p=1 背景层只有 {card_chrome_alpha(1.0):.3f}，必须全实")
if card_chrome_alpha(0.67) > 0.2:
    add("G10 同步", f"进场到 p=0.67 背景层已经 {card_chrome_alpha(0.67):.2f}"
        "——卡片还在路上飞，会被压在玻璃底下成灰影")

TOTAL = sum(len(v) for v in GROUPS.values())
print("container transform 对拍：%d 组，问题 %d 条" % (10, TOTAL))
for k2, v in GROUPS.items():
    print("  %s: %d" % (k2, len(v)))
    for msg in v[:6]:
        print("      " + msg)
if TOTAL == 0:
    print("  全部通过")

sys.exit(1 if TOTAL else 0)
