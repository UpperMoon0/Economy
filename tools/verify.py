#!/usr/bin/env python3
"""Canonical Economy verification harness.

Layers:
  fast  - Python harness tests + JVM/unit/regression tests across versions
  core  - fast layer plus the canonical real-world Forge GameTest
  live  - real dedicated server + graphical client on selected/all loader targets
  full  - core followed by all live loader targets
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import json
import os
from pathlib import Path
import subprocess
import sys
import time

import live_join_test

ROOT = Path(__file__).resolve().parents[1]


@contextmanager
def checkout_lock():
    """Prevent concurrent verification runs from deleting each other's run state."""
    path = ROOT / "build" / "verification.lock"
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a+b") as lock:
        lock.write(b"0")
        lock.flush()
        lock.seek(0)
        try:
            if os.name == "nt":
                import msvcrt
                msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as exc:
            raise RuntimeError("another Economy verification run owns this checkout") from exc
        try:
            yield
        finally:
            if os.name == "nt":
                lock.seek(0)
                msvcrt.locking(lock.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                import fcntl
                fcntl.flock(lock, fcntl.LOCK_UN)


def gradle(*tasks: str) -> list[str]:
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    return [str(wrapper), *tasks, "--no-daemon", "--console=plain", "--build-cache"]


def run_python_harness_tests() -> None:
    subprocess.run(
        [sys.executable, "-m", "unittest", "discover", "-s", "tools", "-p", "test_*.py", "-v"],
        cwd=ROOT,
        check=True,
    )


def run_fast() -> None:
    print("[verify] fast: harness + JVM/version regression suites", flush=True)
    run_python_harness_tests()
    subprocess.run(gradle("testAllVersions"), cwd=ROOT, check=True)


def run_core() -> None:
    print("[verify] core: harness + JVM/version suites + real-world GameTest", flush=True)
    run_python_harness_tests()
    subprocess.run(gradle("coreCheck"), cwd=ROOT, check=True)


def run_live(targets: list[str]) -> None:
    for target in targets:
        print(f"[verify] live: {target}", flush=True)
        live_join_test.run_target(ROOT, target, timeout=360, setup_timeout=900)


def main() -> int:
    parser = argparse.ArgumentParser(description="Run Economy's canonical layered verification suite")
    parser.add_argument("mode", choices=("fast", "core", "live", "full"), nargs="?", default="full")
    parser.add_argument("--target", action="append", choices=live_join_test.TARGETS,
                        help="live target; repeat to select multiple (default: all for live/full)")
    args = parser.parse_args()

    targets = args.target or list(live_join_test.TARGETS)
    started = time.monotonic()
    stages: list[dict[str, object]] = []

    try:
        with checkout_lock():
            actions: list[tuple[str, object]] = []
            if args.mode == "fast":
                actions = [("fast", run_fast)]
            elif args.mode == "core":
                actions = [("core", run_core)]
            elif args.mode == "live":
                actions = [(f"live-{target}", lambda t=target: run_live([t])) for target in targets]
            else:
                actions = [("core", run_core)] + [
                    (f"live-{target}", lambda t=target: run_live([t])) for target in targets
                ]

            for name, action in actions:
                stage_started = time.monotonic()
                stage: dict[str, object] = {"name": name, "status": "failed"}
                stages.append(stage)
                try:
                    action()  # type: ignore[operator]
                    stage["status"] = "passed"
                finally:
                    stage["seconds"] = round(time.monotonic() - stage_started, 2)
    except (OSError, RuntimeError, subprocess.CalledProcessError, TimeoutError) as exc:
        print(f"[verify] FAILED: {exc}", file=sys.stderr)
        result = 1
    else:
        print(f"[verify] PASS ({args.mode}) in {time.monotonic() - started:.1f}s", flush=True)
        result = 0
    finally:
        result_path = ROOT / "build" / f"verification-{args.mode}.json"
        result_path.parent.mkdir(parents=True, exist_ok=True)
        result_path.write_text(
            json.dumps({"stages": stages, "seconds": round(time.monotonic() - started, 2)}, indent=2),
            encoding="utf-8",
        )

    return result


if __name__ == "__main__":
    raise SystemExit(main())
