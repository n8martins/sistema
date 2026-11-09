#!/usr/bin/env python3
"""Renders the launcher's TV banner and app icon in the SISTEMA style.

Usage: python3 make_art.py <launcher/app/src/main>   (needs Pillow)
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

MAIN = Path(sys.argv[1])
FONTS = MAIN / "assets" / "fonts"
INK = (17, 14, 12)
PAPER = (238, 231, 216)
ACCENT = (184, 68, 43)
ACCENT_SOFT = (217, 122, 60)


def glow(size, center, radius, color, alpha):
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx, cy = center
    d.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=color + (alpha,))
    return layer.filter(ImageFilter.GaussianBlur(radius * 0.55))


def tracked(draw, xy, text, font, fill, tracking):
    x, y = xy
    for ch in text:
        draw.text((x, y), ch, font=font, fill=fill)
        x += draw.textlength(ch, font=font) + tracking
    return x


def tracked_width(draw, text, font, tracking):
    return sum(draw.textlength(ch, font=font) for ch in text) + tracking * (len(text) - 1)


def banner(path, scale=4):
    w, h = 320 * scale, 180 * scale
    img = Image.new("RGBA", (w, h), INK + (255,))
    img.alpha_composite(glow((w, h), (int(w * 0.9), int(-h * 0.15)), int(w * 0.45), ACCENT, 120))
    img.alpha_composite(glow((w, h), (int(w * 0.05), int(h * 1.1)), int(w * 0.3), ACCENT_SOFT, 50))
    d = ImageDraw.Draw(img)
    serif = ImageFont.truetype(str(FONTS / "fraunces_medium.ttf"), 46 * scale)
    mono = ImageFont.truetype(str(FONTS / "mono_medium.ttf"), 11 * scale)
    word, tr = "SISTEMA", 5 * scale
    tw = tracked_width(d, word, serif, tr)
    tracked(d, ((w - tw) / 2, h * 0.30), word, serif, PAPER, tr)
    sub, tr2 = "HOME", 6 * scale
    sw = tracked_width(d, sub, mono, tr2)
    tracked(d, ((w - sw) / 2, h * 0.66), sub, mono, ACCENT_SOFT, tr2)
    img.resize((320, 180), Image.LANCZOS).convert("RGB").save(path, optimize=True)


def icon(path, px, scale=4):
    s = px * scale
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    base = Image.new("RGBA", (s, s), INK + (255,))
    base.alpha_composite(glow((s, s), (int(s * 0.85), int(s * 0.05)), int(s * 0.5), ACCENT, 130))
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.22), fill=255)
    img.paste(base, (0, 0), mask)
    d = ImageDraw.Draw(img)
    font = ImageFont.truetype(str(FONTS / "fraunces_display.ttf"), int(s * 0.66))
    bbox = d.textbbox((0, 0), "S", font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    x, y = (s - tw) / 2 - bbox[0] - s * 0.04, (s - th) / 2 - bbox[1]
    d.text((x, y), "S", font=font, fill=PAPER)
    r = s * 0.045
    cx, cy = x + bbox[2] + s * 0.05, y + bbox[3] - r
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=ACCENT_SOFT)
    img.resize((px, px), Image.LANCZOS).save(path, optimize=True)


(MAIN / "res" / "drawable-xhdpi").mkdir(parents=True, exist_ok=True)
banner(MAIN / "res" / "drawable-xhdpi" / "banner.png")
for folder, px in (("mipmap-mdpi", 48), ("mipmap-hdpi", 72), ("mipmap-xhdpi", 96), ("mipmap-xxhdpi", 144), ("mipmap-xxxhdpi", 192)):
    (MAIN / "res" / folder).mkdir(parents=True, exist_ok=True)
    icon(MAIN / "res" / folder / "ic_launcher.png", px)
print("banner and icons written")
