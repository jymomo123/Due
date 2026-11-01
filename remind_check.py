import calendar
from datetime import date, datetime, time as dtime, timedelta

ONCE, DAILY, WEEKLY, MONTHLY = 'ONCE', 'DAILY', 'WEEKLY', 'MONTHLY'
COUNTDOWN, ELAPSED = 'COUNTDOWN', 'ELAPSED'


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


def effective(rule, mode):
    if mode == ELAPSED and rule == ONCE:
        return DAILY
    return rule


def minus_months(d, k):
    total = (d.year * 12 + d.month - 1) - k
    return clamp_to_month(total // 12, total % 12 + 1, d.day)


def plus_months(dt, k):
    return datetime.combine(minus_months(dt.date(), -k), dt.time())


def month_day_in(y, m, dom):
    return clamp_to_month(y, m, dom)


def remind_day(target, frm, rule, weekday=0, month_day=0):
    if target < frm:
        return None
    best = None
    if rule == ONCE:
        best = target
    elif rule == DAILY:
        best = frm
    elif rule == WEEKLY:
        wd = weekday if 1 <= weekday <= 7 else target.isoweekday()
        anchor = target - timedelta(days=(target.isoweekday() - wd + 7) % 7)
        if anchor >= frm:
            best = anchor - timedelta(days=((anchor - frm).days // 7) * 7)
    else:
        dom = month_day if 1 <= month_day <= 31 else target.day
        y, m = target.year, target.month
        anchor = month_day_in(y, m, dom)
        if anchor > target:
            m -= 1
            if m < 1:
                m, y = 12, y - 1
            anchor = month_day_in(y, m, dom)
        if anchor >= frm:
            day = anchor
            k = 1
            while k <= 240:
                my, mm = y, m - k
                while mm < 1:
                    mm += 12
                    my -= 1
                cand = month_day_in(my, mm, dom)
                if cand < frm:
                    break
                day = cand
                k += 1
            best = day
    return best if best is not None else target


def countdown_at(ev, today, rule, now):
    for attempt in (0, 1):
        frm = today + timedelta(days=attempt)
        target = next_date(ev['anchor'], ev['repeat'], frm)
        day = remind_day(target, frm, rule, ev.get('weekday', 0), ev.get('monthday', 0))
        if day is None:
            continue
        at = datetime.combine(day, dtime(ev['hour'], ev['minute']))
        if at > now:
            return at
    return None


def elapsed_at(ev, today, rule, now):
    end = ev['end']
    if end is not None and today > end:
        return None
    start = ev['anchor'] if today < ev['anchor'] else today
    if rule in (ONCE, DAILY):
        first = datetime.combine(start, dtime(ev['hour'], ev['minute']))
        at = first if first > now else first + timedelta(days=1)
    elif rule == WEEKLY:
        wd = ev.get('weekday', 0) if 1 <= ev.get('weekday', 0) <= 7 else start.isoweekday()
        ahead = (wd - start.isoweekday()) % 7
        first = datetime.combine(start + timedelta(days=ahead),
                                 dtime(ev['hour'], ev['minute']))
        at = first if first > now else first + timedelta(weeks=1)
    else:
        dom = ev.get('monthday', 0) if 1 <= ev.get('monthday', 0) <= 31 else start.day
        y, m = start.year, start.month
        first = datetime.combine(month_day_in(y, m, dom), dtime(ev['hour'], ev['minute']))
        if first.date() < start:
            m += 1
            if m > 12:
                m, y = 1, y + 1
            first = datetime.combine(month_day_in(y, m, dom),
                                     dtime(ev['hour'], ev['minute']))
        if first > now:
            at = first
        else:
            m += 1
            if m > 12:
                m, y = 1, y + 1
            at = datetime.combine(month_day_in(y, m, dom),
                                  dtime(ev['hour'], ev['minute']))
    if end is not None and at.date() > end:
        return None
    return at


def next_trigger(ev, now):
    today = now.date()
    rule = effective(ev['rule'], ev['mode'])
    if ev['mode'] == ELAPSED:
        return elapsed_at(ev, today, rule, now)
    return countdown_at(ev, today, rule, now)


def ev(mode, anchor, rule, repeat='NONE', end=None, hour=9, minute=0,
       weekday=0, monthday=0):
    return dict(mode=mode, anchor=anchor, rule=rule, repeat=repeat,
                end=end, hour=hour, minute=minute, weekday=weekday, monthday=monthday)


problems = []
checks = 0


def ok(cond, msg):
    global checks
    checks += 1
    if not cond:
        problems.append(msg)


def advance(e, now, limit=200):
    seq = []
    for _ in range(limit):
        at = next_trigger(e, now)
        if at is None:
            break
        seq.append(at)
        now = at + timedelta(seconds=1)
    return seq


def gaps(seq):
    return [(b - a) for a, b in zip(seq, seq[1:])]


def check_common(name, e, now, seq):
    for at in seq:
        ok(at > now, f'{name}：算出 {at} 不在 {now} 之后')
    for a, b in zip(seq, seq[1:]):
        ok(b > a, f'{name}：不递增 {a} → {b}')


NOW = datetime(2026, 10, 31, 11, 0)

TARGET = date(2026, 11, 20)
base = dict(mode=COUNTDOWN, anchor=TARGET, repeat='NONE', end=None, hour=9, minute=0)

for rule, label in ((ONCE, '只在当天'), (DAILY, '每天'), (WEEKLY, '每周'), (MONTHLY, '每月')):
    e = dict(base, rule=rule)
    seq = advance(e, NOW)
    name = f'一次性倒数·{label}'
    check_common(name, e, NOW, seq)
    ok(seq, f'{name}：一个提醒都没排出来')
    if not seq:
        continue
    for at in seq:
        ok(at.date() <= TARGET, f'{name}：{at} 越过了目标日 {TARGET}')
    ok(seq[-1].date() == TARGET, f'{name}：最后一个提醒点是 {seq[-1].date()}，不是目标日')

    if rule == ONCE:
        ok(len(seq) == 1, f'{name}：应该只响 1 次，实际 {len(seq)} 次')
    elif rule == DAILY:
        ok(len(seq) == 20, f'{name}：应该 20 次，实际 {len(seq)} 次')
        ok(seq[0].date() == date(2026, 11, 1), f'{name}：第一次是 {seq[0].date()}，不是 11-01')
        ok(all(g == timedelta(days=1) for g in gaps(seq)), f'{name}：间隔不是 1 天')
    elif rule == WEEKLY:
        ok(all(g == timedelta(days=7) for g in gaps(seq)), f'{name}：间隔不是 7 天 {gaps(seq)}')
        ok(len(seq) == 3, f'{name}：11-20 往回每 7 天只该有 3 个点，实际 {len(seq)}')
    else:
        ok(all(28 <= g.days <= 31 for g in gaps(seq)), f'{name}：间隔不是一个月 {gaps(seq)}')
        ok(len(seq) == 1, f'{name}：应该只有目标日 1 次，实际 {len(seq)} 次')

for rule in (ONCE, DAILY, WEEKLY, MONTHLY):
    e = ev(COUNTDOWN, date(2026, 10, 1), rule)
    ok(next_trigger(e, NOW) is None, f'已过的一次性事件·{rule}：还在排提醒')

today = NOW.date()
e = ev(COUNTDOWN, today, ONCE, hour=23, minute=0)
at = next_trigger(e, NOW)
ok(at == datetime.combine(today, dtime(23, 0)), f'今天·时间未到：算出 {at}')
e = ev(COUNTDOWN, today, ONCE, hour=9, minute=0)
ok(next_trigger(e, NOW) is None, '今天·时间已过·只在当天：不该再响')
e = ev(COUNTDOWN, today, DAILY, hour=9, minute=0)
ok(next_trigger(e, NOW) is None, '今天·时间已过·每天（一次性）：不该排明天')
e = dict(mode=COUNTDOWN, anchor=today, repeat='DAILY', rule=DAILY,
         end=None, hour=9, minute=0)
at = next_trigger(e, NOW)
ok(at == datetime.combine(today + timedelta(days=1), dtime(9, 0)),
   f'今天·时间已过·每天（每天重复的事件）：应该排明天，算出 {at}')

birthday = dict(mode=COUNTDOWN, anchor=date(1990, 3, 15), repeat='YEARLY',
                end=None, hour=9, minute=0)
for rule in (ONCE, DAILY, WEEKLY, MONTHLY):
    e = dict(birthday, rule=rule)
    seq = advance(e, NOW, limit=120)
    name = f'每年生日·{rule}'
    check_common(name, e, NOW, seq)
    ok(seq, f'{name}：一个提醒都没排出来')
    if not seq:
        continue
    if rule == ONCE:
        years = {at.year for at in seq}
        ok(len(seq) >= 2 and max(years) > 2027, f'{name}：跨周期后停了 {len(seq)} 次')
        ok(all(at.month == 3 and at.day == 15 for at in seq), f'{name}：提醒日不是生日当天')

monthly_ev = dict(mode=COUNTDOWN, anchor=date(2026, 1, 31), repeat='MONTHLY',
                  end=None, hour=9, minute=0)
for rule in (ONCE, WEEKLY, MONTHLY):
    e = dict(monthly_ev, rule=rule)
    seq = advance(e, NOW, limit=60)
    check_common(f'每月事件·{rule}', e, NOW, seq)
    ok(seq, f'每月事件·{rule}：一个提醒都没排出来')
    for at in seq:
        ok(at.day <= calendar.monthrange(at.year, at.month)[1],
           f'每月事件·{rule}：算出不存在的日期 {at.date()}')

elapsed = ev(ELAPSED, date(2026, 1, 1), DAILY)
for rule, step_days in ((DAILY, 1), (WEEKLY, 7)):
    e = dict(elapsed, rule=rule)
    seq = advance(e, NOW, limit=10)
    name = f'计时无终点·{rule}'
    check_common(name, e, NOW, seq)
    ok(len(seq) == 10, f'{name}：应该排满 10 次，实际 {len(seq)}')
    ok(all(g == timedelta(days=step_days) for g in gaps(seq)),
       f'{name}：间隔不是 {step_days} 天 {gaps(seq)}')

e = dict(elapsed, rule=MONTHLY)
seq = advance(e, NOW, limit=10)
ok(all(28 <= g.days <= 31 for g in gaps(seq)), f'计时无终点·每月：间隔不是一个月 {gaps(seq)}')
ok(seq[0].date() == date(2026, 11, 30), f'计时无终点·每月：11 月该夹到 30 号，算出 {seq[0].date()}')

e = ev(ELAPSED, date(2026, 10, 1), DAILY, end=date(2026, 10, 20))
seq = advance(e, datetime(2026, 10, 15, 11, 0), limit=60)
ok(seq, '计时有终点：一个提醒都没排出来')
for at in seq:
    ok(at.date() <= date(2026, 10, 20), f'计时有终点：{at} 越过了终点')
ok(seq[-1].date() == date(2026, 10, 20), f'计时有终点：最后一次是 {seq[-1].date()}，不是终点')
ok(len(seq) == 5, f'计时有终点：10-15 到 10-20 应该 5 次，实际 {len(seq)}')

e = ev(ELAPSED, date(2026, 1, 1), DAILY, end=date(2026, 10, 1))
ok(next_trigger(e, NOW) is None, '终点已过：还在排提醒')

e = ev(ELAPSED, date(2026, 12, 1), DAILY)
at = next_trigger(e, NOW)
ok(at == datetime(2026, 12, 1, 9, 0), f'起始日在未来：应该从 12-01 开始，算出 {at}')

e = ev(COUNTDOWN, TARGET, ONCE)
seq = advance(e, NOW)
ok(len(seq) == 1 and seq[0].date() == TARGET,
   f'老数据·倒数：应该只在目标日响一次，实际 {seq}')

e = ev(ELAPSED, date(2026, 1, 1), ONCE)
seq = advance(e, NOW, limit=5)
ok(len(seq) == 5 and all(g == timedelta(days=1) for g in gaps(seq)),
   f'老数据·计时：应该每天响，实际 {[str(x) for x in seq]}')
ok(effective(ONCE, ELAPSED) == DAILY, 'effective()：计时模式下的 ONCE 没折算成 DAILY')
ok(effective(ONCE, COUNTDOWN) == ONCE, 'effective()：倒数模式下的 ONCE 不该被折算')

for rule in (ONCE, DAILY, WEEKLY, MONTHLY):
    e = ev(COUNTDOWN, date(2027, 6, 1), rule)
    seq = advance(e, NOW, limit=400)
    ok(seq, f'目标日当天必响·{rule}：一个提醒都没排出来')
    if not seq:
        continue
    ok(seq[-1].date() == date(2027, 6, 1),
       f'目标日当天必响·{rule}：最后一次是 {seq[-1].date()}')


TGT = date(2026, 11, 20)
ok(TGT.isoweekday() == 5, '用例前提：2026-11-20 应该是周五')

e = ev(COUNTDOWN, TGT, WEEKLY, weekday=3)
seq = advance(e, NOW)
check_common('倒数·每周三', e, NOW, seq)
ok(seq, '倒数·每周三：一个提醒都没排出来')
if seq:
    weekly = [at for at in seq if at.date() != TGT]
    ok(weekly, '倒数·每周三：一个周三都没排出来')
    ok(all(at.isoweekday() == 3 for at in weekly),
       f'倒数·每周三：周期内的不全是周三 {[str(x) for x in weekly]}')
    ok(all(g == timedelta(days=7) for g in gaps(weekly)),
       f'倒数·每周三：间隔不是 7 天 {gaps(weekly)}')
    ok(weekly[-1].date() == date(2026, 11, 18),
       f'倒数·每周三：最后一个周三该是 11-18，算出 {weekly[-1].date()}')
    ok(seq[-1].date() == TGT,
       f'倒数·每周三：最后一条该兜底到目标日，算出 {seq[-1].date()}')
    ok(all(at.date() <= TGT for at in seq), '倒数·每周三：越过了目标日')
    ok(len(seq) == 4, f'倒数·每周三：应该 3 个周三 + 目标日共 4 次，实际 {len(seq)}')

ok(advance(ev(COUNTDOWN, TGT, WEEKLY), NOW)
   == advance(ev(COUNTDOWN, TGT, WEEKLY, weekday=TGT.isoweekday()), NOW),
   '默认星期几（0）和"目标日那个星期几"排出来的点不一样')
ok(advance(ev(COUNTDOWN, TGT, WEEKLY), NOW)
   != advance(ev(COUNTDOWN, TGT, WEEKLY, weekday=3), NOW),
   '指定星期三之后排出来的点一点没变，自定义没生效')

e = ev(COUNTDOWN, TGT, MONTHLY, monthday=5)
seq = advance(e, NOW)
ok(seq, '倒数·每月5号：一个提醒都没排出来')
if seq:
    monthly = [at for at in seq if at.date() != TGT]
    ok(monthly and all(at.day == 5 for at in monthly),
       f'倒数·每月5号：周期内的不全是 5 号 {[str(x) for x in seq]}')
    ok(monthly[-1].date() == date(2026, 11, 5),
       f'倒数·每月5号：最后一个该是 11-05，算出 {monthly[-1].date()}')
    ok(seq[-1].date() == TGT,
       f'倒数·每月5号：最后一条该兜底到目标日，算出 {seq[-1].date()}')

e = ev(COUNTDOWN, date(2026, 11, 3), WEEKLY, weekday=3)
seq = advance(e, NOW)
ok(len(seq) == 1,
   f'兜底·每周三：应该只兜底响 1 次，实际 {len(seq)} 次 {[str(x) for x in seq]}')
if seq:
    ok(seq[0].date() == date(2026, 11, 3),
       f'兜底·每周三：应该落在目标日，实际 {seq[0].date()}')

e = ev(COUNTDOWN, date(2026, 11, 3), MONTHLY, monthday=15)
seq = advance(e, NOW)
ok(len(seq) == 1 and seq and seq[0].date() == date(2026, 11, 3),
   f'兜底·每月15号：应该只落在目标日，实际 {[str(x) for x in seq]}')

e = ev(ELAPSED, date(2026, 1, 1), WEEKLY, weekday=1)
seq = advance(e, NOW, limit=6)
check_common('计时·每周一', e, NOW, seq)
ok(seq, '计时·每周一：一个提醒都没排出来')
if seq:
    ok(seq[0] == datetime(2026, 11, 2, 9, 0), f'计时·每周一：第一次是 {seq[0]}，应该是 11-02')
    ok(all(at.isoweekday() == 1 for at in seq),
       f'计时·每周一：不全是周一 {[str(x) for x in seq]}')
    ok(all(g == timedelta(days=7) for g in gaps(seq)),
       f'计时·每周一：间隔不是 7 天 {gaps(seq)}')

e = ev(ELAPSED, date(2026, 1, 1), MONTHLY, monthday=15)
seq = advance(e, NOW, limit=6)
ok(seq, '计时·每月15号：一个提醒都没排出来')
if seq:
    ok(seq[0] == datetime(2026, 11, 15, 9, 0), f'计时·每月15号：第一次是 {seq[0]}')
    ok(all(at.day == 15 for at in seq),
       f'计时·每月15号：不全是 15 号 {[str(x) for x in seq]}')

e = ev(ELAPSED, date(2026, 1, 1), MONTHLY, monthday=31)
seq = advance(e, datetime(2026, 2, 10, 11, 0), limit=3)
ok(seq, '计时·每月31号：一个提醒都没排出来')
if seq:
    ok(seq[0].date() == date(2026, 2, 28),
       f'计时·每月31号：2 月该夹到 28 号，算出 {seq[0].date()}')
    ok(seq[1].date() == date(2026, 3, 31),
       f'计时·每月31号：3 月该回到 31 号，算出 {seq[1].date()}')

e = ev(COUNTDOWN, date(2026, 3, 15), MONTHLY, monthday=31)
seq = advance(e, datetime(2026, 2, 10, 11, 0))
ok(seq, '倒数·每月31号：一个提醒都没排出来')
if seq:
    ok(seq[0].date() == date(2026, 2, 28),
       f'倒数·每月31号：第一个该是 2 月末，算出 {seq[0].date()}')
    ok(seq[-1].date() == date(2026, 3, 15),
       f'倒数·每月31号：最后一次该兜底到目标日，算出 {seq[-1].date()}')

print(f'提醒调度对拍：{checks} 项断言，问题 {len(problems)} 条')
for p in problems[:40]:
    print(f'! {p}')
if len(problems) > 40:
    print(f'… 还有 {len(problems) - 40} 条')
raise SystemExit(1 if problems else 0)
