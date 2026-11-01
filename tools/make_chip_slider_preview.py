# -*- coding: utf-8 -*-
"""生成 0.42 的对照预览：分类筛选条的下划线，从"各画各的"改成"一条滑过去"。

为什么要有这个：这是**动效**改动，静态截图什么都说明不了。
上面那条是 0.41（点一下，这条灭那条亮），下面那条是 0.42（同一条线滑过去）。
两条都能点，也能一起自动演示。

CSS 的 transition 和 Compose 的 spring 不是一回事，但"位置和宽度同时收"
这件事是一样的——两边都用同一个时长、同一条缓动，所以形状的变化方式对得上。

用法：python tools/make_chip_slider_preview.py
"""
import base64
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "preview/chip-slider.html"

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
<title>如期 · 分类筛选条的滑动指示条</title>
<style>
__FACES__

:root{
  --ink:#14263C; --ink-mid:#33506E; --ink-soft:#4B6785;
  --ice0:#F7FBFF; --ice1:#E8F1FC; --ice2:#D8E7F9;
  --accent:#2F76F0;
  --page:#F2F7FE;
  --line:rgba(127,168,214,.26);
}
*{box-sizing:border-box}
html,body{margin:0}
body{
  font-family:"Due",system-ui,-apple-system,"PingFang SC",sans-serif;
  background:var(--page); color:var(--ink);
  padding:34px 20px 52px;
}
.wrap{max-width:760px;margin:0 auto}
h1{font-size:19px;font-weight:400;margin:0 0 6px;letter-spacing:.2px}
.sub{font-size:12.5px;color:var(--ink-soft);line-height:1.9;margin:0 0 22px}
.sub b{font-weight:400;color:var(--ink-mid)}

.bar{display:flex;gap:8px;margin:0 0 22px}
.bar button{
  font-family:inherit;font-size:12.5px;color:var(--ink-mid);
  background:#fff;border:1px solid var(--line);border-radius:999px;
  padding:7px 15px;cursor:pointer;
}
.bar button[aria-pressed="true"]{color:var(--ink);border-color:var(--accent)}

.panel{
  border-radius:24px;padding:20px 22px 18px;margin-bottom:20px;
  background:
    radial-gradient(120% 70% at 16% 4%, rgba(169,204,245,.55), transparent 62%),
    radial-gradient(100% 60% at 88% 96%, rgba(168,216,222,.5), transparent 60%),
    linear-gradient(180deg,var(--ice0),var(--ice1) 50%,var(--ice2));
  border:1px solid rgba(255,255,255,.75);
  box-shadow:0 18px 44px -30px rgba(20,38,60,.45);
}
.cap{display:flex;align-items:baseline;gap:9px;margin:0 0 12px}
.tag{
  font-size:10.5px;letter-spacing:.6px;color:var(--ink-soft);
  border:1px solid var(--line);border-radius:6px;padding:2px 7px;
}
.cap span{font-size:12px;color:var(--ink-soft)}
.cap b{font-weight:400;color:var(--ink-mid)}

/* 筛选条本体 */
.strip{position:relative;display:flex;gap:18px;align-items:flex-start;overflow:hidden}
.chip{
  position:relative;display:flex;align-items:baseline;gap:5px;
  padding:4px 0 7px;cursor:pointer;white-space:nowrap;
  -webkit-user-select:none;user-select:none;
  transition:transform .12s ease;
}
.chip:active{transform:scale(.94)}
.chip .n{font-size:16px;color:var(--ink-mid);transition:color .12s ease}
.chip .c{font-size:13px;color:var(--ink-soft);transition:color .12s ease}
.chip.on .n{font-weight:700;color:var(--ink)}
.chip.on .c{color:var(--accent)}

/* 0.42 的占位：每格都按粗体排一份透明的，盒子宽度钉死，切字重不再推挤邻居 */
.strip.stable .n{display:grid}
.strip.stable .n::before{
  content:attr(data-label);font-weight:700;grid-area:1/1;visibility:hidden;
}
.strip.stable .n .t{grid-area:1/1}

/* 0.41：每格自己画一条，切换时这条灭那条亮 */
.strip.instant .chip::after{
  content:"";position:absolute;left:0;right:0;bottom:0;height:2px;
  background:var(--accent);opacity:0;
}
.strip.instant .chip.on::after{opacity:1}

/* 0.42：整条只有一条，位置和宽度都跟着选中项走 */
.thumb{
  position:absolute;bottom:0;height:2px;border-radius:1px;
  background:var(--accent);pointer-events:none;
  transition:left .3s cubic-bezier(.2,.85,.25,1), width .3s cubic-bezier(.2,.85,.25,1);
}

.note{
  margin:30px 0 0;font-size:12.5px;line-height:2.05;color:var(--ink-soft);
  border-top:1px solid var(--line);padding-top:20px;
}
.note b{font-weight:400;color:var(--ink-mid)}
</style>
</head>
<body>
<div class="wrap">
  <h1>分类筛选条：一条线滑过去</h1>
  <p class="sub">
    上面是 0.41（每格各画一条下划线，切换时<b>这条灭、那条亮</b>）；
    下面是 0.42（整条只有一条，<b>位置和宽度一起滑</b>）。两条都能点。
  </p>

  <div class="bar">
    <button id="demo" aria-pressed="false">自动演示</button>
  </div>

  <div class="panel">
    <div class="cap"><span class="tag">0.41</span><span>瞬切 · 中间什么都不发生</span></div>
    <div class="strip instant" id="old">__CHIPS__</div>
  </div>

  <div class="panel">
    <div class="cap"><span class="tag">0.42</span><b>滑过去 · 被移动的是同一个东西</b></div>
    <div class="strip stable" id="new">__CHIPS__<div class="thumb"></div></div>
  </div>

  <p class="note">
    <b>为什么要改。</b>手指刚点下去，注意力还在那一格上，指示物却已经瞬移到了别处——
    中间没有任何东西告诉人"它去哪了"。眼睛看到的是<b>闪烁</b>，不是<b>移动</b>。
    同一个东西滑过去，因果关系才是完整的。
    <br>
    <b>为什么位置和宽度一起收。</b>从窄的一格滑到宽的一格，它是一边平移一边变形。
    先拉长再缩回那种弹性（Instagram 的标签栏）在这个界面里太吵——
    这里要的只是"它没消失"。
    <br>
    <b>顺带修掉的抖动。</b>选中时字重 400 → 700，字宽会变：那一格变宽，
    它右边的每一格跟着右移，指示条要追的目标也在动，切一次能抖三下。
    0.42 每一格都留了一份透明的粗体占位，盒子宽度钉死，抖动从源头消失
    （中文本来等宽、看不出代价，英文分类名才需要这一下）。
    上面那条 0.41 没有这一层，把「全部」切到「工作」时能看见整排往左蹭一下。
    <br>
    <b>和真机的差别。</b>真机上用的是 spring（阻尼 0.9、刚度 MediumLow），
    落位收得比这里的 cubic-bezier 更"实"一点，末尾几乎不过冲。
  </p>
</div>

<script>
const LABELS = [
  ["全部", 12],
  ["工作", 5],
  ["生活", 4],
  ["学习", 3],
];

function fill(strip) {
  const chips = LABELS.map(([label, n], i) =>
    '<div class="chip' + (i === 0 ? ' on' : '') + '">' +
      '<span class="n" data-label="' + label + '"><span class="t">' + label + '</span></span>' +
      '<span class="c">' + n + '</span>' +
    '</div>').join('');
  strip.insertAdjacentHTML('afterbegin', chips);
  const list = [...strip.querySelectorAll('.chip')];
  list.forEach((c, i) => c.addEventListener('click', () => pick(strip, i)));
  return list;
}

function pick(strip, i) {
  const chips = [...strip.querySelectorAll('.chip')];
  chips.forEach((c, k) => c.classList.toggle('on', k === i));
  const thumb = strip.querySelector('.thumb');
  if (thumb) {
    const c = chips[i];
    thumb.style.left = c.offsetLeft + 'px';
    thumb.style.width = c.offsetWidth + 'px';
  }
  strip.dataset.picked = i;
}

const oldStrip = document.getElementById('old');
const newStrip = document.getElementById('new');
fill(oldStrip);
fill(newStrip);

// 首帧直接把指示条摆到位，不演"从最左边飞过来"
function settle(strip) {
  const i = Number(strip.dataset.picked || 0);
  const c = strip.querySelectorAll('.chip')[i];
  const t = strip.querySelector('.thumb');
  if (!t) return;
  t.style.transition = 'none';
  t.style.left = c.offsetLeft + 'px';
  t.style.width = c.offsetWidth + 'px';
  void t.offsetWidth;
  t.style.transition = '';
}

settle(newStrip);
window.addEventListener('resize', () => settle(newStrip));

// #2 直接停在第三格，方便截图核对指示条落点
const h = parseInt(location.hash.slice(1), 10);
if (!isNaN(h)) {
  pick(oldStrip, h);
  pick(newStrip, h);
}

// 自动演示：两条同时切，眼睛直接比
let timer = null, step = 0;
const demoBtn = document.getElementById('demo');
demoBtn.addEventListener('click', () => {
  if (timer) {
    clearInterval(timer);
    timer = null;
    demoBtn.setAttribute('aria-pressed', 'false');
    return;
  }
  demoBtn.setAttribute('aria-pressed', 'true');
  timer = setInterval(() => {
    step = (step + 1) % LABELS.length;
    pick(oldStrip, step);
    pick(newStrip, step);
  }, 1100);
});
</script>
</body>
</html>
"""

html = TEMPLATE.replace("__FACES__", faces).replace("__CHIPS__", "")
OUT.write_text(html, encoding="utf-8")
print("写出 %s（%.1f MB）" % (OUT, OUT.stat().st_size / 1024 / 1024))
