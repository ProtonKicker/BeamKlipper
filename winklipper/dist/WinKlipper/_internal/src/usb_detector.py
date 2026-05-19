"""
USB printer detection and serial port management for Windows.
Uses pyserial to enumerate COM ports and identify 3D printer MCUs.
"""
import logging
import threading
import time
from typing import List, Optional, Dict, Callable
from dataclasses import dataclass

import serial
import serial.tools.list_ports

from src.config import KNOWN_MCU_VIDS

logger = logging.getLogger(__name__)


@dataclass
class SerialDeviceInfo:
    """Information about a detected serial device."""
    port: str
    description: str
    hwid: str
    vid: Optional[int] = None
    pid: Optional[int] = None
    serial_number: Optional[str] = None
    manufacturer: Optional[str] = None
    product: Optional[str] = None
    is_known_mcu: bool = False
    chip_type: str = "Unknown"

    @property
    def display_name(self) -> str:
        name = f"{self.port} - {self.description}"
        if self.is_known_mcu:
            name = f"{self.port} - {self.chip_type} ({self.description})"
        # Sanitize for console output
        return name.encode('ascii', errors='replace').decode('ascii')


def identify_chip_type(vid: Optional[int], description: str) -> str:
    """Identify the USB-serial chip type from VID/PID or description."""
    desc_lower = description.lower() if description else ""

    if vid in KNOWN_MCU_VIDS:
        return KNOWN_MCU_VIDS[vid]

    if "ch340" in desc_lower or "ch341" in desc_lower:
        return "Qinheng CH34x"
    if "cp210" in desc_lower:
        return "Silicon Labs CP210x"
    if "ftdi" in desc_lower or "ft232" in desc_lower:
        return "FTDI"
    if "pl2303" in desc_lower or "prolific" in desc_lower:
        return "Prolific PL2303"
    if "arduino" in desc_lower:
        return "Arduino"
    if "teensy" in desc_lower:
        return "Teensy"
    if "pico" in desc_lower or "rp2040" in desc_lower:
        return "Raspberry Pi Pico"
    if "stm32" in desc_lower:
        return "STM32"
    if "mbed" in desc_lower:
        return "ARM mbed"

    return "Unknown"


def detect_serial_devices() -> List[SerialDeviceInfo]:
    """Detect all serial devices and identify printer MCUs."""
    devices = []
    try:
        ports = serial.tools.list_ports.comports()
        for port in ports:
            vid = port.vid
            pid = port.pid
            chip = identify_chip_type(vid, port.description)
            is_mcu = vid in KNOWN_MCU_VIDS if vid else False

            device = SerialDeviceInfo(
                port=port.device,
                description=port.description,
                hwid=port.hwid,
                vid=vid,
                pid=pid,
                serial_number=getattr(port, "serial_number", None),
                manufacturer=getattr(port, "manufacturer", None),
                product=getattr(port, "product", None),
                is_known_mcu=is_mcu,
                chip_type=chip,
            )
            devices.append(device)
            logger.debug(f"Found serial device: {device.display_name}")
    except Exception as e:
        logger.error(f"Error detecting serial devices: {e}")

    return devices


def find_mcu_ports() -> List[SerialDeviceInfo]:
    """Find only known MCU serial ports."""
    all_devices = detect_serial_devices()
    return [d for d in all_devices if d.is_known_mcu]


def test_port_openable(port: str, baud: int = 250000) -> bool:
    """Test if a serial port can be opened."""
    try:
        with serial.Serial(port, baud, timeout=0.1) as s:
            s.close()
            return True
    except (serial.SerialException, PermissionError, OSError) as e:
        logger.debug(f"Port {port} not available: {e}")
        return False


class USBMonitor:
    """Monitors USB serial device changes and notifies callbacks."""

    def __init__(self, poll_interval: float = 2.0):
        self.poll_interval = poll_interval
        self._running = False
        self._thread: Optional[threading.Thread] = None
        self._last_devices: Dict[str, SerialDeviceInfo] = {}
        self._callbacks: List[Callable] = []
        self._lock = threading.Lock()

    def add_callback(self, callback: Callable[[str, SerialDeviceInfo], None]):
        """Add a callback for device changes.
        Callback receives (action, device) where action is 'added' or 'removed'.
        """
        self._callbacks.append(callback)

    def start(self):
        """Start monitoring USB devices."""
        if self._running:
            return
        self._running = True
        self._thread = threading.Thread(target=self._monitor_loop, daemon=True, name="USBMonitor")
        self._thread.start()
        logger.info("USB monitor started")

    def stop(self):
        """Stop monitoring USB devices."""
        self._running = False
        if self._thread:
            self._thread.join(timeout=5)
            self._thread = None
        logger.info("USB monitor stopped")

    def _monitor_loop(self):
        """Main monitoring loop."""
        while self._running:
            try:
                current = {d.port: d for d in detect_serial_devices()}

                with self._lock:
                    last_ports = set(self._last_devices.keys())
                    current_ports = set(current.keys())

                    added = current_ports - last_ports
                    removed = last_ports - current_ports

                    for port in added:
                        device = current[port]
                        logger.info(f"USB device added: {device.display_name}")
                        for cb in self._callbacks:
                            try:
                                cb("added", device)
                            except Exception as e:
                                logger.error(f"Callback error: {e}")

                    for port in removed:
                        device = self._last_devices[port]
                        logger.info(f"USB device removed: {device.display_name}")
                        for cb in self._callbacks:
                            try:
                                cb("removed", device)
                            except Exception as e:
                                logger.error(f"Callback error: {e}")

                    self._last_devices = current

            except Exception as e:
                logger.error(f"USB monitor error: {e}")

            for _ in range(int(self.poll_interval * 10)):
                if not self._running:
                    break
                time.sleep(0.1)

    def get_current_devices(self) -> List[SerialDeviceInfo]:
        """Get current list of serial devices."""
        return list(self._last_devices.values())
