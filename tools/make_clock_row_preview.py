# -*- coding: utf-8 -*-
"""生成 0.45 的对照预览：秒表从"数字右边"搬到"数字下面"，并且放大。

左边是 0.43（秒表挂在数字同一行右边，用「·」隔开），
右边是 0.44（秒表单独一行，展开/收起有动画）。

**这张图要看的不是秒表，是天数**。秒表在同一行时它要占 2em 宽，
`rememberFittedNumberSp` 把两者加成同一个分母，于是天数被压到 86sp；
搬走之后天数回到 220sp——同一个读数，差 2.5 倍。

尺寸按真实比例算：卡片宽 390px 当 390dp，内边距 26dp，
数字字号由 `min(按宽反推, 按高反推)` 得出，行盒 1.15em（和字体度量一致）。
右边那张可以点「展开 / 收起」看动画，也可以直接开着让它循环。

用法：python tools/make_clock_row_preview.py
"""
import base64
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "preview/clock-row.html"

FONTS = {
    400: ROOT / "app/src/main/res/font/noto_sc_400.ttf",
    700: ROOT / "app/src/main/res/font/noto_sc_700.ttf",
}

faces = "\n".join(
    '@font-face{font-family:"Due";src:url(data:font/ttf;base64,%s) format("truetype");'
    "font-weight:%d;font-display:block}" % (base64.b64encode(p.read_bytes()).decode(), w)
    for w, p in FONTS.items()
)

TEMPLATE = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>如期 · 秒表挪到天数下面</title>
<style>
__FACES__

:root{
  --ice:#E8F1FC; --ice2:#DCE9F8;
  --ink:#14263C; --ink-mid:#33506E; --ink-soft:#4B6785;
  --glass:rgba(255,255,255,.68);
  --glass-b:rgba(255,255,255,.44);
  --edge:rgba(255,255,255,.95);
  --accent:#2F6FA8;
  --hair:rgba(20,38,60,.14);
}
:root[data-theme="dark"]{
  --ice:#0B1622; --ice2:#101E2C;
  --ink:#E8F1FA; --ink-mid:#A9C0D6; --ink-soft:#7E97B0;
  --glass:rgba(255,255,255,.15);
  --glass-b:rgba(255,255,255,.095);
  --edge:rgba(255,255,255,.30);
  --accent:#7FB4E0;
  --hair:rgba(232,241,250,.14);
}

*{box-sizing:border-box;margin:0;padding:0}
body{
  font-family:"Due",system-ui,sans-serif;
  background:var(--ice2); color:var(--ink);
  min-height:100vh; padding:34px 30px 44px;
  display:flex; flex-direction:column; align-items:center; gap:26px;
  -webkit-font-smoothing:antialiased;
}
:root[data-theme="dark"] body{background:#080F17}

.head{display:flex;align-items:center;gap:14px;flex-wrap:wrap;justify-content:center}
.head h1{font-size:15px;font-weight:700;letter-spacing:.02em;opacity:.9}
.head .sub{font-size:12px;color:var(--ink-soft);font-weight:400}
button{
  font-family:inherit; font-size:12px; font-weight:700;
  color:var(--ink); background:var(--glass);
  border:1px solid var(--hair); border-radius:999px;
  padding:7px 15px; cursor:pointer;
  transition:transform 120ms ease, background 120ms ease;
}
button:hover{background:var(--edge)}
button:active{transform:scale(.95)}

.stage{display:flex;gap:44px;align-items:flex-start;flex-wrap:wrap;justify-content:center}

.col{display:flex;flex-direction:column;gap:12px;align-items:center}
.tag{
  font-size:11.5px;letter-spacing:.09em;font-weight:700;
  color:var(--ink-soft);text-transform:uppercase;
}
.tag b{color:var(--accent);letter-spacing:0}

/* 卡片：390px 当 390dp */
.card{
  width:390px; border-radius:28px; overflow:hidden;
  background:linear-gradient(180deg,var(--glass),var(--glass-b));
  box-shadow:
    inset 0 0 0 1px var(--edge),
    inset 0 30px 60px -30px rgba(255,255,255,.55),
    0 26px 60px -30px rgba(11,22,34,.42);
  padding:22px 26px 20px;
  transition:height 260ms cubic-bezier(0,0,.2,1);
}
:root[data-theme="dark"] .card{
  box-shadow:
    inset 0 0 0 1px var(--edge),
    inset 0 30px 60px -30px rgba(255,255,255,.10),
    0 26px 60px -30px rgba(0,0,0,.75);
}

/* 竖线 + 内容 */
.body{display:flex;gap:16px;align-items:flex-start}
.rule{width:3px;border-radius:2px;background:var(--accent);align-self:stretch;flex:none}
.inner{flex:1;min-width:0}

.title{font-size:26px;font-weight:700;line-height:1.15;margin-bottom:10px}

/* 大数字。行盒 1.15em，和 Compose 里的字体度量一致 */
.numrow{display:flex;align-items:flex-end;line-height:1.15;white-space:nowrap}
.num{font-weight:900;letter-spacing:-.02em}
.unit{font-size:.2em;font-weight:400;margin-left:.08em;padding-bottom:.8em;opacity:1}

/* 0.43 那版：秒表挂在同一行右边 */
.dot{font-size:.2em;padding:0 8px .8em;opacity:.45}
.clock-inline{font-size:.45em;font-weight:400;padding-bottom:.356em;letter-spacing:.01em;font-variant-numeric:tabular-nums}

/* 0.44：秒表单独一行。用 grid 0fr→1fr 模拟 expandVertically(expandFrom=Top) */
.clockbox{
  display:grid; grid-template-rows:0fr; opacity:0;
  transition:grid-template-rows 260ms cubic-bezier(0,0,.2,1), opacity 260ms;
}
.clockbox.open{grid-template-rows:1fr; opacity:1}
.clockbox>.clip{overflow:hidden;min-height:0}
.clock{font-size:57px;font-weight:400;line-height:1.15;padding-top:8px;font-variant-numeric:tabular-nums}

.meta{font-size:16px;line-height:1.15;color:var(--ink-mid);padding-top:8px;white-space:nowrap}

.note{font-size:12px;line-height:1.7;color:var(--ink-soft);max-width:330px;text-align:center}
.note b{color:var(--accent)}
</style>
</head>
<body>

<div class="head">
  <h1>秒表挪到天数下面</h1>
  <span class="sub">同一张卡 · 同一个读数</span>
  <button id="th">深色主题</button>
  <button id="demo">展开 / 收起</button>
</div>

<div class="stage">
  <div class="col">
    <div class="tag">0.43 &nbsp;·&nbsp; 同一行</div>
    <div class="card">
      <div class="body">
        <div class="rule"></div>
        <div class="inner">
          <div class="title">春节回家</div>
          <div class="numrow" style="font-size:86px">
            <span class="num">18</span><span class="unit">天</span>
            <span class="dot">·</span>
            <span class="clock-inline" data-clock>00:00:00</span>
          </div>
          <div class="meta">2027.02.17 · 周三</div>
        </div>
      </div>
    </div>
    <div class="note">秒表要 <b>2em</b> 宽，和天数挤在同一条宽度约束里<br>→ 天数被压到 <b>86sp</b></div>
  </div>

  <div class="col">
    <div class="tag">0.45 &nbsp;·&nbsp; 数字下面</div>
    <div class="card">
      <div class="body">
        <div class="rule"></div>
        <div class="inner">
          <div class="title">春节回家</div>
          <div class="numrow" style="font-size:220px">
            <span class="num">18</span><span class="unit">天</span>
          </div>
          <div class="clockbox" id="box">
            <div class="clip"><div class="clock" data-clock>00:00:00</div></div>
          </div>
          <div class="meta">2027.02.17 · 周三</div>
        </div>
      </div>
    </div>
    <div class="note">各占各的约束 → 天数 <b>220sp</b> · 秒表 <b>57sp</b><br>长按数字开关秒，高度从<b>上沿</b>展开</div>
  </div>
</div>

<script>
// 主题
const root = document.documentElement;
const th = document.getElementById('th');
function setTheme(dark){
  root.dataset.theme = dark ? 'dark' : 'light';
  th.textContent = dark ? '浅色主题' : '深色主题';
}
setTheme(location.hash === '#dark');
th.addEventListener('click', () => setTheme(root.dataset.theme !== 'dark'));

// 秒表：真的在走，不然看不出"每秒在动"
const clocks = [...document.querySelectorAll('[data-clock]')];
function tick(){
  const d = new Date();
  const p = n => String(n).padStart(2, '0');
  const s = p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
  clocks.forEach(el => el.textContent = s);
}
tick();
setInterval(tick, 1000);

// 展开 / 收起。打开 260ms（Motion.Enter），收起 180ms（Motion.Exit）——
// 和 Compose 里那两条 tween 是同一组数。
const box = document.getElementById('box');
let open = true;
box.classList.add('open');
document.getElementById('demo').addEventListener('click', () => {
  open = !open;
  box.style.transitionDuration = open ? '260ms' : '180ms';
  box.classList.toggle('open', open);
});
</script>
</body>
</html>
"""

html = TEMPLATE.replace("__FACES__", faces)
OUT.write_text(html, encoding="utf-8")
print("写出", OUT, "%.1f MB" % (OUT.stat().st_size / 1024 / 1024))
