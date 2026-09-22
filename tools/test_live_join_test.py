from __future__ import annotations

import json
from pathlib import Path
import sys
import tempfile
import unittest

TOOLS = Path(__file__).resolve().parent
if str(TOOLS) not in sys.path:
    sys.path.insert(0, str(TOOLS))

import ci_policy
import live_join_test as live


class CiPolicyTest(unittest.TestCase):
    def test_documentation_only_skips_live_boots(self):
        self.assertFalse(ci_policy.needs_live([
            "README.md",
            "docs/testing.md",
            "changelogs/v0.0.13.md",
            "NOTICE",
        ]))

    def test_runtime_build_workflow_and_unknown_paths_require_live_boots(self):
        for path in (
            "build.gradle",
            "settings.gradle",
            "gradle.properties",
            ".github/workflows/build-and-release.yml",
            "tools/live_join_test.py",
            "common/src/main/resources/test.txt",
            "unknown.file",
        ):
            with self.subTest(path=path):
                self.assertTrue(ci_policy.needs_live([path]))

    def test_empty_diff_fails_closed(self):
        self.assertTrue(ci_policy.needs_live([]))


class LiveHarnessContractTest(unittest.TestCase):
    def test_matrix_contains_every_supported_loader_target_once(self):
        matrix = json.loads(live.matrix_json())
        targets = [entry["target"] for entry in matrix["include"]]
        self.assertEqual(list(live.TARGETS), targets)
        self.assertEqual(len(targets), len(set(targets)))

    def test_exact_head_receipts_require_every_target(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            head = "abc123"
            for target in live.TARGETS:
                (directory / f"{target}.pass").write_text(head + "\n", encoding="utf-8")
            live.verify_receipts(directory, head)

    def test_missing_or_stale_receipt_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            head = "abc123"
            for target in live.TARGETS:
                (directory / f"{target}.pass").write_text(head + "\n", encoding="utf-8")
            (directory / f"{live.TARGETS[0]}.pass").write_text("stale\n", encoding="utf-8")
            with self.assertRaises(RuntimeError):
                live.verify_receipts(directory, head)

            (directory / f"{live.TARGETS[0]}.pass").write_text(head + "\n", encoding="utf-8")
            (directory / f"{live.TARGETS[-1]}.pass").unlink()
            with self.assertRaises(RuntimeError):
                live.verify_receipts(directory, head)

    def test_critical_runtime_signatures_are_fail_closed(self):
        for line in (
            "Critical injection failure",
            "MixinApplyError: broken",
            "Failed to start the minecraft server",
            "Exception in thread main",
        ):
            with self.subTest(line=line):
                self.assertTrue(live.is_fatal_line(line))
        self.assertFalse(live.is_fatal_line("Economy data loaded for dimension minecraft:overworld"))

    def test_only_asset_download_failures_are_retryable_client_setup_failures(self):
        self.assertTrue(live.is_transient_client_setup_failure([
            "> Task :forge-1.20.1:downloadAssets FAILED\n",
            "net.fabricmc.loom.util.download.DownloadException: Failed to download\n",
        ]))
        self.assertFalse(live.is_transient_client_setup_failure([
            "MixinApplyError: broken\n",
            "ECONOMY_LIVE_JOIN_TEST_PASS\n",
        ]))


if __name__ == "__main__":
    unittest.main()
