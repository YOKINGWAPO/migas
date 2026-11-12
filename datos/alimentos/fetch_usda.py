"""Extracts the USDA FoodData Central SR Legacy (public domain) values for the basics CIQUAL lacks into usda.json.

Usage: python3 -I fetch_usda.py SR_LEGACY_ZIP
(FoodData_Central_sr_legacy_food_csv_2018-04.zip from https://fdc.nal.usda.gov/download-datasets,
downloaded to its own folder)
"""
import csv
import io
import json
import sys
import zipfile
from pathlib import Path

IDS = {168411, 171248, 173177, 174275, 167879}
# FDC nutrient ids; sodium (mg) becomes salt (g) as on EU labels: salt = sodium x 2.5.
NUTRIENTS = {"1008": "kcal", "1003": "protein", "1005": "carbs", "1004": "fat", "1079": "fiber",
             "2000": "sugars", "1258": "saturated_fat"}
SODIUM = "1093"


def rows(zf: zipfile.ZipFile, name: str):
    member = next(m for m in zf.namelist() if m.endswith("/" + name))
    with zf.open(member) as raw:
        yield from csv.DictReader(io.TextIOWrapper(raw, encoding="utf-8"))


def main(zip_path: Path) -> None:
    foods = {}
    with zipfile.ZipFile(zip_path) as zf:
        for r in rows(zf, "food.csv"):
            if int(r["fdc_id"]) in IDS:
                foods[int(r["fdc_id"])] = {"fdc_id": int(r["fdc_id"]), "description": r["description"]}
        for r in rows(zf, "food_nutrient.csv"):
            fdc_id = int(r["fdc_id"])
            if fdc_id not in foods:
                continue
            amount = float(r["amount"])
            if r["nutrient_id"] in NUTRIENTS:
                foods[fdc_id][NUTRIENTS[r["nutrient_id"]]] = round(amount, 3)
            elif r["nutrient_id"] == SODIUM:
                foods[fdc_id]["salt"] = round(amount * 2.5 / 1000, 3)
    missing = IDS - set(foods)
    if missing:
        raise SystemExit(f"not in SR Legacy: {sorted(missing)}")
    for f in foods.values():
        if "kcal" not in f:
            raise SystemExit(f"{f['fdc_id']} has no energy in kcal")
    out = [foods[i] for i in sorted(foods)]
    Path(__file__).with_name("usda.json").write_text(json.dumps(out, ensure_ascii=False, indent=1))
    for f in out:
        print(f)


if __name__ == "__main__":
    main(Path(sys.argv[1]))
