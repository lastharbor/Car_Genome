"""Quick eyeball of the generated dataset. Usage: python tools/check_wmi_coverage.py"""

import json
import pathlib

DATASET = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/wmi.json"

GROUPS = {
    "Russia and the CIS": ["XTA", "XTT", "XTC", "XTH", "X96", "X7L", "XW8", "Z94", "Z8N", "X9L", "XWB", "XTM", "XTE"],
    "Europe": ["WVW", "WAU", "WBA", "WDD", "WP0", "TMB", "VSS", "VF1", "VF3", "ZFA", "ZAR", "YV1", "SAL", "SJN", "WMW", "VNK", "SB1", "TRU", "W0L", "ZFF", "UU1", "TMA", "VSS"],
    "Asia": ["JHM", "JTD", "JN1", "JF1", "JMB", "KMH", "KNA", "LSG", "LVS", "MA1", "LFV"],
    "Americas": ["1HG", "1FA", "2T1", "3VW", "5YJ", "1G1", "WA1"],
}


def main() -> None:
    data = json.loads(DATASET.read_text(encoding="utf-8"))
    by_code = {entry["code"]: entry for entry in data["entries"]}
    three = sum(1 for code in by_code if len(code) == 3)
    print(f"{len(by_code)} entries: {three} three-character, {len(by_code) - three} six-character")
    print(f"updated {data['updated']}")

    missing = []
    for group, codes in GROUPS.items():
        print(f"\n{group}")
        for code in dict.fromkeys(codes):
            entry = by_code.get(code)
            if entry is None:
                missing.append(code)
                print(f"  {code:6} MISSING")
                continue
            name = entry.get("make") or entry["manufacturer"]
            country = f" [{entry['country']}]" if entry.get("country") else ""
            print(f"  {code:6} {name}{country}")

    print(f"\n{len(missing)} missing: {missing}" if missing else "\nnothing missing")


if __name__ == "__main__":
    main()
