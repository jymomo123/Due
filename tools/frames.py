"""把一段录屏拆成能看的静帧（需要 ffmpeg）。

为什么需要它：这个工程没有真机、没有模拟器，UI 的实际观感只能靠用户录屏回传。
录屏是 mp4，我看不了视频，但**能看图片**——所以把它按时间均匀拆成若干帧，
再逐张看，等价于"慢放一遍"。

## 用法

    python tools/frames.py 录屏.mp4                # 默认 12 帧
    python tools/frames.py 录屏.mp4 --count 20     # 更密
    python tools/frames.py 录屏.mp4 --from 3 --to 6  # 只看第 3~6 秒（动画那一段）
    python tools/frames.py 录屏.mp4 --width 720    # 字更大（默认 540）

产物落在 `<录屏同名目录>/frames_<时间戳>/`：

    frame_00_0.00s.png …   单帧，按时间命名
    sheet.png              把所有帧拼成一张网格图（先看这张，一眼扫完整段）

## 为什么默认缩到 540 宽

手机录屏一般是 1080×2400。原尺寸一张 PNG 两三兆，十二张读起来很慢；540 宽
足够看清"哪块在动、文字有没有截断、对齐有没有歪"，只有要读小字时才需要 720+。

## 为什么用逐帧 seek 而不是一次 `fps` 滤镜

`-ss` 放在 `-i` 前面是**关键帧 seek**，每次都从最近的关键帧跳到目标时刻，
一段 30 秒的录屏取 12 帧不到两秒就跑完。`fps` 滤镜要把整段解一遍，
而且它按"帧率"抽，不按"我想看这几个时刻"抽——录制掉帧时位置会飘。
"""
import argparse
import datetime as dt
import shutil
import subprocess
import sys
from pathlib import Path

# 工程里不装 ffmpeg，用用户自己那份。
FFMPEG_CANDIDATES = [
    Path("C:/Users/Administrator/ffmpeg/bin/ffmpeg.exe"),
    Path("C:/Users/Administrator/ffmpeg/bin/ffprobe.exe"),
]


def _tool(name):
    for p in FFMPEG_CANDIDATES:
        if p.name == name and p.exists():
            return str(p)
    found = shutil.which(name)
    if found:
        return found
    raise SystemExit(f"找不到 {name}。装一份 ffmpeg 并把 bin 目录加进 PATH。")


def probe(video):
    """读时长、分辨率、帧率。读不到就报错——静默返回 0 会让后面除零。"""
    out = subprocess.run(
        [_tool("ffprobe.exe"), "-v", "error", "-select_streams", "v:0",
         "-show_entries", "stream=width,height,r_frame_rate,nb_frames",
         "-show_entries", "format=duration",
         "-of", "default=noprint_wrappers=1", str(video)],
        capture_output=True, text=True,
    )
    if out.returncode != 0:
        raise SystemExit("ffprobe 读不了这个文件：\n" + out.stderr.strip())
    info = {}
    for line in out.stdout.splitlines():
        if "=" in line:
            k, v = line.split("=", 1)
            info[k.strip()] = v.strip()
    try:
        info["duration"] = float(info.get("duration", 0))
    except ValueError:
        info["duration"] = 0.0
    if info["duration"] <= 0:
        raise SystemExit("这段视频的时长读出来是 0，可能不是完整的 mp4。")
    return info


def grab(video, at, out, width):
    """抽一帧。`-ss` 必须在 `-i` 前面（关键帧 seek，快）。"""
    subprocess.run(
        [_tool("ffmpeg.exe"), "-v", "error", "-y",
         "-ss", f"{at:.3f}", "-i", str(video),
         "-frames:v", "1",
         "-vf", f"scale={width}:-2:flags=lanczos",
         str(out)],
        check=True, capture_output=True,
    )


def sheet(frames, out, cols):
    """把若干帧拼成一张网格图。

    文件名必须是**从 0 开始的连号**（`frame_%02d.png`）：这份 ffmpeg 是
    essentials build，`-pattern_type glob` 没编进去（报 "globbing is not
    supported by this libavformat build"）。所以时间戳只打印、不进文件名——
    `image2` 解复用器认 `%02d`，比 glob 稳。
    """
    rows = (len(frames) + cols - 1) // cols
    subprocess.run(
        [_tool("ffmpeg.exe"), "-v", "error", "-y",
         "-f", "image2", "-start_number", "0",
         "-i", str(frames[0].parent / "frame_%02d.png"),
         "-vf", f"tile={cols}x{rows}:padding=6:color=0x202020",
         "-frames:v", "1", str(out)],
        check=True, capture_output=True,
    )


def main():
    ap = argparse.ArgumentParser(description="录屏拆帧（需要 ffmpeg）")
    ap.add_argument("video", type=Path)
    ap.add_argument("--count", type=int, default=12, help="抽多少帧（默认 12）")
    ap.add_argument("--from", dest="start", type=float, default=None, help="起始秒")
    ap.add_argument("--to", dest="end", type=float, default=None, help="结束秒")
    ap.add_argument("--width", type=int, default=540, help="缩放宽度（默认 540）")
    ap.add_argument("--cols", type=int, default=4, help="网格列数（默认 4）")
    args = ap.parse_args()

    video = args.video
    if not video.exists():
        raise SystemExit(f"没有这个文件：{video}")

    info = probe(video)
    total = info["duration"]
    start = 0.0 if args.start is None else max(0.0, args.start)
    end = total if args.end is None else min(total, args.end)
    if end <= start:
        raise SystemExit(f"时间范围不对：{start} → {end}")

    # 头尾各让开 2%：录屏的第一帧常常是黑屏 / 转场，最后一帧常常是静止的收尾。
    span = end - start
    pad = span * 0.02
    n = max(1, args.count)
    times = [start + pad + (span - 2 * pad) * i / max(1, n - 1) for i in range(n)]

    stamp = dt.datetime.now().strftime("%H%M%S")
    outdir = video.parent / f"frames_{stamp}"
    outdir.mkdir(parents=True, exist_ok=True)

    frames = []
    for i, t in enumerate(times):
        f = outdir / f"frame_{i:02d}.png"
        grab(video, t, f, args.width)
        frames.append(f)

    print(f"{video.name}｜{info.get('width')}×{info.get('height')}"
          f"｜{total:.2f}s｜{info.get('r_frame_rate', '?')} fps")
    print(f"抽了 {len(frames)} 帧 → {outdir}")
    for f, t in zip(frames, times):
        print(f"  {f.name}  {t:.2f}s")

    if len(frames) > 1:
        s = outdir / "sheet.png"
        try:
            sheet(frames, s, args.cols)
            print(f"网格图：{s}")
        except subprocess.CalledProcessError as e:
            # 拼图只是省事，拼不出来不该让整件事失败——单帧已经拿到了。
            print("拼图失败（单帧仍然可用）：" + e.stderr.decode("utf-8", "ignore")[:200],
                  file=sys.stderr)


if __name__ == "__main__":
    main()
