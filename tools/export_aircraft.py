#!/usr/bin/env python3
"""Export reusable SVGs from the same silhouettes and palette used by the live renderer.

Run from any directory: python3 tools/export_aircraft.py
Only Python's standard library is needed. The game draws vectors directly, without loading PNGs.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/pl/miniplanes"
OUTPUT = ROOT / "src/main/resources/pl/miniplanes/assets/aircraft"


def path_constant(source, name):
    expression = re.search(r"static final String " + name + r"\s*=(.*?);", source, re.S).group(1)
    return "".join(json.loads(s) for s in re.findall(r'"(?:[^"\\]|\\.)*"', expression))


def main():
    painter = (JAVA / "view/AirplanePainter.java").read_text()
    palette = re.findall(r'([A-Z]+)\("[^"]+", "(#[0-9A-Fa-f]{6})"\)',
                         (JAVA / "model/LineColor.java").read_text())
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for kind, constant in [("small", "SMALL"), ("widebody", "WIDE")]:
        outline = path_constant(painter, constant)
        for name, color in palette:
            dark = "#" + "".join(f"{round(int(color[i:i+2],16)*.82):02x}" for i in (1,3,5))
            engines = (f'<rect x="-3" y="-13" width="10" height="4" rx="1.5" fill="{dark}"/>\n'
                       f'<rect x="-3" y="9" width="10" height="4" rx="1.5" fill="{dark}"/>\n') if kind == "widebody" else ""
            svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="-36 -32 72 64" width="288" height="256">\n'
                   f'<title>{name.title()} {kind} aircraft</title>\n'
                   f'<path d="{outline}" fill="{color}" stroke="#FFFDF6" stroke-width="2.1" stroke-linejoin="round"/>\n'
                   '<path d="M 22,-2.8 Q 27,-2.2 28,0 Q 27,2.2 22,2.8 L 23,0 Z" fill="#173E47"/>\n'
                   f'{engines}</svg>\n')
            (OUTPUT / f"{kind}-{name.lower()}.svg").write_text(svg)
    print(f"Exported {len(palette)*2} aircraft SVGs")


if __name__ == "__main__":
    main()
