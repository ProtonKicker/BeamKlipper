"""
System tray UI for WinKlipper.
Provides a tray icon with menu for controlling printer instances.
"""
import logging
import threading
from typing import Optional

import pystray
from PIL import Image, ImageDraw

from src.config import ConfigManager
from src.instance import InstanceManager

logger = logging.getLogger(__name__)


def create_icon_image(size: int = 64) -> Image.Image:
    """Create a simple icon image for the tray."""
    img = Image.new("RGB", (size, size), color=(30, 30, 30))
    draw = ImageDraw.Draw(img)

    # Draw a simple printer-like icon
    margin = size // 8
    # Main body
    draw.rectangle(
        [margin, margin, size - margin, size - margin],
        fill=(60, 60, 60),
        outline=(100, 200, 100),
        width=2,
    )
    # Paper slot
    draw.rectangle(
        [size // 3, margin, 2 * size // 3, margin + size // 6],
        fill=(100, 200, 100),
    )
    # Status LED
    draw.ellipse(
        [size - margin - size // 8, size - margin - size // 8,
         size - margin, size - margin],
        fill=(100, 200, 100),
    )
    return img


class TrayApp:
    """System tray application for WinKlipper."""

    def __init__(self, config_mgr: ConfigManager, instance_mgr: InstanceManager):
        self.config_mgr = config_mgr
        self.instance_mgr = instance_mgr
        self._icon: Optional[pystray.Icon] = None
        self._running = False

    def run(self):
        """Start the tray application."""
        self._running = True

        # Register state change callback
        self.instance_mgr.add_state_callback(self._on_state_change)

        # Create tray icon
        icon_image = create_icon_image()
        menu = self._build_menu()

        self._icon = pystray.Icon(
            "winklipper",
            icon_image,
            "WinKlipper",
            menu,
        )

        logger.info("Starting system tray...")
        self._icon.run()

    def stop(self):
        """Stop the tray application."""
        self._running = False
        self.instance_mgr.stop_all()
        if self._icon:
            self._icon.stop()

    def _build_menu(self) -> pystray.Menu:
        """Build the tray menu."""
        instances = self.config_mgr.get_instances()

        if not instances:
            return pystray.Menu(
                pystray.MenuItem("No printers configured", None, enabled=False),
                pystray.Menu.SEPARATOR,
                pystray.MenuItem("Add Printer", self._add_printer),
                pystray.MenuItem("Exit", self._exit),
            )

        items = []

        for i, config in enumerate(instances):
            # Submenu for each printer
            is_running = i in self.instance_mgr.instances

            items.append(
                pystray.MenuItem(
                    f"{config.name} ({'Running' if is_running else 'Stopped'})",
                    None,
                    enabled=False,
                )
            )
            items.append(
                pystray.MenuItem(
                    "    Start" if not is_running else "    Stop",
                    lambda _, idx=i: self._toggle_instance(idx),
                )
            )
            if is_running:
                items.append(
                    pystray.MenuItem(
                        "    Open UI",
                        lambda _, idx=i: self._open_ui(idx),
                    )
                )
            items.append(pystray.Menu.SEPARATOR)

        items.extend([
            pystray.MenuItem("Add Printer", self._add_printer),
            pystray.Menu.SEPARATOR,
            pystray.MenuItem("Exit WinKlipper", self._exit),
        ])

        return pystray.Menu(*items)

    def _on_state_change(self, instance_id: int, component: str, state: str):
        """Handle state changes from instances."""
        # Update tray icon based on state
        if self._icon:
            if state == "running":
                self._icon.title = "WinKlipper - Running"
            elif state == "error":
                self._icon.title = "WinKlipper - Error"
            else:
                self._icon.title = "WinKlipper"

            # Update menu to reflect new state
            try:
                self._icon.update_menu()
            except Exception as e:
                logger.debug(f"Menu update error: {e}")

    def _toggle_instance(self, instance_id: int):
        """Start or stop a printer instance."""
        if instance_id in self.instance_mgr.instances:
            self.instance_mgr.stop_instance(instance_id)
        else:
            self.instance_mgr.start_instance(instance_id)

        if self._icon:
            self._icon.update_menu()

    def _open_ui(self, instance_id: int):
        """Open the web UI for an instance."""
        instance = self.instance_mgr.get_instance(instance_id)
        if instance:
            instance.open_ui()

    def _add_printer(self):
        """Add a new printer instance."""
        self.config_mgr.add_instance()
        if self._icon:
            self._icon.update_menu()
        logger.info("Added new printer instance")

    def _exit(self):
        """Exit the application."""
        logger.info("Exiting WinKlipper...")
        self.stop()
