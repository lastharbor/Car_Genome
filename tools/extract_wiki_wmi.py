"""Builds tools/wmi_wikipedia.json from the "List of common WMI" table.

vPIC only lists manufacturers registered to sell in the United States, so codes
like TMB (Skoda), VSS (SEAT) and SJN (Nissan Sunderland) are missing from it
entirely. The English Wikipedia table, sourced from the German Kraftfahrt-
Bundesamt register, covers them.

Only the WMI and the manufacturer name are taken. The country is deliberately
ignored: VinCountries already derives it from VIN positions 1-2.

Usage:  python tools/extract_wiki_wmi.py
"""

from __future__ import annotations

import json
import pathlib
import re
import sys
import urllib.request

EXPORT_URL = "https://en.wikipedia.org/wiki/Special:Export/Vehicle_identification_number"
OUTPUT = pathlib.Path(__file__).resolve().parent / "wmi_wikipedia.json"

# The cell usually opens with a wiki link holding the manufacturer, followed by
# free text: a vehicle category ("car", "SUV"), a second marque, or a note.
LEADING_LINK = re.compile(r"^\s*\[\[(?:[^|\]]*\|)?([^\]]+)\]\]")
TAIL = re.compile(r"\s+(?:&|also|made by|includes|formerly|until|since)\b.*$", re.I)

# The table appends the vehicle category after the manufacturer link.
CATEGORY = re.compile(
    r"(?:,)?\s+(?:passenger\s+)?(?:car|cars|van|vans|bus|buses|truck|trucks|pickup|"
    r"motorcycle|motorcycles|SUV|MPV|LCV)(?:[/\s]+\w+)*$",
)


def strip_markup(cell: str) -> str:
    cell = re.sub(r"&lt;ref[^&]*?/&gt;", "", cell)
    cell = re.sub(r"&lt;ref.*?&lt;/ref&gt;", "", cell, flags=re.S)
    cell = re.sub(r"&lt;/?[^&]*?&gt;", "", cell)
    cell = re.sub(r"\{\{[^}]*\}\}", "", cell)
    cell = re.sub(r"(row|col)span=\"?\d+\"?\s*\|", "", cell)
    cell = cell.replace("'''", "").replace("''", "").replace("&amp;", "&")
    return re.sub(r"[ \t]+", " ", cell).strip(" |\n")


def manufacturer_of(cell: str) -> str:
    """Prefers the leading wiki link, which is the manufacturer proper."""
    cell = strip_markup(cell)
    link = LEADING_LINK.match(cell)
    if link:
        return CATEGORY.sub("", link.group(1).strip()).strip(" .,")
    cell = re.sub(r"\[\[[^|\]]*\|([^\]]*)\]\]", r"\1", cell)
    cell = re.sub(r"\[\[([^\]]*)\]\]", r"\1", cell)
    cell = re.sub(r"\[https?://\S+\s+([^\]]*)\]", r"\1", cell)
    cell = TAIL.sub("", cell)
    return CATEGORY.sub("", cell).strip(" .,")


def main() -> int:
    print("Fetching the English VIN article")
    request = urllib.request.Request(
        EXPORT_URL,
        headers={"User-Agent": "CarGenome-dataset-builder/1.0 (offline VIN decoder dataset)"},
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        text = response.read().decode("utf-8")

    start = text.find("==List of common WMI==")
    if start < 0:
        print("The article no longer has a 'List of common WMI' section", file=sys.stderr)
        return 1
    table = text[start:]
    table = table[: table.find("\n|}")]

    entries: dict[str, str] = {}
    for row in table.split("\n|-"):
        cells = row.split("||")
        if len(cells) < 2:
            continue
        # Two-cell rows inherit the country through a rowspan, three-cell rows
        # carry it themselves; the manufacturer is always the last cell.
        manufacturer = manufacturer_of(cells[-1])
        if not manufacturer:
            continue
        # A single row can cover several codes, as in "KNA KNC KNE".
        for code in strip_markup(cells[0]).upper().split():
            if re.fullmatch(r"[0-9A-HJ-NPR-Z]{3}", code):
                entries.setdefault(code, manufacturer)

    payload = {
        "source": "en.wikipedia.org/wiki/Vehicle_identification_number#List_of_common_WMI",
        "entries": [
            {"code": code, "manufacturer": entries[code]} for code in sorted(entries)
        ],
    }
    OUTPUT.write_text(
        json.dumps(payload, ensure_ascii=False, indent=1) + "\n",
        encoding="utf-8",
    )
    print(f"Wrote {OUTPUT} ({len(payload['entries'])} entries)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
