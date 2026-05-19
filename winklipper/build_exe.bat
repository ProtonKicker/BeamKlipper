@echo off
REM Build WinKlipper as a standalone Windows executable using PyInstaller
REM Uses --onedir for fast builds, then optionally packages to --onefile later

echo ============================================
echo Building WinKlipper
echo ============================================

REM Check Python
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Python not found
    pause
    exit /b 1
)

REM Install only what we need
echo Installing dependencies...
pip install -r requirements.txt >nul 2>&1
pip install pyinstaller >nul 2>&1

REM Clean previous builds
if exist "dist" rmdir /s /q dist
if exist "build" rmdir /s /q build
if exist "WinKlipper.spec" del /q WinKlipper.spec

echo.
echo Running PyInstaller (--onedir for fast build)...
pyinstaller ^
    --name WinKlipper ^
    --onedir ^
    --windowed ^
    --icon=assets\icons\icon.ico ^
    --add-data "src;src" ^
    --hidden-import=serial ^
    --hidden-import=serial.tools.list_ports ^
    --hidden-import=tornado ^
    --hidden-import=tornado.web ^
    --hidden-import=tornado.websocket ^
    --hidden-import=pystray ^
    --hidden-import=pystray._win32 ^
    --hidden-import=PIL ^
    --hidden-import=win32pipe ^
    --hidden-import=win32file ^
    --hidden-import=psutil ^
    --exclude-module IPython ^
    --exclude-module PyQt5 ^
    --exclude-module PyQt6 ^
    --exclude-module tkinter ^
    --exclude-module numpy ^
    --exclude-module matplotlib ^
    --exclude-module scipy ^
    --exclude-module pandas ^
    --exclude-module pygame ^
    --exclude-module jupyter ^
    --exclude-module notebook ^
    --exclude-module spyder ^
    --collect-all pystray ^
    --noconfirm ^
    src\main.py

if exist "dist\WinKlipper\WinKlipper.exe" (
    echo.
    echo ============================================
    echo SUCCESS: WinKlipper built in dist\WinKlipper\
    echo ============================================
    echo.
    echo To run: dist\WinKlipper\WinKlipper.exe
    echo.
    echo For a single .exe file, run:
    echo   pyinstaller --onefile WinKlipper.spec
    echo (This will take longer but produces one file)
) else (
    echo.
    echo ============================================
    echo FAILED: Build error - exe not found
    echo ============================================
)

pause
