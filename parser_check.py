import re
import sys
import calendar
import pathlib
from datetime import date, timedelta

ROOT = pathlib.Path(__file__).resolve().parent

TODAY = date(2026, 10, 25)

absolute = re.compile(r"(\d{4})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})\s*[日号]?")
everyYear = re.compile(r"每\s*年\s*(?:(\d{1,2})\s*月\s*)?(\d{1,2})\s*[日号]?")
everyMonth = re.compile(r"每\s*(?:个)?\s*月\s*(\d{1,2})\s*[日号]")
nextMonth = re.compile(r"下\s*个?\s*月\s*(\d{1,2})\s*[日号]")
monthDay = re.compile(r"(\d{1,2})\s*[月\-/]\s*(\d{1,2})\s*[日号]?")
relativeDay = re.compile(r"(大后天|后天|明天|明日|今天|今日)")
offset = re.compile(r"(\d{1,3}|[一二两三四五六七八九十]{1,3})\s*(天|日|周|星期|个月|月|年)\s*(后|以后|之后|前|以前|之前)")
weekday = re.compile(r"(下下|下|本|这|每)?\s*(?:周|星期|礼拜)\s*([一二三四五六日天1-7])")
repeatWord = re.compile(r"每\s*(?:个)?\s*(?:年|月|周|星期|天|日)")

YEARLY_WORDS = ["生日", "纪念日", "周年", "结婚", "忌日", "祭日", "入职"]
MONTHLY_WORDS = ["房租", "还款", "信用卡", "工资", "账单", "月供", "花呗", "贷款", "水电", "物业"]
WEEKLY_WORDS = ["例会", "周会", "站会", "复盘"]

DOW = {"一": 0, "1": 0, "二": 1, "2": 1, "三": 2, "3": 2, "四": 3, "4": 3,
       "五": 4, "5": 4, "六": 5, "6": 5, "日": 6, "天": 6, "7": 6}


def clamp(year, month, day):
    return date(year, month, min(day, calendar.monthrange(year, month)[1]))


def month_holding(year, month, day):
    for _ in range(12):
        if calendar.monthrange(year, month)[1] >= day:
            return date(year, month, day)
        month += 1
        if month > 12:
            month, year = 1, year + 1
    return clamp(year, month, day)


def parse_count(s):
    if s.isdigit():
        return int(s)
    d = {"一": 1, "二": 2, "两": 2, "三": 3, "四": 4,
         "五": 5, "六": 6, "七": 7, "八": 8, "九": 9}
    if s == "十":
        return 10
    if len(s) == 1:
        return d.get(s)
    if len(s) == 2 and s[0] == "十":
        return 10 + d[s[1]] if s[1] in d else None
    if len(s) == 2 and s[1] == "十":
        return d[s[0]] * 10 if s[0] in d else None
    if len(s) == 3 and s[1] == "十":
        return d[s[0]] * 10 + d[s[2]] if (s[0] in d and s[2] in d) else None
    return None


def infer_repeat(text):
    if any(w in text for w in YEARLY_WORDS):
        return "YEARLY"
    if any(w in text for w in MONTHLY_WORDS):
        return "MONTHLY"
    if any(w in text for w in WEEKLY_WORDS):
        return "WEEKLY"
    return None


def explicit_repeat(text):
    if "每年" in text:
        return "YEARLY"
    if "每月" in text or "每个月" in text:
        return "MONTHLY"
    if "每周" in text or "每个星期" in text:
        return "WEEKLY"
    if "每天" in text or "每日" in text:
        return "DAILY"
    return None


ABSOLUTE = "ABS"
RELATIVE = "REL"


def match_date(s, today):
    m = absolute.search(s)
    if m:
        y, mo, d = int(m.group(1)), int(m.group(2)), int(m.group(3))
        if 1 <= mo <= 12 and 1 <= d <= 31:
            return clamp(y, mo, d), m.span(), ABSOLUTE, d

    m = everyYear.search(s)
    if m:
        mo = int(m.group(1)) if m.group(1) else today.month
        d = int(m.group(2))
        if 1 <= mo <= 12 and 1 <= d <= 31:
            return clamp(today.year, mo, d), m.span(), ABSOLUTE, d

    m = everyMonth.search(s)
    if m:
        d = int(m.group(1))
        if 1 <= d <= 31:
            return clamp(today.year, today.month, d), m.span(), ABSOLUTE, d

    m = nextMonth.search(s)
    if m:
        d = int(m.group(1))
        if 1 <= d <= 31:
            nm = (today.replace(day=1) + timedelta(days=32)).replace(day=1)
            return clamp(nm.year, nm.month, d), m.span(), RELATIVE, d

    m = relativeDay.search(s)
    if m:
        w = m.group(1)
        d = {"今天": 0, "今日": 0, "明天": 1, "明日": 1, "后天": 2}.get(w, 3)
        dt = today + timedelta(days=d)
        return dt, m.span(), RELATIVE, dt.day

    m = offset.search(s)
    if m:
        before = s[m.start() - 1] if m.start() > 0 else " "
        n = None if before in ("月", "日", "号") else parse_count(m.group(1))
        if n is not None:
            unit = m.group(2)
            forward = m.group(3) in ("后", "以后", "之后")
            signed = n if forward else -n
            if unit in ("天", "日"):
                dt = today + timedelta(days=signed)
            elif unit in ("周", "星期"):
                dt = today + timedelta(weeks=signed)
            elif unit in ("个月", "月"):
                y = today.year + (today.month - 1 + signed) // 12
                mo = (today.month - 1 + signed) % 12 + 1
                dt = clamp(y, mo, today.day)
            else:
                dt = clamp(today.year + signed, today.month, today.day)
            return dt, m.span(), RELATIVE, dt.day

    m = weekday.search(s)
    if m:
        prefix = m.group(1) or ""
        dow = DOW[m.group(2)]
        monday = today - timedelta(days=today.weekday())
        base = {"下下": 2, "下": 1}.get(prefix, 0)
        dt = monday + timedelta(weeks=base) + timedelta(days=dow)
        if prefix not in ("下", "下下") and dt < today:
            dt += timedelta(weeks=1)
        return dt, m.span(), RELATIVE, dt.day

    m = monthDay.search(s)
    if m:
        mo, d = int(m.group(1)), int(m.group(2))
        if 1 <= mo <= 12 and 1 <= d <= 31:
            cand = clamp(today.year, mo, d)
            if cand < today:
                cand = clamp(today.year + 1, mo, d)
            return cand, m.span(), ABSOLUTE, d
    return None


def strip_phrase(s, span):
    head, tail = s[:span[0]], s[span[1]:]
    t = (head + " " + tail)
    t = re.sub(r"^[\s,，。.、:：\-—的在]+", "", t)
    t = re.sub(r"^(之后|以后|之前|以前)\s*", "", t)
    t = re.sub(r"[\s,，。.、:：\-—的]+$", "", t)
    return t.strip()


def next_occurrence(anchor, rule, today):
    if rule == "NONE" or anchor >= today:
        return anchor
    if rule == "DAILY":
        return today
    if rule == "WEEKLY":
        diff = (today - anchor).days
        steps = diff // 7 + (0 if diff % 7 == 0 else 1)
        return anchor + timedelta(days=steps * 7)
    if rule == "MONTHLY":
        y, mo = today.year, today.month
        for _ in range(36):
            c = clamp(y, mo, anchor.day)
            if c >= today:
                return c
            mo += 1
            if mo > 12:
                mo, y = 1, y + 1
        return clamp(y, mo, anchor.day)
    if rule == "YEARLY":
        y = today.year
        for _ in range(12):
            c = clamp(y, anchor.month, anchor.day)
            if c >= today:
                return c
            y += 1
        return clamp(y, anchor.month, anchor.day)
    return anchor


def align_anchor(dt, rule, today, day, kind):
    if rule == "NONE":
        return dt
    if rule == "DAILY":
        return today
    if rule == "WEEKLY":
        monday = today - timedelta(days=today.weekday())
        same = monday + timedelta(days=dt.weekday())
        return same + timedelta(weeks=1) if same < today else same
    if rule == "MONTHLY":
        y, mo = (today.year, today.month) if kind == ABSOLUTE else (dt.year, dt.month)
        return next_occurrence(month_holding(y, mo, day), rule, today)
    return next_occurrence(dt, rule, today)


def parse(raw, today=TODAY):
    s = raw.strip()
    if not s:
        return None
    hit = match_date(s, today)
    explicit = explicit_repeat(s)
    if hit is None and explicit is None:
        return None

    if hit is not None:
        dt, span, kind = hit[0], hit[1], hit[2]
        day = hit[3]
        inferred = infer_repeat(s) if kind == ABSOLUTE else None
        title = strip_phrase(s, span) or "新事件"
    else:
        dt, kind, day = today, RELATIVE, today.day
        inferred = None
        m = repeatWord.search(s)
        title = (strip_phrase(s, m.span()) if m else s) or "新事件"

    rule = explicit or inferred or "NONE"
    return title, align_anchor(dt, rule, today, day, kind), rule


CASES = [
    ("下周五 交房租", "交房租", date(2026, 10, 30), "NONE"),
    ("明天 交报告", "交报告", date(2026, 10, 26), "NONE"),
    ("3天后 体检", "体检", date(2026, 10, 28), "NONE"),
    ("10月25日 妈妈生日", "妈妈生日", date(2026, 10, 25), "YEARLY"),
    ("每年10月25 妈妈生日", "妈妈生日", date(2026, 10, 25), "YEARLY"),
    ("每月15号 还信用卡", "还信用卡", date(2026, 11, 15), "MONTHLY"),
    ("2027-01-01 新年", "新年", date(2027, 1, 1), "NONE"),
    ("后天 出差", "出差", date(2026, 10, 27), "NONE"),
    ("周日 聚餐", "聚餐", date(2026, 10, 25), "NONE"),
    ("下个月5号 出差", "出差", date(2026, 11, 5), "NONE"),
    ("6个月后 复查", "复查", date(2027, 4, 25), "NONE"),
    ("下下周一 项目启动", "项目启动", date(2026, 11, 2), "NONE"),
    ("今天 交周报", "交周报", date(2026, 10, 25), "NONE"),
    ("2月29日 四年一次", "四年一次", date(2027, 2, 28), "NONE"),
    ("随便写点什么", None, None, None),
    ("每周五 例会", "例会", date(2026, 10, 30), "WEEKLY"),
    ("每周日 复盘", "复盘", date(2026, 10, 25), "WEEKLY"),
    ("每天 打卡", "打卡", date(2026, 10, 25), "DAILY"),
    ("每月 房租", "房租", date(2026, 10, 25), "MONTHLY"),
    ("10月25日之后 体检", "体检", date(2026, 10, 25), "NONE"),
    ("1/5 交房租", "交房租", date(2026, 11, 5), "MONTHLY"),
    ("三天后 体检", "体检", date(2026, 10, 28), "NONE"),
    ("十天后 生日", "生日", date(2026, 11, 4), "NONE"),
    ("一周后 去旅行", "去旅行", date(2026, 11, 1), "NONE"),
    ("三个月后 搬家", "搬家", date(2027, 1, 25), "NONE"),
    ("十天前 交房租", "交房租", date(2026, 10, 15), "NONE"),
    ("两年后 毕业", "毕业", date(2028, 10, 25), "NONE"),
    ("十五天后 复查", "复查", date(2026, 11, 9), "NONE"),
    ("一个月后 复查", "复查", date(2026, 11, 25), "NONE"),
]

FEB = date(2026, 2, 10)
CASES_FEB = [
    ("每月31号 还房贷", "还房贷", date(2026, 3, 31), "MONTHLY"),
    ("每月30号 交房租", "交房租", date(2026, 3, 30), "MONTHLY"),
    ("每月29号 打卡", "打卡", date(2026, 3, 29), "MONTHLY"),
    ("每月28号 打卡", "打卡", date(2026, 2, 28), "MONTHLY"),
    ("下个月31号 出差", "出差", date(2026, 3, 31), "NONE"),
]


def run(cases, today):
    fails = 0
    for raw, t_title, t_date, t_rule in cases:
        got = parse(raw, today)
        if t_title is None:
            ok = got is None
            expect = "不解析"
        else:
            ok = got is not None and got[0] == t_title and got[1] == t_date and got[2] == t_rule
            expect = "%s %s %s" % (t_title, t_date, t_rule)
        if not ok:
            fails += 1
        print("%s  %-20s 期望 %-34s 实际 %s" % ("PASS" if ok else "FAIL", raw, expect, got))
    return fails


total = 0
fails = 0
print("今天 = %s（%s）" % (TODAY, "一二三四五六日"[TODAY.weekday()]))
fails += run(CASES, TODAY)
total += len(CASES)
print("\n今天 = %s（%s）" % (FEB, "一二三四五六日"[FEB.weekday()]))
fails += run(CASES_FEB, FEB)
total += len(CASES_FEB)

_, anchor, _ = parse("每月31号 还房贷", FEB)
got = [next_occurrence(anchor, "MONTHLY", date(2026, m, 1)) for m in (3, 4, 5, 6, 7)]
want = [date(2026, 3, 31), date(2026, 4, 30), date(2026, 5, 31),
        date(2026, 6, 30), date(2026, 7, 31)]
total += 1
if got != want:
    fails += 1
    print("FAIL  每月31号 后续排期 期望 %s 实际 %s" % (want, got))
else:
    print("PASS  每月31号 后续排期 31/30/31/30/31")

HINT_FILE = ROOT / "app/src/main/java/com/offline/due/ui/QuickHints.kt"
HINT_MAX_CHARS = 8

if not HINT_FILE.exists():
    total += 1
    fails += 1
    print("FAIL  示例文案：找不到 %s" % HINT_FILE)
else:
    hint_src = HINT_FILE.read_text(encoding="utf-8")
    block = re.search(r"val HINTS: List<String> = listOf\((.*?)\n    \)", hint_src, re.S)
    hints = re.findall(r'"([^"]+)"', block.group(1)) if block else []

    total += 1
    if len(hints) != 20:
        fails += 1
        print("FAIL  示例文案 数量 期望 20 实际 %d" % len(hints))
    else:
        print("PASS  示例文案 共 %d 条" % len(hints))

    for h in hints:
        total += 1
        got = parse(h, TODAY)
        width = len(h.replace(" ", ""))
        if got is None:
            fails += 1
            print("FAIL  %-14s 解析不出来——用户照着打一遍会失败" % h)
        elif not got[0] or got[0] == "新事件":
            fails += 1
            print("FAIL  %-14s 解析出的标题是空的" % h)
        elif width > HINT_MAX_CHARS:
            fails += 1
            print("FAIL  %-14s 有 %d 个字，超过 %d 会被截断" % (h, width, HINT_MAX_CHARS))
        else:
            print("PASS  %-14s → %s %s %s" % (h, got[0], got[1], got[2]))

print("\n%d/%d 通过" % (total - fails, total))

sys.exit(1 if fails else 0)
