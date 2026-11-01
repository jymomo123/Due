# -*- coding: utf-8 -*-
"""生成 0.41 的对照预览：全屏卡片页背后那一层。

三档并排，是为了看清"加大模糊"和"压暗"各自贡献了多少：
  0.40   —— 22dp，无遮罩
  只加糊  —— 30dp，无遮罩
  0.41   —— 30dp + 遮罩 0.24

为什么要有这个东西：模糊和压暗都是"整体观感"上的差别，光看代码里的
`22.dp -> 30.dp` 和 `alpha 0.24` 判断不出够不够。这一屏又要装到手机上
才看得见，所以按真实比例做一个 1:1 的对照。

比例怎么对：手机框宽 390 CSS px 当作 390dp 的屏幕，CSS 的 `blur(Npx)`
就对应 `Modifier.blur(N.dp)`。真机 1080px/480dpi 下 30dp = 90px，
占屏宽 8.3%；这里 30px 占 390px 也是 7.7%，同一量级。

用法：python tools/make_veil_preview.py
"""
import base64
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
FONT = ROOT / "app/src/main/res/font/noto_sc_400.ttf"
OUT = ROOT / "preview/card-veil.html"

b64 = base64.b64encode(FONT.read_bytes()).decode()

TEMPLATE = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>如期 · 卡片页背后的那一层</title>
<style>
@font-face{font-family:"Due";src:url(data:font/ttf;base64,__FONT__) format("truetype");font-weight:400;font-display:block}

:root{
  --ink:#14263C; --ink-mid:#33506E; --ink-soft:#4B6785;
  --ice0:#F7FBFF; --ice1:#E8F1FC; --ice2:#D8E7F9;
  --glass:rgba(255,255,255,.68);
  --glass2:rgba(255,255,255,.44);
  --glass-hi:rgba(255,255,255,.95);
  --glass-lo:rgba(127,168,214,.22);
  --hair:rgba(255,255,255,.72);
  --stroke:rgba(127,168,214,.26);
  --veil:rgba(18,42,69,.24);
  --accent:#2F76F0;
  --aurora1:rgba(169,204,245,.9);
  --aurora2:rgba(190,203,240,.85);
  --aurora3:rgba(168,216,222,.8);
  --page-bg:#F2F7FE;
  --frame:#DDE7F4;
  --shadow:rgba(20,38,60,.45);
}
[data-theme="dark"]{
  --ink:#E8F1FA; --ink-mid:#93AAC4; --ink-soft:#6E88A5;
  --ice0:#0B1622; --ice1:#101E2C; --ice2:#09121C;
  --glass:rgba(255,255,255,.15);
  --glass2:rgba(255,255,255,.095);
  --glass-hi:rgba(255,255,255,.18);
  --glass-lo:rgba(110,155,200,.10);
  --hair:rgba(255,255,255,.11);
  --stroke:rgba(255,255,255,.20);
  --veil:rgba(3,8,14,.46);
  --accent:#6BA6FF;
  --aurora1:rgba(46,92,138,.95);
  --aurora2:rgba(52,69,111,.9);
  --aurora3:rgba(34,84,92,.9);
  --page-bg:#0A121C;
  --frame:#1B2836;
  --shadow:rgba(0,0,0,.6);
}

*{box-sizing:border-box}
html,body{margin:0}
body{
  font-family:"Due",system-ui,-apple-system,"PingFang SC",sans-serif;
  background:var(--page-bg); color:var(--ink);
  padding:34px 20px 48px;
  transition:background .3s ease,color .3s ease;
}
.wrap{max-width:1260px;margin:0 auto}
h1{font-size:19px;font-weight:400;letter-spacing:.2px;margin:0 0 6px}
.sub{font-size:12.5px;color:var(--ink-soft);line-height:1.9;margin:0 0 24px;max-width:720px}
.sub b{font-weight:400;color:var(--ink-mid)}

.toolbar{display:flex;gap:8px;margin:0 0 24px}
.toolbar button{
  font-family:inherit;font-size:12.5px;color:var(--ink-mid);
  background:var(--glass);border:1px solid var(--stroke);
  border-radius:999px;padding:7px 15px;cursor:pointer;
  -webkit-backdrop-filter:blur(8px);backdrop-filter:blur(8px);
  transition:color .2s,border-color .2s,background .2s;
}
.toolbar button[aria-pressed="true"]{color:var(--ink);border-color:var(--glass-hi)}

.stage{display:flex;gap:24px;flex-wrap:wrap;justify-content:center}
figure{margin:0;width:390px}
.phone{
  position:relative;width:390px;height:660px;border-radius:34px;
  overflow:hidden;border:1px solid var(--frame);
  box-shadow:0 26px 60px -30px var(--shadow);
  isolation:isolate;
}
figcaption{
  margin-top:12px;font-size:12.5px;color:var(--ink-mid);line-height:1.75;
}
figcaption .tag{
  font-size:10.5px;letter-spacing:.6px;color:var(--ink-soft);
  border:1px solid var(--stroke);border-radius:6px;padding:2px 7px;margin-right:7px;
}
figcaption em{display:block;font-style:normal;color:var(--ink-soft);font-size:11.5px;margin-top:2px}

/* ── 屏幕里的三层：页面 / 遮罩 / 卡片 ───────────────────── */

.screen{position:absolute;inset:0;overflow:hidden}

/* 冰面 + 极光。模糊的时候这一层跟着一起糊。 */
.page{
  position:absolute;inset:-40px;      /* 外扩，避免模糊把边缘吃掉露出白边 */
  background:
    radial-gradient(120% 60% at 18% 8%,  var(--aurora1), transparent 62%),
    radial-gradient(90% 50% at 86% 22%,  var(--aurora2), transparent 66%),
    radial-gradient(110% 55% at 40% 96%, var(--aurora3), transparent 62%),
    linear-gradient(180deg, var(--ice0), var(--ice1) 46%, var(--ice2));
  padding:80px 62px;
  filter:blur(var(--r,0px));
  transition:filter .3s cubic-bezier(.2,.7,.2,1);
}
.b1{font-size:22px;color:var(--ink);margin:0 0 2px}
.b2{font-size:12.5px;color:var(--ink-soft);margin:0 0 20px}

/* 详情页那张卡 */
.dcard{
  border-radius:22px;padding:18px 18px 16px;
  background:linear-gradient(180deg,var(--glass),var(--glass2));
  border:1px solid var(--hair);
  border-bottom-color:var(--glass-lo);
}
.dcard .num{font-size:58px;line-height:1.02;letter-spacing:-1px}
.dcard .num span{font-size:18px;margin-left:6px}
.dcard .t{font-size:15px;margin-top:10px}
.dcard .d{font-size:11.5px;color:var(--ink-soft);margin-top:5px}

.grp{font-size:10.5px;letter-spacing:1.6px;color:var(--ink-soft);margin:22px 0 8px}
.row{
  display:flex;justify-content:space-between;align-items:center;
  padding:13px 16px;border-radius:14px;margin-bottom:7px;
  background:var(--glass);border:1px solid var(--hair);
  border-bottom-color:var(--glass-lo);
  font-size:14px;
}
.row i{font-style:normal;color:var(--ink-mid);font-size:12.5px}

/* 遮罩：压在糊掉的页面之上、卡片之下 */
.veil{
  position:absolute;inset:0;background:var(--veil);
  opacity:var(--v,0);transition:opacity .3s cubic-bezier(.2,.7,.2,1);
}

/* 大卡：这一屏唯一的主角 */
.bigcard{
  position:absolute;left:10px;right:10px;top:50%;transform:translateY(-50%);
  border-radius:26px;padding:30px 22px 26px;
  background:linear-gradient(180deg,var(--glass),var(--glass2));
  border:1px solid var(--hair);
  border-bottom-color:var(--glass-lo);
}
.bigcard .n{font-size:96px;line-height:1.02;letter-spacing:-2px}
.bigcard .n span{font-size:24px;margin-left:8px}
.bigcard .t{font-size:19px;margin-top:14px}
.bigcard .d{font-size:12.5px;color:var(--ink-soft);margin-top:6px}
.bigcard .u{
  margin-top:18px;padding-top:14px;border-top:1px solid var(--stroke);
  font-size:11.5px;color:var(--ink-soft);letter-spacing:.4px;
}

/* 保存按钮 */
.save{
  position:absolute;left:18px;bottom:26px;
  font-size:13px;color:var(--ink);
  background:var(--glass);border:1px solid var(--hair);
  border-radius:20px;padding:11px 18px;
  -webkit-backdrop-filter:blur(10px);backdrop-filter:blur(10px);
}

.note{
  margin:36px auto 0;max-width:720px;font-size:12.5px;line-height:2.05;
  color:var(--ink-soft);border-top:1px solid var(--stroke);padding-top:20px;
}
.note b{font-weight:400;color:var(--ink-mid)}
</style>
</head>
<body>
<div class="wrap">
  <h1>点开卡片之后，背后那一层</h1>
  <p class="sub">
    三档并排：<b>0.40 原样</b>（22dp，无遮罩）、<b>只加大模糊</b>（30dp，无遮罩）、
    <b>0.41</b>（30dp + 遮罩 0.24）。按真实比例排（框宽 390px 当 390dp），
    三张的糊度可以直接互相比。
  </p>

  <div class="toolbar">
    <button id="th" aria-pressed="false">深色主题</button>
    <button id="an">再播一次</button>
  </div>

  <div class="stage">
    <figure>
      <div class="phone" data-variant="old">
        <div class="screen">
          <div class="page">__PAGE__</div>
          <div class="veil"></div>
          __CARD__
        </div>
      </div>
      <figcaption><span class="tag">0.40</span>22dp · 无遮罩
        <em>细节没了，但整屏还亮着</em></figcaption>
    </figure>

    <figure>
      <div class="phone" data-variant="blur">
        <div class="screen">
          <div class="page">__PAGE__</div>
          <div class="veil"></div>
          __CARD__
        </div>
      </div>
      <figcaption><span class="tag">只加糊</span>30dp · 无遮罩
        <em>块面化开了，可背景和卡片一个亮度</em></figcaption>
    </figure>

    <figure>
      <div class="phone" data-variant="new">
        <div class="screen">
          <div class="page">__PAGE__</div>
          <div class="veil"></div>
          __CARD__
        </div>
      </div>
      <figcaption><span class="tag">0.41</span>30dp · 遮罩 0.24
        <em>背景退成一层冷色，卡片重新有边</em></figcaption>
    </figure>
  </div>

  <p class="note">
    <b>为什么是两件事。</b>模糊只降细节、不改亮度。这一套界面里背景（冰面 #E8F1FC）
    和卡片（白 0.68 的玻璃）本来就只差一档亮度——糊成一片之后，它反而更像一块均匀的浅色板。
    中间那张就是这个状态：看得出一层薄薄的横带，但卡片浮不起来，因为它和背景一样亮。
    右边那张只多了 0.24 的遮罩，背景退到 #B4CBE0，卡片仍是 #E7EEF5，差着一整档。
    <br>
    <b>为什么只给 0.24。</b>卡片是半透明的，遮罩压在它下面，它的颜色会被背景带着走。
    试到 0.32 时卡片已经从"白玻璃"变成"灰玻璃"（#E2E6EB），冰蓝的色相丢了——
    分离感该靠"背景压下去多少"，不是靠"把背景压到多黑"。
    <br>
    <b>遮罩不是黑的。</b>用的是同一色相的深蓝（#122A45），压下去之后背景仍然是冷的；
    纯黑会把冰面的色相当场抽掉。深色主题下浓度给到 0.46，因为深色底再压也压不暗，
    它要压的其实是极光那几条亮带。
    <br>
    <b>真机上的限制。</b>API 31 以下 <b>Modifier.blur 是空操作</b>（RenderEffect 是 31 才有的），
    那批机型上只有遮罩生效——看不到"更糊"，只看得到"变暗"。这是平台限制，不是没做。
  </p>
</div>

<script>
const root = document.documentElement;
const th = document.getElementById('th');
function paint(dark) {
  root.dataset.theme = dark ? 'dark' : 'light';
  th.setAttribute('aria-pressed', String(dark));
  th.textContent = dark ? '浅色主题' : '深色主题';
}
// 直接开 #dark 就是深色，方便截图和分享
paint(location.hash === '#dark');
th.addEventListener('click', () => paint(root.dataset.theme !== 'dark'));

// 三档参数。--r 是模糊半径（px ≈ dp），--v 是遮罩浓度（1 = 令牌里的 alpha）。
const VAR = {
  old:   { r: '22px', v: '0' },
  blur:  { r: '30px', v: '0' },
  new:   { r: '30px', v: '1' },
};

function set(ph, r, v) {
  ph.querySelector('.page').style.setProperty('--r', r);
  ph.querySelector('.veil').style.setProperty('--v', v);
}

document.querySelectorAll('.phone').forEach(ph => {
  const p = VAR[ph.dataset.variant];
  set(ph, p.r, p.v);
});

document.getElementById('an').addEventListener('click', () => {
  const phones = [...document.querySelectorAll('.phone')];
  phones.forEach(ph => set(ph, '0px', '0'));
  setTimeout(() => {
    phones.forEach(ph => {
      const p = VAR[ph.dataset.variant];
      set(ph, p.r, p.v);
    });
  }, 80);
});
</script>
</body>
</html>
"""

PAGE = """
      <div class="b1">如期</div>
      <div class="b2">还有 42 天</div>
      <div class="dcard">
        <div class="num">42<span>天</span></div>
        <div class="t">项目上线</div>
        <div class="d">2026.11.12 · 周四</div>
      </div>
      <div class="grp">其 他 事 件</div>
      <div class="row"><span>春节回家</span><i>还剩 88 天</i></div>
      <div class="row"><span>体检</span><i>还剩 12 天</i></div>
      <div class="row"><span>房租到期</span><i>还剩 5 天</i></div>
      <div class="row"><span>年假清零</span><i>还剩 63 天</i></div>
      <div class="grp">设 置</div>
      <div class="row"><span>显示秒</span><i>开</i></div>
      <div class="row"><span>提醒</span><i>提前一天</i></div>
"""

CARD = """
      <div class="bigcard">
        <div class="n">42<span>天</span></div>
        <div class="t">项目上线</div>
        <div class="d">2026.11.12 · 周四</div>
        <div class="u">点数字换读法 · 长按开关秒 · 点别处返回</div>
      </div>
      <div class="save">保存为图片</div>
"""

html = (TEMPLATE
        .replace("__FONT__", b64)
        .replace("__PAGE__", PAGE)
        .replace("__CARD__", CARD))

OUT.write_text(html, encoding="utf-8")
print("写出 %s（%.0f KB）" % (OUT, OUT.stat().st_size / 1024))
