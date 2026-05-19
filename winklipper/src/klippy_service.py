"""
Klippy service - manages Klipper/Kalico host process.
Handles starting, stopping, and monitoring the Klippy Python process.
"""
import os
import sys
import logging
import subprocess
import threading
import time
import shutil
from pathlib import Path
from typing import Optional, Callable

logger = logging.getLogger(__name__)

# Detect the project root (where klipper/kalico repos live)
PROJECT_ROOT = Path(__file__).resolve().parent.parent


class KlippyService:
    """Manages a single Klipper/Kalico host process."""

    def __init__(
        self,
        instance_id: int,
        klipper_path: Path,
        config_file: Path,
        data_dir: Path,
        firmware_source: str = "klipper",
        extra_args: str = "",
        on_state_change: Optional[Callable] = None,
    ):
        self.instance_id = instance_id
        self.klipper_path = Path(klipper_path)
        self.config_file = Path(config_file)
        self.data_dir = Path(data_dir)
        self.firmware_source = firmware_source  # "klipper" or "kalico"
        self.extra_args = extra_args
        self.on_state_change = on_state_change

        # Paths
        self.comms_dir = data_dir / "comms"
        self.log_dir = data_dir / "logs"
        self.comms_dir.mkdir(parents=True, exist_ok=True)
        self.log_dir.mkdir(parents=True, exist_ok=True)

        # API socket path (named pipe on Windows)
        self.api_socket = self.comms_dir / f"klippy_{instance_id}.sock"

        # Log file
        self.log_file = self.log_dir / "klippy.log"

        # Process state
        self._process: Optional[subprocess.Popen] = None
        self._running = False
        self._state = "stopped"  # stopped, starting, running, error, restarting
        self._output_thread: Optional[threading.Thread] = None
        self._restart_count = 0
        self._max_restarts = 3

    @property
    def state(self) -> str:
        return self._state

    def _set_state(self, state: str):
        self._state = state
        logger.info(f"Klippy [{self.instance_id}] state: {state}")
        if self.on_state_change:
            try:
                self.on_state_change(self.instance_id, "klippy", state)
            except Exception as e:
                logger.error(f"State change callback error: {e}")

    def _get_python_path(self) -> str:
        """Get the Python executable path."""
        return sys.executable

    def _get_klippy_py(self) -> Path:
        """Get the path to klippy.py."""
        return self.klipper_path / "klippy" / "klippy.py"

    def _build_command(self) -> list:
        """Build the Klippy command line."""
        python = self._get_python_path()
        klippy_py = self._get_klippy_py()

        if not klippy_py.exists():
            raise FileNotFoundError(f"klippy.py not found at {klippy_py}")

        cmd = [
            python,
            str(klippy_py),
            str(self.config_file),
            "-I", str(self.api_socket),  # API socket instead of PTY
            "-a", str(self.api_socket),  # API server socket
            "-l", str(self.log_file),
        ]

        # Add extra args
        if self.extra_args.strip():
            cmd.extend(self.extra_args.strip().split())

        return cmd

    def _ensure_chelper(self):
        """Ensure the C helper library is compiled for Windows."""
        chelper_dir = self.klipper_path / "klippy" / "chelper"
        if not chelper_dir.exists():
            logger.warning(f"Chelper directory not found at {chelper_dir}")
            return

        # Check for pre-compiled DLL
        dll_path = chelper_dir / "c_helper.dll"
        so_path = chelper_dir / "c_helper.so"

        if dll_path.exists():
            logger.debug(f"Found pre-compiled c_helper.dll")
            return

        if so_path.exists():
            logger.debug("Found Linux .so file, Windows .dll needed")
            return

        # Try to compile on first run
        logger.info("Compiling c_helper.dll for Windows...")
        self._compile_chelper(chelper_dir, dll_path)

    def _compile_chelper(self, chelper_dir: Path, dll_path: Path):
        """Compile the C helper library for Windows."""
        source_files = [
            "pyhelper.c", "serialqueue.c", "stepcompress.c", "steppersync.c",
            "itersolve.c", "trapq.c", "pollreactor.c", "msgblock.c", "trdispatch.c",
            "kin_cartesian.c", "kin_corexy.c", "kin_corexz.c", "kin_delta.c",
            "kin_deltesian.c", "kin_polar.c", "kin_rotary_delta.c", "kin_winch.c",
            "kin_extruder.c", "kin_shaper.c", "kin_idex.c", "kin_generic.c",
        ]

        # Find gcc (MinGW-w64)
        gcc = shutil.which("gcc")
        if not gcc:
            # Try common MinGW paths
            mingw_paths = [
                Path("C:/msys64/mingw64/bin/gcc.exe"),
                Path("C:/mingw64/bin/gcc.exe"),
                Path("C:/Program Files/mingw-w64/bin/gcc.exe"),
            ]
            for p in mingw_paths:
                if p.exists():
                    gcc = str(p)
                    break

        if not gcc:
            logger.error("gcc not found. Install MinGW-w64 to compile c_helper.dll")
            logger.error("Download from: https://www.mingw-w64.org/downloads/")
            return

        sources = [str(chelper_dir / f) for f in source_files if (chelper_dir / f).exists()]
        if not sources:
            logger.error("No C source files found for compilation")
            return

        cmd = [
            gcc,
            "-Wall", "-g", "-O2", "-shared", "-fPIC",
            "-o", str(dll_path),
        ] + sources

        # Add SSE flags if supported
        cmd.insert(2, "-mfpmath=sse")
        cmd.insert(2, "-msse2")

        try:
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
            if result.returncode == 0:
                logger.info(f"Successfully compiled c_helper.dll")
            else:
                logger.error(f"Failed to compile c_helper.dll: {result.stderr}")
        except Exception as e:
            logger.error(f"Compilation error: {e}")

    def start(self):
        """Start the Klippy process."""
        if self._running:
            logger.warning("Klippy is already running")
            return

        self._ensure_chelper()
        self._set_state("starting")
        self._restart_count = 0

        try:
            cmd = self._build_command()
            logger.info(f"Starting Klippy: {' '.join(cmd)}")

            # Set up environment
            env = os.environ.copy()
            env["PYTHONUNBUFFERED"] = "1"

            # Add klipper path to Python path
            klippy_dir = str(self.klipper_path)
            if "PYTHONPATH" in env:
                env["PYTHONPATH"] = f"{klippy_dir};{env['PYTHONPATH']}"
            else:
                env["PYTHONPATH"] = klippy_dir

            self._process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                stdin=subprocess.PIPE,
                env=env,
                cwd=str(self.klipper_path),
                creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
            )

            self._running = True
            self._output_thread = threading.Thread(
                target=self._read_output, daemon=True, name=f"Klippy-{self.instance_id}"
            )
            self._output_thread.start()

            # Wait briefly to check if process stays alive
            time.sleep(1)
            if self._process.poll() is not None:
                self._set_state("error")
                logger.error(f"Klippy exited immediately with code {self._process.returncode}")
            else:
                self._set_state("running")
                logger.info(f"Klippy started (PID: {self._process.pid})")

        except Exception as e:
            self._set_state("error")
            logger.error(f"Failed to start Klippy: {e}")

    def stop(self):
        """Stop the Klippy process."""
        if not self._running:
            return

        self._running = False
        self._set_state("stopped")

        if self._process:
            try:
                # Send graceful shutdown via stdin
                if self._process.stdin:
                    try:
                        self._process.stdin.write(b"EXIT\n")
                        self._process.stdin.flush()
                    except Exception:
                        pass

                # Wait for graceful exit
                try:
                    self._process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    # Force kill
                    self._process.kill()
                    self._process.wait(timeout=5)

                logger.info(f"Klippy stopped (PID: {self._process.pid})")
            except Exception as e:
                logger.error(f"Error stopping Klippy: {e}")
            finally:
                self._process = None

    def restart(self):
        """Restart the Klippy process."""
        logger.info(f"Restarting Klippy [{self.instance_id}]...")
        self.stop()
        time.sleep(0.5)
        self.start()

    def _read_output(self):
        """Read and log process output."""
        if not self._process or not self._process.stdout:
            return

        try:
            for line in iter(self._process.stdout.readline, b""):
                if not self._running:
                    break
                line_str = line.decode("utf-8", errors="replace").rstrip()
                if line_str:
                    logger.debug(f"Klippy [{self.instance_id}]: {line_str}")

                    # Check for critical errors
                    if "error" in line_str.lower() or "exception" in line_str.lower():
                        logger.warning(f"Klippy error detected: {line_str}")

        except Exception as e:
            logger.error(f"Error reading Klippy output: {e}")

    def _monitor_process(self):
        """Monitor the process and handle crashes."""
        if not self._process:
            return

        try:
            exit_code = self._process.wait()
            self._running = False

            if self._state == "stopped":
                return  # Intentional stop

            logger.warning(f"Klippy exited with code {exit_code}")

            # Auto-restart on crash
            if self._restart_count < self._max_restarts:
                self._restart_count += 1
                self._set_state("restarting")
                time.sleep(2)
                if not self._running:
                    self.start()
            else:
                self._set_state("error")
                logger.error(f"Klippy crashed {self._max_restarts} times, not restarting")

        except Exception as e:
            logger.error(f"Process monitor error: {e}")
            self._set_state("error")

    def is_running(self) -> bool:
        """Check if the process is still running."""
        if self._process is None:
            return False
        return self._process.poll() is None

    def get_log_tail(self, lines: int = 50) -> str:
        """Get the last N lines of the Klippy log."""
        if not self.log_file.exists():
            return ""
        try:
            with open(self.log_file, "r", encoding="utf-8", errors="replace") as f:
                all_lines = f.readlines()
                return "".join(all_lines[-lines:])
        except Exception as e:
            return f"Error reading log: {e}"
