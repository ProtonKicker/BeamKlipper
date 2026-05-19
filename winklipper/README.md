# WinKlipper

Native Windows Klipper/Kalico host application. Run Klipper 3D printer firmware directly on Windows without Linux, Raspberry Pi, or WSL.

## Features

- **Native Windows** - No Linux VM, no WSL, no dual boot
- **Klipper & Kalico** - Support for both Klipper and the feature-rich Kalico fork
- **Multi-printer** - Run multiple printer instances simultaneously
- **Moonraker bundled** - Full API server with Fluidd and Mainsail web UIs
- **USB auto-detection** - Automatically detects printer MCUs via USB
- **System tray** - Lightweight background operation
- **Portable** - Single `.exe` that runs anywhere

## Requirements

- Windows 10 or later (Windows 7 may work with Python 3.8)
- Python 3.10+
- USB OTG connection to your printer's control board

## Quick Start

### 1. Install Dependencies

```bash
cd winklipper
pip install -r requirements.txt
```

### 2. Build Web UIs (optional, for Fluidd/Mainsail)

```bash
# Fluidd
cd ../fluidd-develop/fluidd-develop
npm ci
npm run build

# Mainsail
cd ../../mainsail-develop/mainsail-develop
npm ci
npm run build
```

### 3. Build C Helper (optional, for better performance)

Requires MinGW-w64 or MSYS2 with gcc:

```bash
cd chelper
build_windows.bat
```

Without the compiled C helper, Klipper falls back to Python-only mode which is slower but functional.

### 4. Run

```bash
cd ..
python src/main.py
```

A system tray icon will appear. Right-click to add printers, start/stop instances, and open the web UI.

## Command Line Options

```
--no-tray     Run without system tray (console only)
--headless    Run in headless mode (no UI, auto-start configured printers)
--log-level   Set log level (DEBUG, INFO, WARNING, ERROR)
--config      Path to custom config file
```

## Architecture

```
┌─────────────────────────────────────────────┐
│  WinKlipper (Python + System Tray)          │
│  ├─ USB Monitor (pyserial)                  │
│  ├─ Instance Manager                        │
│  │  ├─ Klippy Service (Klipper/Kalico)      │
│  │  ├─ Moonraker Service                    │
│  │  └─ Web UI Server (Tornado)              │
│  └─ Config Manager                          │
└─────────────────────────────────────────────┘
         │                        │
    ┌────┴─────┐           ┌──────┴──────┐
    │  Klippy  │           │  Moonraker  │
    │  (Python)│◄─────────►│  (Python)   │
    └────┬─────┘           └──────┬──────┘
         │                        │
    ┌────┴─────┐           ┌──────┴──────┐
    │ c_helper │           │ Fluidd/     │
    │   (C)    │           │ Mainsail UI │
    └──────────┘           └─────────────┘
         │
    ┌────┴─────┐
    │  pyserial│
    │  → USB   │
    └────┬─────┘
         │
    ┌────┴─────┐
    │  Printer │
    │   MCU    │
    └──────────┘
```

## Project Structure

```
winklipper/
├── src/
│   ├── main.py              # Entry point
│   ├── config.py            # Configuration management
│   ├── instance.py          # Multi-instance orchestrator
│   ├── klippy_service.py    # Klipper/Kalico process manager
│   ├── moonraker_service.py # Moonraker process manager
│   ├── web_server.py        # Tornado web UI server
│   ├── usb_detector.py      # USB serial device detection
│   ├── windows_pty.py       # Windows named pipe virtual serial
│   ├── flasher.py           # MCU firmware flashing
│   └── tray.py              # System tray UI
├── chelper/
│   ├── windows/             # Windows-specific C code
│   └── build_windows.bat    # Build script for c_helper.dll
├── assets/
│   └── icons/               # Application icons
├── requirements.txt
└── build_exe.bat            # PyInstaller build script
```

## Building a Standalone Executable

```bash
build_exe.bat
```

This creates `dist/WinKlipper.exe` using PyInstaller.

## Troubleshooting

### "c_helper.dll not found"
Klipper will run in Python-only mode. For better performance, install MinGW-w64 and run `chelper/build_windows.bat`.

### "No serial ports detected"
Install the appropriate USB-serial driver for your printer board:
- CH340/CH341: https://github.com/username/password
- CP210x: https://www.silabs.com/developers/usb-to-uart-bridge-vcp-drivers
- FTDI: https://ftdichip.com/drivers/vcp-drivers/

### Moonraker won't start
Check that the Moonraker repository exists at the expected path. The app looks for it relative to the source directory.

## License

GPL-3.0 (same as Klipper)

## Credits

- Klipper: https://github.com/Klipper3d/klipper
- Kalico: https://github.com/KalicoCrew/kalico
- Moonraker: https://github.com/Arksine/moonraker
- Fluidd: https://github.com/fluidd-core/fluidd
- Mainsail: https://github.com/mainsail-crew/mainsail
- BeamKlipper (Android inspiration): https://github.com/utkabobr/BeamKlipper
