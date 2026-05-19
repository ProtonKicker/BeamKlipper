"""
WinKlipper - Native Windows Klipper/Kalico Host
Main entry point.
"""
import os
import sys
import logging
import argparse
import signal
from pathlib import Path

# Add project root to path
PROJECT_ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(PROJECT_ROOT.parent))

from src.config import ConfigManager
from src.instance import InstanceManager
from src.tray import TrayApp
from src.usb_detector import USBMonitor


def setup_logging(level: str = "INFO"):
    """Configure application logging."""
    # Fix Windows console encoding
    if sys.stdout.encoding != 'utf-8':
        import io
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    if sys.stderr.encoding != 'utf-8':
        import io
        sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

    log_format = "%(asctime)s [%(levelname)s] %(name)s: %(message)s"
    date_format = "%H:%M:%S"

    # Console handler
    console = logging.StreamHandler(sys.stdout)
    console.setLevel(getattr(logging, level.upper(), logging.INFO))
    console.setFormatter(logging.Formatter(log_format, datefmt=date_format))

    # File handler (app data directory)
    app_data = Path(os.environ.get("APPDATA", Path.home() / "AppData" / "Roaming"))
    log_dir = app_data / "WinKlipper" / "logs"
    log_dir.mkdir(parents=True, exist_ok=True)
    log_file = log_dir / "winklipper.log"

    file_handler = logging.FileHandler(log_file, encoding="utf-8")
    file_handler.setLevel(logging.DEBUG)
    file_handler.setFormatter(logging.Formatter(log_format, datefmt=date_format))

    # Root logger
    root = logging.getLogger()
    root.setLevel(logging.DEBUG)
    root.addHandler(console)
    root.addHandler(file_handler)

    logging.info(f"Log file: {log_file}")


def check_dependencies():
    """Check that required external repos are available."""
    from src.instance import KLIPPER_PATH, KALICO_PATH, MOONRAKER_PATH, FLUIDD_PATH, MAINSAIL_PATH

    missing = []

    if not KLIPPER_PATH.exists():
        missing.append(f"Klipper: {KLIPPER_PATH}")
    if not KALICO_PATH.exists():
        missing.append(f"Kalico: {KALICO_PATH}")
    if not MOONRAKER_PATH.exists():
        missing.append(f"Moonraker: {MOONRAKER_PATH}")

    if missing:
        logging.warning("Missing repositories:")
        for m in missing:
            logging.warning(f"  - {m}")
        logging.warning("Some features may not work correctly.")
        return False

    # Check for pre-built UI dist folders
    ui_missing = []
    if not FLUIDD_PATH.exists():
        ui_missing.append("Fluidd (needs 'npm run build' in fluidd-develop/fluidd-develop)")
    if not MAINSAIL_PATH.exists():
        ui_missing.append("Mainsail (needs 'npm run build' in mainsail-develop/mainsail-develop)")

    if ui_missing:
        logging.warning("Web UI dist folders not found:")
        for m in ui_missing:
            logging.warning(f"  - {m}")
        logging.warning("Web UI will not be available until built.")

    return True


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(description="WinKlipper - Windows Klipper/Kalico Host")
    parser.add_argument("--no-tray", action="store_true", help="Run without system tray")
    parser.add_argument("--headless", action="store_true", help="Run in headless mode (no UI)")
    parser.add_argument("--log-level", default="INFO", choices=["DEBUG", "INFO", "WARNING", "ERROR"])
    parser.add_argument("--config", type=str, help="Path to config file")
    args = parser.parse_args()

    # Setup logging
    setup_logging(args.log_level)
    logging.info("=" * 60)
    logging.info("WinKlipper starting...")
    logging.info("=" * 60)

    # Check dependencies
    check_dependencies()

    # Initialize config
    config_mgr = ConfigManager(config_path=args.config)

    # Add a default instance if none exist
    if not config_mgr.get_instances():
        logging.info("No printer instances found, adding default...")
        config_mgr.add_instance("My Printer")

    # Initialize instance manager
    instance_mgr = InstanceManager(config_mgr)

    # Start USB monitor for auto-detection
    usb_monitor = USBMonitor()

    def on_usb_change(action, device):
        logging.info(f"USB {action}: {device.display_name}")
        # TODO: Auto-assign detected devices to printer instances

    usb_monitor.add_callback(on_usb_change)
    usb_monitor.start()

    # Handle shutdown
    def shutdown(signum, frame):
        logging.info("Shutdown signal received...")
        usb_monitor.stop()
        instance_mgr.stop_all()
        sys.exit(0)

    signal.signal(signal.SIGINT, shutdown)
    signal.signal(signal.SIGTERM, shutdown)

    if args.headless:
        # Headless mode - just run services
        logging.info("Running in headless mode")
        instance_mgr.start_all_auto()

        try:
            import time
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            shutdown(None, None)

    elif args.no_tray:
        # No tray - just log and run
        logging.info("Running without tray UI")
        instance_mgr.start_all_auto()

        try:
            import time
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            shutdown(None, None)

    else:
        # Run with system tray
        logging.info("Starting system tray UI...")
        tray = TrayApp(config_mgr, instance_mgr)

        # Auto-start instances if configured
        instance_mgr.start_all_auto()

        try:
            tray.run()
        except KeyboardInterrupt:
            shutdown(None, None)


if __name__ == "__main__":
    main()
