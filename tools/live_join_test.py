#!/usr/bin/env python3
"""Launch a dedicated server and a real auto-joining client for Economy."""

from __future__ import annotations

import argparse
import os
import queue
import shutil
import signal
import subprocess
import sys
import threading
import time
from pathlib import Path


PASS_MARKER = "ECONOMY_LIVE_JOIN_TEST_PASS"
SERVER_READY_MARKERS = ("Done (", 'For help, type "help"')
TARGETS = (
    "fabric-1.20.1",
    "forge-1.20.1",
    "fabric-1.21.1",
    "neoforge-1.21.1",
    "neoforge-26.1.2",
)


class OutputPump:
    def __init__(self, process: subprocess.Popen[str], prefix: str) -> None:
        self.process = process
        self.prefix = prefix
        self.lines: queue.Queue[str] = queue.Queue()
        threading.Thread(target=self._read, daemon=True).start()

    def _read(self) -> None:
        assert self.process.stdout is not None
        for line in self.process.stdout:
            print(f"[{self.prefix}] {line}", end="", flush=True)
            self.lines.put(line)

    def wait_for(self, markers: tuple[str, ...], timeout: int) -> str | None:
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if self.process.poll() is not None and self.lines.empty():
                return None
            try:
                line = self.lines.get(timeout=min(1.0, deadline - time.monotonic()))
            except queue.Empty:
                continue
            if any(marker in line for marker in markers):
                return line
        return None


def gradle(root: Path, task: str) -> list[str]:
    wrapper = root / ("gradlew.bat" if os.name == "nt" else "gradlew")
    return [
        str(wrapper), task, "--no-daemon", "--console=plain", "--max-workers=4",
        "-Dorg.gradle.jvmargs=-Xmx2048m",
    ]


def start(command: list[str], root: Path) -> subprocess.Popen[str]:
    options: dict[str, object] = {
        "cwd": root,
        "stdin": subprocess.PIPE,
        "stdout": subprocess.PIPE,
        "stderr": subprocess.STDOUT,
        "text": True,
        "bufsize": 1,
    }
    if os.name == "nt":
        options["creationflags"] = subprocess.CREATE_NEW_PROCESS_GROUP
    else:
        options["start_new_session"] = True
    return subprocess.Popen(command, **options)  # type: ignore[arg-type]


def stop_tree(process: subprocess.Popen[str], graceful_server: bool = False) -> None:
    if process.poll() is not None:
        return
    if graceful_server and process.stdin is not None:
        try:
            process.stdin.write("stop\n")
            process.stdin.flush()
            process.wait(timeout=15)
            return
        except (BrokenPipeError, subprocess.TimeoutExpired):
            pass
    if os.name == "nt":
        subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
    else:
        try:
            os.killpg(process.pid, signal.SIGTERM)
            process.wait(timeout=10)
        except (ProcessLookupError, subprocess.TimeoutExpired):
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass


def prepare(module: Path) -> None:
    server = module / "run" / "live-join" / "server"
    client = module / "run" / "live-join" / "client"
    server.mkdir(parents=True, exist_ok=True)
    client.mkdir(parents=True, exist_ok=True)
    (server / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (server / "server.properties").write_text(
        "online-mode=false\nserver-port=25575\nlevel-name=live-join-world\n"
        "motd=Economy live join test\nspawn-protection=0\n",
        encoding="utf-8",
    )
    (client / "options.txt").write_text(
        "narrator:0\nnarratorHotkey:false\nonboardAccessibility:false\n"
        "skipMultiplayerWarning:true\n",
        encoding="utf-8",
    )


def run_target(root: Path, target: str, timeout: int) -> None:
    prepare(root / target)
    subprocess.run(gradle(root, f":{target}:classes"), cwd=root, check=True)
    server = start(gradle(root, f":{target}:runLiveJoinTestServer"), root)
    server_output = OutputPump(server, f"{target}/server")
    client: subprocess.Popen[str] | None = None
    try:
        if server_output.wait_for(SERVER_READY_MARKERS, timeout) is None:
            raise RuntimeError(f"{target}: server did not become ready")
        client_command = gradle(root, f":{target}:runLiveJoinTestClient")
        if os.name != "nt" and not os.environ.get("DISPLAY"):
            xvfb = shutil.which("xvfb-run")
            if xvfb is None:
                raise RuntimeError("DISPLAY is unset and xvfb-run is not installed")
            client_command = [xvfb, "-a", *client_command]
        client = start(client_command, root)
        client_output = OutputPump(client, f"{target}/client")
        if client_output.wait_for((PASS_MARKER,), timeout) is None:
            raise RuntimeError(f"{target}: client did not report a successful live join")
        if client.wait(timeout=60) != 0:
            raise RuntimeError(f"{target}: client exited unsuccessfully after joining")
        print(f"{target}: PASS", flush=True)
    finally:
        if client is not None:
            stop_tree(client)
        stop_tree(server, graceful_server=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--target", choices=TARGETS, required=True)
    parser.add_argument("--timeout", type=int, default=360)
    args = parser.parse_args()
    run_target(Path(__file__).resolve().parents[1], args.target, args.timeout)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, subprocess.CalledProcessError) as error:
        print(f"LIVE JOIN TEST FAILED: {error}", file=sys.stderr)
        raise SystemExit(1)
