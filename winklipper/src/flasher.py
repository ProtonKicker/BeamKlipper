"""
Windows MCU firmware flashing tools.
Handles building and flashing Klipper/Kalico firmware to printer boards.
"""
import os
import logging
import subprocess
import shutil
from pathlib import Path
from typing import Optional, List

logger = logging.getLogger(__name__)


class FirmwareFlasher:
    """Manages MCU firmware building and flashing."""

    def __init__(self, firmware_source: Path):
        self.firmware_source = Path(firmware_source)
        self._make = self._find_make()

    def _find_make(self) -> Optional[str]:
        """Find the make executable."""
        # Check for make in PATH
        make = shutil.which("make")
        if make:
            return make

        # Check common Windows locations
        make_paths = [
            Path("C:/msys64/usr/bin/make.exe"),
            Path("C:/Program Files/Git/usr/bin/make.exe"),
            Path("C:/cygwin64/bin/make.exe"),
        ]
        for p in make_paths:
            if p.exists():
                return str(p)

        logger.warning("make not found. Install Git for Windows (includes make) or MSYS2.")
        return None

    def menuconfig(self) -> bool:
        """Run make menuconfig for firmware configuration."""
        if not self._make:
            logger.error("make not available")
            return False

        try:
            result = subprocess.run(
                [self._make, "menuconfig"],
                cwd=str(self.firmware_source),
            )
            return result.returncode == 0
        except Exception as e:
            logger.error(f"menuconfig failed: {e}")
            return False

    def build(self) -> bool:
        """Build the firmware."""
        if not self._make:
            logger.error("make not available")
            return False

        logger.info("Building firmware...")
        try:
            result = subprocess.run(
                [self._make, "-j", str(os.cpu_count() or 4)],
                cwd=str(self.firmware_source),
                capture_output=True,
                text=True,
            )
            if result.returncode == 0:
                logger.info("Firmware build successful")
                return True
            else:
                logger.error(f"Firmware build failed: {result.stderr}")
                return False
        except Exception as e:
            logger.error(f"Build error: {e}")
            return False

    def get_firmware_file(self) -> Optional[Path]:
        """Get the path to the built firmware file."""
        out_dir = self.firmware_source / "out"
        if not out_dir.exists():
            return None

        # Look for common firmware file extensions
        for ext in [".bin", ".uf2", ".hex"]:
            files = list(out_dir.glob(f"*{ext}"))
            if files:
                return files[0]

        return None

    def flash_sd_card(self, sd_card_path: Path) -> bool:
        """Copy firmware to SD card for flashing."""
        firmware = self.get_firmware_file()
        if not firmware:
            logger.error("No firmware file found. Build first.")
            return False

        if not sd_card_path.exists():
            logger.error(f"SD card path not found: {sd_card_path}")
            return False

        try:
            # Copy firmware with firmware.bin name (common for STM32 boards)
            dest = sd_card_path / "firmware.bin"
            shutil.copy2(firmware, dest)
            logger.info(f"Firmware copied to {dest}")
            logger.info("Insert SD card into printer board and power cycle to flash.")
            return True
        except Exception as e:
            logger.error(f"Failed to copy firmware to SD card: {e}")
            return False

    def flash_usb(self, serial_port: str) -> bool:
        """Flash firmware via USB (for boards that support it)."""
        firmware = self.get_firmware_file()
        if not firmware:
            logger.error("No firmware file found. Build first.")
            return False

        # Try different flashing methods
        flash_methods = [
            self._flash_katapult,
            self._flash_dfu,
            self._flash_hid,
        ]

        for method in flash_methods:
            if method(serial_port, firmware):
                return True

        logger.error("No suitable flash method found. Try SD card flashing.")
        return False

    def _flash_katapult(self, serial_port: str, firmware: Path) -> bool:
        """Flash via Katapult (formerly CanBoot) bootloader."""
        katapult = self.firmware_source.parent / "katapult"
        if not katapult.exists():
            return False

        flash_script = katapult / "scripts" / "flash_can.py"
        if not flash_script.exists():
            return False

        logger.info(f"Flashing via Katapult on {serial_port}...")
        try:
            result = subprocess.run(
                [
                    sys.executable, str(flash_script),
                    "-f", str(firmware),
                    "-d", serial_port,
                ],
                capture_output=True,
                text=True,
                timeout=60,
            )
            if result.returncode == 0:
                logger.info("Katapult flash successful")
                return True
            else:
                logger.debug(f"Katapult flash failed: {result.stderr}")
                return False
        except Exception as e:
            logger.debug(f"Katapult flash error: {e}")
            return False

    def _flash_dfu(self, serial_port: str, firmware: Path) -> bool:
        """Flash via DFU mode."""
        dfu_util = shutil.which("dfu-util")
        if not dfu_util:
            return False

        logger.info(f"Flashing via DFU...")
        try:
            result = subprocess.run(
                [dfu_util, "-D", str(firmware)],
                capture_output=True,
                text=True,
                timeout=60,
            )
            if result.returncode == 0:
                logger.info("DFU flash successful")
                return True
            else:
                logger.debug(f"DFU flash failed: {result.stderr}")
                return False
        except Exception as e:
            logger.debug(f"DFU flash error: {e}")
            return False

    def _flash_hid(self, serial_port: str, firmware: Path) -> bool:
        """Flash via HID bootloader (common for STM32)."""
        # STM32 HID bootloader flashing would go here
        # This typically requires a separate tool
        return False

    def clean(self):
        """Clean build artifacts."""
        if not self._make:
            return

        try:
            subprocess.run(
                [self._make, "clean"],
                cwd=str(self.firmware_source),
                capture_output=True,
            )
            logger.info("Build cleaned")
        except Exception as e:
            logger.error(f"Clean failed: {e}")


# Need sys for flash_can.py call
import sys
