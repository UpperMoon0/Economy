"""Source contract checks for the native Economy Jenkins candidate.

These guard source-only invariants; they are not a substitute for numbered
native Jenkins builds, status publication, or cutover evidence.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
FILE = ROOT / "Jenkinsfile"
TARGETS = (
    "fabric-1.20.1",
    "forge-1.20.1",
    "fabric-1.21.1",
    "neoforge-1.21.1",
    "neoforge-26.1.2",
)


class JenkinsCandidateContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = FILE.read_text(encoding="utf-8")

    def test_bounded_general_linux_worker_only(self):
        self.assertIn("ci-role-general && ci-os-linux", self.source)
        self.assertNotIn("ci-os-windows", self.source)
        self.assertIn("disableConcurrentBuilds()", self.source)

    def test_exact_sha_and_untrusted_pr_rejected(self):
        self.assertIn("git rev-parse --verify HEAD", self.source)
        self.assertIn("requested != head", self.source)
        self.assertIn("if (env.CHANGE_ID)", self.source)
        self.assertIn("env.BRANCH_NAME != 'main'", self.source)

    def test_original_read_only_tests_and_five_joins(self):
        self.assertIn("test_openui_dependency.py", self.source)
        self.assertIn("unittest discover -s tools -p 'test_*.py'", self.source)
        self.assertIn("python3 -m py_compile", self.source)
        self.assertIn("runGameTestServer", self.source)
        self.assertIn("tools/live_join_test.py --target", self.source)
        for target in TARGETS:
            with self.subTest(target=target):
                self.assertIn(target, self.source)

    def test_pinned_dependency_and_isolated_maven_home(self):
        self.assertIn("1c44920fcc40e486ec5edf1cf82b98c1b633d5bd", self.source)
        self.assertIn("-Duser.home=$WORKSPACE/.ci-home", self.source)
        self.assertIn("openui-maven-sha256.txt", self.source)
        self.assertIn("sort -V", self.source)

    def test_no_publishing_token_or_release_side_effect(self):
        self.assertNotIn("withCredentials(", self.source)
        self.assertNotIn("secrets.", self.source)
        self.assertNotIn("CURSEFORGE_API_TOKEN", self.source)
        self.assertNotIn("api.github.com/repos/", self.source)
        self.assertNotIn("curl --request POST", self.source)

    def test_evidence_and_failure_logs_archived(self):
        self.assertIn("fingerprint: true", self.source)
        self.assertIn("ci-evidence/**", self.source)
        self.assertIn("run/live-join/client/logs", self.source)
        self.assertIn("run/live-join/server/logs", self.source)
        self.assertIn("allowEmptyResults: true", self.source)


if __name__ == "__main__":
    unittest.main()
