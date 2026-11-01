"""二十四节气算法原型验证。

思路：节气就是太阳视黄经达到 15° 整数倍的瞬间。
用 Meeus《Astronomical Algorithms》第 25 章的太阳视黄经近似式，
再用牛顿迭代反解出对应的儒略日。

先用 Python 跑通、和已知日期对账，再移植到 Kotlin。
"""
import math
import datetime

D2R = math.pi / 180.0

# 从春分（黄经 0°）开始，每 15° 一个节气
TERMS = [
    "春分", "清明", "谷雨", "立夏", "小满", "芒种",
    "夏至", "小暑", "大暑", "立秋", "处暑", "白露",
    "秋分", "寒露", "霜降", "立冬", "小雪", "大雪",
    "冬至", "小寒", "大寒", "立春", "雨水", "惊蛰",
]

CST = datetime.timezone(datetime.timedelta(hours=8))


def apparent_solar_longitude(jd):
    """太阳视黄经（度，0..360）。jd 为儒略日。"""
    t = (jd - 2451545.0) / 36525.0
    l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t      # 几何平黄经
    m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t        # 平近点角
    m_rad = m * D2R
    c = ((1.914602 - 0.004817 * t - 0.000014 * t * t) * math.sin(m_rad)
         + (0.019993 - 0.000101 * t) * math.sin(2 * m_rad)
         + 0.000289 * math.sin(3 * m_rad))                     # 中心差
    omega = 125.04 - 1934.136 * t
    # 减去光行差与章动，得到视黄经
    return (l0 + c - 0.00569 - 0.00478 * math.sin(omega * D2R)) % 360.0


def solve_term(year, target_lon):
    """求某年太阳视黄经等于 target_lon 的儒略日。"""
    # 锚点：2000 年春分 ≈ 3 月 20 日 07:12 UTC
    equinox = 2451623.8 + (year - 2000) * 365.2422
    # 黄经 > 270° 的五个节气（小寒 285…惊蛰 345）落在春分**之前**，偏移量取负。
    # 注意阈值必须是 > 而不是 >=：270° 正是冬至本身，它在春分**之后**（12 月），
    # 写成 >= 会让冬至整体倒退一年。
    deg = target_lon - 360.0 if target_lon > 270.0 else target_lon
    jd = equinox + deg / 360.0 * 365.2422

    for _ in range(12):
        lon = apparent_solar_longitude(jd)
        diff = (lon - target_lon + 180.0) % 360.0 - 180.0
        if abs(diff) < 1e-7:
            break
        jd -= diff / 0.9856473          # 太阳每天约走 0.9856°
    return jd


def jd_to_cst(jd):
    unix_ms = (jd - 2440587.5) * 86400000.0
    return (datetime.datetime(1970, 1, 1, tzinfo=datetime.timezone.utc)
            + datetime.timedelta(milliseconds=unix_ms)).astimezone(CST)


def terms_of_year(year):
    out = []
    for i, name in enumerate(TERMS):
        target = (i * 15.0) % 360.0
        dt = jd_to_cst(solve_term(year, target))
        out.append((name, dt.date(), dt.strftime("%H:%M")))
    out.sort(key=lambda x: x[1])
    return out


# 公认的 2026 年节气日期（北京时间），用来对账
EXPECT_2026 = {
    "小寒": "2026-01-05", "大寒": "2026-01-20", "立春": "2026-02-04",
    "雨水": "2026-02-18", "惊蛰": "2026-03-05", "春分": "2026-03-20",
    "清明": "2026-04-05", "谷雨": "2026-04-20", "立夏": "2026-05-05",
    "小满": "2026-05-21", "芒种": "2026-06-05", "夏至": "2026-06-21",
    "小暑": "2026-07-07", "大暑": "2026-07-23", "立秋": "2026-08-07",
    "处暑": "2026-08-23", "白露": "2026-09-07", "秋分": "2026-09-23",
    "寒露": "2026-10-08", "霜降": "2026-10-23", "立冬": "2026-11-07",
    "小雪": "2026-11-22", "大雪": "2026-12-07", "冬至": "2026-12-22",
}

if __name__ == "__main__":
    for y in (2025, 2026, 2027):
        print(f"===== {y} =====")
        for name, d, hm in terms_of_year(y):
            mark = ""
            if y == 2026:
                exp = EXPECT_2026[name]
                mark = "  ✓" if str(d) == exp else f"  ✗ 期望 {exp}"
            print(f"  {name}  {d} {hm}{mark}")

    print()
    bad = 0
    for name, d, hm in terms_of_year(2026):
        if str(d) != EXPECT_2026[name]:
            bad += 1
    print(f"2026 对账：{24 - bad}/24 正确")
