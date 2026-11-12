"""Builds app/src/main/assets/alimentos.csv, the basic-foods table bundled in the app.

Usage: python3 -I build_table.py

Inputs (all in this folder):
  ciqual.json             CIQUAL 2025 as published (extract_ciqual.py)
  nombres_es.json         Spanish names for every CIQUAL code, translated for the app
  correcciones_es.json    hand fixes over nombres_es.json (translations that would mislead)
  comunes.json            the everyday foods with short names and servings, each pointing at a CIQUAL code
  usda.json               values of the everyday foods CIQUAL lacks, from USDA FoodData Central SR Legacy
                          (public domain; fetch_usda.py)
  extra_usda_nombres.json their names and servings

Every value is copied from its source; only names and servings are ours. One conversion: USDA gives
carbohydrate "by difference" (fibre included) and CIQUAL and EU labels give it without fibre, so USDA
carbs are stored minus fibre to keep the whole table on the same basis.
"""
import json
import re
import unicodedata
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE.parents[1] / "app/src/main/assets/alimentos.csv"
NUTRIENTS = ["kcal", "protein", "carbs", "fat", "fiber", "sugars", "saturated_fat", "salt"]
HEADER = ("# source;code;name_es;name_en;kcal;protein;carbs;fat;fiber;sugars;saturated_fat;salt;"
          "serving_es;serving_en;serving_g  (per 100 g; CIQUAL 2025 by Anses, Licence Ouverte / Etalab 2.0; "
          "USDA FoodData Central, public domain)")


def load(name):
    return json.loads((HERE / name).read_text(encoding="utf-8"))


def clean(text: str) -> str:
    text = " ".join(str(text).split())
    if ";" in text:
        raise ValueError(f"';' in {text!r}")
    return text


def search_key(text: str) -> str:
    """Mirrors VoiceParser.normalize in the app: the seeder tells foods apart by this key."""
    t = unicodedata.normalize("NFD", text.lower())
    t = "".join(ch for ch in t if unicodedata.category(ch) != "Mn")
    t = re.sub(r"(\d),(\d)", r"\1.\2", t)
    t = re.sub(r"(\d)([a-z])", r"\1 \2", t)
    t = re.sub(r"[^a-z0-9.,; ]", " ", t)
    t = re.sub(r"(?<!\d)\.|\.(?!\d)", " ", t)
    return re.sub(r"\s+", " ", t).strip()


def number(v) -> str:
    return "" if v is None else f"{v:g}"


def row(source, code, name_es, name_en, values, serving_es="", serving_en="", grams=None) -> str:
    cells = [source, str(code), clean(name_es), clean(name_en)] + [number(values.get(k)) for k in NUTRIENTS]
    cells += [clean(serving_es) if grams else "", clean(serving_en) if grams else "", number(grams)]
    return ";".join(cells)


def main() -> None:
    ciqual = {f["code"]: f for f in load("ciqual.json") if f.get("kcal") is not None}
    names = {int(k): v for k, v in load("nombres_es.json").items()}
    names.update({int(k): v for k, v in load("correcciones_es.json").items()})
    missing = set(ciqual) - set(names)
    if missing:
        raise SystemExit(f"{len(missing)} CIQUAL codes without a Spanish name, e.g. {sorted(missing)[:5]}")

    # The same Spanish name for two different foods would make them indistinguishable in search.
    by_name = {}
    for code in ciqual:
        by_name.setdefault(names[code].casefold(), []).append(code)
    for codes in by_name.values():
        if len(codes) > 1:
            for code in codes:
                names[code] = f"{names[code]} ({ciqual[code]['fr']})"

    rows = []
    for c in load("comunes.json"):
        f = ciqual[c["code"]]
        rows.append(row("CIQUAL", c["code"], c["name"], c["name_en"], f, c["serving"], c["serving_en"], c["grams"]))
    names_usda = load("extra_usda_nombres.json")
    for e in load("usda.json"):
        n = names_usda[str(e["fdc_id"])]
        values = dict(e, carbs=round(max(e["carbs"] - (e.get("fiber") or 0.0), 0.0), 3))
        rows.append(row("USDA", e["fdc_id"], n["name"], n["name_en"], values, n["serving"], n["serving_en"], n["grams"]))
    # An everyday food named exactly like its own full CIQUAL row replaces it (same values, plus a serving).
    curated = {search_key(c["name"] + " " + c["name_en"]): c["code"] for c in load("comunes.json")}
    for code in sorted(ciqual):
        key = search_key(names[code] + " " + ciqual[code]["en"])
        if curated.get(key) == code:
            continue
        rows.append(row("CIQUAL", code, names[code], ciqual[code]["en"], ciqual[code]))

    keys = Counter(search_key(r.split(";")[2] + " " + r.split(";")[3]) for r in rows)
    dupes = {k for k, n in keys.items() if n > 1}
    if dupes:
        raise SystemExit(f"duplicate names: {sorted(dupes)[:5]}")
    OUT.write_text(HEADER + "\n" + "\n".join(rows) + "\n", encoding="utf-8")
    print(f"{len(rows)} rows -> {OUT}")


if __name__ == "__main__":
    main()
