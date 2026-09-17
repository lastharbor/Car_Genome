"""Regenerates app/src/main/assets/wmi.json.

Two sources are merged:

1. wmi_wikipedia.json, the broad list sourced from the German Kraftfahrt-
   Bundesamt register. It is the only one of the three that covers marques
   with no US presence, such as Skoda (TMB) and SEAT (VSS).
2. NHTSA vPIC, which knows every manufacturer registered to sell in the US and
   supplies the tidiest make names. It is queried per vehicle type because no
   endpoint returns every WMI at once.
3. wmi_supplement.json, curated for manufacturers neither source has heard of.
   Post-Soviet marques live there, and so do the country codes for the X2-X0
   prefixes the ISO chart leaves unassigned.

Later sources win. Run extract_wiki_wmi.py and extract_cis_wmi.py first if you
want their inputs refreshed too.

Usage:  python tools/generate_wmi_dataset.py
"""

from __future__ import annotations

import json
import pathlib
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor

API = "https://vpic.nhtsa.dot.gov/api/vehicles"

# Trailers are left out on purpose: they are 9,600 of the roughly 13,000 WMIs
# vPIC publishes, and this app tracks cars.
VEHICLE_TYPES = [
    "Passenger Car",
    "Multipurpose Passenger Vehicle (MPV)",
    "Truck",
    "Bus",
    "Motorcycle",
    "Low Speed Vehicle (LSV)",
    "Incomplete Vehicle",
    "Off-Road Vehicle",
]

# Vehicle types we would rather keep when one WMI shows up under several.
TYPE_PRIORITY = {name: index for index, name in enumerate(VEHICLE_TYPES)}

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "app" / "src" / "main" / "assets" / "wmi.json"
SUPPLEMENT = pathlib.Path(__file__).resolve().parent / "wmi_supplement.json"
WIKIPEDIA = pathlib.Path(__file__).resolve().parent / "wmi_wikipedia.json"
# vPIC blocks a client that queries it too eagerly, and resolving make names
# costs one request per code, so its answers are kept on disk.
CACHE = pathlib.Path(__file__).resolve().parent / "vpic_wmi_cache.json"


HEADERS = {
    "User-Agent": "CarGenome-dataset-builder/1.0 (offline VIN decoder dataset)",
    "Accept": "application/json",
}


def fetch_json(url: str, attempts: int = 5):
    """vPIC answers 403 when it decides a client is impolite, so back off."""
    last_error = None
    for attempt in range(attempts):
        try:
            request = urllib.request.Request(url, headers=HEADERS)
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.load(response)
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
            last_error = error
            time.sleep(2 ** attempt)
    raise RuntimeError(f"giving up on {url}: {last_error}")


def collect_wmis() -> dict[str, dict]:
    collected: dict[str, dict] = {}
    for vehicle_type in VEHICLE_TYPES:
        url = f"{API}/GetWMIsForManufacturer/?vehicleType={urllib.parse.quote(vehicle_type)}&format=json"
        payload = fetch_json(url)
        print(f"  {vehicle_type}: {payload['Count']}")
        for row in payload["Results"]:
            code = (row.get("WMI") or "").strip().upper()
            if not code or len(code) not in (3, 6):
                continue
            existing = collected.get(code)
            if existing is None:
                collected[code] = row
                continue
            current_rank = TYPE_PRIORITY.get(existing.get("VehicleType"), 99)
            new_rank = TYPE_PRIORITY.get(row.get("VehicleType"), 99)
            if new_rank < current_rank:
                collected[code] = row
    return collected


def decode_wmi(code: str) -> tuple[str, dict | None]:
    """DecodeWMI knows the marketing name ("Volkswagen") on top of the legal one."""
    if len(code) != 3:
        return code, None
    try:
        payload = fetch_json(f"{API}/DecodeWMI/{code}?format=json", attempts=2)
    except RuntimeError:
        return code, None
    results = payload.get("Results") or []
    return code, results[0] if results else None


def title_case(name: str) -> str:
    """vPIC shouts every name. Lower-case it without wrecking acronyms."""
    if not name:
        return name
    if name.upper() != name:
        return name

    keep_upper = {
        "AG", "SA", "NV", "BV", "AB", "AS", "SP", "ZOO", "GMBH", "KG", "LLC",
        "INC", "LTD", "PLC", "CO", "CORP", "USA", "UK", "SPA", "SRL", "JSC",
        "OAO", "PAO", "OOO", "ZAO", "PJSC", "BMW", "GM", "FCA", "SAIC", "KIA",
        "MG", "DAF", "MAN", "SEAT", "PSA", "II", "III", "US", "MFG", "AS.",
    }
    words = []
    for word in name.split():
        stripped = word.strip(".,")
        if stripped in keep_upper or (len(stripped) <= 3 and stripped.isalpha() and stripped.isupper() and len(stripped) > 1):
            words.append(word)
        else:
            words.append(word.capitalize())
    return " ".join(words)


def load_cache() -> dict[str, dict]:
    if not CACHE.exists():
        return {}
    return {entry["code"]: entry for entry in json.loads(CACHE.read_text(encoding="utf-8"))["entries"]}


def vpic_entries() -> dict[str, dict]:
    """vPIC-derived entries, refreshed from the API when it will talk to us."""
    cache = load_cache()
    try:
        print("Fetching WMI lists from vPIC")
        collected = collect_wmis()
    except RuntimeError as error:
        if not cache:
            raise
        print(f"  vPIC is refusing requests ({error.__cause__ or 'HTTP error'}); using the cache")
        return cache
    print(f"  unique WMIs: {len(collected)}")

    cached_makes = {code: entry["make"] for code, entry in cache.items() if entry.get("make")}
    if cached_makes:
        print(f"  reusing {len(cached_makes)} make names from the cache")

    three_char = [code for code in collected if len(code) == 3 and code not in cached_makes]
    print(f"  resolving make names for {len(three_char)} codes")
    decoded: dict[str, dict] = {}
    with ThreadPoolExecutor(max_workers=4) as pool:
        for index, (code, result) in enumerate(pool.map(decode_wmi, three_char), start=1):
            if result:
                decoded[code] = result
            if index % 250 == 0:
                print(f"    {index}/{len(three_char)}")

    entries: dict[str, dict] = {}
    for code, row in sorted(collected.items()):
        extra = decoded.get(code, {})
        manufacturer = title_case((row.get("Name") or "").strip())
        make = (extra.get("CommonName") or extra.get("Make") or cached_makes.get(code) or "").strip()
        entry = {"code": code, "manufacturer": manufacturer}
        if make and make.upper() != manufacturer.upper():
            entry["make"] = title_case(make)
        vehicle_type = (row.get("VehicleType") or "").strip()
        if vehicle_type:
            entry["vehicleType"] = vehicle_type
        entries[code] = entry

    CACHE.write_text(
        json.dumps(
            {"source": "NHTSA vPIC", "entries": [entries[code] for code in sorted(entries)]},
            ensure_ascii=False,
            indent=1,
        ) + "\n",
        encoding="utf-8",
    )
    return entries


def main() -> int:
    entries: dict[str, dict] = {}

    wikipedia = json.loads(WIKIPEDIA.read_text(encoding="utf-8"))
    for entry in wikipedia["entries"]:
        entries[entry["code"].upper()] = dict(entry, code=entry["code"].upper())
    print(f"Seeded {len(entries)} entries from the Wikipedia list")

    from_vpic = vpic_entries()
    dropped = 0
    for code, entry in sorted(from_vpic.items()):
        if entry.get("vehicleType") == "Trailer":
            dropped += 1
            continue
        entries[code] = entry
    print(f"Merged {len(from_vpic) - dropped} vPIC entries ({dropped} trailers skipped)")

    supplement = json.loads(SUPPLEMENT.read_text(encoding="utf-8"))
    for entry in supplement["entries"]:
        code = entry["code"].upper()
        entry["code"] = code
        # The curated table wins: it exists precisely where vPIC is wrong or blank.
        entries[code] = entry
    print(f"Merged {len(supplement['entries'])} curated entries, {len(entries)} total")

    dataset = {
        "source": "en.wikipedia.org VIN article (KBA register) + NHTSA vPIC + tools/wmi_supplement.json",
        "updated": __import__("datetime").date.today().isoformat(),
        "entries": [entries[code] for code in sorted(entries)],
    }
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(
        json.dumps(dataset, ensure_ascii=False, indent=1) + "\n",
        encoding="utf-8",
    )
    size_kb = OUTPUT.stat().st_size / 1024
    print(f"Wrote {OUTPUT} ({len(dataset['entries'])} entries, {size_kb:.0f} KB)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
