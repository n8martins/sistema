#!/usr/bin/env python3
"""Cut the SISTEMA brand fonts into small static TTFs for the TV launcher.

Source: the variable fonts from github.com/google/fonts (SIL Open Font License).
Needs: pip install fonttools
Usage: python3 make_fonts.py <dir with the downloaded variable fonts> <output dir>
"""
import sys
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from fontTools import subset

SRC, OUT = Path(sys.argv[1]), Path(sys.argv[2])
OUT.mkdir(parents=True, exist_ok=True)

# Latin, Latin-1, typographic punctuation, degree sign, arrows.
UNICODES = (
    list(range(0x20, 0x7F))
    + list(range(0xA0, 0x100))
    + list(range(0x2010, 0x2028))
    + list(range(0x2030, 0x203B))
    + [0x2122, 0x2190, 0x2191, 0x2192, 0x2193, 0x2212, 0x2022]
)

CUTS = [
    # file in SRC, axis location, output name
    ("Fraunces[SOFT,WONK,opsz,wght].ttf", {"opsz": 144, "wght": 300, "SOFT": 40, "WONK": 0}, "fraunces_display.ttf"),
    ("Fraunces[SOFT,WONK,opsz,wght].ttf", {"opsz": 72, "wght": 500, "SOFT": 0, "WONK": 0}, "fraunces_medium.ttf"),
    ("Fraunces-Italic[SOFT,WONK,opsz,wght].ttf", {"opsz": 48, "wght": 340, "SOFT": 60, "WONK": 0}, "fraunces_italic.ttf"),
    ("IBMPlexSans[wdth,wght].ttf", {"wdth": 100, "wght": 200}, "sans_thin.ttf"),
    ("IBMPlexSans[wdth,wght].ttf", {"wdth": 100, "wght": 300}, "sans_light.ttf"),
    ("IBMPlexSans[wdth,wght].ttf", {"wdth": 100, "wght": 400}, "sans_regular.ttf"),
    ("IBMPlexSans[wdth,wght].ttf", {"wdth": 100, "wght": 500}, "sans_medium.ttf"),
    ("JetBrainsMono[wght].ttf", {"wght": 400}, "mono_regular.ttf"),
    ("JetBrainsMono[wght].ttf", {"wght": 500}, "mono_medium.ttf"),
]

for src, loc, name in CUTS:
    font = TTFont(SRC / src)
    axes = {a.axisTag for a in font["fvar"].axes}
    static = instancer.instantiateVariableFont(font, {k: v for k, v in loc.items() if k in axes}, updateFontNames=False)
    opts = subset.Options()
    opts.layout_features = ["kern", "liga", "calt", "tnum", "lnum", "case"]
    opts.name_IDs = ["*"]
    opts.notdef_outline = True
    opts.hinting = False
    sub = subset.Subsetter(opts)
    sub.populate(unicodes=UNICODES)
    sub.subset(static)
    if src.startswith("IBMPlex"):
        # "Plex" is a Reserved Font Name under the OFL, so the modified cut gets its own name.
        for rec in static["name"].names:
            if rec.nameID in (1, 3, 4, 6, 16, 18) and "Plex" in rec.toUnicode():
                rec.string = rec.toUnicode().replace("IBM Plex Sans", "Sistema Sans").replace("IBMPlexSans", "SistemaSans").replace("Plex", "Sistema")
    static.save(OUT / name)
    print(f"{name}: {(OUT / name).stat().st_size // 1024} KB")
