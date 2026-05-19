"""
System tray UI for WinKlipper.
Provides a tray icon with menu for controlling printer instances.
Includes a Tkinter-based config browser for selecting sample configs.
"""
import logging
import threading
import tkinter as tk
from tkinter import ttk, messagebox
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

    margin = size // 8
    draw.rectangle(
        [margin, margin, size - margin, size - margin],
        fill=(60, 60, 60),
        outline=(100, 200, 100),
        width=2,
    )
    draw.rectangle(
        [size // 3, margin, 2 * size // 3, margin + size // 6],
        fill=(100, 200, 100),
    )
    draw.ellipse(
        [size - margin - size // 8, size - margin - size // 8,
         size - margin, size - margin],
        fill=(100, 200, 100),
    )
    return img


class ConfigSelectorDialog:
    """Tkinter dialog for selecting a sample printer config."""

    def __init__(self, config_browser, parent=None):
        self.config_browser = config_browser
        self.parent = parent
        self.selected_config = None
        self.selected_serial = ""

        self.root = tk.Toplevel(parent) if parent else tk.Tk()
        self.root.title("Select Printer Config")
        self.root.geometry("700x500")
        self.root.minsize(600, 400)
        self.root.transient(parent) if parent else None

        self._build_ui()
        self._populate_configs()

    def _build_ui(self):
        main_frame = ttk.Frame(self.root, padding=10)
        main_frame.pack(fill=tk.BOTH, expand=True)

        # Search bar
        search_frame = ttk.Frame(main_frame)
        search_frame.pack(fill=tk.X, pady=(0, 10))

        ttk.Label(search_frame, text="Search:").pack(side=tk.LEFT, padx=(0, 5))
        self.search_var = tk.StringVar()
        self.search_var.trace("w", self._on_search)
        search_entry = ttk.Entry(search_frame, textvariable=self.search_var, width=40)
        search_entry.pack(side=tk.LEFT, fill=tk.X, expand=True)

        # Category tabs
        self.notebook = ttk.Notebook(main_frame)
        self.notebook.pack(fill=tk.BOTH, expand=True)

        self.treeviews = {}

        # Add "Simple List" tab first (matches Beam Klipper style)
        simple_frame = ttk.Frame(self.notebook)
        self.notebook.add(simple_frame, text="All Configs")
        self.treeviews["simple"] = self._create_simple_list(simple_frame)

        # Add "Popular" tab
        popular_frame = ttk.Frame(self.notebook)
        self.notebook.add(popular_frame, text="Popular")
        self.treeviews["popular"] = self._create_treeview(popular_frame)

        categories = self.config_browser.get_categories()
        for cat_key, cat_info in categories.items():
            cat_frame = ttk.Frame(self.notebook)
            self.notebook.add(cat_frame, text=cat_info["label"])
            self.treeviews[cat_key] = self._create_treeview(cat_frame)

        # Serial port entry
        serial_frame = ttk.Frame(main_frame)
        serial_frame.pack(fill=tk.X, pady=(10, 0))

        ttk.Label(serial_frame, text="Serial Port:").pack(side=tk.LEFT, padx=(0, 5))
        self.serial_var = tk.StringVar()
        serial_entry = ttk.Entry(serial_frame, textvariable=self.serial_var, width=20)
        serial_entry.pack(side=tk.LEFT, padx=(0, 10))

        ttk.Label(serial_frame, text="(leave blank to auto-detect)",
                  foreground="gray").pack(side=tk.LEFT)

        # Buttons
        btn_frame = ttk.Frame(main_frame)
        btn_frame.pack(fill=tk.X, pady=(10, 0))

        ttk.Button(btn_frame, text="Select", command=self._on_select).pack(side=tk.RIGHT, padx=5)
        ttk.Button(btn_frame, text="Cancel", command=self._on_cancel).pack(side=tk.RIGHT)

        # Bind double-click
        for tree in self.treeviews.values():
            tree.bind("<Double-1>", lambda e: self._on_select())

    def _create_simple_list(self, parent) -> ttk.Treeview:
        """Create a simple flat list showing just filenames (matches Beam Klipper style)."""
        tree = ttk.Treeview(parent, columns=("filename",), show="headings", selectmode="browse")
        tree.heading("filename", text="Config File")
        tree.column("filename", width=500, minwidth=300)

        scrollbar = ttk.Scrollbar(parent, orient=tk.VERTICAL, command=tree.yview)
        tree.configure(yscrollcommand=scrollbar.set)

        tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)

        return tree

    def _create_treeview(self, parent) -> ttk.Treeview:
        columns = ("name", "source", "filename")
        tree = ttk.Treeview(parent, columns=columns, show="headings", selectmode="browse")

        tree.heading("name", text="Printer / Config")
        tree.heading("source", text="Source")
        tree.heading("filename", text="File")

        tree.column("name", width=350, minwidth=200)
        tree.column("source", width=80, minwidth=60)
        tree.column("filename", width=200, minwidth=100)

        scrollbar = ttk.Scrollbar(parent, orient=tk.VERTICAL, command=tree.yview)
        tree.configure(yscrollcommand=scrollbar.set)

        tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)

        return tree

    def _populate_configs(self):
        # Simple List tab (flat filenames like Beam Klipper)
        all_configs = self.config_browser.get_all_configs()
        for cfg in all_configs:
            self.treeviews["simple"].insert("", tk.END, values=(cfg.filename,), tags=(cfg.filename,))

        # Popular tab
        popular = self.config_browser.get_popular_printers()
        for cfg in popular:
            self.treeviews["popular"].insert("", tk.END, values=(
                cfg.display_name, cfg.source.title(), cfg.filename
            ), tags=(cfg.filename,))

        # Category tabs
        for cat_key in self.treeviews:
            if cat_key in ("simple", "popular"):
                continue
            configs = self.config_browser.get_by_category(cat_key)
            for cfg in configs:
                self.treeviews[cat_key].insert("", tk.END, values=(
                    cfg.display_name, cfg.source.title(), cfg.filename
                ), tags=(cfg.filename,))

    def _on_search(self, *args):
        query = self.search_var.get().strip()
        if not query:
            self._populate_configs()
            return

        results = self.config_browser.search(query)

        # Clear all treeviews
        for tree in self.treeviews.values():
            for item in tree.get_children():
                tree.delete(item)

        # Populate simple list with filtered results
        for cfg in results:
            self.treeviews["simple"].insert("", tk.END, values=(cfg.filename,), tags=(cfg.filename,))

        # Populate other tabs with filtered results
        for key, tree in self.treeviews.items():
            if key == "simple":
                continue
            for cfg in results:
                tree.insert("", tk.END, values=(
                    cfg.display_name, cfg.source.title(), cfg.filename
                ), tags=(cfg.filename,))

        # Switch to simple list tab to show results
        self.notebook.select(0)

    def _get_selected_config(self):
        """Get the currently selected config entry."""
        # Find which treeview is active
        active_tree = None
        for key, tree in self.treeviews.items():
            if tree.winfo_viewable():
                active_tree = tree
                break

        if not active_tree:
            return None

        selection = active_tree.selection()
        if not selection:
            return None

        item = active_tree.item(selection[0])
        # Simple list has 1 column (filename), others have 3
        if len(item["values"]) == 1:
            filename = item["values"][0]
        else:
            filename = item["values"][2]

        # Find the matching config entry
        all_configs = self.config_browser.get_all_configs()
        for cfg in all_configs:
            if cfg.filename == filename:
                return cfg
        return None

    def _on_select(self):
        cfg = self._get_selected_config()
        if not cfg:
            messagebox.showwarning("No Selection", "Please select a printer config first.")
            return

        self.selected_config = cfg
        self.selected_serial = self.serial_var.get().strip()
        self.root.destroy()

    def _on_cancel(self):
        self.root.destroy()

    def show(self) -> Optional[tuple]:
        """Show the dialog and return (config_entry, serial_port) or None."""
        self.root.wait_window()
        if self.selected_config:
            return (self.selected_config, self.selected_serial)
        return None


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
        self.instance_mgr.add_state_callback(self._on_state_change)

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
                items.append(
                    pystray.MenuItem(
                        "    Change Config",
                        lambda _, idx=i: self._change_config(idx),
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
        if self._icon:
            if state == "running":
                self._icon.title = "WinKlipper - Running"
            elif state == "error":
                self._icon.title = "WinKlipper - Error"
            else:
                self._icon.title = "WinKlipper"

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

    def _change_config(self, instance_id: int):
        """Change the config for a running instance."""
        def _show_dialog():
            result = ConfigSelectorDialog(
                self.instance_mgr.config_browser
            ).show()
            if result:
                cfg_entry, serial_port = result
                instance = self.instance_mgr.get_instance(instance_id)
                if instance:
                    instance.apply_sample_config(cfg_entry, serial_port)
                    # Restart to apply new config
                    instance.restart()

        threading.Thread(target=_show_dialog, daemon=True).start()

    def _add_printer(self):
        """Add a new printer instance with config selection."""
        def _show_dialog():
            result = ConfigSelectorDialog(
                self.instance_mgr.config_browser
            ).show()

            if result:
                cfg_entry, serial_port = result
                instance_config = self.config_mgr.add_instance(cfg_entry.display_name)
                idx = len(self.config_mgr.get_instances()) - 1

                # Update config with serial port
                if serial_port:
                    self.config_mgr.update_instance(idx, serial_port=serial_port)

                # Create instance and apply config
                instance = self.instance_mgr.start_instance(idx)
                if instance:
                    instance.apply_sample_config(cfg_entry, serial_port)
                    # Restart to apply
                    instance.restart()
                    instance.open_ui()
            else:
                # User cancelled, add with defaults
                self.config_mgr.add_instance()

            if self._icon:
                self._icon.update_menu()

        threading.Thread(target=_show_dialog, daemon=True).start()

    def _exit(self):
        """Exit the application."""
        logger.info("Exiting WinKlipper...")
        self.stop()
