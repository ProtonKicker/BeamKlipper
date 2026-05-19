"""
Printer instance manager - orchestrates Klippy, Moonraker, and Web UI for each printer.
Supports multiple concurrent printer instances.
"""
import os
import logging
import webbrowser
from pathlib import Path
from typing import Dict, Optional, List, Callable

from src.config import ConfigManager, PrinterInstanceConfig
from src.klippy_service import KlippyService
from src.moonraker_service import MoonrakerService
from src.web_server import WebUIManager

logger = logging.getLogger(__name__)

# Resolve paths to bundled repos
PROJECT_ROOT = Path(__file__).resolve().parent.parent
KLIPPER_PATH = PROJECT_ROOT.parent / "klipper-master" / "klipper-master"
KALICO_PATH = PROJECT_ROOT.parent / "kalico-main" / "kalico-main"
MOONRAKER_PATH = PROJECT_ROOT.parent / "moonraker-master" / "moonraker-master"
FLUIDD_PATH = PROJECT_ROOT.parent / "fluidd-develop" / "fluidd-develop" / "dist"
MAINSAIL_PATH = PROJECT_ROOT.parent / "mainsail-develop" / "mainsail-develop" / "dist"


class PrinterInstance:
    """Represents a single running printer instance."""

    def __init__(self, instance_id: int, config: PrinterInstanceConfig, config_mgr: ConfigManager):
        self.id = instance_id
        self.config = config
        self.config_mgr = config_mgr

        # Data directory
        self.data_dir = config_mgr.ensure_instance_dirs(instance_id)

        # Determine firmware source path
        if config.firmware_source == "kalico":
            self.firmware_path = KALICO_PATH
        else:
            self.firmware_path = KLIPPER_PATH

        # Config file path
        self.printer_cfg = self.data_dir / "config" / "printer.cfg"
        self.moonraker_conf = self.data_dir / "config" / "moonraker.conf"

        # Create default printer.cfg if it doesn't exist
        if not self.printer_cfg.exists():
            self._create_default_printer_cfg()

        # Services
        self.klippy: Optional[KlippyService] = None
        self.moonraker: Optional[MoonrakerService] = None
        self.web_ui: Optional[WebUIManager] = None

        # State
        self._state_callbacks: List[Callable] = []

    def _create_default_printer_cfg(self):
        """Create a minimal printer.cfg template."""
        template = f"""# WinKlipper auto-generated printer configuration
# Edit this file to match your printer hardware

[mcu]
serial: {self.config.serial_port or '/dev/ttyUSB0'}
restart_method: command

[printer]
kinematics: cartesian
max_velocity: 300
max_accel: 3000
max_z_velocity: 5
max_z_accel: 100

[stepper_x]
step_pin: PF0
dir_pin: PF1
enable_pin: !PD7
microsteps: 16
rotation_distance: 40
endstop_pin: ^PE5
position_endstop: 0
position_max: 200
homing_speed: 50

[stepper_y]
step_pin: PF6
dir_pin: !PF7
enable_pin: !PF2
microsteps: 16
rotation_distance: 40
endstop_pin: ^PJ1
position_endstop: 0
position_max: 200
homing_speed: 50

[stepper_z]
step_pin: PL3
dir_pin: PL1
enable_pin: !PK0
microsteps: 16
rotation_distance: 8
endstop_pin: ^PD3
position_endstop: 0.5
position_max: 200

[extruder]
step_pin: PA4
dir_pin: PA6
enable_pin: !PA2
microsteps: 16
rotation_distance: 33.680
nozzle_diameter: 0.400
filament_diameter: 1.750
heater_pin: PB4
sensor_type: EPCOS 100K B57560G104F
sensor_pin: PK5
control: pid
pid_Kp: 22.2
pid_Ki: 1.08
pid_Kd: 114
min_temp: 0
max_temp: 250

[heater_bed]
heater_pin: PH5
sensor_type: EPCOS 100K B57560G104F
sensor_pin: PK6
control: watermark
min_temp: 0
max_temp: 120

[fan]
pin: PH6

[mcu host]
serial: /tmp/klipper_host_mcu

[temperature_sensor host]
sensor_type: temperature_host

[virtual_sdcard]
path: {self.data_dir / "gcodes"}

[display_status]

[respond]

[exclude_object]
"""
        self.printer_cfg.parent.mkdir(parents=True, exist_ok=True)
        with open(self.printer_cfg, "w", encoding="utf-8") as f:
            f.write(template)
        logger.info(f"Created default printer.cfg at {self.printer_cfg}")

    def on_state_change(self, component: str, state: str):
        """Handle state changes from services."""
        logger.info(f"Instance {self.id} [{component}]: {state}")
        for cb in self._state_callbacks:
            try:
                cb(self.id, component, state)
            except Exception as e:
                logger.error(f"State callback error: {e}")

    def add_state_callback(self, callback: Callable):
        self._state_callbacks.append(callback)

    def start(self):
        """Start all services for this instance."""
        logger.info(f"Starting printer instance {self.id}: {self.config.name}")

        # Update serial port in printer.cfg if it changed
        if self.config.serial_port:
            self._update_serial_port()

        # Start Klippy
        self.klippy = KlippyService(
            instance_id=self.id,
            klipper_path=self.firmware_path,
            config_file=self.printer_cfg,
            data_dir=self.data_dir,
            firmware_source=self.config.firmware_source,
            extra_args=self.config.extra_klippy_args,
            on_state_change=lambda idx, comp, state: self.on_state_change(comp, state),
        )
        self.klippy.start()

        # Start Moonraker
        klippy_uds = self.data_dir / "comms" / f"klippy_{self.id}.sock"
        self.moonraker = MoonrakerService(
            instance_id=self.id,
            moonraker_path=MOONRAKER_PATH,
            config_file=self.moonraker_conf,
            data_dir=self.data_dir,
            port=self.config.moonraker_port,
            klippy_uds=klippy_uds,
            extra_args=self.config.extra_moonraker_args,
            on_state_change=lambda idx, comp, state: self.on_state_change(comp, state),
        )
        self.moonraker.start()

        # Start Web UI
        ui_path = self.config_mgr.config.default_ui
        self.web_ui = WebUIManager(
            instance_id=self.id,
            port=self.config.web_port,
            moonraker_port=self.config.moonraker_port,
            ui_path=ui_path,
            fluidd_path=FLUIDD_PATH if FLUIDD_PATH.exists() else None,
            mainsail_path=MAINSAIL_PATH if MAINSAIL_PATH.exists() else None,
        )
        self.web_ui.start()

    def stop(self):
        """Stop all services for this instance."""
        logger.info(f"Stopping printer instance {self.id}: {self.config.name}")

        if self.web_ui:
            self.web_ui.stop()
        if self.moonraker:
            self.moonraker.stop()
        if self.klippy:
            self.klippy.stop()

    def restart(self):
        """Restart all services."""
        self.stop()
        import time
        time.sleep(1)
        self.start()

    def open_ui(self):
        """Open the web UI in the default browser."""
        if self.web_ui:
            url = self.web_ui.url
            logger.info(f"Opening UI: {url}")
            webbrowser.open(url)

    def _update_serial_port(self):
        """Update the serial port in printer.cfg."""
        if not self.printer_cfg.exists():
            return

        try:
            content = self.printer_cfg.read_text(encoding="utf-8")
            lines = content.split("\n")
            new_lines = []
            in_mcu_section = False
            serial_updated = False

            for line in lines:
                if line.strip() == "[mcu]":
                    in_mcu_section = True
                elif line.strip().startswith("[") and in_mcu_section:
                    in_mcu_section = False

                if in_mcu_section and line.strip().startswith("serial:"):
                    new_lines.append(f"serial: {self.config.serial_port}")
                    serial_updated = True
                else:
                    new_lines.append(line)

            if not serial_updated and in_mcu_section is False:
                # Add serial to [mcu] section
                new_content = content.replace(
                    "[mcu]",
                    f"[mcu]\nserial: {self.config.serial_port}"
                )
            else:
                new_content = "\n".join(new_lines)

            self.printer_cfg.write_text(new_content, encoding="utf-8")
            logger.info(f"Updated serial port to {self.config.serial_port}")
        except Exception as e:
            logger.error(f"Failed to update serial port in printer.cfg: {e}")

    @property
    def is_running(self) -> bool:
        """Check if the instance is running."""
        klippy_ok = self.klippy.is_running() if self.klippy else False
        moonraker_ok = self.moonraker.is_running() if self.moonraker else False
        return klippy_ok or moonraker_ok


class InstanceManager:
    """Manages multiple printer instances."""

    def __init__(self, config_mgr: ConfigManager):
        self.config_mgr = config_mgr
        self.instances: Dict[int, PrinterInstance] = {}
        self._state_callbacks: List[Callable] = []

    def add_state_callback(self, callback: Callable):
        self._state_callbacks.append(callback)

    def _notify_state(self, instance_id: int, component: str, state: str):
        for cb in self._state_callbacks:
            try:
                cb(instance_id, component, state)
            except Exception as e:
                logger.error(f"State callback error: {e}")

    def start_instance(self, instance_id: int) -> Optional[PrinterInstance]:
        """Start a printer instance."""
        config = self.config_mgr.get_instance(instance_id)
        if not config:
            logger.error(f"Instance {instance_id} not found in config")
            return None

        if instance_id in self.instances:
            logger.warning(f"Instance {instance_id} already exists")
            return self.instances[instance_id]

        instance = PrinterInstance(instance_id, config, self.config_mgr)
        instance.add_state_callback(
            lambda idx, comp, state: self._notify_state(idx, comp, state)
        )
        instance.start()
        self.instances[instance_id] = instance
        return instance

    def stop_instance(self, instance_id: int):
        """Stop a printer instance."""
        if instance_id in self.instances:
            self.instances[instance_id].stop()
            del self.instances[instance_id]

    def stop_all(self):
        """Stop all running instances."""
        for instance_id in list(self.instances.keys()):
            self.stop_instance(instance_id)

    def start_all_auto(self):
        """Start all instances marked for auto-start."""
        for i, config in enumerate(self.config_mgr.get_instances()):
            if config.auto_start and config.enabled:
                self.start_instance(i)

    def get_instance(self, instance_id: int) -> Optional[PrinterInstance]:
        return self.instances.get(instance_id)

    def get_all_instances(self) -> List[PrinterInstance]:
        return list(self.instances.values())
