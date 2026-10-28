#!/usr/bin/env python3
"""CI only: when a step fails, re-emit the important part of its captured log as GitHub error
annotations, so the failure is readable from the run page and the checks API without downloading
full logs. Usage: annotate-log.py <label> <logfile>..."""
import os
import re
import sys

label = sys.argv[1]
MAX_ANNOTATIONS = 8
CHUNK = 3500

def escape(s: str) -> str:
    return s.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")

ansi = re.compile(r"\x1b\[[0-9;]*m")
emitted = 0
for path in sys.argv[2:]:
    if not os.path.exists(path) or emitted >= MAX_ANNOTATIONS:
        continue
    with open(path, encoding="utf-8", errors="replace") as f:
        lines = [ansi.sub("", l.rstrip("\n")) for l in f]
    interesting = [l for l in lines if re.search(r"\[ERROR\]|::error|FAIL|Exception|error:|Error:|expected|Caused by", l)]
    body = "\n".join((interesting or lines)[-150:])
    tail = "\n".join(lines[-40:])
    text = body + "\n----- last lines -----\n" + tail
    for i in range(0, len(text), CHUNK):
        if emitted >= MAX_ANNOTATIONS:
            break
        print(f"::error title={escape(f'{label} {os.path.basename(path)} part {i // CHUNK + 1}')}::{escape(text[i:i + CHUNK])}")
        emitted += 1
