#!/usr/bin/env python3
"""CI only: summarises Surefire/Failsafe XML reports.

Writes a Markdown table to $GITHUB_STEP_SUMMARY and emits one GitHub error annotation per failed
or errored test (so failures are visible on the run page and through the checks API). Skipped
tests are listed explicitly: Phase 0 expects none.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

label = sys.argv[1] if len(sys.argv) > 1 else "tests"
totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
problems = []
skipped = []
suite_counts = {"unit": 0, "integration": 0}

for path in sorted(glob.glob("target/surefire-reports/TEST-*.xml") + glob.glob("target/failsafe-reports/TEST-*.xml")):
    suite_kind = "integration" if "failsafe-reports" in path else "unit"
    root = ET.parse(path).getroot()
    suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
    for suite in suites:
        suite_counts[suite_kind] += len(suite.findall("testcase"))
        for key in totals:
            totals[key] += int(suite.get(key, 0))
        for case in suite.findall("testcase"):
            name = f"{case.get('classname')}.{case.get('name')}"
            for kind in ("failure", "error"):
                el = case.find(kind)
                if el is not None:
                    detail = (el.get("message") or "") + "\n" + (el.text or "")
                    problems.append((name, kind, detail.strip()))
            if case.find("skipped") is not None:
                skipped.append(name)

lines = [f"### {label}", "", "| tests | failures | errors | skipped |", "|---|---|---|---|",
         f"| {totals['tests']} | {totals['failures']} | {totals['errors']} | {totals['skipped']} |", ""]
for name, kind, detail in problems:
    lines.append(f"- **{kind}** `{name}`")
for name in skipped:
    lines.append(f"- skipped `{name}`")

summary = os.environ.get("GITHUB_STEP_SUMMARY")
if summary:
    with open(summary, "a", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
print("\n".join(lines))

def escape(s: str) -> str:
    return s.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")

for name, kind, detail in problems:
    print(f"::error title={escape(label + ' ' + kind + ': ' + name)}::{escape(detail[:3000])}")
print(f"::notice title={escape(label + ' test totals')}::tests={totals['tests']} failures={totals['failures']} "
      f"errors={totals['errors']} skipped={totals['skipped']}")
missing_suites = [name for name, count in suite_counts.items() if count == 0]
if missing_suites:
    print(f"::error title={escape(label)}::Missing required test evidence: {', '.join(missing_suites)}")
    sys.exit(1)
if totals["tests"] == 0:
    print(f"::error title={escape(label)}::No test reports found - the test suite did not run")
    sys.exit(1)
if problems or skipped or totals["failures"] or totals["errors"] or totals["skipped"]:
    sys.exit(1)
