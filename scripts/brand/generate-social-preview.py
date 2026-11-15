#!/usr/bin/env python3
"""Generate the repository social preview / README hero image (1280x640)."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SHOT = ROOT / "screenshots" / "dashboard-overview.png"
OUT = ROOT / "docs" / "assets" / "social-preview.png"

W, H = 1280, 640
FONT_DIR = Path("/usr/share/fonts/opentype/noto")
BOLD = FONT_DIR / "NotoSansCJK-Bold.ttc"
MEDIUM = FONT_DIR / "NotoSansCJK-Medium.ttc"
REGULAR = FONT_DIR / "NotoSansCJK-Regular.ttc"
SC = 2  # Noto Sans CJK SC face inside the TTC

BG_TOP = (6, 11, 22)
BG_BOTTOM = (10, 21, 41)
BRAND = (23, 111, 214)
ACCENT = (125, 211, 252)
TEXT = (243, 247, 255)
MUTED = (146, 167, 196)
FAINT = (95, 114, 143)


def font(path: Path, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(path), size, index=SC)


def vertical_gradient(size, top, bottom):
    img = Image.new("RGB", size, top)
    px = img.load()
    for y in range(size[1]):
        t = y / (size[1] - 1)
        row = tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        for x in range(size[0]):
            px[x, y] = row
    return img


def add_glow(base, center, radius, color, strength):
    glow = Image.new("RGBA", base.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(glow)
    cx, cy = center
    d.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=color + (strength,))
    glow = glow.filter(ImageFilter.GaussianBlur(radius * 0.55))
    base.alpha_composite(glow)


HEX_POINTS = [(16, 1.5), (28.56, 8.75), (28.56, 23.25), (16, 30.5), (3.44, 23.25), (3.44, 8.75)]
MARK_BLUE = (69, 115, 251)
MARK_CYAN = (0, 212, 255)
MARK_CROSSBAR = (125, 211, 252)


def _round_line(draw, points, width, fill):
    draw.line(points, fill=fill, width=width, joint="curve")
    r = width / 2
    for px, py in points:
        draw.ellipse([px - r, py - r, px + r, py + r], fill=fill)


def draw_mark(img, origin, size):
    x, y = origin
    s = size / 32
    pts = [(x + px * s, y + py * s) for px, py in HEX_POINTS]
    box = (round(x), round(y), round(x + size), round(y + size))
    gw, gh = box[2] - box[0], box[3] - box[1]

    # Addax family gradient, drawn diagonally and clipped to the hexagon.
    grad = Image.new("RGBA", (gw, gh))
    px = grad.load()
    for yy in range(gh):
        for xx in range(gw):
            t = (xx + yy) / (gw + gh)
            px[xx, yy] = tuple(
                round(MARK_BLUE[i] + (MARK_CYAN[i] - MARK_BLUE[i]) * t) for i in range(3)
            ) + (255,)
    mask = Image.new("L", (gw, gh), 0)
    local = [(p[0] - box[0], p[1] - box[1]) for p in pts]
    ImageDraw.Draw(mask).polygon(local, fill=255)
    img.paste(grad, (box[0], box[1]), mask)

    d = ImageDraw.Draw(img)
    white = (255, 255, 255, 255)
    _round_line(d, [(x + 10.1 * s, y + 23.4 * s), (x + 16 * s, y + 9.6 * s), (x + 21.9 * s, y + 23.4 * s)], max(2, round(2.6 * s)), white)
    _round_line(
        d,
        [(x + 12.3 * s, y + 18.7 * s), (x + 19.7 * s, y + 18.7 * s)],
        max(2, round(2.8 * s)),
        MARK_CROSSBAR + (255,),
    )
    r = 1.7 * s
    cx, cy = x + 16 * s, y + 9.6 * s
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=ACCENT + (255,))


def rounded_image(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.size[0] - 1, img.size[1] - 1], radius, fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def screenshot_card():
    shot = Image.open(SHOT).convert("RGB")
    width = 604
    height = round(shot.height * width / shot.width)
    shot = shot.resize((width, height), Image.LANCZOS)
    shot = rounded_image(shot, 14)

    bar = 30
    card = Image.new("RGBA", (width, height + bar), (0, 0, 0, 0))
    top = Image.new("RGBA", (width, bar), (16, 26, 44, 255))
    top = rounded_image(top, 14)
    card.alpha_composite(top, (0, 0))
    d = ImageDraw.Draw(card)
    d.rectangle([0, bar - 14, width, bar], fill=(16, 26, 44, 255))
    for i, c in enumerate([(255, 107, 107), (255, 196, 87), (107, 214, 140)]):
        cx = 22 + i * 20
        d.ellipse([cx - 5, bar // 2 - 5, cx + 5, bar // 2 + 5], fill=c + (160,))
    card.alpha_composite(shot, (0, bar))
    d = ImageDraw.Draw(card)
    d.rounded_rectangle(
        [-1, -1, width, height + bar], radius=15, outline=(255, 255, 255, 40), width=2
    )
    return card


def main():
    base = vertical_gradient((W, H), BG_TOP, BG_BOTTOM).convert("RGBA")
    add_glow(base, (120, -40), 520, BRAND, 120)
    add_glow(base, (1180, 680), 460, (0, 150, 199), 70)

    grid = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(grid)
    for x in range(0, W, 40):
        gd.line([(x, 0), (x, H)], fill=(255, 255, 255, 7))
    for y in range(0, H, 40):
        gd.line([(0, y), (W, y)], fill=(255, 255, 255, 7))
    base.alpha_composite(grid)

    card = screenshot_card()
    shadow = Image.new("RGBA", base.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    cx, cy = 632, 112
    sd.rounded_rectangle(
        [cx, cy + 14, cx + card.width, cy + card.height + 24], radius=20, fill=(0, 0, 0, 170)
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(26))
    base.alpha_composite(shadow)
    base.alpha_composite(card, (cx, cy))

    draw_mark(base, (80, 88), 64)

    d = ImageDraw.Draw(base)
    d.text((80, 176), "Addax Admin", font=font(BOLD, 54), fill=TEXT + (255,))
    d.text((82, 254), "让数据采集像水电一样可靠", font=font(BOLD, 30), fill=ACCENT + (255,))
    d.text(
        (82, 300),
        "面向 Addax 离线引擎的 T+1 ODS 采集控制台",
        font=font(MEDIUM, 20),
        fill=MUTED + (255,),
    )

    d.rounded_rectangle([82, 352, 138, 356], radius=2, fill=BRAND + (255,))

    lines = [
        "采集定义 · 调度执行 · 补数回补",
        "数据对账 · 字段血缘 · 质量断言",
        "告警中心 · 安全审计 · 多节点 HA",
    ]
    for i, line in enumerate(lines):
        y = 384 + i * 34
        d.rounded_rectangle([82, y + 7, 88, y + 13], radius=1, fill=ACCENT + (255,))
        d.text((100, y), line, font=font(REGULAR, 18), fill=(176, 193, 216, 255))

    d.text(
        (82, 542),
        "Apache-2.0  ·  Vue 3  ·  Spring Boot 4",
        font=font(REGULAR, 15),
        fill=FAINT + (255,),
    )
    d.text(
        (82, 570),
        "github.com/wgzhao/addax-admin",
        font=font(MEDIUM, 16),
        fill=ACCENT + (220,),
    )

    base.convert("RGB").save(OUT, optimize=True)
    print(f"wrote {OUT} ({OUT.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
