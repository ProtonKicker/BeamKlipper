"""
Windows PTY replacement using named pipes.
Klipper expects a Unix PTY at /tmp/printer for frontends to connect.
On Windows, we create a named pipe that serves the same purpose.
"""
import os
import logging
import threading
import time
from typing import Optional, Tuple

logger = logging.getLogger(__name__)

# Check if we're on Windows
IS_WINDOWS = os.name == "nt"

if IS_WINDOWS:
    import win32pipe
    import win32file
    import pywintypes


class WindowsNamedPipe:
    """
    Creates a Windows named pipe that acts as a virtual serial port.
    Klipper can write to one end, and Moonraker/frontends connect to the other.

    Pipe path: \\.\pipe\klippy_<instance_id>
    """

    def __init__(self, pipe_name: str, instance_id: int = 0):
        self.pipe_name = pipe_name
        self.instance_id = instance_id
        self.full_path = f"\\\\.\\pipe\\klippy_{instance_id}_{pipe_name}"
        self._pipe_handle = None
        self._running = False
        self._client_connected = False
        self._read_buffer = bytearray()
        self._lock = threading.Lock()
        self._read_thread: Optional[threading.Thread] = None
        self._on_data_callback = None

    def create(self) -> str:
        """Create the named pipe and return its path."""
        try:
            self._pipe_handle = win32pipe.CreateNamedPipe(
                self.full_path,
                win32pipe.PIPE_ACCESS_DUPLEX,
                win32pipe.PIPE_TYPE_BYTE | win32pipe.PIPE_READMODE_BYTE | win32pipe.PIPE_WAIT,
                1,  # max instances
                65536,  # out buffer
                65536,  # in buffer
                0,  # default timeout
                None,  # security attrs
            )
            logger.info(f"Created named pipe: {self.full_path}")
            return self.full_path
        except Exception as e:
            logger.error(f"Failed to create named pipe: {e}")
            raise

    def wait_for_client(self, timeout: float = 30.0) -> bool:
        """Wait for a client to connect to the pipe."""
        try:
            logger.info(f"Waiting for client on {self.full_path}...")
            win32pipe.ConnectNamedPipe(self._pipe_handle, None)
            self._client_connected = True
            logger.info(f"Client connected to {self.full_path}")
            return True
        except pywintypes.error as e:
            # ERROR_PIPE_CONNECTED (535) means client already connected
            if e.args[0] == 535:
                self._client_connected = True
                logger.info(f"Client already connected to {self.full_path}")
                return True
            logger.error(f"Pipe connection error: {e}")
            return False

    def start_reading(self, on_data_callback):
        """Start async reading from the pipe."""
        self._on_data_callback = on_data_callback
        self._running = True
        self._read_thread = threading.Thread(target=self._read_loop, daemon=True, name="PipeReader")
        self._read_thread.start()

    def _read_loop(self):
        """Read loop for the pipe."""
        while self._running and self._client_connected:
            try:
                result, data = win32file.ReadFile(self._pipe_handle, 4096)
                if result == 0 and data:
                    if self._on_data_callback:
                        self._on_data_callback(data)
            except Exception as e:
                if self._running:
                    logger.debug(f"Pipe read error: {e}")
                time.sleep(0.01)

    def write(self, data: bytes):
        """Write data to the pipe."""
        if self._pipe_handle and self._client_connected:
            try:
                win32file.WriteFile(self._pipe_handle, data)
            except Exception as e:
                logger.error(f"Pipe write error: {e}")

    def close(self):
        """Close the pipe."""
        self._running = False
        if self._pipe_handle:
            try:
                win32file.CancelIo(self._pipe_handle)
            except Exception:
                pass
            try:
                win32pipe.DisconnectNamedPipe(self._pipe_handle)
            except Exception:
                pass
            try:
                self._pipe_handle.Close()
            except Exception:
                pass
            self._pipe_handle = None
        logger.info(f"Closed named pipe: {self.full_path}")


class WindowsVirtualSerial:
    """
    Higher-level virtual serial port for Klipper on Windows.
    Uses named pipes internally but provides a serial-like interface.
    """

    def __init__(self, instance_id: int = 0):
        self.instance_id = instance_id
        self.pipe = WindowsNamedPipe("printer", instance_id)
        self._pipe_path = None

    def setup(self) -> str:
        """
        Set up the virtual serial port.
        Returns the pipe path that Klipper should use.
        """
        self._pipe_path = self.pipe.create()
        return self._pipe_path

    def get_klippy_arg(self) -> str:
        """Get the argument to pass to Klipper for the virtual serial port."""
        return self._pipe_path

    def close(self):
        """Clean up the virtual serial port."""
        self.pipe.close()


def create_windows_pty_fallback(input_path: str) -> int:
    """
    Fallback for Klipper's create_pty on Windows.
    Since Klipper's util.create_pty() uses Unix PTYs, we need an alternative.

    This function is called by our patched version of klippy.py.
    Returns a file descriptor that Klipper can use.

    For Windows, we use a simpler approach:
    - Create a temp file that both Klipper and Moonraker can access
    - Or use TCP sockets (Moonraker supports this natively)

    Actually, the cleanest approach is to skip the PTY entirely and use
    Klipper's -a (API server socket) flag instead. Moonraker connects
    via the API socket, not the PTY.
    """
    logger.warning("Windows PTY fallback called - consider using -a API socket instead")
    return -1
