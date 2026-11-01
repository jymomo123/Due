"""读 PNG 的像素。只够用：8bit / 非隔行 / RGB 或 RGBA。

环境里没有 PIL，而"这条带子到底差多少"这种事必须看数字，
不能靠"我觉得看不见了"。所以自己解一遍 PNG。

用法：
    python tools/px.py <png> <x> <y0> <y1> [step]
"""
import sys
import zlib
import struct


def load(path):
    raw = open(path, "rb").read()
    assert raw[:8] == b"\x89PNG\r\n\x1a\n", "不是 PNG"
    pos = 8
    idat = b""
    w = h = depth = ctype = None
    while pos < len(raw):
        ln = struct.unpack(">I", raw[pos:pos + 4])[0]
        typ = raw[pos + 4:pos + 8]
        data = raw[pos + 8:pos + 8 + ln]
        if typ == b"IHDR":
            w, h, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", data)
            assert depth == 8 and interlace == 0, "只支持 8bit 非隔行"
        elif typ == b"IDAT":
            idat += data
        elif typ == b"IEND":
            break
        pos += 12 + ln
    ch = {0: 1, 2: 3, 4: 2, 6: 4}[ctype]
    buf = zlib.decompress(idat)
    stride = w * ch
    out = bytearray(h * stride)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        f = buf[p]
        p += 1
        line = bytearray(buf[p:p + stride])
        p += stride
        if f == 1:
            for i in range(ch, stride):
                line[i] = (line[i] + line[i - ch]) & 255
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 255
        elif f == 3:
            for i in range(stride):
                a = line[i - ch] if i >= ch else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 255
        elif f == 4:
            for i in range(stride):
                a = line[i - ch] if i >= ch else 0
                b = prev[i]
                c = prev[i - ch] if i >= ch else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        out[y * stride:(y + 1) * stride] = line
        prev = line
    return w, h, ch, out


def px(img, x, y):
    w, h, ch, buf = img
    i = (y * w + x) * ch
    return buf[i], buf[i + 1], buf[i + 2]


def hexs(p):
    return "#%02X%02X%02X" % p


if __name__ == "__main__":
    path = sys.argv[1]
    x = int(sys.argv[2])
    y0, y1 = int(sys.argv[3]), int(sys.argv[4])
    step = int(sys.argv[5]) if len(sys.argv) > 5 else 4
    img = load(path)
    prev = None
    for y in range(y0, y1 + 1, step):
        p = px(img, x, y)
        d = "" if prev is None else "   Δ=%d" % (abs(p[0] - prev[0]) + abs(p[1] - prev[1]) + abs(p[2] - prev[2]))
        print("y=%4d  %s%s" % (y, hexs(p), d))
        prev = p
