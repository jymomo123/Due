# -*- coding: utf-8 -*-
"""0.58.3 全屏卡片页「退出去」的逐帧示意：边升边缩，最后落在详情卡上。

**为什么需要它**：用户回传的录屏里，退出这一段是"大卡淡掉 + 小卡露出来"，
两个尺寸交叉淡出，看起来顿一下。改法是把退场从「只平移」改成「平移 + 缩放」，
但**这个工程没有真机**，改完只跑了编译和静态自检——"落点对不对"只能靠算，
再画出来给自己和用户各看一眼。

**图里是什么**：五帧，p = 0 / 0.25 / 0.5 / 0.75 / 1。
实心卡是**移动中的那张**，虚线框是**详情卡的位置**（它就在那儿等着）。
画的是几何，不是观感：详情页在真实设备上是**糊的**（模糊随 p 退到 0），
图里没画那层模糊，所以中段会比真机看着"重影"更明显。

几何全部来自录屏实测（540×1178 的帧，1.5 px/dp）：
    全屏卡片  左 16  上 385  宽 506  高 355
    详情卡片  左 32  上 140  宽 457  高 315

用法：python tools/make_card_close_preview.py
产物：preview/card-close-0583.html + .png
"""
import base64
import pathlib
import subprocess

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "preview/card-close-0583.html"
PNG = ROOT / "preview/card-close-0583.png"
CHROME = "C:/Program Files/Google/Chrome/Application/chrome.exe"

FONTS = {
    400: ROOT / "app/src/main/res/font/noto_sc_400.ttf",
    500: ROOT / "app/src/main/res/font/noto_sc_500.ttf",
    700: ROOT / "app/src/main/res/font/noto_sc_700.ttf",
}

faces = "\n".join(
    '@font-face{font-family:"Due";src:url(data:font/ttf;base64,%s) format("truetype");'
    "font-weight:%d;font-display:block}" % (base64.b64encode(p.read_bytes()).decode(), w)
    for w, p in FONTS.items()
)

# ── 实测几何（px，1.5 px/dp） ──────────────────────────────
SRC = (16, 385, 506, 355)   # 全屏卡片页那张卡
DST = (32, 140, 457, 315)   # 详情页那张卡
SCREEN = (540, 1178)        # 录屏原尺寸（360dp @1.5）
SCALE = 0.34                # 整屏缩到 184×400，一行放得下五帧

INK = "#14263C"
INK_MID = "#33506E"
INK_SOFT = "#4B6785"
ACCENT = "#2F76F0"


def lerp(a, b, t):
    return a + (b - a) * t


def frame(p):
    x = lerp(SRC[0], DST[0], p)
    y = lerp(SRC[1], DST[1], p)
    w = lerp(SRC[2], DST[2], p)
    h = lerp(SRC[3], DST[3], p)
    return x, y, w, h


def card_html(p, i):
    x, y, w, h = frame(p)
    # 数字按**宽度**反推（真机就是 `rememberFittedNumberSp` 按可用宽度算的）。
    # 只按高度算的话，「148 天」会横向溢出被卡片裁掉——那正是 0.58.0 之前
    # 宽档位上出过的那个 bug，画图时别把它再画一遍。
    # 再乘 SCALE：w / h 是**原尺寸**的 px，而卡片是按 SCALE 缩着画的。
    # 漏了这一步，字号会是卡片的 3 倍，数字直接从卡片底下穿出去。
    num = min(w * 0.30, h * 0.55) * SCALE
    return f"""
    <div class="cell">
      <div class="frame" style="width:{SCREEN[0]*SCALE:.1f}px;height:{SCREEN[1]*SCALE:.1f}px">
        <div class="page"></div>
        <div class="target" style="
            left:{DST[0]*SCALE:.1f}px;top:{DST[1]*SCALE:.1f}px;
            width:{DST[2]*SCALE:.1f}px;height:{DST[3]*SCALE:.1f}px"></div>
        <div class="card" style="
            left:{x*SCALE:.1f}px;top:{y*SCALE:.1f}px;
            width:{w*SCALE:.1f}px;height:{h*SCALE:.1f}px">
          <div class="bar"></div>
          <div class="cat" style="font-size:{num*0.10:.1f}px">1</div>
          <div class="num" style="font-size:{num:.1f}px;line-height:{num*1.15:.1f}px">148
            <span class="unit" style="font-size:{num*0.20:.1f}px">天</span></div>
          <div class="date" style="font-size:{num*0.13:.1f}px">2027年3月28日 · 周日</div>
        </div>
      </div>
      <div class="cap">p = {p:.2f}</div>
      <div class="sub">{w:.0f} × {h:.0f}　左上 {x:.0f},{y:.0f}</div>
    </div>"""


cells = "".join(card_html(p, i) for i, p in enumerate([0.0, 0.25, 0.5, 0.75, 1.0]))

html = f"""<!DOCTYPE html>
<html lang="zh"><head><meta charset="utf-8"><style>
{faces}
*{{box-sizing:border-box;margin:0;padding:0}}
body{{width:{(SCREEN[0]*SCALE+14)*5+50:.0f}px;padding:20px;
  background:linear-gradient(160deg,#EFF5FB,#DAE7F4 60%,#D3E2F1);
  font-family:"Due",system-ui,sans-serif;color:{INK}}}
h1{{font-size:15px;font-weight:700;margin-bottom:4px}}
.sub0{{font-size:11.5px;color:{INK_SOFT};line-height:1.6;margin-bottom:14px}}
.strip{{display:flex;gap:14px;align-items:flex-start}}
.cell{{width:{SCREEN[0]*SCALE:.1f}px}}
.frame{{position:relative;border-radius:14px;overflow:hidden;
  background:rgba(255,255,255,.35);box-shadow:inset 0 0 0 1px rgba(75,103,133,.18)}}
.page{{position:absolute;inset:0;
  background:linear-gradient(180deg,rgba(255,255,255,.55),rgba(214,231,246,.75))}}
.target{{position:absolute;border:1.5px dashed rgba(47,118,240,.75);border-radius:11px}}
.card{{position:absolute;background:rgba(255,255,255,.93);border-radius:11px;
  box-shadow:0 2px 10px rgba(20,38,60,.14);overflow:hidden;
  padding:8px 9px}}
.bar{{position:absolute;left:6px;top:8px;bottom:8px;width:2.5px;
  background:{ACCENT};border-radius:2px}}
.cat{{margin-left:11px;font-weight:700;color:{INK}}}
.num{{margin-left:9px;font-weight:700;color:{INK};letter-spacing:-.5px;white-space:nowrap}}
.unit{{color:{INK_MID}}}
.date{{margin-left:11px;margin-top:3px;color:{INK_MID}}}
.cap{{margin-top:7px;font-size:11px;font-weight:700;color:{INK}}}
.sub{{font-size:10px;color:{INK_SOFT};margin-top:1px}}
.foot{{margin-top:14px;padding-top:10px;border-top:1px dashed rgba(75,103,133,.3);
  font-size:11px;line-height:1.7;color:{INK_SOFT}}}
.foot b{{color:{INK}}}
</style></head><body>
<h1>退出全屏卡片页：边升边缩，落在详情卡上</h1>
<div class="sub0">实心卡 = 移动中的那张　虚线框 = 详情卡的位置。
图中背景**没有画模糊**——真机上详情页在这 180ms 里是从 30dp 逐渐清晰的。</div>
<div class="strip">{cells}</div>
<div class="foot">
起点 <b>506×355</b>、左上 <b>(16, 385)</b>；终点 <b>457×315</b>、左上 <b>(32, 140)</b>。
宽比 <b>0.903</b>、高比 <b>0.887</b>——<b>不是等比</b>，差 1.8%：
卡片高度里有一段是固定字号的日期行，不随宽度缩放。等比缩会在底边留一条缝，
所以宽高各按自己的比例走；1.8% 的压扁眼睛看不出来，底边的缝看得出来。
</div>
</body></html>"""

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(html, encoding="utf-8")
print("html:", OUT)

subprocess.run(
    [
        CHROME,
        "--headless=new",
        "--force-device-scale-factor=2",
        "--hide-scrollbars",
        "--window-size=1010,580",
        f"--screenshot={PNG}",
        OUT.as_uri(),
    ],
    check=True,
    capture_output=True,
)
print("png:", PNG, PNG.stat().st_size, "bytes")
