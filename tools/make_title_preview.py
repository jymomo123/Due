# -*- coding: utf-8 -*-
"""0.58.3 详情页标题 A/B：左「改前 18sp」，右「改后 26sp + ␣还有」。

**为什么需要它**：这一轮改的是「标题变大 + 后面接一个词」，两件事都会改变
**每行能放几个字**，而后果只有在长标题上才看得出来——短标题两个版本都好看，
看不出区别。用户手上没有能装包的机器（我也没真机），所以做一张按代码算的对照图。

**它不是截图**：字号 / 行盒 / 字距 / 可用宽度全部取自代码，但渲染是 Chrome
（HTML），不是 Compose。能证明"字号差多少、什么时候省略、词在什么位置"，
证明不了像素级观感。

数字来源：
  18sp = `headlineSmall`（18/26/SemiBold/−0.2）
  26sp = `headlineMedium`（26/36/SemiBold/−0.3）
  可用宽度 261dp = 卡片 320 − 2×Gutter 20 − 19（竖线 3 + 间距 16）
  「␣还有」= 0.227em × 26 + 2 × 25.7 = 57.0dp（21.8%）

用法：python tools/make_title_preview.py
产物：preview/title-0583.html + preview/title-0583.png
"""
import base64
import pathlib
import subprocess

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "preview/title-0583.html"
PNG = ROOT / "preview/title-0583.png"
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

# ── 全部来自代码 ─────────────────────────────────────────────
LINE_W = 261          # 标题行可用宽度
OLD_SP, OLD_LH, OLD_TRACK = 18, 26, -0.2
NEW_SP, NEW_LH, NEW_TRACK = 26, 36, -0.3
INK = "#14263C"
INK_MID = "#33506E"
INK_SOFT = "#4B6785"

SAMPLES = [
    ("和妈妈一起去看那场演出", "11 字 · 两版都放得下"),
    ("项目上线最后期限确认会议", "12 字 · 改后刚好两行"),
    ("第一次见面旅行结婚周年纪念日放假考试", "19 字 · 改后第三行被省略，词也没了"),
]


def old_line(text):
    return (
        f'<div class="line old" style="font-size:{OLD_SP}px;line-height:{OLD_LH}px;'
        f'letter-spacing:{OLD_TRACK}px">{text}</div>'
    )


def new_line(text, word):
    return (
        f'<div class="line new" style="font-size:{NEW_SP}px;line-height:{NEW_LH}px;'
        f'letter-spacing:{NEW_TRACK}px">{text}'
        f'<span style="color:{INK_MID}"> {word}</span></div>'
    )


rows = "\n".join(
    f"""
    <div class="sample">
      <div class="cap">{note}</div>
      <div class="pair">
        <div class="col"><div class="tag">改前 · {OLD_SP}sp</div>{old_line(t)}</div>
        <div class="col"><div class="tag">改后 · {NEW_SP}sp + 词</div>{new_line(t, '还有')}</div>
      </div>
    </div>"""
    for t, note in SAMPLES
)

html = f"""<!DOCTYPE html>
<html lang="zh"><head><meta charset="utf-8"><style>
{faces}
*{{box-sizing:border-box;margin:0;padding:0}}
body{{
  width:640px;padding:22px;
  background:linear-gradient(160deg,#EFF5FB,#DAE7F4 60%,#D3E2F1);
  font-family:"Due",system-ui,sans-serif;color:{INK};
}}
h1{{font-size:15px;font-weight:700;letter-spacing:.2px;margin-bottom:4px}}
.sub{{font-size:11.5px;color:{INK_SOFT};letter-spacing:.3px;margin-bottom:16px;line-height:1.5}}
.sample{{margin-bottom:14px}}
.cap{{font-size:11px;color:{INK_SOFT};letter-spacing:.4px;margin-bottom:6px}}
.pair{{display:flex;gap:14px;align-items:flex-start}}
.col{{
  width:{LINE_W}px;flex:0 0 {LINE_W}px;position:relative;
  background:rgba(255,255,255,.72);border-radius:18px;padding:12px 14px;
  box-shadow:0 1px 2px rgba(20,38,60,.05),0 6px 16px rgba(20,38,60,.06);
}}
.tag{{
  font-size:10px;color:{INK_SOFT};letter-spacing:.5px;margin-bottom:6px;
  padding-bottom:5px;border-bottom:1px dashed rgba(75,103,133,.28);
}}
.line{{
  font-weight:700;color:{INK};
  display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:2;
  overflow:hidden;
}}
.foot{{
  margin-top:6px;font-size:11px;color:{INK_SOFT};line-height:1.7;letter-spacing:.2px;
  border-top:1px dashed rgba(75,103,133,.3);padding-top:10px;
}}
.foot b{{color:{INK};font-weight:700}}
</style></head><body>
<h1>详情页标题：18sp → 26sp，后面接「␣还有」</h1>
<div class="sub">两列宽度都是标题行真实可用宽度 <b>261dp</b>。省略行为用两行截断模拟
<code>maxLines = 2 + Ellipsis</code>，不是真机截图。</div>
{rows}
<div class="foot">
26sp 下一个汉字 <b>25.7dp</b>，一行 ≈ 10.1 字；「␣还有」<b>57.0dp</b>，占 <b>21.8%</b>。
两行上限从 ≈ 29 字降到 <b>≈ 16 字</b>——<b>省略号吃的是末尾，标题一长先没掉的是那个词。</b>
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
        "--window-size=640,620",
        f"--screenshot={PNG}",
        OUT.as_uri(),
    ],
    check=True,
    capture_output=True,
)
print("png:", PNG, PNG.stat().st_size, "bytes")
