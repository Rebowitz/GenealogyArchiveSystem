#!/usr/bin/env python3
"""GAS-P0-006: execute the real guard against isolated report fixtures."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
SCRIPT = Path(__file__).with_name("test-summary.py").resolve()
class SummaryGuardTest(unittest.TestCase):
    def run_guard(self, reports):
        with tempfile.TemporaryDirectory() as folder:
            for name, xml in reports.items():
                target = Path(folder) / "target" / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(xml, encoding="utf-8")
            env = dict(os.environ)
            env.pop("GITHUB_STEP_SUMMARY", None)
            return subprocess.run([sys.executable, str(SCRIPT), "guard regression"],
                                  cwd=folder, env=env, stdout=subprocess.PIPE,
                                  stderr=subprocess.PIPE, universal_newlines=True)
    def xml(self, status=""):
        return '<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase name="probe">' + status + '</testcase></testsuite>'
    def test_both_suites_required_and_each_suite_failure_rejected(self):
        unit = "surefire-reports/TEST-probe.xml"
        integration = "failsafe-reports/TEST-probe.xml"
        for reports in ({}, {unit:self.xml()}, {integration:self.xml()},
                        {unit:self.xml(),integration:'<testsuite tests="0"/>'},
                        {unit:self.xml("<failure/>"),integration:self.xml()},
                        {unit:self.xml(),integration:self.xml("<error/>")},
                        {unit:self.xml(),integration:self.xml("<skipped/>")}):
            with self.subTest(reports=reports):
                self.assertNotEqual(self.run_guard(reports).returncode, 0)
        self.assertEqual(self.run_guard({unit:self.xml(),integration:self.xml()}).returncode, 0)
if __name__ == "__main__":
    unittest.main()
