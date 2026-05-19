"""
Sample config browser - lets users select from bundled Klipper/Kalico configs.
Organized by categories: printer models, board templates, examples, and add-ons.
"""
import os
import re
import logging
import shutil
from pathlib import Path
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass

logger = logging.getLogger(__name__)

# Categories for organizing configs
CATEGORIES = {
    "printer": {
        "label": "Printer Templates",
        "pattern": r"^printer-",
        "description": "Pre-configured templates for specific printer models",
    },
    "board": {
        "label": "Board Templates",
        "pattern": r"^generic-",
        "description": "Generic board configs (need manual pin/steps adjustment)",
    },
    "example": {
        "label": "Kinematic Examples",
        "pattern": r"^example-",
        "description": "Base kinematic configurations (cartesian, corexy, delta, etc.)",
    },
    "sample": {
        "label": "Add-ons & Extras",
        "pattern": r"^sample-",
        "description": "Optional modules (macros, multi-mcu, CAN bus, LCD, etc.)",
    },
    "kit": {
        "label": "Kit Printers",
        "pattern": r"^kit-",
        "description": "Kit printer configurations (Voron, Zav, etc.)",
    },
}


@dataclass
class ConfigEntry:
    """A single sample config file."""
    filename: str
    display_name: str
    category: str
    source: str  # "klipper" or "kalico"
    source_path: Path
    description: str = ""

    @property
    def full_display_name(self) -> str:
        return f"{self.display_name} ({self.source.title()})"


def parse_config_name(filename: str) -> Tuple[str, str]:
    """
    Parse a config filename into a human-readable display name.
    Returns (display_name, category_key).
    """
    # Determine category
    category = "other"
    for key, info in CATEGORIES.items():
        if re.match(info["pattern"], filename):
            category = key
            break

    # Clean up the filename for display
    name = filename.replace(".cfg", "")

    # Remove prefix
    for prefix in ["printer-", "generic-", "example-", "sample-", "kit-"]:
        if name.startswith(prefix):
            name = name[len(prefix):]
            break

    # Convert hyphens/underscores to spaces and title-case
    name = name.replace("-", " ").replace("_", " ")

    # Fix common patterns
    name = re.sub(r" v(\d)", r" V\1", name)
    name = re.sub(r"(\d{4})$", r"(\1)", name)
    name = re.sub(r"(\d+\.\d+)", r"V\1", name)
    name = name.title()

    # Fix known abbreviations
    replacements = {
        "Btt": "BTT",
        "Biqu": "BIQU",
        "Skr": "SKR",
        "E3": "E3",
        "E3 Dip": "E3 DIP",
        "Rrf": "RRF",
        "V1.0": "v1.0",
        "V1.1": "v1.1",
        "V1.2": "v1.2",
        "V2.0": "v2.0",
        "V2.1": "v2.1",
        "V3.0": "v3.0",
        "I3": "i3",
        "I3dbeez9": "i3DBEEZ9",
        "Cr10": "CR-10",
        "Cr20": "CR-20",
        "Cr30": "CR-30",
        "Cr5": "CR-5",
        "Cr6": "CR-6",
        "Ender2": "Ender 2",
        "Ender3": "Ender 3",
        "Ender5": "Ender 5",
        "Ender6": "Ender 6",
        "Kobra": "Kobra",
        "Vyper": "Vyper",
        "Sapphire": "Sapphire",
        "TwoTrees": "Two Trees",
        "Voron2": "Voron 2",
        "Zav3D": "ZAV3D",
        "Ratrig": "RatRig",
        "Mmu2S": "MMU2S",
        "Idex": "IDEX",
        "Pwm": "PWM",
        "Canbus": "CAN Bus",
        "Lcd": "LCD",
        "Mcu": "MCU",
        "Btt": "BTT",
        "Ebb": "EBB",
        "Sb": "SB",
        "Huvud": "Huvud",
        "Duet3": "Duet 3",
        "Duet2": "Duet 2",
        "Rambo": "RAMBo",
        "Ramps": "RAMPS",
        "Re-Arm": "Re-Arm",
        "Rumba": "RUMBA",
        "Sgenl": "SGEN-L",
        "Octopus": "Octopus",
        "Manta": "Manta",
        "Monster8": "Monster 8",
        "Leviathan": "Leviathan",
        "Gemini": "Gemini",
        "Infinty": "Infinity",
        "Cheetah": "Cheetah",
        "Spider": "Spider",
        "Fysetc": "Fysetc",
        "Gt2560": "GT2560",
        "Mks": "MKS",
        "Robin": "Robin",
        "Nano": "Nano",
        "Printrboard": "Printrboard",
        "Prusa": "Prusa",
        "Buddy": "Buddy",
        "Mightyboard": "Mightyboard",
        "Minitronics1": "Minitronics 1",
        "Radds": "Radds",
        "Ruramps": "RURAMPS",
        "Simulavr": "SimulAVR",
        "Smoothieboard": "Smoothieboard",
        "Th3D": "TH3D",
        "Ezboard": "EZBoard",
        "Ultimaker": "Ultimaker",
        "Ultimainboard": "UltiMainboard",
        "Alligator": "Alligator",
        "Archim2": "Archim 2",
        "Azteeg": "Azteeg",
        "Cramps": "CRAMPS",
        "Creality": "Creality",
        "Duex": "DUEX",
        "Maestro": "Maestro",
        "6Hc": "6HC",
        "6Xd": "6XD",
        "Mini": "Mini",
        "Einsy": "Einsy",
        "Flyboard": "Flyboard",
        "F6": "F6",
        "S6": "S6",
        "I3DBEEZ9": "i3DBEEZ9",
        "Mellow": "Mellow",
        "Melzi": "Melzi",
        "Pico": "Pico",
        "Pro": "Pro",
        "Plus": "Plus",
        "Max": "Max",
        "Turbo": "Turbo",
        "Mz": "MZ",
        "Lite": "Lite",
        "Se": "SE",
        "Neo": "Neo",
        "S1": "S1",
        "S1 Plus": "S1 Plus",
        "S1Plus": "S1 Plus",
        "Ht": "HT",
        "D1": "D1",
        "V1": "V1",
        "V2": "V2",
        "V3": "V3",
        "X2": "X2",
        "X3": "X3",
        "X5": "X5",
        "X8": "X8",
        "X5Sa": "X5SA",
        "Xy-2-Pro": "XY-2 Pro",
        "Xy 2 Pro": "XY-2 Pro",
        "Sp 5": "SP-5",
        "Sp 3": "SP-3",
        "Kp3S": "KP3S",
        "Lk4": "LK4",
        "Er20": "ER20",
        "Q5": "Q5",
        "Qqs": "QQS",
        "Odin5": "Odin 5",
        "F3": "F3",
        "301": "301",
        "A10T": "A10T",
        "A20T": "A20T",
        "Leo": "LEO",
        "Taz6": "TAZ6",
        "M2": "M2",
        "D1": "D1",
        "Crux1": "Crux1",
        "P802E": "P802E",
        "P802M": "P802M",
        "Sv01": "SV01",
        "Sv05": "SV05",
        "Sv06": "SV06",
        "S8": "S8",
        "T3": "T3",
        "Tarantula": "Tarantula",
        "Flash": "Flash",
        "K8200": "K8200",
        "K8800": "K8800",
        "Aquila": "Aquila",
        "Sermoon": "SerMoon",
        "B1": "B1",
        "Bx": "BX",
        "Hephestos": "Hephestos",
        "Genius": "Genius",
        "Sidewinder": "Sidewinder",
        "Thinker": "Thinker",
        "Creator": "Creator",
        "Fokoos": "Fookos",
        "Geeetech": "Geeetech",
        "Hiprecy": "Hiprecy",
        "Kingroon": "Kingroon",
        "Longer": "Longer",
        "Lulzbot": "LulzBot",
        "Makergear": "MakerGear",
        "Micromake": "MicroMake",
        "Modix": "MODIX",
        "Monoprice": "MonoPrice",
        "Mtw": "MTW",
        "Robo3D": "Robo3D",
        "Seemecnc": "SeeMeCNC",
        "Rostock": "Rostock",
        "Sovol": "Sovol",
        "Sunlu": "Sunlu",
        "Tevo": "TEVO",
        "Tronxy": "TronXY",
        "Twotrees": "TwoTrees",
        "Velleman": "Velleman",
        "Wanhao": "Wanhao",
        "Duplicator": "Duplicator",
        "Anycubic": "Anycubic",
        "Kossel": "Kossel",
        "4Max": "4Max",
        "4MaxPro": "4Max Pro",
        "Alfawise": "Alfawise",
        "U30": "U30",
        "Anet": "Anet",
        "A4": "A4",
        "A8": "A8",
        "A10": "A10",
        "E10": "E10",
        "E16": "E16",
        "Artillery": "Artillery",
        "Elegoo": "ELEGOO",
        "Neptune": "Neptune",
        "Eryone": "Eryone",
        "Flashforge": "FlashForge",
        "Flsun": "FLSUN",
    }

    for old, new in replacements.items():
        name = name.replace(old, new)

    return name, category


def scan_configs(source_path: Path, source_name: str) -> List[ConfigEntry]:
    """Scan a Klipper/Kalico source directory for sample configs."""
    config_dir = source_path / "config"
    if not config_dir.exists():
        return []

    entries = []
    for cfg_file in sorted(config_dir.glob("*.cfg")):
        display_name, category = parse_config_name(cfg_file.name)
        entries.append(ConfigEntry(
            filename=cfg_file.name,
            display_name=display_name,
            category=category,
            source=source_name,
            source_path=cfg_file,
        ))

    return entries


def get_config_content(source_path: Path, filename: str) -> Optional[str]:
    """Read the content of a sample config file."""
    cfg_file = source_path / "config" / filename
    if cfg_file.exists():
        return cfg_file.read_text(encoding="utf-8")
    return None


def apply_config(
    source_path: Path,
    filename: str,
    dest_path: Path,
    serial_port: str = "",
    mcu_type: str = "",
) -> bool:
    """
    Copy a sample config to the destination and optionally customize it.
    Returns True on success.
    """
    content = get_config_content(source_path, filename)
    if content is None:
        logger.error(f"Config file not found: {filename}")
        return False

    # Apply customizations
    if serial_port:
        # Replace serial port in [mcu] section
        content = re.sub(
            r"(serial:\s*).*",
            f"serial: {serial_port}",
            content,
        )

    dest_path.parent.mkdir(parents=True, exist_ok=True)
    dest_path.write_text(content, encoding="utf-8")
    logger.info(f"Applied config {filename} to {dest_path}")
    return True


class ConfigBrowser:
    """High-level interface for browsing and applying sample configs."""

    def __init__(self, klipper_path: Path, kalico_path: Path):
        self.klipper_path = klipper_path
        self.kalico_path = kalico_path
        self._cache: Optional[List[ConfigEntry]] = None

    def get_all_configs(self, source: str = "all") -> List[ConfigEntry]:
        """Get all available configs, optionally filtered by source."""
        if self._cache is not None and source == "all":
            return self._cache

        entries = []
        if source in ("all", "klipper"):
            entries.extend(scan_configs(self.klipper_path, "klipper"))
        if source in ("all", "kalico"):
            entries.extend(scan_configs(self.kalico_path, "kalico"))

        if source == "all":
            self._cache = entries

        return entries

    def get_by_category(self, category: str, source: str = "all") -> List[ConfigEntry]:
        """Get configs filtered by category."""
        all_configs = self.get_all_configs(source)
        return [c for c in all_configs if c.category == category]

    def get_categories(self) -> Dict[str, Dict]:
        """Get available categories with metadata."""
        return CATEGORIES

    def search(self, query: str, source: str = "all") -> List[ConfigEntry]:
        """Search configs by name."""
        query_lower = query.lower()
        all_configs = self.get_all_configs(source)
        return [
            c for c in all_configs
            if query_lower in c.display_name.lower() or query_lower in c.filename.lower()
        ]

    def get_popular_printers(self, source: str = "all") -> List[ConfigEntry]:
        """Get commonly-used printer configs first."""
        popular_patterns = [
            "ender3", "ender3 pro", "ender3 v2", "ender5",
            "cr-10", "cr-10s", "voron",
            "skr mini e3", "octopus",
            "kobra", "neptune", "sv06",
        ]
        all_configs = self.get_by_category("printer", source)
        result = []
        for pattern in popular_patterns:
            for cfg in all_configs:
                if pattern in cfg.display_name.lower():
                    result.append(cfg)

        # Add remaining configs not already included
        included = {c.filename for c in result}
        for cfg in all_configs:
            if cfg.filename not in included:
                result.append(cfg)

        return result
