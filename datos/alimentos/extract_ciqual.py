"""Extracts CIQUAL 2025 (ANSES, Licence Ouverte / Etalab 2.0) from its official XML into ciqual.json.

Usage: python3 -I extract_ciqual.py DOWNLOAD_DIR   (alim_*.xml, alim_grp_*.xml and compo_*.xml from
https://doi.org/10.57745/RDMHWY, downloaded to their own folder)

Values are copied as published. CIQUAL marks: "-" not measured (kept as None), "traces" (0) and
"< x" below the detection limit (0, as food labels do).
"""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

NUTRIENTS = {"328": "kcal", "25000": "protein", "31000": "carbs", "40000": "fat",
             "34100": "fiber", "32000": "sugars", "40302": "saturated_fat", "10004": "salt"}
NUMBER = re.compile(r"^\d+(,\d+)?$")


def text(el, tag):
    child = el.find(tag)
    return (child.text or "").strip() if child is not None and child.text else ""


def value(raw: str):
    raw = raw.strip()
    if raw in ("", "-"):
        return None
    if raw.lower() == "traces" or raw.startswith("<"):
        return 0.0
    if not NUMBER.match(raw):
        raise ValueError(f"unexpected CIQUAL value {raw!r}")
    return float(raw.replace(",", "."))


def main(src: Path, out: Path) -> None:
    alim_xml = next(src.glob("alim_2*.xml"))
    compo_xml = next(src.glob("compo_*.xml"))
    foods = {}
    for a in ET.parse(alim_xml).getroot():
        code = text(a, "alim_code")
        foods[code] = {"code": int(code), "fr": text(a, "alim_nom_fr"), "en": text(a, "alim_nom_eng"),
                       "group": text(a, "alim_ssgrp_code")}
    for _, c in ET.iterparse(compo_xml):
        if c.tag != "COMPO":
            continue
        key = NUTRIENTS.get(text(c, "const_code"))
        if key:
            foods[text(c, "alim_code")][key] = value(text(c, "teneur"))
        c.clear()
    rows = sorted(foods.values(), key=lambda f: f["code"])
    out.write_text(json.dumps(rows, ensure_ascii=False, indent=0))
    with_kcal = sum(1 for f in rows if f.get("kcal") is not None)
    print(f"{len(rows)} foods, {with_kcal} with kcal")


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(__file__).with_name("ciqual.json"))
