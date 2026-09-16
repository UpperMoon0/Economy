#!/usr/bin/env python3
"""Launch real Economy dedicated servers and auto-joining graphical clients.

The live layer is intentionally narrow: JVM tests own domain logic and the
canonical Forge GameTest owns world/storage behavior. This harness verifies the
remaining failure surface on every supported loader/version: production
bootstrap, Economy server lifecycle, networking-compatible client startup and a
real multiplayer join.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import json
import os
from pathlib import Path
import queue
import shutil
import signal
import subprocess
import sys
import threading
import time


PASS_MARKER = "ECONOMY_LIVE_JOIN_TEST_PASS"
ECONOMY_SERVER_READY_MARKER = "Economy data loaded for dimension"
SERVER_READY_MARKERS = ("Done (", 'For help, type "help"')
SERVER_BOOT_MARKERS = (
    "Starting minecraft server version",
    "Starting Minecraft server",
    "Starting minecraft server",
)
CRITICAL_RUNTIME_MARKERS = (
    "Critical injection failure",
    "InjectionError",
    "InvalidInjectionException",
    "MixinApplyError",
    "MixinTransformerError",
    "Failed to start the minecraft server",
    "Encountered an unexpected exception",
    "This crash report has been saved to:",
    "Exception in thread",
    "Failed to encode packet",
    "Failed to decode packet",
)
TARGETS = (
    "fabric-1.20.1",
    "forge-1.20.1",
    "fabric-1.21.1",
    "neoforge-1.21.1",
    "neoforge-26.1.2",
)


def is_fatal_line(line: str) -> bool:
    return any(marker in line for marker in CRITICAL_RUNTIME_MARKERS)


def matrix_json() -> str:
    return json.dumps({"include": [{"target": target} for target in TARGETS]}, separators=(",", ":"))


def verify_receipts(directory: Path, head: str) -> None:
    expected = {f"{target}.pass" for target in TARGETS}
    actual = {path.name for path in directory.glob("*.pass")}
    if actual != expected:
        raise RuntimeError(
            f"live receipt set differs: missing={sorted(expected - actual)} extra={sorted(actual - expected)}"
        )
    for name in sorted(expected):
        value = (directory / name).read_text(encoding="utf-8").strip()
        if value != head:
            raise RuntimeError(f"stale live receipt {name}: expected {head}, found {value}")


@contextmanager
def checkout_lock(root: Path):
    """Prevent concurrent local live runs from erasing the same run directories."""
    path = root / "build" / "live-join.lock"
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
            raise RuntimeError("another Economy live verification run owns this checkout") from exc
        try:
            yield
        finally:
            if os.name == "nt":
                lock.seek(0)
                msvcrt.locking(lock.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                import fcntl
                fcntl.flock(lock, fcntl.LOCK_UN)


class OutputPump:
    def __init__(self, process: subprocess.Popen[str], prefix: str, log_path: Path) -> None:
        self.process = process
        self.prefix = prefix
        self.log_path = log_path
        self.lines: queue.Queue[str] = queue.Queue()
        self.history: list[str] = []
        self.log_path.parent.mkdir(parents=True, exist_ok=True)
        threading.Thread(target=self._read, daemon=True).start()

    def _read(self) -> None:
        assert self.process.stdout is not None
        with self.log_path.open("a", encoding="utf-8") as log_file:
            for line in self.process.stdout:
                print(f"[{self.prefix}] {line}", end="", flush=True)
                log_file.write(line)
                log_file.flush()
                self.history.append(line)
                self.lines.put(line)

    def _check(self, line: str) -> None:
        if is_fatal_line(line):
            raise RuntimeError(f"{self.prefix}: fatal runtime signature: {line.strip()}")

    def wait_for(self, markers: tuple[str, ...], timeout: int) -> str | None:
        for line in self.history:
            self._check(line)
            if any(marker in line for marker in markers):
                return line
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if self.process.poll() is not None and self.lines.empty():
                return None
            try:
                line = self.lines.get(timeout=min(1.0, max(0.0, deadline - time.monotonic())))
            except queue.Empty:
                continue
            self._check(line)
            if any(marker in line for marker in markers):
                return line
        return None

    def wait_for_server_ready(self, setup_timeout: int, ready_timeout: int) -> str | None:
        setup_deadline = time.monotonic() + setup_timeout
        ready_deadline: float | None = None
        while True:
            deadline = ready_deadline if ready_deadline is not None else setup_deadline
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                return None
            if self.process.poll() is not None and self.lines.empty():
                return None
            try:
                line = self.lines.get(timeout=min(1.0, remaining))
            except queue.Empty:
                continue
            self._check(line)
            if any(marker in line for marker in SERVER_READY_MARKERS):
                return line
            if ready_deadline is None and any(marker in line for marker in SERVER_BOOT_MARKERS):
                ready_deadline = time.monotonic() + ready_timeout

    def contains(self, marker: str) -> bool:
        return any(marker in line for line in self.history)


def gradle(root: Path, task: str) -> list[str]:
    wrapper = root / ("gradlew.bat" if os.name == "nt" else "gradlew")
    return [
        str(wrapper), task, "--no-daemon", "--console=plain", "--max-workers=4", "--build-cache",
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
        subprocess.run(
            ["taskkill", "/PID", str(process.pid), "/T", "/F"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        )
    else:
        try:
            os.killpg(process.pid, signal.SIGTERM)
            process.wait(timeout=10)
        except (ProcessLookupError, subprocess.TimeoutExpired):
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass


def _safe_reset(path: Path, root: Path) -> None:
    resolved = path.resolve()
    root_resolved = root.resolve()
    if resolved == root_resolved or not resolved.is_relative_to(root_resolved):
        raise RuntimeError(f"unsafe live-test cleanup target: {resolved}")
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True, exist_ok=True)


def prepare(root: Path, module: Path) -> tuple[Path, Path]:
    run_root = module / "run" / "live-join"
    _safe_reset(run_root, root)
    server = run_root / "server"
    client = run_root / "client"
    server.mkdir(parents=True, exist_ok=True)
    client.mkdir(parents=True, exist_ok=True)
    (server / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (server / "server.properties").write_text(
        "online-mode=false\nserver-port=25575\nlevel-name=live-join-world\n"
        "motd=Economy live join test\nspawn-protection=0\n"
        "view-distance=3\nsimulation-distance=3\ngenerate-structures=false\n",
        encoding="utf-8",
    )
    (client / "options.txt").write_text(
        "narrator:0\nnarratorHotkey:false\nonboardAccessibility:false\n"
        "skipMultiplayerWarning:true\nrenderDistance:3\nsimulationDistance:3\n"
        "maxFps:60\nenableVsync:false\npauseOnLostFocus:false\n",
        encoding="utf-8",
    )
    return server, client


def validate_log_health(*paths: Path) -> None:
    failures: list[str] = []
    for path in paths:
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for marker in CRITICAL_RUNTIME_MARKERS:
            if marker in text:
                failures.append(f"{path}: {marker}")
    if failures:
        raise RuntimeError("critical runtime errors found after live pass: " + ", ".join(failures))


def tail(path: Path, lines: int = 100) -> str:
    if not path.exists():
        return f"<missing {path}>"
    return "\n".join(path.read_text(encoding="utf-8", errors="replace").splitlines()[-lines:])


def run_target(root: Path, target: str, timeout: int, setup_timeout: int) -> None:
    if target not in TARGETS:
        raise ValueError(f"unsupported target: {target}")
    server_dir, client_dir = prepare(root, root / target)
    server_log = server_dir / "logs" / "process.log"
    client_log = client_dir / "logs" / "process.log"

    # The run task owns compilation for this runtime. CI may have built the target
    # already; Gradle then reuses those outputs rather than performing a duplicate
    # explicit :classes/build invocation here.
    server = start(gradle(root, f":{target}:runLiveJoinTestServer"), root)
    server_output = OutputPump(server, f"{target}/server", server_log)
    client: subprocess.Popen[str] | None = None
    try:
        if server_output.wait_for_server_ready(setup_timeout, timeout) is None:
            raise RuntimeError(f"{target}: server did not become ready")
        if not server_output.contains(ECONOMY_SERVER_READY_MARKER):
            if server_output.wait_for((ECONOMY_SERVER_READY_MARKER,), 20) is None:
                raise RuntimeError(f"{target}: server booted without completing Economy runtime binding")

        client_command = gradle(root, f":{target}:runLiveJoinTestClient")
        if os.name != "nt" and not os.environ.get("DISPLAY"):
            xvfb = shutil.which("xvfb-run")
            if xvfb is None:
                raise RuntimeError("DISPLAY is unset and xvfb-run is not installed")
            client_command = [xvfb, "-a", *client_command]

        client = start(client_command, root)
        client_output = OutputPump(client, f"{target}/client", client_log)
        if client_output.wait_for((PASS_MARKER,), timeout) is None:
            raise RuntimeError(f"{target}: client did not report a successful Economy live join")
        try:
            client_exit = client.wait(timeout=60)
        except subprocess.TimeoutExpired as exc:
            raise RuntimeError(f"{target}: client did not exit after reporting success") from exc
        if client_exit != 0:
            raise RuntimeError(f"{target}: client exited unsuccessfully after joining ({client_exit})")

        validate_log_health(server_log, client_log)
        print(f"{target}: PASS", flush=True)
    except Exception:
        print(f"\n--- {target} server log tail ---", file=sys.stderr)
        print(tail(server_log), file=sys.stderr)
        print(f"\n--- {target} client log tail ---", file=sys.stderr)
        print(tail(client_log), file=sys.stderr)
        raise
    finally:
        if client is not None:
            stop_tree(client)
        stop_tree(server, graceful_server=True)


def write_receipt(directory: Path, target: str, head: str) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    (directory / f"{target}.pass").write_text(head.strip() + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--target", choices=TARGETS)
    parser.add_argument("--all", action="store_true", help="run every supported loader target sequentially")
    parser.add_argument("--matrix", action="store_true", help="print the CI target matrix as JSON")
    parser.add_argument("--verify-receipts", type=Path)
    parser.add_argument("--head", help="exact commit SHA for receipt writing/verification")
    parser.add_argument("--receipt-dir", type=Path)
    parser.add_argument("--timeout", type=int, default=360,
                        help="seconds allowed for Minecraft readiness/client join after game startup")
    parser.add_argument("--setup-timeout", type=int, default=900,
                        help="seconds allowed for Gradle/Loom setup before the server begins booting")
    args = parser.parse_args()

    if args.matrix:
        print(matrix_json())
        return 0
    if args.verify_receipts is not None:
        if not args.head:
            parser.error("--verify-receipts requires --head")
        verify_receipts(args.verify_receipts, args.head)
        return 0
    if args.all and args.target:
        parser.error("use either --all or --target, not both")
    targets = list(TARGETS) if args.all else ([args.target] if args.target else [])
    if not targets:
        parser.error("one of --target, --all, --matrix or --verify-receipts is required")
    if args.receipt_dir is not None and not args.head:
        parser.error("--receipt-dir requires --head")

    root = Path(__file__).resolve().parents[1]
    with checkout_lock(root):
        for target in targets:
            assert target is not None
            run_target(root, target, args.timeout, args.setup_timeout)
            if args.receipt_dir is not None:
                write_receipt(args.receipt_dir, target, args.head)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, subprocess.CalledProcessError, TimeoutError, ValueError) as error:
        print(f"LIVE JOIN TEST FAILED: {error}", file=sys.stderr)
        raise SystemExit(1)
