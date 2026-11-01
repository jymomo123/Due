import re
import pathlib
import sys

ROOT = pathlib.Path(__file__).parent / "app/src/main/java"
PKG = "com.offline.due"

STDLIB = {
    "entries", "valueOf", "values", "name", "ordinal", "copy", "component1",
    "hashCode", "equals", "toString", "forEach", "forEachIndexed", "map", "filter",
    "size", "isEmpty", "isNotEmpty", "isBlank", "isNotBlank", "length", "first",
    "last", "getOrNull", "contains", "let", "run", "apply", "also", "takeIf",
    "sortedBy", "sortedWith", "joinToString", "trim", "plus", "minus", "count",
    "indexOf", "get", "indices", "label", "days", "absDays", "progress",
    "isToday", "isFuture", "target", "cycleStart", "today",
}

files = sorted(ROOT.rglob("*.kt"))
if not files:
    print("没找到 kotlin 源文件")
    sys.exit(1)


def balanced(src, open_idx):
    pairs = {"(": ")", "{": "}", "[": "]", "<": ">"}
    o = src[open_idx]
    c = pairs[o]
    depth, i, buf = 0, open_idx, ""
    while i < len(src):
        ch = src[i]
        if ch == o:
            depth += 1
            if depth == 1:
                i += 1
                continue
        elif ch == c:
            depth -= 1
            if depth == 0:
                return buf, i
        buf += ch
        i += 1
    return buf, len(src)


def split_args(buf):
    out, depth, cur, prev = [], 0, "", ""
    for ch in buf:
        if ch == "<":
            depth += 1
        elif ch == ">" and prev != "-":
            depth -= 1
        elif ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(cur)
            cur = ""
        else:
            cur += ch
        prev = ch
    if cur.strip():
        out.append(cur)
    return [a.strip() for a in out if a.strip()]


def strip_noise(src):
    out, i, n = [], 0, len(src)
    while i < n:
        if src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j == -1 else j
        elif src.startswith("/*", i):
            j = src.find("*/", i)
            end = n if j == -1 else j + 2
            out.append("\n" * src.count("\n", i, end))
            i = end
        elif src[i] == '"' and src.startswith('"""', i):
            j = src.find('"""', i + 3)
            end = n if j == -1 else j + 3
            out.append('""' + "\n" * src.count("\n", i, end))
            i = end
        elif src[i] == '"':
            i += 1
            while i < n and src[i] != '"':
                i += 2 if src[i] == "\\" else 1
            i += 1
            out.append('""')
        elif src[i] == "'":
            i += 1
            while i < n and src[i] != "'":
                i += 2 if src[i] == "\\" else 1
            i += 1
            out.append("''")
        else:
            out.append(src[i])
            i += 1
    return "".join(out)


_SRC_CACHE = {}


def source_of(path):
    s = _SRC_CACHE.get(path)
    if s is None:
        s = strip_noise(pathlib.Path(path).read_text(encoding="utf-8"))
        _SRC_CACHE[path] = s
    return s


IDENT = r"([A-Za-z_]\w*)"

types = {}
funcs = {}

for f in files:
    src = source_of(f)
    pkg = (re.search(r"^package\s+([\w.]+)", src, re.M) or [None, ""])[1]
    rel = str(f.relative_to(ROOT)).replace("\\", "/")

    for m in re.finditer(
        r"\bfun\s+(?:<[^>]*>\s*)?(?:[A-Za-z_][\w.<>?]*\.)?" + IDENT + r"\s*\(", src
    ):
        name = m.group(1)
        body, _ = balanced(src, m.end() - 1)
        params, named = [], []
        for p in split_args(body):
            p = re.sub(r"@\w+", "", p).strip()
            if not p:
                continue
            head = p.split(":")[0].strip() if ":" in p else ""
            head = head.split()[-1] if head else ""
            if head and re.fullmatch(r"[A-Za-z_]\w*", head):
                params.append(head)
                if "=" in p:
                    named.append(head)
        funcs[f"{pkg}.{name}"] = {"file": rel, "params": params, "optional": named}

    for m in re.finditer(r"^(?:private\s+|internal\s+)?(?:const\s+)?(val|var)\s+" + IDENT, src, re.M):
        fq = f"{pkg}.{m.group(2)}"
        info = types.setdefault(fq, {"file": rel, "kind": "topval", "members": set(),
                                     "ctor": [], "is_enum": False})
        end = src.find("\n", m.end())
        line = src[m.start(): end if end != -1 else len(src)]
        if "ompositionLocalOf" in line:
            info["members"].update({"current", "provides"})

    for m in re.finditer(r"\b(object|class|interface)\s+" + IDENT, src):
        fq = f"{pkg}.{m.group(2)}"
        types.setdefault(fq, {"file": rel, "kind": m.group(1), "members": set(),
                              "ctor": [], "is_enum": False})
        if m.group(1) == "class" and src[m.end():m.end() + 1] == "(":
            body, _ = balanced(src, m.end())
            for p in split_args(body):
                if ":" in p:
                    h = p.split(":")[0].strip().split()[-1]
                    if re.fullmatch(r"[A-Za-z_]\w*", h):
                        types[fq]["ctor"].append(h)

    for m in re.finditer(r"\benum\s+class\s+" + IDENT, src):
        fq = f"{pkg}.{m.group(1)}"
        types.setdefault(fq, {"file": rel, "kind": "enum", "members": set(),
                              "ctor": [], "is_enum": True})
        types[fq]["is_enum"] = True
        body, _ = balanced(src, src.index("{", m.end()))
        for c in re.finditer(r"(?:^|\n)\s{2,}([A-Z][A-Z0-9_]*)\s*[,;(]", body):
            types[fq]["members"].add(c.group(1))

for f in files:
    src = source_of(f)
    pkg = (re.search(r"^package\s+([\w.]+)", src, re.M) or [None, ""])[1]

    for m in re.finditer(r"^(?:private\s+|internal\s+)?(?:const\s+)?(val|var)\s+" + IDENT, src, re.M):
        types.setdefault(f"{pkg}.__top__", {"file": "", "kind": "top", "members": set(),
                                            "ctor": [], "is_enum": False})
        types[f"{pkg}.__top__"]["members"].add(m.group(2))

    spans = []
    for tm in re.finditer(r"\b(?:object|class|interface)\s+" + IDENT, src):
        j = tm.end()
        if j < len(src) and src[j] == "(":
            _, j = balanced(src, j)
            j += 1
        brace = src.find("{", j)
        if brace == -1:
            continue
        between = src[j:brace]
        if not re.fullmatch(r"\s*(:\s*[\w.<>(), ]*)?\s*", between):
            continue
        _, close = balanced(src, brace)
        spans.append((brace, close, f"{pkg}.{tm.group(1)}"))

    for m in re.finditer(r"^[ \t]{4,}(?:@\w+\s+)*(?:private\s+|internal\s+|public\s+|override\s+|"
                         r"suspend\s+|inline\s+|operator\s+|abstract\s+)*"
                         r"(?:const\s+)?(val|var|fun)\s*(?:<[^>]{1,80}>\s*)?" + IDENT, src, re.M):
        pos = m.start()
        best = None
        for b, c, fq in spans:
            if b < pos < c:
                if best is None or (c - b) < (best[1] - best[0]):
                    best = (b, c, fq)
        if best and best[2] in types:
            types[best[2]]["members"].add(m.group(2))

    for b, c, fq in spans:
        if fq.rsplit(".", 1)[-1] != "Companion" or fq not in types:
            continue
        outer = None
        for b2, c2, fq2 in spans:
            if b2 < b and c2 > c and fq2 != fq:
                if outer is None or (c2 - b2) < (outer[1] - outer[0]):
                    outer = (b2, c2, fq2)
        if outer and outer[2] in types:
            types[outer[2]]["members"] |= types[fq]["members"]

for fq, info in types.items():
    info["members"].update(info.get("ctor", []))

problems = []

IMPORT_RE = re.compile(r"^import\s+(" + re.escape(PKG) + r"\.[\w.]+)", re.M)
for f in files:
    src = source_of(f)
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    for m in IMPORT_RE.finditer(src):
        fq = m.group(1)
        if fq in types or fq in funcs:
            continue
        parts = fq.split(".")
        if ".".join(parts[:-1]) in types or fq in types:
            continue
        if parts[-1] in ("R", "BuildConfig") or "R." in fq:
            continue
        problems.append(f"[import] {rel}: {fq} —— 目标不存在")

short = {}
for k in list(types) + list(funcs):
    short.setdefault(k.split(".")[-1], []).append(k)

for f in files:
    src = source_of(f)
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    for m in re.finditer(r"\b([A-Z][A-Za-z0-9_]*)\.([a-z]\w*)", src):
        cls, mem = m.group(1), m.group(2)
        if cls.isupper():
            continue
        if mem in STDLIB or cls in ("LocalDate", "YearMonth", "ChronoUnit", "Regex",
                                    "MutableStateFlow", "StateFlow", "Intent", "Context",
                                    "Log", "Bundle", "TextUtils", "Build", "Toast"):
            continue
        cands = short.get(cls, [])
        if not cands:
            continue
        cands = [
            c for c in cands
            if not (types.get(c, {}).get("kind") == "topval"
                    and not types[c]["members"])
        ]
        if not cands:
            continue
        if any(mem in types[c]["members"] for c in cands if c in types):
            continue
        if any(c in funcs for c in cands):
            continue
        problems.append(f"[member] {rel}: {cls}.{mem} —— {'/'.join(cands)} 无此成员")

for f in files:
    src = source_of(f)
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    for name, info in funcs.items():
        short_name = name.split(".")[-1]
        if short_name in ("copy", "get", "set"):
            continue
        for m in re.finditer(r"(?<![\w.])" + re.escape(short_name) + r"\s*\(", src):
            line_start = src.rfind("\n", 0, m.start()) + 1
            line = src[line_start:src.find("\n", m.start())]
            if re.search(r"\bfun\s+" + re.escape(short_name), line):
                continue
            body, _ = balanced(src, m.end() - 1)
            for a in split_args(body):
                am = re.match(r"^(\w+)\s*=(?!=)", a)
                if not am:
                    continue
                arg = am.group(1)
                if arg not in info["params"]:
                    problems.append(
                        f"[named] {rel}: {short_name}({arg} = ...) —— "
                        f"{name} 的形参是 {info['params']}")

seen, out = set(), []
for p in problems:
    if p not in seen:
        seen.add(p)
        out.append(p)

RES = pathlib.Path(__file__).parent / "app/src/main/res"
KINDS = ("id", "drawable", "layout", "string", "xml", "color", "mipmap", "style", "dimen")
BITMAP_EXT = (".png", ".jpg", ".jpeg", ".webp")


def collect_resources():
    defined = {k: set() for k in KINDS}
    for f in RES.rglob("*.xml"):
        text = f.read_text(encoding="utf-8")
        for m in re.finditer(r"@\+id/(\w+)", text):
            defined["id"].add(m.group(1))
        folder = f.parent.name.split("-")[0]
        if folder in defined:
            defined[folder].add(f.stem)
        for m in re.finditer(r'<(string|color|style|dimen)\s+name="([\w.]+)"', text):
            defined[m.group(1)].add(m.group(2))
    for f in RES.rglob("*"):
        if f.is_dir() or f.suffix.lower() not in BITMAP_EXT:
            continue
        folder = f.parent.name.split("-")[0]
        if folder not in defined:
            continue
        stem = f.name[: -len(f.suffix)]
        if stem.endswith(".9"):
            stem = stem[:-2]
        defined[folder].add(stem)
    return defined


resources = collect_resources()

for f in files:
    src = source_of(f)
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    for m in re.finditer(r"\bR\.(\w+)\.(\w+)", src):
        kind, name = m.group(1), m.group(2)
        if kind in resources and name not in resources[kind]:
            problems.append(f"[res] {rel}: R.{kind}.{name} —— 资源不存在")

for f in sorted(RES.rglob("*.xml")):
    text = f.read_text(encoding="utf-8")
    rel = str(f.relative_to(RES.parent)).replace("\\", "/")
    for m in re.finditer(r"(?<![+\w])@(drawable|layout|string|xml|color|mipmap|id|style|dimen)/([\w.]+)", text):
        kind, name = m.group(1), m.group(2)
        if name not in resources[kind]:
            problems.append(f"[res] {rel}: @{kind}/{name} —— 资源不存在")

UI = ROOT / PKG.replace(".", "/") / "ui"
for f in sorted(UI.glob("*Screen.kt")):
    src = source_of(f)
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    if not re.search(r"\b(event|original|item)\s*:\s*CountdownEvent\b", src):
        continue
    if re.search(r"\.title\b", src):
        continue
    if "EventCard(" in src:
        continue
    problems.append(
        f"[ui] {rel}: 展示单条事件但通篇没有 .title —— 用户认不出这是哪件事"
    )

for f in sorted(ROOT.rglob("*.kt")):
    src = source_of(f)
    if "fun GlassDialog(" in src:
        continue
    if not re.search(r"(?<![\w.])Dialog\(", src):
        continue
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    problems.append(
        f"[ui] {rel}: 直接用了裸 Dialog —— 没有进出动画，应走 GlassDialog"
    )

for f in sorted(ROOT.rglob("*.kt")):
    src = source_of(f)
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    for m in re.finditer(r"\.navigationBarsPadding\(\)", src):
        if ".imePadding()" in src[m.end(): m.end() + 300]:
            problems.append(
                f"[inset] {rel}: navigationBarsPadding + imePadding 会相加，"
                f"改用 navigationBars.union(ime)"
            )
            break

for f in sorted(ROOT.rglob("*.kt")):
    src = source_of(f)
    if "DatePickerDialog(" in src:
        rel = str(f.relative_to(ROOT)).replace("\\", "/")
        problems.append(
            f"[ui] {rel}: 用了 M3 的 DatePickerDialog —— 不透明且无进出动画，"
            f"应把 DatePicker 放进 GlassDialog"
        )

for f in sorted(ROOT.rglob("*.kt")):
    src = source_of(f)
    if "widget/" in str(f).replace("\\", "/"):
        continue
    if not re.search(r"CountdownMath\.(headline|precise|cardNumber)\(", src):
        continue
    if "RollingText(" in src:
        continue
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    problems.append(
        f"[ui] {rel}: 天数/精确读数用了裸 Text —— 数字变化不会有滚动动画，应走 RollingText"
    )

TOOLBAR_PAD = (
    r"Row\(\s*modifier\s*=\s*Modifier\s*\.fillMaxWidth\(\)\s*"
    r"\.padding\(\s*start\s*=\s*Gutter,\s*end\s*=\s*Gutter,\s*top\s*=\s*10\.dp"
)


def call_blocks(src: str, marker: str, regex: bool = False) -> list[str]:
    out = []
    start = 0
    pattern = re.compile(marker) if regex else None
    while True:
        if regex:
            found = pattern.search(src, start)
            if not found:
                return out
            i = found.start()
            j = i + found.group(0).rfind("(")
        else:
            i = src.find(marker, start)
            if i < 0:
                return out
            j = i + len(marker) - 1
        depth, k = 0, j
        while k < len(src):
            c = src[k]
            if c in "([{":
                depth += 1
            elif c in ")]}":
                depth -= 1
                if depth == 0:
                    break
            k += 1
        end = k + 1
        m = end
        while m < len(src) and src[m] in " \t\r\n":
            m += 1
        if m < len(src) and src[m] == "{":
            depth, k = 0, m
            while k < len(src):
                c = src[k]
                if c == "{":
                    depth += 1
                elif c == "}":
                    depth -= 1
                    if depth == 0:
                        break
                k += 1
            end = k + 1
        out.append(src[j:end])
        start = end


for f in sorted(ROOT.rglob("*.kt")):
    src = source_of(f)
    if "fun KeyBar(" in src:
        continue
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    for block in call_blocks(src, "KeyBar("):
        if "TextAction(" in block:
            problems.append(
                f"[ui] {rel}: KeyBar 里放了裸 TextAction —— 顶栏的键要用 KeyCap，"
                f"每个键一块独立玻璃，否则看不出可点边界"
            )
    if re.search(TOOLBAR_PAD, src):
        problems.append(
            f"[ui] {rel}: 手写了顶栏 —— 应改用 KeyBar + KeyCap，"
            f"否则圆角/厚度/阴影会和别处慢慢分叉"
        )

for f in sorted(ROOT.rglob("*.kt")):
    src = source_of(f)
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    for block in call_blocks(src, r"(?<![A-Za-z])Text\(", regex=True):
        if "fontSize =" not in block:
            continue
        if "displaySp(" not in block and "numSp" not in block:
            continue
        if "lineHeight" in block:
            continue
        problems.append(
            f"[ui] {rel}: Text 覆盖了 fontSize 却没给 lineHeight —— "
            f"字形会被样式里的行高裁掉，数字会缺头尾、动画看着像静止"
        )

CONTRAST_MIN = 4.5


def _lin(c: float) -> float:
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def _luminance(rgb: int) -> float:
    return (
        0.2126 * _lin(((rgb >> 16) & 0xFF) / 255)
        + 0.7152 * _lin(((rgb >> 8) & 0xFF) / 255)
        + 0.0722 * _lin((rgb & 0xFF) / 255)
    )


def _contrast(a: int, b: int) -> float:
    la, lb = _luminance(a), _luminance(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


theme_kt = ROOT / "com/offline/due/ui/theme/Theme.kt"
if theme_kt.exists():
    tsrc = source_of(theme_kt)
    for variant in ("LightGlass", "DarkGlass", "BlossomLight", "BlossomDark"):
        block = re.search(
            rf"private val {variant} = GlassTokens\((.*?)\n\)", tsrc, re.S
        )
        if not block:
            continue
        body = block.group(1)
        grad_m = re.search(r"gradient = listOf\(([^\n]*)\)", body)
        if not grad_m:
            continue
        backgrounds = [int(x, 16) for x in re.findall(r"0xFF([0-9A-Fa-f]{6})", grad_m.group(1))]
        for role in ("ink", "inkMid", "inkSoft"):
            m = re.search(rf"\b{role} = Color\(0xFF([0-9A-Fa-f]{{6}})\)", body)
            if not m:
                continue
            fg = int(m.group(1), 16)
            worst = min(_contrast(fg, bg) for bg in backgrounds)
            if worst < CONTRAST_MIN:
                problems.append(
                    f"[theme] Theme.kt {variant}.{role} (#{m.group(1)}) 在背景上只有 "
                    f"{worst:.2f}:1，低于小字要求的 {CONTRAST_MIN}:1 —— 真机上读不出来"
                )

TOKEN_SIDES = ("horizontal", "start", "end")
TOKEN_VALUES = (16, 20, 24)
for f in sorted(ROOT.rglob("*.kt")):
    src = source_of(f)
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    if f.name == "Bits.kt":
        src = re.sub(
            r"^val (Gutter|RowInset|CardInset) = \d+\.dp$", "", src, flags=re.M
        )
    for side in TOKEN_SIDES:
        for val in TOKEN_VALUES:
            for _ in re.finditer(rf"\b{side}\s*=\s*{val}\.dp\b", src):
                problems.append(
                    f"[ui] {rel}: {side} = {val}.dp 是裸写的留白 —— "
                    f"三档令牌里已经有它，改用 CardInset / Gutter / RowInset"
                )

MOTION_WHITELIST = {
    "com/offline/due/ui/components/Aurora.kt": {1600},
}
for f in sorted(ROOT.rglob("*.kt")):
    src = source_of(f)
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    allowed = MOTION_WHITELIST.get(rel, set())
    for m in re.finditer(r"\btween\s*(?:<[^>]*>)?\s*\(\s*(\d+)\s*[,)]", src):
        ms = int(m.group(1))
        if ms in allowed:
            continue
        problems.append(
            f"[motion] {rel}: tween({ms}) 是裸写的时长 —— "
            f"改用 Motion.Press / Exit / Enter / Page，同组动画才会同步"
        )

CLOCK_MAX_LINES = 30
for f in sorted(ROOT.rglob("*.kt")):
    src = source_of(f)
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    if "LocalNow.current" not in src:
        continue
    lines = src.splitlines()
    for m in re.finditer(r"LocalNow\.current", src):
        decl = None
        for k in range(src.count("\n", 0, m.start()), -1, -1):
            if re.match(r"^(private\s+)?fun\s+(\w+)", lines[k]):
                decl = k
                break
        if decl is None:
            problems.append(
                f"[clock] {rel}: LocalNow.current 不在任何顶层函数里 —— "
                f"读秒级时钟必须在 composable 里，不能写在对象初始化或常量里"
            )
            continue
        name = re.match(r"^(private\s+)?fun\s+(\w+)", lines[decl])
        is_private = name.group(1) is not None
        fname = name.group(2)
        depth, started, end = 0, False, decl
        for j in range(decl, len(lines)):
            for ch in lines[j]:
                if ch == "{":
                    depth += 1
                    started = True
                elif ch == "}":
                    depth -= 1
            if started and depth <= 0:
                end = j
                break
        body_lines = end - decl + 1
        if not is_private:
            problems.append(
                f"[clock] {rel}: fun {fname} 读了 LocalNow.current 却不是 private —— "
                f"秒级时钟只能在「只做这一件事」的小 composable 里读，"
                f"否则整个大组件每秒重组一次"
            )
        elif body_lines > CLOCK_MAX_LINES:
            problems.append(
                f"[clock] {rel}: fun {fname} 读了 LocalNow.current，"
                f"函数体却有 {body_lines} 行（上限 {CLOCK_MAX_LINES}）—— "
                f"它还在干别的活，把读时钟的那一小段单独拆出去"
            )

PRESS_SILENT_LOW = 0.99
for f in sorted(ROOT.rglob("*.kt")):
    src = source_of(f)
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    for block in call_blocks(src, r"\bpressable\s*\(", regex=True):
        m = re.search(r"\bscale\s*=\s*([0-9.]+)f", block)
        if not m:
            continue
        try:
            val = float(m.group(1))
        except ValueError:
            continue
        if PRESS_SILENT_LOW < val < 1.0:
            problems.append(
                f"[press] {rel}: pressable(scale = {m.group(1)}f) 小到肉眼看不见 —— "
                f"整行用 scale = 1f 靠按压底纹反馈，小控件用 0.96 以下"
            )

TOKEN_FILES = ("ui/theme/Shape.kt", "ui/theme/Motion.kt")
BARE_RADIUS = re.compile(r"RoundedCornerShape\(\s*\d+(?:\.\d+)?\.dp\s*\)")
BARE_SPRING = re.compile(r"\bspring\s*(?:<[^>]*>)?\(\s*dampingRatio")
for f in sorted(ROOT.rglob("*.kt")):
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    if rel.endswith(TOKEN_FILES):
        continue
    src = source_of(f)
    for m in BARE_RADIUS.finditer(src):
        problems.append(
            f"[token] {rel}: {m.group(0)} —— 圆角要走 Radius.card/panel/chip/dot/hair"
        )
    if BARE_SPRING.search(src):
        problems.append(
            f"[token] {rel}: 裸写 spring(dampingRatio = …) —— 走 Motion.press/settle/glide"
        )

NEUTRAL_LITERALS = {"FFFFFFFF", "FF000000", "00000000"}
HUE_FILES = ("ui/components/ColorCanvas.kt",)
COLOR_DEF_FILES = ("ui/theme/Theme.kt",)
COLOR_DIRS = ("ui/components/", "ui/theme/")
COLOR_LITERAL = re.compile(r"Color\(\s*0x([0-9A-Fa-f]{8})\s*\)")
for f in sorted(ROOT.rglob("*.kt")):
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    rel_full = "/" + rel
    if not any(d in rel_full for d in COLOR_DIRS) \
            or rel.endswith(HUE_FILES) or rel.endswith(COLOR_DEF_FILES):
        continue
    src = source_of(f)
    for m in COLOR_LITERAL.finditer(src):
        hexval = m.group(1).upper()
        if hexval in NEUTRAL_LITERALS:
            continue
        ln = src.count("\n", 0, m.start()) + 1
        problems.append(
            f"[tone] {rel}:{ln} —— 写死色值 #{hexval[2:]}，"
            f"换色相时它不会跟着变；走 GlassTokens"
        )

LINE_HEIGHT_UNSPEC = re.compile(r"lineHeight\s*=\s*TextUnit\.Unspecified")
for f in sorted(ROOT.rglob("*.kt")):
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    src = source_of(f)
    if "TextUnit.Unspecified" not in src:
        continue
    copy_spans = []
    for m in re.finditer(r"\.copy\s*\(", src):
        open_at = src.index("(", m.start())
        depth = 0
        for i in range(open_at, len(src)):
            c = src[i]
            if c == "(":
                depth += 1
            elif c == ")":
                depth -= 1
                if depth == 0:
                    copy_spans.append((open_at, i))
                    break
    for m in LINE_HEIGHT_UNSPEC.finditer(src):
        pos = m.start()
        if any(a <= pos <= b for a, b in copy_spans):
            continue
        ln = src.count("\n", 0, pos) + 1
        problems.append(
            f"[lineh] {rel}:{ln} —— `Text(lineHeight = Unspecified)` 不生效，"
            f"merge 会保留样式里的行高；改成 style.copy(lineHeight = ...)"
        )

TYPO_SLOTS = (
    "displayLarge", "displayMedium", "displaySmall",
    "headlineLarge", "headlineMedium", "headlineSmall",
    "titleLarge", "titleMedium", "titleSmall",
    "bodyLarge", "bodyMedium", "bodySmall",
    "labelLarge", "labelMedium", "labelSmall",
)
for f in sorted(ROOT.rglob("*.kt")):
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    src = source_of(f)
    if "Typography(" not in src:
        continue
    for m in re.finditer(r"(?<![\w.])Typography\s*\(", src):
        body, _ = balanced(src, src.index("(", m.start()))
        given = set(re.findall(r"(\w+)\s*=(?!=)", body))
        missing = [s for s in TYPO_SLOTS if s not in given]
        if missing:
            ln = src.count("\n", 0, m.start()) + 1
            problems.append(
                f"[typo] {rel}:{ln} —— Typography(...) 漏了 {', '.join(missing)}，"
                f"那几档会静默落回 M3 默认字体"
            )

SETTINGS_DECL = re.compile(r"data class Settings\s*\(")
for f in sorted(ROOT.rglob("SettingsStore.kt")):
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    src = source_of(f)
    decl = SETTINGS_DECL.search(src)
    if not decl:
        continue
    decl_open = src.index("(", decl.start())
    decl_body, _ = balanced(src, decl_open)
    declared = []
    for arg in split_args(decl_body):
        am = re.match(r"^(?:val\s+|var\s+)?(\w+)\s*:", arg.strip())
        if am:
            declared.append(am.group(1))
    if not declared:
        problems.append(f"[persist] {rel} —— 没解析出 Settings 的字段，这条规则没生效")
        continue

    for ctor in re.finditer(r"(?<![\w.])Settings\s*\(", src):
        open_at = src.index("(", ctor.start())
        if open_at == decl_open:
            continue
        body, _ = balanced(src, open_at)
        given = set(re.findall(r"(\w+)\s*=(?!=)", body))
        if len(given) < 5:
            continue
        missing = [p for p in declared if p not in given]
        if missing:
            ln = src.count("\n", 0, open_at) + 1
            problems.append(
                f"[persist] {rel}:{ln} —— Settings 有字段没在读盘时赋值："
                f"{', '.join(missing)}。改了设置下次启动会悄悄回到默认值"
            )


BLACK_FILE = "com/offline/due/ui/theme/Theme.kt"
for f in files:
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    src = source_of(f)
    hits = list(re.finditer(r"fontWeight\s*=\s*FontWeight\.(?:Black|W900)", src))
    if not hits:
        continue
    if rel != BLACK_FILE:
        for m in hits:
            ln = src.count("\n", 0, m.start()) + 1
            problems.append(
                f"[black] {rel}:{ln} —— 900 只压了 34 个字形，这里会静默回退到系统字体。"
                f"改用 SemiBold / Bold，或把字加进 build_fonts.py 的 DISPLAY_CHARS"
            )
        continue
    styles = [(m.start(), m.group(1)) for m in
              re.finditer(r"(\w+)\s*=\s*TextStyle\(", src)]
    for m in hits:
        owner = None
        for at, name in styles:
            if at < m.start():
                owner = name
            else:
                break
        if owner is None or not owner.startswith("display"):
            ln = src.count("\n", 0, m.start()) + 1
            problems.append(
                f"[black] {rel}:{ln} —— 900 只能用在 display* 样式里（现在在 {owner} 里）"
            )

for f in files:
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    src = source_of(f)
    for m in re.finditer(r"\bclose\s*\{", src):
        body, _end = balanced(src, m.end() - 1)
        if "onDismiss" in body:
            continue
        ln = src.count("\n", 0, m.start()) + 1
        problems.append(
            f"[dialog] {rel}:{ln} —— `close {{ … }}` 里没有 onDismiss。"
            f"退场动画结束后 Dialog 不会自己消失，会变成一个透明但仍吃触摸的全屏窗口"
        )


THICKNESS = ("surfaceTop", "surfaceBottom", "frostTop", "frostBottom")
for f in files:
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    src = source_of(f)
    for m in re.finditer(
        r"\.(" + "|".join(THICKNESS) + r")\s*\.copy\(\s*alpha\s*=\s*([^)]*)", src
    ):
        token, expr = m.group(1), m.group(2)
        if ".alpha" in expr:
            continue
        if not re.match(r"^[\d.]+f?$", expr.strip()):
            continue
        ln = src.count("\n", 0, m.start()) + 1
        problems.append(
            f"[thickness] {rel}:{ln} —— `{token}.copy(alpha = {expr.strip()})` 覆盖了令牌的"
            f"「厚度」。深色下 {token} 是低 alpha 的白，覆盖成高 alpha 会得到一片纯白，"
            f"压在上面的 ink 字就看不见了；要更厚就改 weight 或乘 .alpha"
        )


def _call_blocks(text, name):
    found = []
    for m in re.finditer(re.escape(name) + r"\(", text):
        i, depth = m.end(), 1
        while i < len(text) and depth:
            if text[i] == "(":
                depth += 1
            elif text[i] == ")":
                depth -= 1
            i += 1
        found.append((m.start(), text[m.end():i - 1]))
    return found


for f in sorted(ROOT.rglob("*.kt")):
    src = source_of(f)
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    for start, block in _call_blocks(src, "listOf"):
        lit = re.search(r"Color\.(White|Black)\b", block)
        if not lit:
            continue
        if not re.search(r"alpha\s*=\s*0(?:\.0+)?f?\s*[,)]", block):
            continue
        ln = src.count("\n", 0, start) + 1
        problems.append(
            f"[fade] {rel}:{ln} —— 用 `Color.{lit.group(1)}` 做渐隐。绝对色只有在"
            f"被盖的那一层正好接近白/黑时才是对的（深色面板上会是一条白横带）。"
            f"目标色要取「被盖那一层看起来的颜色」：玻璃面板是半透明白压在页面底色上，"
            f"即 `lerp(页面底色, surfaceBottom, 填充alpha)`"
        )


manifest = ROOT.parents[2] / "src/main/AndroidManifest.xml"
if not manifest.exists():
    problems.append(f"[back] 找不到清单：{manifest}（路径写错了，这一遍等于没跑）")
elif 'android:enableOnBackInvokedCallback="true"' not in manifest.read_text(encoding="utf-8"):
        problems.append(
            "[back] AndroidManifest.xml —— 缺 `android:enableOnBackInvokedCallback=\"true\"`。"
            "没有它 `PredictiveBackHandler` 永远收不到进度，侧滑返回全程没有反馈，"
            "而且代码看起来是对的"
        )

for f in sorted(ROOT.rglob("*.kt")):
    src = source_of(f)
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    for start, block in _call_blocks(src, "PredictiveBack"):
        m = re.search(r"onProgress\s*=\s*\{", block)
        if not m:
            continue
        i = m.end() - 1
        depth = 0
        end = i
        while end < len(block):
            if block[end] == "{":
                depth += 1
            elif block[end] == "}":
                depth -= 1
                if depth == 0:
                    break
            end += 1
        body = block[i:end]
        if "launch" in body:
            ln = src.count("\n", 0, start) + 1
            problems.append(
                f"[back] {rel}:{ln} —— `onProgress` 里 launch 协程。"
                f"手势进度一秒六十次，每次都排一个协程去改值，画面会越划越粘。"
                f"这里只该同步写一个 `mutableFloatStateOf`，要补间放到 onCancel / onBack 里"
            )


for f in sorted(ROOT.rglob("*.kt")):
    src = source_of(f)
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    for start, block in _call_blocks(src, "AnimatedContent"):
        if "slideInHorizontally" not in block and "slideOutHorizontally" not in block:
            continue
        if "clipToBounds" in block:
            continue
        ln = src.count("\n", 0, start) + 1
        problems.append(
            f"[clip] {rel}:{ln} —— 横向滑动的 `AnimatedContent` 没有 `clipToBounds()`。"
            f"它不裁自己的内容，退场页会从内容区左右那道 20dp 留白里露出来一截。"
            f"纵向位移可以不裁（滑出屏幕就看不见），横向必须裁"
        )


PER_FRAME_VARS = {"backLiftP", "cardDragP", "previewA"}


DRAW_PHASE_BLOCKS = (
    r"graphicsLayer\s*(?:\([^()]*\))?\s*\{",
    r"drawWithContent\s*\{",
    r"drawBehind\s*\{",
    r"drawWithCache\s*\{",
)


def _inside_draw_phase(src, pos):
    for pat in DRAW_PHASE_BLOCKS:
        for m in re.finditer(pat, src):
            ob = m.end() - 1
            _, end = balanced(src, ob)
            if ob <= pos <= end:
                return True
    return False


for f in sorted(ROOT.rglob("*.kt")):
    src = source_of(f)
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    for name in PER_FRAME_VARS:
        for m in re.finditer(rf"\b{name}\b", src):
            pos = m.start()
            ls = src.rfind("\n", 0, pos) + 1
            le = src.find("\n", pos)
            line = src[ls: le if le != -1 else len(src)]
            if f"var {name} by" in line:
                continue
            if re.search(rf"\b{name}\s*=[^=]", line):
                continue
            if re.search(rf"initialValue\s*=\s*{name}\b", line):
                continue
            if _inside_draw_phase(src, pos):
                continue
            ln = src.count("\n", 0, pos) + 1
            problems.append(
                f"[gesture] {rel}:{ln} —— `{name}` 是手势进度，一秒变六十次，"
                f"这里却在**组合期**读它（绘制阶段的 lambda 里才合法）。读它的 composable 会每帧重组"
                f"（通常就是 `AppRoot`，整棵树）。写进 `graphicsLayer {{ … }}` / "
                f"`drawWithContent {{ … }}` 的 lambda，那是绘制阶段才读"
            )


def _strip_comments(src):
    out, i, n = [], 0, len(src)
    while i < n:
        if src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j == -1 else j
        elif src.startswith("/*", i):
            j = src.find("*/", i)
            end = n if j == -1 else j + 2
            out.append("\n" * src.count("\n", i, end))
            i = end
        else:
            out.append(src[i])
            i += 1
    return "".join(out)


def _when_keys(src, marker):
    i = src.find(marker)
    if i == -1:
        return None
    ob = src.find("{", src.find("when", i))
    if ob == -1:
        return None
    body, _ = balanced(src, ob)
    keys = set()
    for m in re.finditer(r'((?:"[A-Za-z_]+"\s*,\s*)*"[A-Za-z_]+")\s*->', body):
        keys.update(re.findall(r'"([A-Za-z_]+)"', m.group(1)))
    return keys


for f in sorted(ROOT.rglob("*.kt")):
    src = _strip_comments(f.read_text(encoding="utf-8"))
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    depth = _when_keys(src, "fun routeDepth(")
    back = _when_keys(src, "fun backTarget(")
    if depth is None or back is None:
        continue
    for k in sorted(depth - back):
        problems.append(
            f"[route] {rel} —— 路由 `{k}` 在 `routeDepth` 里有、`backTarget` 里没有。"
            f"从它按返回会走 `else`，一路送回首页。加路由必须两处都加"
        )
    for k in sorted(back - depth):
        problems.append(
            f"[route] {rel} —— 路由 `{k}` 在 `backTarget` 里有、`routeDepth` 里没有。"
            f"它的转场方向会走 `else -> 0`，进出方向反了"
        )


LITERAL_FONT_SP = re.compile(r"fontSize\s*=\s*(\d+(?:\.\d+)?)\.sp")

for f in sorted(ROOT.rglob("*.kt")):
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    if "/ui/theme/" in rel:
        continue
    src = source_of(f)
    for m in LITERAL_FONT_SP.finditer(src):
        if float(m.group(1)) >= 60:
            continue
        ln = src.count("\n", 0, m.start()) + 1
        problems.append(
            f"[font] {rel}:{ln} —— 字号写成了字面量 `{m.group(1)}.sp`，"
            f"它**不跟用户的字号系数**（`Typography` 的十五个样式都乘过系数）。"
            f"用 `MaterialTheme.typography.xxx`、或 `base.fontSize * 比例`、"
            f"或 `displaySp(...)`"
        )


for f in sorted(ROOT.rglob("*.kt")):
    src = source_of(f)
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    for m in re.finditer(r"graphicsLayer\s*(?:\([^()]*\))?\s*\{", src):
        body, _end = balanced(src, m.end() - 1)
        if "return@graphicsLayer" not in body:
            continue
        if "noMorph(" in body:
            continue
        ln = src.count("\n", 0, m.start()) + 1
        problems.append(
            f"[layer] {rel}:{ln} —— 这个 `graphicsLayer` 里有提前 return，"
            f"但没走 `noMorph()` 复位。`graphicsLayer` 的 lambda 重跑时"
            f"**没赋值的属性保持上一次的值**，提前 return 会把上一帧的缩放 / 位移留在那儿"
        )


for f in [ROOT / "com/offline/due/ui/theme/Theme.kt"]:
    if not f.exists():
        continue
    src32 = source_of(f)
    for variant in ("LightGlass", "DarkGlass", "BlossomLight", "BlossomDark"):
        blk = re.search(rf"private val {variant} = GlassTokens\((.*?)\n\)", src32, re.S)
        if not blk:
            continue
        body = blk.group(1)
        c_m = re.search(r"\bcontrast = Color\(0xFF([0-9A-Fa-f]{6})\)", body)
        o_m = re.search(r"\bonContrast = Color\(0xFF([0-9A-Fa-f]{6})\)", body)
        if not c_m or not o_m:
            problems.append(
                f"[theme] Theme.kt {variant} 缺 `contrast` 或 `onContrast` —— "
                f"四套令牌必须都有，漏一套就是「某个主题下捐赠键没颜色」"
            )
            continue
        ratio = _contrast(int(o_m.group(1), 16), int(c_m.group(1), 16))
        if ratio < CONTRAST_MIN:
            problems.append(
                f"[theme] Theme.kt {variant} 捐赠键字色 (#{o_m.group(1)}) 压在 "
                f"反差色 (#{c_m.group(1)}) 上只有 {ratio:.2f}:1，"
                f"低于 {CONTRAST_MIN}:1 —— 换到这个配色就是一块读不出字的按钮"
            )


for f in [ROOT / "com/offline/due/data/EventStore.kt"]:
    if not f.exists():
        continue
    src33 = source_of(f)
    if "fun rollPastToElapsed" not in src33:
        problems.append(
            "[store] EventStore.kt 里没有 `rollPastToElapsed` —— "
            "过了的一次性倒数不会被改写成计时，重启之后还是负数"
        )
        continue
    init_m = re.search(r"\n    fun init\([^)]*\) \{(.*?)\n    \}", src33, re.S)
    if init_m is None:
        problems.append("[store] EventStore.kt 找不到 `fun init(...)`，这条规则没法验")
    elif "rollPastToElapsed()" not in init_m.group(1):
        ln = src33.count("\n", 0, init_m.start()) + 1
        problems.append(
            f"[store] EventStore.kt:{ln} —— `init()` 里没有调用 `rollPastToElapsed()`。"
            f"函数写了不接线的后果是：显示层兜住了，数据层没兜住，"
            f"重启之后那条事件还是「倒数 −5 天」"
        )


for f in [ROOT / "com/offline/due/ui/components/EventRow.kt"]:
    if not f.exists():
        continue
    src34 = source_of(f)
    for i, line in enumerate(src34.split("\n"), start=1):
        if "CountdownMath.precise(" in line and ".clock()" not in line:
            problems.append(
                f"[row] EventRow.kt:{i} —— 列表行的秒表用了 `text()`（带天数），"
                f"右边大数字已经是同一个数了。这里只许 `clock()`"
            )


for f in [ROOT / "com/offline/due/ui/components/EventRow.kt"]:
    if not f.exists():
        continue
    src35 = source_of(f)

    if "Modifier.width(unitWidth)" not in src35:
        problems.append(
            "[row] EventRow.kt —— 「天」那一格没有占位 Spacer（`Modifier.width(unitWidth)`）。"
            "后果：不写「天」的行（就是「今天」）数字比别的行右移 16.60dp"
        )

    m35 = re.search(r"\nprivate fun ClockLine\((.*?)\n\}\n", src35, re.S)
    if m35 is None:
        problems.append("[row] EventRow.kt 找不到 `private fun ClockLine(`，这条规则没法验")
    else:
        body35 = m35.group(1)
        inline = re.search(r"fontSize\s*=\s*[^\n]*coerceAtMost", body35)
        named = re.search(r"val\s+(\w+)\s*=[^\n]*\n?[^\n]*coerceAtMost", body35)
        ok = inline is not None
        if not ok and named is not None:
            ok = re.search(r"fontSize\s*=\s*" + named.group(1) + r"\b", body35) is not None
        if not ok:
            problems.append(
                "[row] EventRow.kt `ClockLine` —— 秒表字号没有经过 `coerceAtMost`。"
                "宽松档 0.30 × 60sp = 18sp，和标题的 19.44sp 只差 7%，层级塌了"
            )


ALLOWED_BACK_HANDLER = {
    "com/offline/due/ui/components/BackSwipe.kt",
    "com/offline/due/ui/components/Bits.kt",
    "com/offline/due/ui/EditScreen.kt",
}

for f in sorted(ROOT.rglob("*.kt")):
    src = source_of(f)
    rel = str(f.relative_to(ROOT)).replace("\\", "/")
    for m in re.finditer(r"\bBackHandler\s*[({]", src):
        if rel in ALLOWED_BACK_HANDLER:
            continue
        ln = src.count("\n", 0, m.start()) + 1
        problems.append(
            f"[back] {rel}:{ln} —— 用 `BackHandler` 接返回。它收不到手势进度，"
            f"侧滑时画面不动；而且会把外层 `PredictiveBack` 的 `onBack` 抢走、"
            f"留下抬在半路的 `backLiftP`。改用 `PredictiveBack`"
            f"（页内一层用 `rememberSubPageBack` + `subPageSlide`），"
            f"并且通过 `onSubBack` 把外层关掉"
        )


for f in [ROOT / "com/offline/due/ui/components/BackSwipe.kt"]:
    if not f.exists():
        continue
    src37 = source_of(f)

    i_collect = src37.find("events.collect")
    if i_collect < 0:
        problems.append("[back] BackSwipe.kt 找不到 `events.collect`，这条规则没法验")
    else:
        i_busy = src37.find("busy = true")
        if i_busy < 0:
            problems.append("[back] BackSwipe.kt 找不到 `busy = true`，这条规则没法验")
        elif i_busy < i_collect:
            ln = src37.count("\n", 0, i_busy) + 1
            problems.append(
                f"[back] BackSwipe.kt:{ln} —— `busy = true` 出现在 `events.collect` 之前。"
                f"`collect` 可能永远不结束，锁会永久为真，返回键从此失灵"
            )

    if "if (my == gen[0]) onCancel()" not in src37:
        problems.append(
            "[back] BackSwipe.kt —— `catch (CancellationException)` 里的 `onCancel()` "
            "没有代际判据。滞留在 `collect` 上的老协程被取消时会把新手势刚设好的量擦掉"
        )
    if "if (my == gen[0]) busy = false" not in src37:
        problems.append(
            "[back] BackSwipe.kt —— `busy` 的清除没有代际判据。老协程晚到的 `finally` "
            "会把新手势刚上的闸又放开，补演期间的第二次返回就漏过去了"
        )


for f in [ROOT / "com/offline/due/ui/components/Glass.kt"]:
    if not f.exists():
        continue
    src38 = source_of(f)

    n_aurora = src38.count("Aurora(")
    if n_aurora == 0:
        problems.append("[bg] Glass.kt 找不到 `Aurora(`，这条规则没法验")
    elif n_aurora > 1:
        problems.append(
            f"[bg] Glass.kt 有 {n_aurora} 处 `Aurora(`。这条规则只认唯一调用点，"
            f"多出来的一处不受检查——先确认它是不是画在页面里的第二份帘幕"
        )
    else:
        i_solid = src38.find("style != BgStyle.SOLID")
        if i_solid < 0:
            problems.append(
                "[bg] Glass.kt 的极光没有 `style != BgStyle.SOLID` 判据。"
                "纯色模式下帘幕会压在用户选的颜色上，手势返回时垫的却是纯色 —— 一动就变色"
            )
        elif i_solid > src38.find("Aurora("):
            problems.append(
                "[bg] Glass.kt —— `Aurora(` 出现在 `style != BgStyle.SOLID` 之前，"
                "等于没有判据（纯色底上照样叠帘幕）"
            )


f39 = ROOT / "com/offline/due/ui/components/ColorCanvas.kt"
if f39.exists():
    src39 = source_of(f39)
    if "fun backdropValueBand" not in src39:
        problems.append(
            "[picker] ColorCanvas.kt 没有 `backdropValueBand`。"
            "底色页的「深浅」滑块要拿它当行程，夹取区间也必须是同一份"
        )
    for lit, why in (
        ("0.84f", "浅色底色的亮度下限"),
        ("0.06f", "深色底色的亮度下限"),
        ("0.18f", "深色底色的亮度上限"),
    ):
        n39 = src39.count(lit)
        if n39 == 0:
            problems.append(
                f"[picker] ColorCanvas.kt 里找不到 {lit}（{why}）。"
                f"区间没了，这条规则没法验"
            )
        elif n39 > 1:
            problems.append(
                f"[picker] ColorCanvas.kt 里 {lit} 出现 {n39} 次（{why}）。"
                f"夹取区间只许写在 `backdropValueBand` 一处——"
                f"第二处会和滑块行程对不上，出现「拖了一半颜色不动」"
            )

f39b = ROOT / "com/offline/due/ui/BackdropScreen.kt"
if f39b.exists():
    src39b = source_of(f39b)
    i39, n39bar = 0, 0
    while True:
        j39 = src39b.find("ShadeBar(", i39)
        if j39 < 0:
            break
        n39bar += 1
        if "vRange" not in src39b[j39:j39 + 360]:
            problems.append(
                "[picker] BackdropScreen.kt 的 `ShadeBar(` 没传 `vRange`。"
                "默认行程 0..1，而底色只认安全亮度——滑块大半段会是死的"
            )
        i39 = j39 + 1
    if n39bar == 0:
        problems.append("[picker] BackdropScreen.kt 找不到 `ShadeBar(`，这条规则没法验")


f40 = ROOT / "com/offline/due/ui/components/Glass.kt"
if f40.exists():
    src40 = source_of(f40)
    if "fun pageBaseColor" not in src40:
        problems.append(
            "[bg] Glass.kt 没有 `pageBaseColor`。要和背景同色的装饰层没有出口，"
            "下面那条规则就没法遵守"
        )
    elif src40.count("gradient.getOrNull(") != 1:
        problems.append(
            f"[bg] Glass.kt 里 `gradient.getOrNull(` 出现 "
            f"{src40.count('gradient.getOrNull(')} 次，只该在 `pageBaseColor` 里出现一次"
        )

for f40b in files:
    rel40 = f40b.relative_to(ROOT).as_posix()
    if rel40 == "com/offline/due/ui/components/Glass.kt":
        continue
    src40b = source_of(f40b)
    n40 = src40b.count("gradient.getOrNull(")
    if n40:
        problems.append(
            f"[bg] {rel40} 用 `gradient.getOrNull(` 当页面底色（{n40} 处）。"
            f"令牌渐变只在冰川模式下等于背景——纯色 / 照片底上会多出一条"
            f"不同色的带子。改用 `pageBaseColor`"
        )


f41 = ROOT / "com/offline/due/MainActivity.kt"
if f41.exists():
    src41 = source_of(f41)
    i41 = src41.find("val healStaleGesture")
    if i41 < 0:
        problems.append(
            "[back] MainActivity.kt 没有 `healStaleGesture`。全局闸门 `backBusy` "
            "一旦闩残留就是永久全灭——手指落下来必须能把它作废"
        )
    else:
        seg41 = src41[i41:i41 + 1400]
        for token, why in (
            ("dragActive", "`dragActive` 残留"),
            ("backLiftOn", "`backLiftOn` 残留"),
            ("lastDragAt", "400ms 内有没有进度"),
        ):
            if token not in seg41:
                problems.append(
                    f"[back] `healStaleGesture` 里没判 {why}（找不到 `{token}`）。"
                    f"少判一条就有一半残留收不干净"
                )
    if ".then(healStaleWatch)" not in src41:
        problems.append(
            "[back] `healStaleWatch` 没挂在根 `Box` 上。观察者必须罩住整棵树，"
            "挂在某一页里就只能救那一页"
        )


DELEGATE_OPS = {"getValue", "setValue", "provideDelegate"}
for f in files:
    src42 = source_of(f)
    body42 = re.sub(r"^[ \t]*import[^\n]*$", "", src42, flags=re.M)
    for m in re.finditer(
        r"^[ \t]*import[ \t]+([\w.]+)(?:[ \t]+as[ \t]+(\w+))?[ \t]*$", src42, re.M
    ):
        name42 = m.group(2) or m.group(1).split(".")[-1]
        if name42 in DELEGATE_OPS:
            continue
        if not re.search(r"\b" + re.escape(name42) + r"\b", body42):
            line42 = src42.count("\n", 0, m.start()) + 1
            problems.append(
                f"[import] {f.name}:{line42} `{m.group(1)}` 没被用到。"
                f"编译器不报未使用导入，留着会让下一个读代码的人以为这里用了它"
            )


seen2, out2 = set(), []
for p in problems:
    if p not in seen2:
        seen2.add(p)
        out2.append(p)
print(f"源文件 {len(files)}｜类型 {sum(1 for k in types if not k.endswith('__top__'))}"
      f"｜函数签名 {len(funcs)}｜资源 {sum(len(v) for v in resources.values())}")
print(f"问题 {len(out2)} 条\n")
for p in out2:
    print("  " + p)

sys.exit(1 if out2 else 0)
