import sys
import calendar
from datetime import date, datetime, timedelta


def clamp_to_month(y, m, d):
    return date(y, m, min(max(d, 1), calendar.monthrange(y, m)[1]))


def next_weekly(anchor, today):
    diff = (today - anchor).days
    return anchor + timedelta(days=(diff // 7 + (0 if diff % 7 == 0 else 1)) * 7)


def next_monthly(anchor, today):
    y, m = today.year, today.month
    for _ in range(36):
        c = clamp_to_month(y, m, anchor.day)
        if c >= today:
            return c
        m += 1
        if m > 12:
            m, y = 1, y + 1
    return clamp_to_month(y, m, anchor.day)


def next_yearly(anchor, today):
    y = today.year
    for _ in range(12):
        c = clamp_to_month(y, anchor.month, anchor.day)
        if c >= today:
            return c
        y += 1
    return clamp_to_month(y, anchor.month, anchor.day)


def next_date(anchor, rule, today):
    if rule == 'NONE':
        return anchor
    if anchor >= today:
        return anchor
    if rule == 'DAILY':
        return today
    if rule == 'WEEKLY':
        return next_weekly(anchor, today)
    if rule == 'MONTHLY':
        return next_monthly(anchor, today)
    return next_yearly(anchor, today)


def previous_date(anchor, rule, target):
    if rule == 'NONE':
        return anchor
    if rule == 'DAILY':
        return target - timedelta(days=1)
    if rule == 'WEEKLY':
        return target - timedelta(weeks=1)
    if rule == 'MONTHLY':
        y, m = target.year, target.month - 1
        if m == 0:
            m, y = 12, y - 1
        return clamp_to_month(y, m, anchor.day)
    return clamp_to_month(target.year - 1, anchor.month, anchor.day)


def occurs_on(anchor, rule, d):
    if d < anchor:
        return False
    if rule == 'NONE':
        return anchor == d
    if rule == 'DAILY':
        return True
    if rule == 'WEEKLY':
        return (d - anchor).days % 7 == 0
    if rule == 'MONTHLY':
        return d.day == clamp_to_month(d.year, d.month, anchor.day).day
    if rule == 'YEARLY':
        return d.month == anchor.month and d.day == clamp_to_month(d.year, d.month, anchor.day).day
    return False


def countdown_days(anchor, rule, today, include_start):
    target = next_date(anchor, rule, today)
    raw = (target - today).days
    return (raw - 1) if (include_start and raw < 0) else raw, target


def info_of(anchor, rule, today, include_start):
    target = next_date(anchor, rule, today)
    raw = (target - today).days
    days = raw - 1 if (include_start and raw < 0) else raw
    if rule == 'NONE' and raw < 0:
        return 'ELAPSED', -days, target
    return 'COUNTDOWN', days, target


def progress_countdown(anchor, rule, today):
    target = next_date(anchor, rule, today)
    start = previous_date(anchor, rule, target)
    total = (target - start).days
    if total <= 0:
        return 1.0
    return min(max((today - start).days / total, 0.0), 1.0)


def precise_countdown(anchor, rule, now, include_start=False):
    today = now.date()
    target = next_date(anchor, rule, today)
    bonus = 1 if include_start else 0
    if rule == 'NONE' and target < today:
        frm = datetime.combine(anchor - timedelta(days=bonus), datetime.min.time())
        secs = int((now - frm).total_seconds())
        neg = secs < 0
        if neg:
            secs = -secs
        return secs // 86400, (secs % 86400) // 3600, (secs % 3600) // 60, secs % 60, neg
    if target < today:
        to = target - timedelta(days=bonus)
    else:
        to = target + timedelta(days=1)
    secs = int((datetime.combine(to, datetime.min.time()) - now).total_seconds())
    neg = secs < 0
    if neg:
        secs = -secs
    return secs // 86400, (secs % 86400) // 3600, (secs % 3600) // 60, secs % 60, neg


GROUPS = {}


def add(group, msg):
    GROUPS.setdefault(group, []).append(msg)


ANCHORS = []
for _m in range(1, 13):
    for _d in (1, 15, 28, 29, 30, 31):
        try:
            ANCHORS.append(date(2024, _m, _d))
        except ValueError:
            pass
ANCHORS += [date(2023, 2, 28), date(2024, 2, 29), date(2025, 12, 31), date(2026, 1, 1)]

for rule in ('DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY'):
    for a in ANCHORS:
        d = a
        while d <= date(2031, 12, 31):
            if occurs_on(a, rule, d) != (next_date(a, rule, d) == d):
                add('G1 occurs_on≠next_date',
                    f"anchor={a} rule={rule} d={d} occurs={occurs_on(a, rule, d)}")
            d += timedelta(days=1)

for rule in ('DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY'):
    for a in ANCHORS:
        d = date(2024, 1, 1)
        while d <= date(2028, 12, 31):
            if next_date(a, rule, d) < d:
                add('G2 next_date<today', f"anchor={a} rule={rule} today={d}")
            d += timedelta(days=7)

for rule in ('NONE', 'DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY'):
    for a in ANCHORS:
        d = date(2024, 1, 1)
        while d <= date(2028, 12, 31):
            t = next_date(a, rule, d)
            if previous_date(a, rule, t) > t:
                add('G3 previous>target', f"anchor={a} rule={rule} target={t}")
            d += timedelta(days=11)

for rule in ('NONE', 'DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY'):
    for a in ANCHORS:
        d = date(2024, 1, 1)
        while d <= date(2028, 12, 31):
            p = progress_countdown(a, rule, d)
            if not (0.0 <= p <= 1.0):
                add('G4 progress 越界', f"{p} anchor={a} rule={rule} today={d}")
            d += timedelta(days=13)

for inc in (False, True):
    for rule in ('NONE', 'WEEKLY', 'MONTHLY', 'YEARLY'):
        for a in ANCHORS:
            for hour in (0, 1, 10, 23):
                d = date(2025, 1, 1)
                while d <= date(2027, 12, 31):
                    now = datetime.combine(d, datetime.min.time()) + timedelta(hours=hour)
                    dd, _hh, _mm, _ss, neg = precise_countdown(a, rule, now, inc)
                    mode, big, _ = info_of(a, rule, d, inc)
                    exact_midnight = (hour == 0)
                    switched = (mode == 'ELAPSED')
                    want_dd = big + 1 if (exact_midnight and big >= 0 and not switched) else big
                    if neg != (big < 0) or dd != want_dd:
                        add('G5 precise≠大数字',
                            f"inc={inc} anchor={a} rule={rule} now={now} "
                            f"precise={'-' if neg else ''}{dd}d 大数字={big}")
                    d += timedelta(days=17)

CASES = [
    ("一次性，5 天后",         date(2026,10,30), 'NONE',    date(2026,10,25), False, 5),
    ("一次性，就是今天",       date(2026,10,25), 'NONE',    date(2026,10,25), False, 0),
    ("一次性，5 天前",         date(2026,10,20), 'NONE',    date(2026,10,25), False, 5),
    ("一次性，5 天前含起始日", date(2026,10,20), 'NONE',    date(2026,10,25), True,  6),
    ("每年 3/15，从 10/25 看", date(2020,3,15),  'YEARLY',  date(2026,10,25), False, 141),
    ("每月 31 号，2/27 看",    date(2020,1,31),  'MONTHLY', date(2026,2,27),  False, 1),
    ("每月 31 号，4/29 看",    date(2020,1,31),  'MONTHLY', date(2026,4,29),  False, 1),
    ("每周，锚点 7 天前",      date(2026,10,18), 'WEEKLY',  date(2026,10,25), False, 0),
    ("每天",                   date(2026,1,1),   'DAILY',   date(2026,10,25), False, 0),
]
for name, a, rule, today, inc, want in CASES:
    mode, got, target = info_of(a, rule, today, inc)
    if got != want:
        add('G6 倒数期望值',
            f"{name}: 期望 {want}，实际 {got}（mode={mode} target={target}）")

FLOOR, CEIL = 0.42, 0.28
BG_LIGHT = 0xF7FBFF
BG_DARK = 0x101E2C


def _lum(rgb):
    def lin(c):
        s = c / 255
        return s / 12.92 if s <= 0.04045 else ((s + 0.055) / 1.055) ** 2.4
    return 0.2126 * lin((rgb >> 16) & 0xFF) + 0.7152 * lin((rgb >> 8) & 0xFF) + 0.0722 * lin(rgb & 0xFF)


def _readable(argb, dark):
    l = _lum(argb)
    if dark:
        if l >= FLOOR:
            return argb
        t = min(max((FLOOR - l) / (1 - l), 0.0), 1.0)
        def up(c):
            return max(0, min(255, int(c + (255 - c) * t)))
        return (up((argb >> 16) & 0xFF) << 16) | (up((argb >> 8) & 0xFF) << 8) | up(argb & 0xFF)

    if l <= CEIL or l <= 0:
        return argb

    def scaled(k):
        def f(c):
            return max(0, min(255, int(c * k)))
        return (f((argb >> 16) & 0xFF) << 16) | (f((argb >> 8) & 0xFF) << 8) | f(argb & 0xFF)

    lo, hi = 0.0, 1.0
    for _ in range(8):
        mid = (lo + hi) / 2
        if _lum(scaled(mid)) > CEIL:
            hi = mid
        else:
            lo = mid
    return scaled(lo)


PROBE_COLORS = [
    0xFFFFFF, 0xFFFDE7, 0x00FF00, 0x00FFFF, 0xFF0000, 0x0000FF,
    0x14263C, 0x9AA5B1, 0x7EC8F0, 0xFFD9E0, 0x000000, 0x808080,
    0xFFFF00, 0xF0F0F0, 0x2E8B57,
]
for _c in PROBE_COLORS:
    _out = _readable(_c, False)
    _cr = (_lum(BG_LIGHT) + 0.05) / (_lum(_out) + 0.05)
    if _cr < 3.0:
        add('G7 浅色对比不足', f"#{_c:06X} → #{_out:06X} 只有 {_cr:.2f}:1（大字要 3:1）")

    _out = _readable(_c, True)
    _cr = (_lum(_out) + 0.05) / (_lum(BG_DARK) + 0.05)
    if _cr < 3.0:
        add('G7 深色对比不足', f"#{_c:06X} → #{_out:06X} 只有 {_cr:.2f}:1（大字要 3:1）")


def add_months(d, n):
    y = d.year + (d.month - 1 + n) // 12
    m = (d.month - 1 + n) % 12 + 1
    return clamp_to_month(y, m, d.day)


def period_between(a, b):
    total = (b.year - a.year) * 12 + (b.month - a.month)
    days = b.day - a.day
    if total > 0 and days < 0:
        total -= 1
        days = (b - add_months(a, total)).days
    elif total < 0 and days > 0:
        total += 1
        days -= calendar.monthrange(b.year, b.month)[1]
    years = int(total / 12)
    months = total - years * 12
    return years, months, days


def span_parts(a, b, with_years, extra=False):
    y, m, d = period_between(a, b + timedelta(days=1) if extra else b)
    months = m if with_years else y * 12 + m
    parts = []
    if with_years and y > 0:
        parts.append((y, '年'))
    if months > 0:
        parts.append((months, '月'))
    if d > 0:
        parts.append((d, '日'))
    return parts or [(0, '日')], months


SPAN_CASES = [
    (date(2026, 10, 27), date(2028, 1, 5),  '1年2月9日',  '14月9日'),
    (date(2026, 10, 27), date(2026, 11, 27), '1月',       '1月'),
    (date(2026, 10, 27), date(2026, 10, 27), '0日',       '0日'),
    (date(2026, 1, 31), date(2026, 3, 1),   '1月1日',    '1月1日'),
    (date(2026, 1, 31), date(2026, 2, 28),  '28日',      '28日'),
    (date(2024, 2, 29), date(2025, 3, 1),   '1年1日',    '12月1日'),
    (date(2026, 10, 27), date(2027, 10, 27), '1年',       '12月'),
    (date(2026, 3, 31), date(2026, 4, 30),  '30日',      '30日'),
]


def _fmt(parts):
    return ''.join('%d%s' % p for p in parts)


for a, b, want_ymd, want_md in SPAN_CASES:
    got_ymd, _ = span_parts(a, b, True)
    got_md, _ = span_parts(a, b, False)
    if _fmt(got_ymd) != want_ymd:
        add('G8 年月日拆解', f"{a} → {b}: 期望 {want_ymd}，实际 {_fmt(got_ymd)}")
    if _fmt(got_md) != want_md:
        add('G8 月日拆解', f"{a} → {b}: 期望 {want_md}，实际 {_fmt(got_md)}")


import random
random.seed(20261027)
for _ in range(4000):
    _a = date(2020, 1, 1) + timedelta(days=random.randrange(0, 4000))
    _b = _a + timedelta(days=random.randrange(0, 3000))
    _y, _m, _d = period_between(_a, _b)

    if _m < 0 or _m > 11:
        add('G8 月份越界', f"{_a} → {_b}: 月分量 {_m}")
    if _d < 0:
        add('G8 天数为负', f"{_a} → {_b}: 天分量 {_d}")
    if add_months(_a, _y * 12 + _m) + timedelta(days=_d) != _b:
        add('G8 无法还原', f"{_a} → {_b}: {_y}年{_m}月{_d}日 加回去对不上")

    _, _tm = span_parts(_a, _b, False)
    if add_months(_a, _tm) > _b:
        add('G8 月数偏大', f"{_a} → {_b}: 已经 {_tm} 个整月了，不对")
    if _tm > 0 and add_months(_a, _tm - 1) > _b:
        add('G8 月数偏小', f"{_a} → {_b}: 应该还能再多一个月")


EXTRA_DAY_CASES = [
    (date(2026, 10, 27), date(2028, 1, 5),  '1年2月10日'),
    (date(2026, 10, 27), date(2026, 11, 27), '1月1日'),
    (date(2026, 1, 31), date(2026, 2, 28),  '1月1日'),
    (date(2026, 1, 1),  date(2026, 1, 31),  '1月'),
    (date(2026, 10, 27), date(2026, 10, 27), '1日'),
]

for a, b, want in EXTRA_DAY_CASES:
    got, _ = span_parts(a, b, True, extra=True)
    if _fmt(got) != want:
        add('G8 含起始日', f"{a} → {b}: 期望 {want}，实际 {_fmt(got)}")


for inc in (False, True):
    for a in ANCHORS:
        for rule in ('NONE', 'DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY'):
            d = date(2024, 1, 1)
            while d <= date(2028, 12, 31):
                mode, days, target = info_of(a, rule, d, inc)
                raw = (next_date(a, rule, d) - d).days

                if mode == 'ELAPSED':
                    if rule != 'NONE' or raw >= 0:
                        add('G9 不该折却折了',
                            f"anchor={a} rule={rule} today={d} raw={raw}")
                    if days < 0:
                        add('G9 折完还是负数',
                            f"anchor={a} today={d} days={days}")
                    want = (d - a).days + (1 if inc else 0)
                    if days != want:
                        add('G9 折完口径不对',
                            f"anchor={a} today={d} inc={inc}: 期望 {want}，实际 {days}")
                else:
                    if rule == 'NONE' and raw >= 0 and days != raw:
                        add('G9 没折却改了数',
                            f"anchor={a} today={d} raw={raw} days={days}")
                d += timedelta(days=1)

for inc in (False, True):
    mode, days, _ = info_of(date(2026, 10, 31), 'NONE', date(2026, 10, 31), inc)
    if mode != 'COUNTDOWN' or days != 0:
        add('G9 今天被折成计时', f"inc={inc}: mode={mode} days={days}")


TOTAL = sum(len(v) for v in GROUPS.values())
print("不变量对拍：%d 组，问题 %d 条" % (9, TOTAL))
for k, v in GROUPS.items():
    print("  %s: %d" % (k, len(v)))
    for msg in v[:6]:
        print("      " + msg)
if TOTAL == 0:
    print("  全部通过")

sys.exit(1 if TOTAL else 0)
