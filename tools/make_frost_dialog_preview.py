# -*- coding: utf-8 -*-
"""生成 0.43 的对照预览：弹窗的玻璃，从"透明"改成"毛玻璃"。

左边是 0.42（`weight = 1f` → 填充 0.82 → 0.48），右边是 0.43（frost 0.90 → 0.78）。
两张都放在同一份列表内容上——**关键是看弹窗下半截**：
左边能从弹窗里读出后面的行，右边只透出一片明暗。

弹窗那块的玻璃按 GlassPanel 的四层画：填充 → 对角高光 + 四角聚光 →
下沿厚度 → 1dp 描边。CSS 里用多层 background + 伪元素近似。

用法：python tools/make_frost_dialog_preview.py
"""
import base64
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "preview/frost-dialog.html"

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
<title>如期 · 弹窗的毛玻璃</title>
<style>
__FACES__

:root{
  --ink:#14263C; --ink-mid:#33506E; --ink-soft:#4B6785;
  --ice0:#F7FBFF; --ice1:#E8F1FC; --ice2:#D8E7F9;
  --glass:rgba(255,255,255,.68); --glass2:rgba(255,255,255,.44);
  --hair:rgba(255,255,255,.72); --stroke:rgba(127,168,214,.26);
  --accent:#2F76F0;
  --page:#F2F7FE; --frame:#DDE7F4;
  --scrim:rgba(12,26,42,.16);
  --au1:rgba(169,204,245,.9); --au2:rgba(190,203,240,.85); --au3:rgba(168,216,222,.8);
  --shadow:rgba(20,38,60,.45);
}
[data-theme="dark"]{
  --ink:#E8F1FA; --ink-mid:#93AAC4; --ink-soft:#6E88A5;
  --ice0:#0B1622; --ice1:#101E2C; --ice2:#09121C;
  --glass:rgba(255,255,255,.15); --glass2:rgba(255,255,255,.095);
  --hair:rgba(255,255,255,.11); --stroke:rgba(255,255,255,.20);
  --accent:#6BA6FF;
  --page:#0A121C; --frame:#1B2836;
  --scrim:rgba(0,0,0,.28);
  --au1:rgba(46,92,138,.95); --au2:rgba(52,69,111,.9); --au3:rgba(34,84,92,.9);
  --shadow:rgba(0,0,0,.6);
}

*{box-sizing:border-box}
html,body{margin:0}
body{
  font-family:"Due",system-ui,-apple-system,"PingFang SC",sans-serif;
  background:var(--page); color:var(--ink);
  padding:34px 20px 52px;
}
.wrap{max-width:940px;margin:0 auto}
h1{font-size:19px;font-weight:400;margin:0 0 6px;letter-spacing:.2px}
.sub{font-size:12.5px;color:var(--ink-soft);line-height:1.9;margin:0 0 22px;max-width:720px}
.sub b{font-weight:400;color:var(--ink-mid)}

.bar{display:flex;gap:8px;margin:0 0 22px}
.bar button{
  font-family:inherit;font-size:12.5px;color:var(--ink-mid);
  background:var(--glass);border:1px solid var(--stroke);border-radius:999px;
  padding:7px 15px;cursor:pointer;
}

.stage{display:flex;gap:26px;flex-wrap:wrap;justify-content:center}
figure{margin:0;width:360px}
.phone{
  position:relative;width:360px;height:520px;border-radius:32px;overflow:hidden;
  border:1px solid var(--frame);box-shadow:0 26px 60px -30px var(--shadow);
}
figcaption{margin-top:12px;font-size:12.5px;color:var(--ink-mid);line-height:1.7}
figcaption .tag{
  font-size:10.5px;letter-spacing:.6px;color:var(--ink-soft);
  border:1px solid var(--stroke);border-radius:6px;padding:2px 7px;margin-right:7px;
}
figcaption em{display:block;font-style:normal;color:var(--ink-soft);font-size:11.5px;margin-top:2px}

/* ── 底下的页面 ───────────────────────────────────────── */
.page{
  position:absolute;inset:0;padding:22px 18px;
  background:
    radial-gradient(120% 60% at 18% 6%, var(--au1), transparent 62%),
    radial-gradient(90% 50% at 86% 20%, var(--au2), transparent 66%),
    radial-gradient(110% 55% at 40% 98%, var(--au3), transparent 62%),
    linear-gradient(180deg,var(--ice0),var(--ice1) 46%,var(--ice2));
}
.page .t{font-size:22px;margin:0 0 2px}
.page .s{font-size:12px;color:var(--ink-soft);margin:0 0 16px}
.row{
  display:flex;justify-content:space-between;align-items:center;
  padding:12px 14px;border-radius:14px;margin-bottom:7px;
  background:var(--glass);border:1px solid var(--hair);
  font-size:13.5px;
}
.row i{font-style:normal;color:var(--ink-mid);font-size:12px}

/* 弹窗窗口那层压暗。真机上由系统窗口给，这里给得很轻 */
.scrim{position:absolute;inset:0;background:var(--scrim)}

/* ── 弹窗那块玻璃 ─────────────────────────────────────── */
.dialog{
  position:absolute;left:26px;right:26px;top:50%;transform:translateY(-50%);
  border-radius:26px;padding:20px 18px;
  background:linear-gradient(180deg,var(--fillT),var(--fillB));
  box-shadow:inset 0 0 0 1px var(--edge);
  overflow:hidden;
}
/* 对角高光 + 四角聚光（GlassPanel 的第二层） */
.dialog::before{
  content:"";position:absolute;inset:0;pointer-events:none;
  background:
    radial-gradient(60px 60px at 26px 26px,  var(--glow), transparent 70%),
    radial-gradient(60px 60px at calc(100% - 26px) 26px, var(--glow), transparent 70%),
    radial-gradient(60px 60px at 26px calc(100% - 26px), var(--glow), transparent 70%),
    radial-gradient(60px 60px at calc(100% - 26px) calc(100% - 26px), var(--glow), transparent 70%),
    linear-gradient(125deg, var(--hi) 0%, transparent 62%);
}
/* 下沿厚度 */
.dialog::after{
  content:"";position:absolute;left:0;right:0;bottom:0;height:5px;
  background:linear-gradient(180deg, transparent, var(--foot));
}
.dialog .dt{font-size:11px;letter-spacing:1.6px;color:var(--ink-soft);margin:0 0 12px}
.dialog .op{
  display:flex;align-items:center;gap:12px;
  padding:12px 2px;font-size:16px;color:var(--ink-mid);
}
.dialog .op .bar3{width:3px;height:15px;border-radius:2px;background:transparent}
.dialog .op.on{color:var(--ink);font-weight:700}
.dialog .op.on .bar3{background:var(--accent)}

.note{
  margin:32px 0 0;font-size:12.5px;line-height:2.05;color:var(--ink-soft);
  border-top:1px solid var(--stroke);padding-top:20px;max-width:720px;
}
.note b{font-weight:400;color:var(--ink-mid)}
</style>
</head>
<body>
<div class="wrap">
  <h1>弹窗的玻璃：从"透明"到"毛玻璃"</h1>
  <p class="sub">
    两张底下的内容是同一份。<b>看弹窗的下半截</b>：左边能从弹窗里读出后面的行，
    右边只透出一片明暗。差别不在"更白"，在<b>背后那层东西是不是还认得出笔画</b>。
  </p>

  <div class="bar">
    <button id="th" aria-pressed="false">深色主题</button>
  </div>

  <div class="stage">
    <figure>
      <div class="phone" data-v="old">
        <div class="page">__PAGE__</div>
        <div class="scrim"></div>
        __DIALOG__
      </div>
      <figcaption><span class="tag">0.42</span>weight = 1f
        <em>填充 0.82 → 0.48</em></figcaption>
    </figure>

    <figure>
      <div class="phone" data-v="new">
        <div class="page">__PAGE__</div>
        <div class="scrim"></div>
        __DIALOG__
      </div>
      <figcaption><span class="tag">0.43</span>frost 0.90 → 0.78
        <em>更厚、更平</em></figcaption>
    </figure>
  </div>

  <p class="note">
    <b>问题不在"不够白"。</b>冰面本来就接近白，把白色从 0.82 提到 0.90 几乎看不出。
    真正的问题是<b>下半截只挡掉一半</b>——0.48 的填充让背后的正文以 52% 透上来，
    那是"能读出字"，也就是透明玻璃。
    <br>
    <b>磨砂的判据是"背后变成一片低对比的雾"。</b>所以两档都提到 0.9 上下、
    差值从 0.34 收到 0.12：背景仍以 10%~22% 透上来（有材质感、有色相），
    但透上来的是明暗，不是笔画。
    <br>
    <b>深色主题那边不是把白色调浓。</b>0.18 的白在深底上只会得到一块发灰的半透明板，
    背后极光一条条透上来。用的是一块<b>比背景亮的深蓝</b>（#2A3A4D @ 0.92）：
    深色主题里"浮起来"等于"更亮"，同时它本身够厚，把背后的东西压掉。
    <br>
    <b>范围。</b>挂在弹窗容器上，所以七个弹窗（选择 / 输入 / 确认 / 日期 / 时间 /
    启动提示 / 手势提示）一起变——包括右上角「⋯」那颗和设置里所有弹窗。
    参数化的话七处都要记得传，漏一个的症状是"这个弹窗好像薄一点"，没人会去查。
  </p>
</div>

<script>
const root = document.documentElement;
const th = document.getElementById('th');
th.addEventListener('click', () => {
  const dark = root.dataset.theme !== 'dark';
  root.dataset.theme = dark ? 'dark' : 'light';
  th.setAttribute('aria-pressed', String(dark));
  th.textContent = dark ? '浅色主题' : '深色主题';
});
if (location.hash === '#dark') th.click();

// 两档玻璃的四层参数。数值直接对着 GlassTokens / GlassPanel 抄。
const GLASS = {
  light: {
    old: { fillT:'rgba(255,255,255,.816)', fillB:'rgba(255,255,255,.484)',
           hi:'rgba(255,255,255,.143)', glow:'rgba(255,255,255,.10)',
           edge:'rgba(255,255,255,.59)', foot:'rgba(20,38,60,.05)' },
    new: { fillT:'rgba(255,255,255,.90)',  fillB:'rgba(255,255,255,.78)',
           hi:'rgba(255,255,255,.43)',  glow:'rgba(255,255,255,.13)',
           edge:'rgba(255,255,255,.59)', foot:'rgba(20,38,60,.05)' },
  },
  dark: {
    old: { fillT:'rgba(255,255,255,.18)', fillB:'rgba(255,255,255,.105)',
           hi:'rgba(255,255,255,.027)', glow:'rgba(255,255,255,.065)',
           edge:'rgba(255,255,255,.11)', foot:'rgba(232,241,250,.055)' },
    new: { fillT:'rgba(42,58,77,.92)',    fillB:'rgba(34,48,63,.86)',
           hi:'rgba(255,255,255,.081)', glow:'rgba(255,255,255,.10)',
           edge:'rgba(255,255,255,.11)', foot:'rgba(232,241,250,.055)' },
  },
};

function paint() {
  const theme = root.dataset.theme === 'dark' ? 'dark' : 'light';
  document.querySelectorAll('.phone').forEach(ph => {
    const p = GLASS[theme][ph.dataset.v];
    const d = ph.querySelector('.dialog');
    d.style.setProperty('--fillT', p.fillT);
    d.style.setProperty('--fillB', p.fillB);
    d.style.setProperty('--hi', p.hi);
    d.style.setProperty('--glow', p.glow);
    d.style.setProperty('--edge', p.edge);
    d.style.setProperty('--foot', p.foot);
  });
}
new MutationObserver(paint).observe(root, {attributes:true, attributeFilter:['data-theme']});
paint();
</script>
</body>
</html>
"""

PAGE = """
        <div class="t">如期</div>
        <div class="s">今年还剩 65 天 · 今天霜降</div>
        <div class="row"><span>项目上线</span><i>还剩 42 天</i></div>
        <div class="row"><span>春节回家</span><i>还剩 88 天</i></div>
        <div class="row"><span>体检</span><i>还剩 12 天</i></div>
        <div class="row"><span>房租到期</span><i>还剩 5 天</i></div>
        <div class="row"><span>年假清零</span><i>还剩 63 天</i></div>
        <div class="row"><span>交论文初稿</span><i>还剩 21 天</i></div>
        <div class="row"><span>驾照换证</span><i>还剩 140 天</i></div>
"""

DIALOG = """
        <div class="dialog">
          <div class="dt">如 期</div>
          <div class="op"><span class="bar3"></span>搜索</div>
          <div class="op"><span class="bar3"></span>日历</div>
          <div class="op"><span class="bar3"></span>年历</div>
          <div class="op on"><span class="bar3"></span>设置</div>
        </div>
"""

html = TEMPLATE.replace("__FACES__", faces).replace("__PAGE__", PAGE).replace("__DIALOG__", DIALOG)
OUT.write_text(html, encoding="utf-8")
print("写出 %s（%.1f MB）" % (OUT, OUT.stat().st_size / 1024 / 1024))
