"""Prints tappable centres from a uiautomator dump. Usage: python tools/ui_nodes.py <dump.xml>"""

import re
import sys

sys.stdout.reconfigure(encoding="utf-8")
text = open(sys.argv[1], encoding="utf-8").read()
pattern = re.compile(r'text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
for label, x1, y1, x2, y2 in pattern.findall(text):
    if label.strip():
        print(f"{(int(x1) + int(x2)) // 2:5},{(int(y1) + int(y2)) // 2:5}  {label[:70]}")
