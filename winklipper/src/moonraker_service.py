"""
Moonraker service - manages the Moonraker API server process.
Handles starting, stopping, and monitoring Moonraker.
"""
import os
import sys
import logging
import subprocess
import threading
import time
from pathlib import Path
from typing import Optional, Callable

logger = logging.getLogger(__name__)


class MoonrakerService:
    """Manages a single Moonraker API server process."""

    def __init__(
        self,
        instance_id: int,
        moonraker_path: Path,
        config_file: Path,
        data_dir: Path,
        port: int = 7125,
        klippy_uds: Optional[Path] = None,
        extra_args: str = "",
        on_state_change: Optional[Callable] = None,
    ):
        self.instance_id = instance_id
        self.moonraker_path = Path(moonraker_path)
        self.config_file = Path(config_file)
        self.data_dir = Path(data_dir)
        self.port = port
        self.klippy_uds = klippy_uds
        self.extra_args = extra_args
        self.on_state_change = on_state_change

        # Paths
        self.log_dir = data_dir / "logs"
        self.log_dir.mkdir(parents=True, exist_ok=True)
        self.log_file = self.log_dir / "moonraker.log"

        # Process state
        self._process: Optional[subprocess.Popen] = None
        self._running = False
        self._state = "stopped"
        self._output_thread: Optional[threading.Thread] = None

    @property
    def state(self) -> str:
        return self._state

    def _set_state(self, state: str):
        self._state = state
        logger.info(f"Moonraker [{self.instance_id}] state: {state}")
        if self.on_state_change:
            try:
                self.on_state_change(self.instance_id, "moonraker", state)
            except Exception as e:
                logger.error(f"State change callback error: {e}")

    def _build_command(self) -> list:
        """Build the Moonraker command line."""
        python = sys.executable

        # Moonraker entry point
        moonraker_py = self.moonraker_path / "moonraker" / "moonraker.py"
        if not moonraker_py.exists():
            # Try alternative entry point
            moonraker_py = self.moonraker_path / "moonraker" / "__main__.py"

        if not moonraker_py.exists():
            raise FileNotFoundError(f"Moonraker entry point not found at {moonraker_py}")

        cmd = [
            python,
            str(moonraker_py),
            "-c", str(self.config_file),
        ]

        if self.extra_args.strip():
            cmd.extend(self.extra_args.strip().split())

        return cmd

    def _generate_config(self) -> Path:
        """Generate a Moonraker config file if it doesn't exist."""
        if self.config_file.exists():
            return self.config_file

        klippy_uds = self.klippy_uds or (self.data_dir / "comms" / f"klippy_{self.instance_id}.sock")

        config_content = f"""[server]
host = 127.0.0.1
port = {self.port}
enable_debug_logging = False

[authorization]
trusted_clients =
    127.0.0.0/8
    ::1/128

[file_manager]
config_path = {self.data_dir / "config"}
path = {self.data_dir / "gcodes"}

[klippy_uds]
klippy_uds_address = {klippy_uds}

[octoprint_compat]

[history]

[update_manager]
enable_auto_refresh = False
"""
        self.config_file.parent.mkdir(parents=True, exist_ok=True)
        with open(self.config_file, "w", encoding="utf-8") as f:
            f.write(config_content)

        logger.info(f"Generated Moonraker config: {self.config_file}")
        return self.config_file

    def start(self):
        """Start the Moonraker process."""
        if self._running:
            logger.warning("Moonraker is already running")
            return

        self._set_state("starting")

        try:
            self._generate_config()
            cmd = self._build_command()
            logger.info(f"Starting Moonraker: {' '.join(cmd)}")

            # Set up environment
            env = os.environ.copy()
            env["PYTHONUNBUFFERED"] = "1"

            # Add moonraker path to Python path
            moonraker_dir = str(self.moonraker_path)
            if "PYTHONPATH" in env:
                env["PYTHONPATH"] = f"{moonraker_dir};{env['PYTHONPATH']}"
            else:
                env["PYTHONPATH"] = moonraker_dir

            self._process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                stdin=subprocess.PIPE,
                env=env,
                cwd=str(self.moonraker_path),
                creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
            )

            self._running = True
            self._output_thread = threading.Thread(
                target=self._read_output, daemon=True, name=f"Moonraker-{self.instance_id}"
            )
            self._output_thread.start()

            # Wait briefly to check if process stays alive
            time.sleep(1)
            if self._process.poll() is not None:
                self._set_state("error")
                logger.error(f"Moonraker exited immediately with code {self._process.returncode}")
            else:
                self._set_state("running")
                logger.info(f"Moonraker started on port {self.port} (PID: {self._process.pid})")

        except Exception as e:
            self._set_state("error")
            logger.error(f"Failed to start Moonraker: {e}")

    def stop(self):
        """Stop the Moonraker process."""
        if not self._running:
            return

        self._running = False
        self._set_state("stopped")

        if self._process:
            try:
                self._process.terminate()
                try:
                    self._process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    self._process.kill()
                    self._process.wait(timeout=5)
                logger.info(f"Moonraker stopped (PID: {self._process.pid})")
            except Exception as e:
                logger.error(f"Error stopping Moonraker: {e}")
            finally:
                self._process = None

    def restart(self):
        """Restart the Moonraker process."""
        logger.info(f"Restarting Moonraker [{self.instance_id}]...")
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
                    logger.debug(f"Moonraker [{self.instance_id}]: {line_str}")
        except Exception as e:
            logger.error(f"Error reading Moonraker output: {e}")

    def is_running(self) -> bool:
        """Check if the process is still running."""
        if self._process is None:
            return False
        return self._process.poll() is None

    def get_url(self) -> str:
        """Get the Moonraker API URL."""
        return f"http://127.0.0.1:{self.port}"
