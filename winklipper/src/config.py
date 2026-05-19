"""
Application configuration and settings management.
Handles printer instances, paths, ports, and user preferences.
"""
import os
import json
import logging
from pathlib import Path
from typing import Dict, List, Optional, Any
from dataclasses import dataclass, field, asdict

logger = logging.getLogger(__name__)

# Default paths relative to app data directory
DEFAULT_BASE_PORT_WEB = 8888
DEFAULT_BASE_PORT_MOONRAKER = 7125
DEFAULT_BAUDRATE = 250000
MAX_INSTANCES = 8

# Known printer MCU USB VID:PID pairs
KNOWN_MCU_VIDS = {
    0x1D50: "Klipper",
    0x0483: "STM32 (ST)",
    0x0403: "FTDI",
    0x10C4: "Silicon Labs (CP210x)",
    0x067B: "Prolific (PL2303)",
    0x1A86: "Qinheng (CH340/CH341)",
    0x2341: "Arduino",
    0x1B4F: "SparkFun",
    0x03EB: "Atmel",
    0x1209: "OpenMoko",
    0x16C0: "Votiro (Teensy)",
    0x2E8A: "Raspberry Pi (Pico)",
}


@dataclass
class PrinterInstanceConfig:
    """Configuration for a single printer instance."""
    name: str = "Printer 1"
    enabled: bool = True
    serial_port: str = ""
    serial_baud: int = DEFAULT_BAUDRATE
    firmware_source: str = "klipper"  # "klipper" or "kalico"
    web_port: int = DEFAULT_BASE_PORT_WEB
    moonraker_port: int = DEFAULT_BASE_PORT_MOONRAKER
    config_dir: str = ""
    printer_cfg: str = ""
    auto_start: bool = False
    extra_klippy_args: str = ""
    extra_moonraker_args: str = ""

    def to_dict(self) -> dict:
        return asdict(self)

    @classmethod
    def from_dict(cls, data: dict) -> "PrinterInstanceConfig":
        valid_keys = {f.name for f in cls.__dataclass_fields__.values()}
        filtered = {k: v for k, v in data.items() if k in valid_keys}
        return cls(**filtered)


@dataclass
class AppConfig:
    """Main application configuration."""
    instances: List[Dict[str, Any]] = field(default_factory=list)
    klipper_source: str = "klipper"  # default firmware source
    kalico_source: str = "kalico"
    moonraker_source: str = "moonraker"
    fluidd_enabled: bool = True
    mainsail_enabled: bool = True
    default_ui: str = "fluidd"  # "fluidd" or "mainsail"
    auto_detect_usb: bool = True
    minimize_to_tray: bool = True
    start_minimized: bool = False
    log_level: str = "INFO"
    data_dir: str = ""

    def to_dict(self) -> dict:
        d = asdict(self)
        d["instances"] = self.instances
        return d

    @classmethod
    def from_dict(cls, data: dict) -> "AppConfig":
        valid_keys = {f.name for f in cls.__dataclass_fields__.values()}
        filtered = {k: v for k, v in data.items() if k in valid_keys}
        return cls(**filtered)


class ConfigManager:
    """Manages application configuration stored as JSON."""

    def __init__(self, config_path: Optional[str] = None):
        if config_path:
            self.config_path = Path(config_path)
        else:
            app_data = Path(os.environ.get("APPDATA", Path.home() / "AppData" / "Roaming"))
            self.config_path = app_data / "WinKlipper" / "config.json"

        self.config = AppConfig()
        self._load()

    def _load(self):
        """Load configuration from disk."""
        if self.config_path.exists():
            try:
                with open(self.config_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                self.config = AppConfig.from_dict(data)
                logger.info(f"Loaded config from {self.config_path}")
            except Exception as e:
                logger.error(f"Failed to load config: {e}")
                self.config = AppConfig()
        else:
            logger.info(f"No config found at {self.config_path}, using defaults")
            self._save()

    def _save(self):
        """Save configuration to disk."""
        self.config_path.parent.mkdir(parents=True, exist_ok=True)
        try:
            with open(self.config_path, "w", encoding="utf-8") as f:
                json.dump(self.config.to_dict(), f, indent=2)
            logger.debug(f"Saved config to {self.config_path}")
        except Exception as e:
            logger.error(f"Failed to save config: {e}")

    def save(self):
        self._save()

    def add_instance(self, name: str = None) -> PrinterInstanceConfig:
        """Add a new printer instance and return its config."""
        existing = self.get_instances()
        idx = len(existing) + 1
        instance = PrinterInstanceConfig(
            name=name or f"Printer {idx}",
            web_port=DEFAULT_BASE_PORT_WEB + idx * 10,
            moonraker_port=DEFAULT_BASE_PORT_MOONRAKER + idx * 10,
        )
        self.config.instances.append(instance.to_dict())
        self._save()
        return instance

    def remove_instance(self, index: int):
        """Remove a printer instance by index."""
        if 0 <= index < len(self.config.instances):
            self.config.instances.pop(index)
            self._save()

    def update_instance(self, index: int, **kwargs):
        """Update a printer instance's configuration."""
        if 0 <= index < len(self.config.instances):
            self.config.instances[index].update(kwargs)
            self._save()

    def get_instance(self, index: int) -> Optional[PrinterInstanceConfig]:
        """Get a printer instance config by index."""
        if 0 <= index < len(self.config.instances):
            return PrinterInstanceConfig.from_dict(self.config.instances[index])
        return None

    def get_instances(self) -> List[PrinterInstanceConfig]:
        """Get all printer instance configs."""
        return [PrinterInstanceConfig.from_dict(d) for d in self.config.instances]

    def get_instance_dir(self, index: int) -> Path:
        """Get the data directory for a printer instance."""
        if self.config.data_dir:
            base = Path(self.config.data_dir)
        else:
            base = Path(os.environ.get("USERPROFILE", Path.home())) / "WinKlipper"
        return base / f"printer_{index}"

    def ensure_instance_dirs(self, index: int) -> Path:
        """Create and return the data directory for a printer instance."""
        d = self.get_instance_dir(index)
        d.mkdir(parents=True, exist_ok=True)
        (d / "config").mkdir(exist_ok=True)
        (d / "gcodes").mkdir(exist_ok=True)
        (d / "logs").mkdir(exist_ok=True)
        (d / "comms").mkdir(exist_ok=True)
        return d
