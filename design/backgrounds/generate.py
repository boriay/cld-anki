#!/usr/bin/env python3
"""Single source of truth for the weather/time meadow backgrounds.

Scenes are described once as a list of primitive shapes and emitted to two
formats from the same geometry, so the platforms never drift apart:

  * Universal SVG  -> design/backgrounds/bg_meadow_<state>.svg
    (render directly on iOS / web, or rasterise to PNG/WebP)
  * Android VectorDrawable -> android/app/src/main/res/drawable/bg_meadow_<state>.xml

One file per (time, condition) so a client maps a weather state straight to an
asset. Run from anywhere; paths are resolved relative to this file.
"""
import os
import random

VW, VH = 1080, 1920  # shared viewport / viewBox

# ---------------------------------------------------------------------------
# Shared geometry
# ---------------------------------------------------------------------------
FAR_HILL = f"M0,1280 Q540,1130 1080,1280 L1080,{VH} L0,{VH} Z"
NEAR_HILL = f"M0,1500 Q540,1360 1080,1500 L1080,{VH} L0,{VH} Z"

FLOWERS = [
    (140, 1660, "#E94F4F"), (260, 1740, "#F2D94E"), (430, 1620, "#FFFFFF"),
    (600, 1720, "#E94F4F"), (760, 1650, "#F2D94E"), (910, 1740, "#FFFFFF"),
    (340, 1820, "#F2D94E"), (700, 1840, "#E94F4F"), (980, 1830, "#FFFFFF"),
]
STARS = [
    (120, 220, 5), (300, 140, 4), (480, 300, 6), (250, 420, 4),
    (820, 180, 5), (960, 360, 4), (640, 240, 5), (180, 560, 4),
    (900, 560, 5), (520, 120, 4), (740, 460, 4),
]

# ---------------------------------------------------------------------------
# Primitives — each is a small dict the two renderers know how to draw.
# ---------------------------------------------------------------------------
def sky(top, bottom):
    return {"t": "sky", "top": top, "bottom": bottom}

def circle(cx, cy, r, color, alpha=None):
    return {"t": "circle", "cx": cx, "cy": cy, "r": r, "color": color, "alpha": alpha}

def fill(d, color, alpha=None):
    return {"t": "path", "d": d, "color": color, "alpha": alpha}

def line(x1, y1, x2, y2, color, w, alpha=None):
    return {"t": "line", "x1": x1, "y1": y1, "x2": x2, "y2": y2,
            "color": color, "w": w, "alpha": alpha}

# ---------------------------------------------------------------------------
# Composite scene elements
# ---------------------------------------------------------------------------
def cloud(cx, cy, s, color, alpha=None):
    return [
        circle(cx, cy, int(90 * s), color, alpha),
        circle(cx - int(120 * s), cy + int(30 * s), int(70 * s), color, alpha),
        circle(cx + int(120 * s), cy + int(30 * s), int(75 * s), color, alpha),
        circle(cx, cy + int(45 * s), int(95 * s), color, alpha),
    ]

def sun(cx, cy):
    return [circle(cx, cy, 170, "#FFE27A", 0.45), circle(cx, cy, 120, "#FFD23F")]

def moon(cx, cy, color="#EDEFF5"):
    return [circle(cx, cy, 150, "#FFFFFF", 0.12), circle(cx, cy, 110, color)]

def stars():
    return [circle(x, y, r, "#FFFFFF", 0.9) for x, y, r in STARS]

def rain(color):
    # Scattered wind-driven streaks: random position, varied length and opacity,
    # but a shared slant (dx/dy ~ 0.37) so it reads as rain. Fixed seed -> stable.
    rnd = random.Random(19920317)
    out = []
    for _ in range(72):
        x = rnd.randint(40, VW - 10)
        y = rnd.randint(80, 1240)
        length = rnd.randint(48, 92)
        dx = round(length * 0.37)
        w = rnd.choice([5, 6, 7])
        a = round(rnd.uniform(0.45, 0.9), 2)
        out.append(line(x, y, x - dx, y + length, color, w, a))
    return out

def snow(color):
    # Scattered flakes (not a grid): jittered positions, varied size and opacity.
    # Fixed seed keeps regeneration deterministic.
    rnd = random.Random(20240620)
    out = []
    for _ in range(64):
        x = rnd.randint(15, VW - 15)
        y = rnd.randint(120, 1360)
        r = rnd.randint(7, 19)
        a = round(rnd.uniform(0.55, 0.95), 2)
        out.append(circle(x, y, r, color, a))
    return out

def meadow(far, near, snowcap=False):
    out = [fill(FAR_HILL, far), fill(NEAR_HILL, near)]
    if snowcap:
        out += [fill(FAR_HILL, "#FFFFFF", 0.55), fill(NEAR_HILL, "#FFFFFF", 0.4)]
    return out

def flowers(muted=False):
    out = []
    a = 0.5 if muted else None
    for cx, cy, color in FLOWERS:
        out.append(circle(cx, cy, 18, color, a))
        out.append(circle(cx, cy, 7, "#FFE27A", a))
    return out

def _flatten(*groups):
    out = []
    for g in groups:
        out.extend(g if isinstance(g, list) else [g])
    return out

# ---------------------------------------------------------------------------
# The 8 scenes
# ---------------------------------------------------------------------------
SCENES = {
    "day_sunny": _flatten(sky("#5BB8E8", "#CDEEFB"), sun(840, 360),
                          meadow("#6FBF4F", "#4E9E36"), flowers()),
    "day_cloudy": _flatten(sky("#9AA7B2", "#D2DBE2"), cloud(360, 380, 1.3, "#FFFFFF"),
                           cloud(760, 300, 1.0, "#F2F5F8"), meadow("#67B24A", "#499234"),
                           flowers()),
    "day_rain": _flatten(sky("#6B7682", "#9BA6B0"), cloud(360, 360, 1.4, "#C9D0D7"),
                         cloud(760, 320, 1.1, "#B7BFC8"), rain("#AEE0FF"),
                         meadow("#4F9A52", "#3C7E3A"), flowers(muted=True)),
    "day_snow": _flatten(sky("#AEB9C4", "#E6EEF3"), cloud(360, 360, 1.3, "#FFFFFF"),
                         cloud(760, 320, 1.0, "#EDF1F5"), snow("#FFFFFF"),
                         meadow("#7FB87E", "#5E985C", snowcap=True), flowers(muted=True)),
    "night_sunny": _flatten(sky("#0B1026", "#27407A"), stars(), moon(840, 340),
                            meadow("#2E5A3A", "#21442C"), flowers(muted=True)),
    "night_cloudy": _flatten(sky("#10162E", "#2A3A63"), stars(), moon(840, 320, "#C9CEDC"),
                             cloud(380, 380, 1.3, "#3A4866"), cloud(780, 320, 1.0, "#445273"),
                             meadow("#284F33", "#1D3B27")),
    "night_rain": _flatten(sky("#0E1322", "#27324A"), cloud(380, 360, 1.4, "#2C3650"),
                           cloud(780, 320, 1.1, "#36425F"), rain("#7FA8C9"),
                           meadow("#264A31", "#1B3724")),
    "night_snow": _flatten(sky("#141C30", "#364360"), stars(), cloud(380, 360, 1.3, "#3B496A"),
                           snow("#FFFFFF"), meadow("#33543C", "#244031", snowcap=True)),
}

# ---------------------------------------------------------------------------
# Renderers
# ---------------------------------------------------------------------------
def render_svg(elements):
    body, defs, gid = [], [], 0
    for e in elements:
        t = e["t"]
        if t == "sky":
            gid += 1
            defs.append(
                f'    <linearGradient id="sky{gid}" gradientUnits="userSpaceOnUse"'
                f' x1="540" y1="0" x2="540" y2="1280">\n'
                f'      <stop offset="0" stop-color="{e["top"]}"/>\n'
                f'      <stop offset="1" stop-color="{e["bottom"]}"/>\n'
                f'    </linearGradient>')
            body.append(f'  <rect x="0" y="0" width="{VW}" height="{VH}" fill="url(#sky{gid})"/>')
        elif t == "circle":
            op = f' fill-opacity="{e["alpha"]}"' if e["alpha"] is not None else ""
            body.append(f'  <circle cx="{e["cx"]}" cy="{e["cy"]}" r="{e["r"]}" fill="{e["color"]}"{op}/>')
        elif t == "path":
            op = f' fill-opacity="{e["alpha"]}"' if e["alpha"] is not None else ""
            body.append(f'  <path d="{e["d"]}" fill="{e["color"]}"{op}/>')
        elif t == "line":
            op = f' stroke-opacity="{e["alpha"]}"' if e.get("alpha") is not None else ""
            body.append(f'  <line x1="{e["x1"]}" y1="{e["y1"]}" x2="{e["x2"]}" y2="{e["y2"]}"'
                        f' stroke="{e["color"]}" stroke-width="{e["w"]}" stroke-linecap="round"{op}/>')
    defs_block = f'  <defs>\n' + "\n".join(defs) + "\n  </defs>\n" if defs else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {VW} {VH}"'
            f' width="{VW//4}" height="{VH//4}">\n' + defs_block + "\n".join(body) + "\n</svg>\n")


def _circle_path(cx, cy, r):
    return f"M{cx-r},{cy} a{r},{r} 0 1,0 {2*r},0 a{r},{r} 0 1,0 {-2*r},0 Z"


def render_avd(elements):
    out = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    xmlns:aapt="http://schemas.android.com/aapt"',
        f'    android:width="{VW//4}dp"',
        f'    android:height="{VH//4}dp"',
        f'    android:viewportWidth="{VW}"',
        f'    android:viewportHeight="{VH}">',
    ]
    for e in elements:
        t = e["t"]
        if t == "sky":
            out += [
                '  <path android:pathData="M0,0 L1080,0 L1080,1920 L0,1920 Z">',
                '    <aapt:attr name="android:fillColor">',
                '      <gradient android:type="linear"',
                '          android:startX="540" android:startY="0"',
                '          android:endX="540" android:endY="1280"',
                f'          android:startColor="{e["top"]}" android:endColor="{e["bottom"]}"/>',
                '    </aapt:attr>',
                '  </path>',
            ]
        elif t == "circle":
            a = f' android:fillAlpha="{e["alpha"]}"' if e["alpha"] is not None else ""
            out.append(f'  <path android:pathData="{_circle_path(e["cx"], e["cy"], e["r"])}"'
                       f' android:fillColor="{e["color"]}"{a}/>')
        elif t == "path":
            a = f' android:fillAlpha="{e["alpha"]}"' if e["alpha"] is not None else ""
            out.append(f'  <path android:pathData="{e["d"]}" android:fillColor="{e["color"]}"{a}/>')
        elif t == "line":
            d = f'M{e["x1"]},{e["y1"]} L{e["x2"]},{e["y2"]}'
            a = f' android:strokeAlpha="{e["alpha"]}"' if e.get("alpha") is not None else ""
            out.append(f'  <path android:pathData="{d}" android:strokeColor="{e["color"]}"'
                       f' android:strokeWidth="{e["w"]}" android:strokeLineCap="round"{a}/>')
    out.append('</vector>')
    return "\n".join(out) + "\n"


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    svg_dir = here
    avd_dir = os.path.normpath(os.path.join(here, "..", "..", "android",
                                            "app", "src", "main", "res", "drawable"))
    os.makedirs(svg_dir, exist_ok=True)
    os.makedirs(avd_dir, exist_ok=True)
    for name, elements in SCENES.items():
        svg_path = os.path.join(svg_dir, f"bg_meadow_{name}.svg")
        avd_path = os.path.join(avd_dir, f"bg_meadow_{name}.xml")
        with open(svg_path, "w") as f:
            f.write(render_svg(elements))
        with open(avd_path, "w") as f:
            f.write(render_avd(elements))
        print("wrote", os.path.relpath(svg_path, here), "+", os.path.relpath(avd_path, here))


if __name__ == "__main__":
    main()
