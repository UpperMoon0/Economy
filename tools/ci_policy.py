"""Decide whether a change needs real Minecraft client/server verification.

Only documentation-only changes may skip the expensive runtime layer. Unknown
paths fail closed and require it.
"""
from __future__ import annotations

import argparse
from pathlib import PurePosixPath
import subprocess


def needs_live(paths: list[str]) -> bool:
    def documentation(path: str) -> bool:
        p = PurePosixPath(path)
        return not path.startswith(".github/") and (
            (p.suffix.lower() in {".md", ".txt"}
             and (path.startswith("docs/") or path.startswith("changelogs/") or "/" not in path))
            or path in {"LICENSE", "NOTICE"}
        )

    # An empty/unknown diff is not grounds for weakening verification.
    return not paths or any(not documentation(path) for path in paths)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("base")
    parser.add_argument("head")
    args = parser.parse_args()
    changed = subprocess.check_output(
        ["git", "diff", "--name-only", "-z", f"{args.base}...{args.head}"],
        text=True,
    ).split("\0")
    print("true" if needs_live([path for path in changed if path]) else "false")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
