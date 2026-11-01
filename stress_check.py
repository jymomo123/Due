import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).parent
SRC = ROOT / "app/src/main/java"

problems = []


def strip_noise(src: str) -> str:
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    src = re.sub(r"//[^\n]*", "", src)
    src = re.sub(r'"""(?:.|\n)*?"""', '""', src)
    src = re.sub(r'"(?:\\.|[^"\\])*"', '""', src)
    src = re.sub(r"'(?:\\.|[^'\\])*'", "''", src)
    return src


def inside(src: str, pos: int, call: str) -> bool:
    for m in re.finditer(r"\b" + re.escape(call) + r"\s*\{", src):
        depth = 0
        start = m.end() - 1
        for i in range(start, len(src)):
            if src[i] == "{":
                depth += 1
            elif src[i] == "}":
                depth -= 1
                if depth == 0:
                    if start <= pos <= i:
                        return True
                    break
    return False


def line_of(src: str, pos: int) -> int:
    return src.count("\n", 0, pos) + 1


files = sorted(SRC.rglob("*.kt"))
for f in files:
    raw = f.read_text(encoding="utf-8")
    src = strip_noise(raw)
    rel = str(f.relative_to(ROOT)).replace("\\", "/")

    for m in re.finditer(r"\b!!\b", src):
        if inside(src, m.start(), "runCatching"):
            continue
        problems.append(f"[crash] {rel}:{line_of(src, m.start())} —— 非空断言 !!，换个分支就会 NPE")

    for m in re.finditer(r"\.valueOf\(", src):
        if inside(src, m.start(), "runCatching"):
            continue
        problems.append(
            f"[crash] {rel}:{line_of(src, m.start())} —— enum.valueOf() 未包 runCatching，脏数据会崩"
        )

    for m in re.finditer(r"\.(first|last|single|max|min)\(\)", src):
        problems.append(
            f"[crash] {rel}:{line_of(src, m.start())} —— .{m.group(1)}() 在空集合上会抛，用 {m.group(1)}OrNull()"
        )

    for m in re.finditer(r"remember\s*(?:\([^)]*\))?\s*\{", src):
        depth, start = 0, m.end() - 1
        end = start
        for i in range(start, len(src)):
            if src[i] == "{":
                depth += 1
            elif src[i] == "}":
                depth -= 1
                if depth == 0:
                    end = i
                    break
        body = src[start:end]
        if "mutableStateOf" in body or "mutableFloatStateOf" in body:
            continue
        for clock in ("LocalDate.now(", "LocalDateTime.now(", "System.currentTimeMillis("):
            if clock in body:
                problems.append(
                    f"[logic] {rel}:{line_of(src, m.start())} —— remember 里缓存了 {clock[:-1]}，值会过期"
                )
                break

    for m in re.finditer(r"while\s*\(\s*true\s*\)", src):
        depth, start = 0, src.find("{", m.end())
        if start < 0:
            continue
        end = start
        for i in range(start, len(src)):
            if src[i] == "{":
                depth += 1
            elif src[i] == "}":
                depth -= 1
                if depth == 0:
                    end = i
                    break
        body = src[start:end]
        if not any(k in body for k in ("delay(", "withFrameNanos", "awaitPointerEvent", "yield(", "suspend")):
            problems.append(
                f"[crash] {rel}:{line_of(src, m.start())} —— while(true) 里没有挂起点，会卡死线程"
            )

    if "/ui/" in rel:
        for m in re.finditer(r"\b(readText|writeText|FileInputStream|FileOutputStream)\b", src):
            problems.append(
                f"[perf] {rel}:{line_of(src, m.start())} —— UI 层直接做文件 IO，应放进 LaunchedEffect/IO 协程"
            )

seen, out = set(), []
for p in problems:
    if p not in seen:
        seen.add(p)
        out.append(p)

print(f"扫描 {len(files)} 个文件｜问题 {len(out)} 条\n")
for p in out:
    print("  " + p)

sys.exit(1 if out else 0)
