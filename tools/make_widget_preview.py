# -*- coding: utf-8 -*-
"""生成 0.46 的对照预览：桌面小组件。

四件事一起看：

  1. **字体**。0.45 的四个 TextView 都没写 fontFamily，桌面上是系统字体；
     0.46 指定了思源黑体（数字 900 / 标题 700 / 日期 400）。
  2. **字号**。0.45 是四档硬编码（22/28/28/33）且用 sp（跟随系统字号）；
     0.46 按可用宽高反推，用 dp。
  3. **竖条**。0.45 的竖条 match_parent 拉满格子，2 格高时比内容还长；
     0.46 让它跟着内容走。
  4. **尺寸**。同一套算法在 1×1 / 2×1 / 2×2 / 4×2 下分别长什么样。

字号不是手写的，是 JS 复刻 `TodayWidgetProvider.update()` 里那段算法算出来的
（可用宽高 → em 折算 → 取 min → clamp 16..64）。改 Kotlin 那边的数，
这边要跟着改，否则预览就不再是"预览"。

用法：python tools/make_widget_preview.py
"""
import base64
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "preview/widget-sizes.html"

FONTS = {
    400: ROOT / "app/src/main/res/font/noto_sc_400.ttf",
    700: ROOT / "app/src/main/res/font/noto_sc_700.ttf",
    900: ROOT / "app/src/main/res/font/noto_sc_900.ttf",
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
<title>如期 · 桌面小组件</title>
<style>
__FACES__

:root{
  --ice:#E3EEFB; --ice2:#D3E3F6;
  --ink:#14263C; --ink-mid:#33506E; --ink-soft:#4B6785;
  --hair:rgba(20,38,60,.12);
  --accent:#2F6FA8;
}
:root[data-theme="dark"]{
  --ice:#0B1622; --ice2:#101E2C;
  --ink:#E8F1FA; --ink-mid:#A9C0D6; --ink-soft:#7E97B0;
  --hair:rgba(232,241,250,.14);
  --accent:#7FB4E0;
}

*{box-sizing:border-box;margin:0;padding:0}
body{
  font-family:"Due",system-ui,sans-serif;
  background:var(--ice2); color:var(--ink);
  min-height:100vh; padding:30px 26px 46px;
  display:flex; flex-direction:column; align-items:center; gap:24px;
  -webkit-font-smoothing:antialiased;
}
:root[data-theme="dark"] body{background:#070E16}

.head{display:flex;align-items:center;gap:14px;flex-wrap:wrap;justify-content:center}
.head h1{font-size:15px;font-weight:700}
.head .sub{font-size:12px;color:var(--ink-soft)}
button{
  font-family:inherit;font-size:12px;font-weight:700;color:var(--ink);
  background:rgba(255,255,255,.55);border:1px solid var(--hair);
  border-radius:999px;padding:7px 15px;cursor:pointer;
}
:root[data-theme="dark"] button{background:rgba(255,255,255,.10)}
button:active{transform:scale(.95)}

.sec{display:flex;flex-direction:column;gap:14px;align-items:center;width:100%}
.sec > h2{
  font-size:11.5px;letter-spacing:.10em;font-weight:700;
  color:var(--ink-soft);text-transform:uppercase;
}

.row{display:flex;gap:26px;align-items:flex-end;flex-wrap:wrap;justify-content:center}
.case{display:flex;flex-direction:column;gap:9px;align-items:center}
.cap{font-size:11px;color:var(--ink-soft);letter-spacing:.03em}
.cap b{color:var(--accent)}

/* 小组件本体。尺寸由 JS 写进 style，1dp = 1px */
.w{
  position:relative;
  border-radius:24px;
  background:linear-gradient(180deg,#F7FBFF,#DCE9F8);
  box-shadow:inset 0 0 0 1px rgba(255,255,255,.80), 0 14px 34px -18px rgba(11,22,34,.45);
  padding:11px 14px;
  display:flex; align-items:center;
  overflow:hidden;
}
:root[data-theme="dark"] .w{
  background:linear-gradient(180deg,#18262F,#0B141C);
  box-shadow:inset 0 0 0 1px rgba(255,255,255,.15), 0 14px 34px -18px rgba(0,0,0,.8);
}

/* 竖条 + 内容：内层 wrap_content，竖条 match_parent 它
   —— 这是 0.46 修的那条"竖条拉满格子" */
.wbody{display:flex;align-items:center;width:100%}
.rule{width:3dp;width:3px;background:#4A8FE7;align-self:stretch;flex:none;border-radius:1.5px}
.texts{display:flex;flex-direction:column;min-width:0;flex:1}
.wide-inner{display:flex;align-items:center;margin-left:12px;min-width:0;flex:1}
.wide-inner .texts{margin-left:14px}

.t-title{font-weight:700;line-height:1.15;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.t-num{font-weight:900;line-height:1.15;white-space:nowrap;overflow:hidden}
.t-date{line-height:1.15;margin-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.t-clock{line-height:1.15;margin-top:2px;font-variant-numeric:tabular-nums;white-space:nowrap}

/* 0.45 的对照：用系统字体渲染 */
.sys{font-family:"Microsoft YaHei","PingFang SC",system-ui,sans-serif}

.legend{font-size:11.5px;line-height:1.85;color:var(--ink-soft);max-width:760px;text-align:center}
.legend b{color:var(--accent)}
.legend code{font-family:ui-monospace,monospace;font-size:11px;opacity:.85}
</style>
</head>
<body>

<div class="head">
  <h1>桌面小组件</h1>
  <span class="sub">同一套算法，四种尺寸</span>
  <button id="th">深色主题</button>
</div>

<div class="sec">
  <h2>0.45 → 0.46（2×2）</h2>
  <div class="row">
    <div class="case">
      <div class="w" data-w="180" data-h="126" data-mode="old"></div>
      <div class="cap">0.45 &nbsp;·&nbsp; <b>系统字体</b> · 33sp</div>
    </div>
    <div class="case">
      <div class="w" data-w="180" data-h="126" data-mode="new"></div>
      <div class="cap">0.46 &nbsp;·&nbsp; <b>思源黑体</b> · 按尺寸算</div>
    </div>
  </div>
</div>

<div class="sec">
  <h2>0.46 在四种尺寸下</h2>
  <div class="row">
    <div class="case">
      <div class="w" data-w="110" data-h="56" data-mode="new"></div>
      <div class="cap">1×1 &nbsp;110×56</div>
    </div>
    <div class="case">
      <div class="w" data-w="180" data-h="56" data-mode="new"></div>
      <div class="cap">2×1 &nbsp;180×56 &nbsp;·&nbsp; 横排</div>
    </div>
    <div class="case">
      <div class="w" data-w="180" data-h="126" data-mode="new"></div>
      <div class="cap">2×2 &nbsp;180×126</div>
    </div>
    <div class="case">
      <div class="w" data-w="390" data-h="126" data-mode="new"></div>
      <div class="cap">4×2 &nbsp;390×126 &nbsp;·&nbsp; 横排</div>
    </div>
  </div>
</div>

<div class="legend">
  高度不够时<b>从下往上砍</b>：先砍秒表，再砍日期，最后砍标题，数字只缩不砍。<br>
  字号 = <code>min(可用宽 ÷ 文案 em 宽, 剩余高 ÷ 1.15)</code>，夹在 16~64dp。<br>
  单位是 <b>dp 不是 sp</b>——系统字号调到 1.3 倍时，sp 会把数字顶出格子。
</div>

<script>
const root = document.documentElement;
const th = document.getElementById('th');
function setTheme(dark){
  root.dataset.theme = dark ? 'dark' : 'none';
  th.textContent = dark ? '浅色主题' : '深色主题';
}
setTheme(location.hash === '#dark');
th.addEventListener('click', () => {
  setTheme(root.dataset.theme !== 'dark');
  renderAll();
});

// 思源黑体数字的 advance，和 Kotlin 里的 emWidth() 同一份规则
function emWidth(s){
  let e = 0;
  for (const c of s) e += c.codePointAt(0) < 0x2E80 ? 0.555 : 1.0;
  return e;
}

// 和 TodayWidgetProvider 里那两组常量对应：LIGHT_* / DARK_*
function ink(){
  const dark = root.dataset.theme === 'dark';
  return {
    title: dark ? '#93AAC4' : '#4C6684',
    num:   dark ? '#E8F1FA' : '#14263C',
    sub:   dark ? '#7C93AC' : '#6B83A0',
  };
}

const TITLE = '春节回家';
const NUMBER = '18';
const DATE = '2月17日 · 还有 18 天';
const CLOCK = '23:45:12';

// ── 0.46：TodayWidgetProvider.update() 里那段 ──────────────
function layoutNew(w, h){
  const innerW = Math.max(40, w - 14 * 2 - 3 - 12);
  const innerH = Math.max(24, h - 11 * 2);
  const titleH = 11 * 1.15;
  const dateH = 10 * 1.15 + 2;
  const clockH = 11 * 1.15 + 2;
  const minNumH = 20 * 1.15;

  const showTitle = innerH - titleH - dateH >= minNumH;
  const showDate = innerH - dateH >= minNumH;
  const showClock = showDate &&
    innerH - (showTitle ? titleH : 0) - dateH - clockH >= minNumH;

  const usedH = (showTitle ? titleH : 0) + (showDate ? dateH : 0) +
    (showClock ? clockH : 0);
  const byW = innerW / emWidth(NUMBER);
  const byH = (innerH - usedH) / 1.15;
  const num = Math.min(64, Math.max(16, Math.min(byW, byH)));
  return {showTitle, showDate, showClock, num};
}

// ── 0.46 横排：宽 / 高 >= 2.6 时走这一支 ────────────────
function layoutNewWide(w, h){
  const innerW = Math.max(40, w - 14 * 2 - 3 - 12);
  const innerH = Math.max(24, h - 11 * 2);
  const titleH = 11 * 1.15, dateH = 10 * 1.15 + 2, clockH = 11 * 1.15 + 2;
  const showTitle = true;
  const showDate = innerH >= titleH + dateH;
  const showClock = innerH >= titleH + dateH + clockH;
  const byW = (innerW * 0.55) / emWidth(NUMBER);
  const byH = innerH / 1.15;
  const num = Math.min(80, Math.max(16, Math.min(byW, byH)));
  return {showTitle, showDate, showClock, num, wide:true};
}

// ── 0.45：四档硬编码 ─────────────────────────────────────
function layoutOld(w, h){
  const tiny = h < 76;
  const compact = h < 104;
  const num = tiny ? 22 : (compact ? 28 : (w < 132 ? 28 : 33));
  return {showTitle: !tiny, showDate: !tiny && !compact, showClock: !compact, num};
}

function render(el){
  const w = +el.dataset.w, h = +el.dataset.h;
  const isOld = el.dataset.mode === 'old';
  const L = isOld ? layoutOld(w, h)
    : (w / h >= 2.6 ? layoutNewWide(w, h) : layoutNew(w, h));
  el.style.width = w + 'px';
  el.style.height = h + 'px';

  const c = ink();
  const num = '<div class="t-num" style="font-size:' + L.num.toFixed(1) +
              'px;color:' + c.num + '">' + NUMBER + '</div>';
  const texts = [];
  if (L.showTitle) {
    texts.push('<div class="t-title" style="font-size:11px;color:' + c.title + '">' + TITLE + '</div>');
  }
  if (L.showDate) {
    texts.push('<div class="t-date" style="font-size:10px;color:' + c.sub + '">' + DATE + '</div>');
  }
  if (L.showClock) {
    texts.push('<div class="t-clock" style="font-size:11px;color:' + c.sub + '">' + CLOCK + '</div>');
  }

  const cls = 'texts' + (isOld ? ' sys' : '');
  let body;
  if (L.wide) {
    // 横排：数字在左、说明在右
    body = '<div class="wbody"><div class="rule"></div>' +
      '<div class="wide-inner">' + num +
      '<div class="' + cls + '">' + texts.join('') + '</div></div></div>';
  } else {
    // 竖排：数字在说明上面
    const first = texts.length ? texts.shift() : '';
    body = '<div class="wbody"><div class="rule"></div>' +
      '<div class="' + cls + '">' + first + num + texts.join('') + '</div></div>';
  }
  el.innerHTML = body;

  // 数字实际字号写回 caption，方便核对算法
  const cap = el.parentElement.querySelector('.cap');
  if (cap && !cap.dataset.done) {
    cap.innerHTML += ' &nbsp;→ <b>' + L.num.toFixed(1) + 'dp</b>';
    cap.dataset.done = '1';
  }
}

function renderAll(){ document.querySelectorAll('.w').forEach(render); }
renderAll();
</script>
</body>
</html>
"""

html = TEMPLATE.replace("__FACES__", faces)
OUT.write_text(html, encoding="utf-8")
print("写出", OUT, "%.1f MB" % (OUT.stat().st_size / 1024 / 1024))
