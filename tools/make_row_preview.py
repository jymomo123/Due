# -*- coding: utf-8 -*-
"""0.58.0 列表行改版的对照预览：左边是录屏实拍（改前），右边是按代码算的示意（改后）。

**为什么需要它**：这一轮改的是「左贴顶 / 右贴底 / 秒表挪到数字下面 / 负数折成计时」，
全是位置关系，靠文字说不清；而且新布局用户和我都没见过（改完只跑了编译和静态自检）。

**左边那张是真的**：从 `Record_2026-10-31-17-43-30` 里裁出来的原始像素，
720px 宽（= 360dp @2x），连行高都不用估。

**右边那张是算出来的**：字号、行盒、留白全部取自代码（`Theme.kt` 的 Typography、
`RowDensity.STANDARD`、`EventRow` 里的 `0.195em` / `0.11em` 偏移），
但它是 HTML 不是 Compose——**能证明"结构和顺序对"，证明不了像素**。
两边行高本来就对不齐：实拍那一列的字受 `includeFontPadding` 影响，行盒比
`1.15em` 还要高一点，而 HTML 里没有这个概念。看图看的是"谁在上谁在下、
右边缘是不是一条线"，不是量像素。

用法：python tools/make_row_preview.py
产物：preview/row-058.html（截图走 Chrome headless，见 RULES.md「工程」）
"""
import base64
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "preview/row-058.html"
SHOT = pathlib.Path("C:/Users/Administrator/AppData/Local/Temp/rec")

FONTS = {
    400: ROOT / "app/src/main/res/font/noto_sc_400.ttf",
    500: ROOT / "app/src/main/res/font/noto_sc_500.ttf",
    700: ROOT / "app/src/main/res/font/noto_sc_700.ttf",
    900: ROOT / "app/src/main/res/font/noto_sc_900.ttf",
}

faces = "\n".join(
    '@font-face{font-family:"Due";src:url(data:font/ttf;base64,%s) format("truetype");'
    "font-weight:%d;font-display:block}" % (base64.b64encode(p.read_bytes()).decode(), w)
    for w, p in FONTS.items()
)


def img(path):
    b = base64.b64encode(pathlib.Path(path).read_bytes()).decode()
    return f"data:image/png;base64,{b}"


# ── 尺寸：全部来自代码，不是"看着差不多" ───────────────────────
W = 360          # 屏宽 dp（录屏 720px @2x）
PAD_H = 16       # RowInset
PAD_V = 13       # RowDensity.STANDARD.verticalPaddingDp
BAR = 3          # 事件色竖线
BAR_GAP = 13
COL_GAP = 12
NUM = 48         # displaySp(RowDensity.STANDARD.numberSp)
NUM_BOX = NUM * 1.15     # RollingText(fontSize=) → 行盒 = 字体 1.15em
UNIT_RAISE = NUM * 0.11  # 「天」抬到数字基线：0.195em − 4.1sp
# 秒表 = 0.30 × 数字，但封顶 labelLarge 14sp（0.58.2）。
# 不封顶的话宽松档是 0.30 × 60 = 18sp，而那一档标题是 19.44sp，比值 0.93。
CLOCK = min(NUM * 0.30, 14)
UNIT_W = 13 * 1.0 + 0.6  # 「天」盒宽 = 1.000em × 13sp + 0.6sp 字距
CLOCK_BOX = CLOCK * 1.15
CLOCK_LIFT = NUM * 0.195  # 抬进数字行盒的死区

CSS = """
*{box-sizing:border-box;margin:0;padding:0}
body{background:#eef2f7;font-family:"Due",system-ui;color:#0d1b2a;
     padding:28px 20px 40px;display:flex;gap:28px;justify-content:center;
     -webkit-font-smoothing:antialiased}
.pane{width:%(W)spx}
.pane h2{font-size:13px;font-weight:500;color:#5b6b7d;letter-spacing:.4px;
         margin-bottom:10px;display:flex;align-items:baseline;gap:8px}
.pane h2 b{font-size:15px;font-weight:700;color:#16283a}
.pane h2 span{font-weight:400;color:#8b9aab;font-size:11.5px}
.screen{width:%(W)spx;background:#f7fbff;border-radius:18px;overflow:hidden;
        box-shadow:0 10px 30px rgba(20,50,80,.10)}
.shot{width:100%%;display:block}
.note{font-size:11px;color:#7a8b9d;line-height:1.6;margin-top:10px}
.note b{color:#c7385e;font-weight:500}

.row{position:relative;padding:%(PAD_V)spx %(PAD_H)spx;
     display:flex;align-items:stretch;gap:0}
.row + .row{box-shadow:inset 0 1px 0 rgba(20,50,80,.055)}
.bar{flex:0 0 %(BAR)spx;border-radius:1.5px;background:#3f8fe0;
     margin-right:%(BAR_GAP)spx}
.today{background:#4a1626;color:#fff}
.today .bar{background:rgba(255,255,255,.85)}

/* ── 左列：贴顶 ───────────────────────────────── */
.left{flex:1 1 0;min-width:0;display:flex;flex-direction:column;
      align-self:flex-start}
.title{font-size:18px;line-height:26px;font-weight:700;letter-spacing:-.2px;
       display:flex;align-items:baseline;gap:0;white-space:nowrap}
.title .txt{overflow:hidden;text-overflow:ellipsis}
/* 0.58.0：引线没了，只剩一个空格（字体里 U+0020 = 0.227em × 18sp = 4.1px） */
.gap{flex:0 0 4.1px}
/* 状态词和标题同字体同字号，只靠颜色分主次 */
.state{font-size:18px;line-height:26px;font-weight:700;letter-spacing:-.2px;
       opacity:.72;flex:0 0 auto}
.sub{font-size:13px;line-height:20px;opacity:.72;padding-top:2px;
     white-space:nowrap;overflow:hidden;text-overflow:ellipsis}

/* ── 右列：贴底、全部右对齐 ───────────────────── */
.right{flex:0 0 auto;align-self:flex-end;display:flex;flex-direction:column;
       align-items:flex-end}
.numline{display:flex;align-items:flex-end}
.num{font-size:%(NUM)spx;line-height:%(NUM_BOX)spx;font-weight:900;
     letter-spacing:-1.8px}
.unit{font-size:13px;line-height:18px;font-weight:500;letter-spacing:.6px;
      margin-left:3px;margin-bottom:%(UNIT_RAISE)spx;opacity:.72}
.clock{font-size:%(CLOCK)spx;line-height:%(CLOCK_BOX)spx;font-weight:500;
       letter-spacing:.6px;font-feature-settings:"tnum";
       position:relative;top:-%(CLOCK_LIFT)spx;opacity:.72}
.cat{font-size:11.5px;line-height:16px;font-weight:500;letter-spacing:1.6px;
     padding-top:1px;opacity:.55;max-width:78px;overflow:hidden;
     white-space:nowrap;text-overflow:ellipsis}
.today .sub,.today .state,.today .unit,.today .clock{opacity:.62}
.unitslot{margin-left:3px;width:%(UNIT_W)spx}
.today .cat{opacity:.55}
.pink{background:#fdeef4}
.blue .num,.blue .unit{color:#1b6be0}
""" % dict(W=W, PAD_H=PAD_H, PAD_V=PAD_V, BAR=BAR, BAR_GAP=BAR_GAP,
           NUM=NUM, NUM_BOX=NUM_BOX, UNIT_RAISE=UNIT_RAISE,
           CLOCK=round(CLOCK, 1), CLOCK_BOX=round(CLOCK_BOX, 2),
           CLOCK_LIFT=round(CLOCK_LIFT, 2), UNIT_W=round(UNIT_W, 2))


def row(title, state, sub, num, unit, clock, cat, cls="", num_cls=""):
    # 「天」那一格**写不写都要占住**（0.58.2）：不占的话「今天」那一行的数字
    # 会比别的行右移 16.60dp，而那一行恰好是唯一的反色块。
    unit_html = (f'<div class="unit">{unit}</div>' if unit
                 else '<div class="unitslot"></div>')
    clock_html = f'<div class="clock">{clock}</div>' if clock else ""
    sub_html = f'<div class="sub">{sub}</div>' if sub else ""
    # 「今天」那一行不写状态词（`if (!inverted)`），连那个空格也不要
    state_html = (f'<span class="gap"></span><span class="state">{state}</span>'
                  if state else "")
    return f"""
    <div class="row {cls}">
      <div class="bar"></div>
      <div class="left">
        <div class="title"><span class="txt">{title}</span>{state_html}</div>
        {sub_html}
      </div>
      <div class="right {num_cls}">
        <div class="numline"><div class="num">{num}</div>{unit_html}</div>
        {clock_html}
        <div class="cat">{cat}</div>
      </div>
    </div>"""


AFTER = "".join([
    row("1", "还有", "2027年3月28日", "148", "天", "", "生活"),
    row("B", "已经", "走到 2026年11月14日", "29607", "天", "17:43:31", "生活", "blue"),
    row("456", "已经", "2026年10月15日", "16", "天", "17:43:30", "生活",
        "pink blue"),
    row("1", "", "就是今天", "0", "", "", "生活", "today"),
])

HTML = f"""<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<title>如期 0.58.0 · 列表行对照</title>
<style>{faces}{CSS}</style></head>
<body>
  <div class="pane">
    <h2><b>改前</b><span>录屏实拍 · 720px @2x</span></h2>
    <div class="screen">
      <img class="shot" src="{img(SHOT / 'before_two.png')}" alt="before">
    </div>
    <div class="note">
      第三行左列那串 <b>−16 天 17:43:30</b> 和右边大数字说的是同一件事；
      第四行的大数字本身就是 <b>负数</b>。
    </div>
  </div>

  <div class="pane">
    <h2><b>改后</b><span>按代码算的示意 · 不是截图</span></h2>
    <div class="screen">{AFTER}</div>
    <div class="note">
      左列只剩「标题 / 日期」，贴顶；右列「天数 / 秒表 / 分类」贴底，右边缘一条线。
      秒表只出 <b>17:43:30</b>，天数不再出现第二次；<b>−16</b> 折成计时 <b>16 天</b>。
      「已经 / 还有」和标题同字体同字号，中间只剩<b>一个空格</b>（4.1dp）。
      <br>0.58.2：第四行「0」不再贴着右边——「天」那一格<b>空着也占位</b>
      （16.6dp），四行数字落在同一条竖线上；秒表封顶 <b>14sp</b>，不再追平标题。
    </div>
  </div>
</body></html>
"""

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(HTML, encoding="utf-8")
print("写出", OUT, f"({len(HTML) // 1024} KB)")
