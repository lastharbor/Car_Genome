"""Builds tools/wmi_supplement.json from the Russian Wikipedia VIN article.

vPIC only knows manufacturers registered to sell in the United States, which
leaves out every post-Soviet marque. The Russian article carries a sourced
table of WMI codes for Russia, Ukraine, Belarus, Kazakhstan and their
neighbours, including the small-volume builders that are identified by
positions 12-14 as well as by the WMI.

Usage:  python tools/extract_cis_wmi.py
"""

from __future__ import annotations

import json
import pathlib
import re
import sys
import urllib.parse
import urllib.request

ARTICLE = "Идентификационный номер транспортного средства"
EXPORT_URL = "https://ru.wikipedia.org/wiki/Special:Export/" + urllib.parse.quote(ARTICLE)

OUTPUT = pathlib.Path(__file__).resolve().parent / "wmi_supplement.json"

COUNTRIES = {
    "Россия": "RU",
    "Украина": "UA",
    "Беларусь": "BY",
    "Белоруссия": "BY",
    "Казахстан": "KZ",
    "Узбекистан": "UZ",
    "Киргизия": "KG",
    "Кыргызстан": "KG",
    "Азербайджан": "AZ",
    "Армения": "AM",
    "Грузия": "GE",
    "Молдавия": "MD",
    "Молдова": "MD",
    "Таджикистан": "TJ",
    "Туркмения": "TM",
    "Литва": "LT",
    "Латвия": "LV",
    "Эстония": "EE",
}

# Codes missing from the article but listed in the Russian Ministry of Industry
# and Trade order N 9 of 14 January 2010, which tabulates manufacturer, model
# and the first three VIN characters for every car in the scrappage programme.
MINISTRY_ENTRIES = [
    {"code": "X7L", "manufacturer": "ЗАО «Рено Россия» (Автофрамос)", "country": "RU", "make": "Renault"},
    {"code": "Z94", "manufacturer": "ООО «Хендэ Мотор Мануфактуринг Рус»", "country": "RU", "make": "Hyundai"},
    {"code": "X7M", "manufacturer": "ООО «ТагАЗ»", "country": "RU", "make": "ТагАЗ"},
    {"code": "X98", "manufacturer": "ОАО «АВТОВАЗ»", "country": "RU", "make": "LADA"},
    {"code": "XU3", "manufacturer": "ОАО «Соллерс-Набережные Челны»", "country": "RU"},
    {"code": "Z7G", "manufacturer": "ООО «Соллерс-Елабуга»", "country": "RU"},
    {"code": "Z88", "manufacturer": "ООО «Северстальавто-Кама»", "country": "RU"},
]

# Marques worth surfacing as a make on their own, keyed by WMI.
MAKES = {
    "XTA": "LADA",
    "XTT": "УАЗ",
    "XTC": "КамАЗ",
    "XTH": "ГАЗ",
    "X96": "ГАЗ",
    "XTK": "ИжАвто",
    "XWK": "ИжАвто",
    "XTZ": "ЗИЛ",
    "XTY": "ЛиАЗ",
    "XTE": "ЗАЗ",
    "XTM": "МАЗ",
    "XTU": "Тролза",
    "XTJ": "СеАЗ",
    "XTB": "Москвич",
    "X9L": "Chevrolet",
    "X7L": "Renault",
    "X9F": "Ford",
    "XW7": "Toyota",
    "XW8": "Volkswagen",
    "Z8N": "Nissan",
    "Z94": "Hyundai",
    "XWB": "Daewoo",
    "XUU": "Автотор",
    "XWE": "Автотор",
    "XWF": "Автотор",
}


def clean(text: str) -> str:
    """Turns a wikitable cell into a plain manufacturer name."""
    text = re.sub(r"\{\{iw\|[^|}]*\|([^|}]*)\|[^}]*\}\}", r"\1", text)
    text = re.sub(r"\{\{[^}]*\}\}", "", text)
    text = re.sub(r"\[\[[^|\]]*\|([^\]]*)\]\]", r"\1", text)
    text = re.sub(r"\[\[([^\]]*)\]\]", r"\1", text)
    text = re.sub(r"\[https?://\S+\s+([^\]]*)\]", r"\1", text)
    text = re.sub(r"\[https?://\S+\]", "", text)
    text = re.sub(r"<[^>]+>", " ", text)
    text = re.sub(r"rowspan=\"?\d+\"?", "", text)
    text = re.sub(r"colspan=\"?\d+\"?", "", text)
    text = text.replace("'''", "").replace("''", "")
    text = text.replace("«", "\u00ab").replace("»", "\u00bb")
    return re.sub(r"\s+", " ", text).strip(" |")


def split_row(row: str) -> list[str]:
    """Rows mix inline `||` separators with one-cell-per-line formatting."""
    cells: list[str] = []
    for line in row.split("\n"):
        line = line.strip()
        if not line or line.startswith("!"):
            continue
        if line.startswith("|"):
            line = line[1:]
        cells.extend(part for part in line.split("||"))
    return [clean(cell) for cell in cells]


def parse_table(body: str, code_pattern: re.Pattern[str]) -> list[dict]:
    entries: list[dict] = []
    for row in body.split("\n|-"):
        cells = [cell for cell in split_row(row) if cell]
        if len(cells) < 2:
            continue
        match = code_pattern.fullmatch(cells[0].replace(" ", ""))
        if not match:
            continue
        code = "".join(match.groups())
        # Cyrillic В and А look like Latin ones and do sneak into the article.
        code = code.translate(str.maketrans("АВСЕНКМОРТХУ", "ABCEHKMOPTXY"))
        if not re.fullmatch(r"[0-9A-HJ-NPR-Z]+", code):
            continue

        manufacturer = cells[1]
        country = None
        for cell in cells[2:]:
            if cell in COUNTRIES:
                country = COUNTRIES[cell]
        if manufacturer and country:
            entry = {"code": code, "manufacturer": manufacturer, "country": country}
            if code in MAKES:
                entry["make"] = MAKES[code]
            entries.append(entry)
    return entries


def main() -> int:
    print(f"Fetching {ARTICLE}")
    request = urllib.request.Request(
        EXPORT_URL,
        headers={"User-Agent": "CarGenome-dataset-builder/1.0 (offline VIN decoder dataset)"},
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        text = response.read().decode("utf-8")

    tables = re.findall(r"\{\|\s*class=\"wikitable\"(.*?)\n\|\}", text, re.S)
    print(f"  found {len(tables)} wikitables")

    wmi_entries: list[dict] = []
    detailed_entries: list[dict] = []
    for body in tables:
        if "! WMI" in body or "!WMI" in body:
            wmi_entries += parse_table(body, re.compile(r"([0-9A-ZА-Я]{3})"))
        elif "! VIN" in body or "!VIN" in body:
            # Rows look like "X89……..AA3…": WMI, filler, then positions 12-14.
            detailed_entries += parse_table(
                body,
                re.compile(r"([0-9A-ZА-Я]{3})[^0-9A-ZА-Я]+([0-9A-ZА-Я]{3})[^0-9A-ZА-Я]*"),
            )

    print(f"  {len(wmi_entries)} three-character codes, {len(detailed_entries)} six-character codes")

    merged: dict[str, dict] = {}
    for entry in wmi_entries + detailed_entries + MINISTRY_ENTRIES:
        merged.setdefault(entry["code"], entry)
    print(f"  {len(MINISTRY_ENTRIES)} ministry entries")

    payload = {
        "source": f"ru.wikipedia.org/wiki/{ARTICLE} + Приказ Минпромторга РФ от 14.01.2010 N 9",
        "entries": [merged[code] for code in sorted(merged)],
    }
    OUTPUT.write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n",
        encoding="utf-8",
    )
    print(f"Wrote {OUTPUT} ({len(payload['entries'])} entries)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
