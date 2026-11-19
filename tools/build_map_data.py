#!/usr/bin/env python3
"""Regenerate bundled data: python3 tools/build_map_data.py countries.geojson airports.csv

Optional developer tool; requires Shapely. The game needs neither Python nor a network.
Inputs: Natural Earth ne_50m_admin_0_countries.geojson and OurAirports airports.csv.
"""
import csv
import json
import sys
from pathlib import Path
from shapely.geometry import shape, box

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/pl/miniplanes/maps"
REGIONS = [
    ("europe", "Europe", "Small distances. Endless possibilities.", [-14, 42, 33, 66], 2200,
     "LHR CDG FRA MAD LIS DUB EDI OSL ARN HEL CPH AMS BRU ZRH VIE PRG WAW KRK BUD FCO MXP BCN NCE ATH IST OTP SOF BEG ZAG TIA RIX TLL VNO HAM BER"),
    ("americas", "The Americas", "One network. Two continents.", [-133, -32, -57, 62], 4000,
     "MEX MIA BOG JFK YYZ YVR YUL LAX SFO SEA DEN DFW ORD ATL BOS HAV SJU GUA SJO PTY CCS UIO LIM LPB SCL EZE MVD GRU GIG BSB REC FOR MAO ASU MDE"),
]
SHAPES = ["CIRCLE", "TRIANGLE", "SQUARE", "CIRCLE", "TRIANGLE", "CIRCLE", "SQUARE",
          "DIAMOND", "CIRCLE", "TRIANGLE", "SQUARE", "CIRCLE", "TRIANGLE", "STAR",
          "CIRCLE", "TRIANGLE", "SQUARE", "DIAMOND", "CIRCLE", "TRIANGLE", "SQUARE",
          "CIRCLE", "HEXAGON", "TRIANGLE", "CIRCLE", "SQUARE", "CIRCLE", "TRIANGLE",
          "DIAMOND", "CIRCLE", "SQUARE", "TRIANGLE", "PENTAGON", "CIRCLE", "CROSS"]

def main():
    countries = json.loads(Path(sys.argv[1]).read_text())["features"]
    with open(sys.argv[2], newline="") as f:
        rows = list(csv.DictReader(f))
    lookup = {r["iata_code"]: r for r in rows if r["type"] == "large_airport"}
    ROOT.mkdir(exist_ok=True, parents=True)
    for key, title, subtitle, bounds, threshold, codes in REGIONS:
        west, east, south, north = bounds
        polygons = []
        for feature in countries:
            if key == "americas":
                # Retain whole regional coastlines, including Alaska and Greenland. Do not
                # include the other side of the Atlantic or cut Canada at an arbitrary latitude.
                if feature["properties"]["CONTINENT"] not in ("North America", "South America") and feature["properties"]["ADMIN"] != "France":
                    continue
                clipped = shape(feature["geometry"]).intersection(box(-180, -60, -10, 85))
            else:
                clipped = shape(feature["geometry"]).intersection(box(max(-180, west-50), max(-85, south-30), min(180, east+50), min(85, north+20)))
            if clipped.is_empty:
                continue
            clipped = clipped.simplify(.015, preserve_topology=True)
            parts = [clipped] if clipped.geom_type == "Polygon" else getattr(clipped, "geoms", [])
            rings = []
            for part in parts:
                if part.geom_type != "Polygon" or part.area < .002:
                    continue
                for ring in [part.exterior, *part.interiors]:
                    rings.append([[round(x, 4), round(y, 4)] for x, y in ring.coords])
            if rings:
                polygons.append({"name": feature["properties"]["ADMIN"], "rings": rings})
        airports = []
        for code, symbol in zip(codes.split(), SHAPES, strict=True):
            r = lookup[code]
            airports.append({"code": code, "name": r["name"], "city": r["municipality"].split(" (")[0] or code,
                             "country": r["iso_country"], "latitude": float(r["latitude_deg"]),
                             "longitude": float(r["longitude_deg"]), "shape": symbol})
        (ROOT / f"{key}-airports.json").write_text(json.dumps(airports, ensure_ascii=False, indent=2) + "\n")
        meta = dict(id=key, title=title, subtitle=subtitle, west=west, east=east, south=south, north=north,
                    longHaulKm=threshold, countries=polygons)
        (ROOT / f"{key}.json").write_text(json.dumps(meta, ensure_ascii=False, separators=(",", ":")) + "\n")
        print(f"{title}: {len(airports)} airports, {len(polygons)} country outlines")

if __name__ == "__main__":
    main()
