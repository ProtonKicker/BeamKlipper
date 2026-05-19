@echo off
REM Build c_helper.dll for Windows using MinGW-w64
REM Run this from the winklipper directory

echo ============================================
echo Building c_helper.dll for Windows
echo ============================================

REM Find gcc
where gcc >nul 2>&1
if %errorlevel% neq 0 (
    echo gcc not found in PATH. Checking common locations...
    if exist "C:\msys64\mingw64\bin\gcc.exe" (
        set "GCC=C:\msys64\mingw64\bin\gcc.exe"
    ) else if exist "C:\mingw64\bin\gcc.exe" (
        set "GCC=C:\mingw64\bin\gcc.exe"
    ) else (
        echo ERROR: gcc not found. Install MinGW-w64:
        echo   https://www.mingw-w64.org/downloads/
        echo   or MSYS2: https://www.msys2.org/
        pause
        exit /b 1
    )
) else (
    set "GCC=gcc"
)

echo Using: %GCC%

REM Determine klipper source path
set "KLIPPER_DIR=%~dp0..\klipper-master\klipper-master"
if not exist "%KLIPPER_DIR%" (
    set "KLIPPER_DIR=%~dp0..\kalico-main\kalico-main"
)

set "CHELPER_DIR=%KLIPPER_DIR%\klippy\chelper"

if not exist "%CHELPER_DIR%" (
    echo ERROR: chelper directory not found at %CHELPER_DIR%
    pause
    exit /b 1
)

echo Source: %CHELPER_DIR%

REM Source files
set "SOURCES=pyhelper.c serialqueue.c stepcompress.c steppersync.c itersolve.c trapq.c pollreactor.c msgblock.c trdispatch.c kin_cartesian.c kin_corexy.c kin_corexz.c kin_delta.c kin_deltesian.c kin_polar.c kin_rotary_delta.c kin_winch.c kin_extruder.c kin_shaper.c kin_idex.c kin_generic.c"

REM Check for Windows-specific modifications
set "WIN_DIR=%~dp0windows"
if exist "%WIN_DIR%\pyhelper_win.c" (
    echo Using Windows-modified pyhelper.c
    set "PYHELPER=%WIN_DIR%\pyhelper_win.c"
) else (
    set "PYHELPER=%CHELPER_DIR%\pyhelper.c"
)

REM Build command
set "OUTPUT=%CHELPER_DIR%\c_helper.dll"

echo.
echo Compiling...
%GCC% -Wall -g -O2 -shared -fPIC -mfpmath=sse -msse2 ^
    -o "%OUTPUT%" ^
    "%PYHELPER%" ^
    "%CHELPER_DIR%\serialqueue.c" ^
    "%CHELPER_DIR%\stepcompress.c" ^
    "%CHELPER_DIR%\steppersync.c" ^
    "%CHELPER_DIR%\itersolve.c" ^
    "%CHELPER_DIR%\trapq.c" ^
    "%CHELPER_DIR%\pollreactor.c" ^
    "%CHELPER_DIR%\msgblock.c" ^
    "%CHELPER_DIR%\trdispatch.c" ^
    "%CHELPER_DIR%\kin_cartesian.c" ^
    "%CHELPER_DIR%\kin_corexy.c" ^
    "%CHELPER_DIR%\kin_corexz.c" ^
    "%CHELPER_DIR%\kin_delta.c" ^
    "%CHELPER_DIR%\kin_deltesian.c" ^
    "%CHELPER_DIR%\kin_polar.c" ^
    "%CHELPER_DIR%\kin_rotary_delta.c" ^
    "%CHELPER_DIR%\kin_winch.c" ^
    "%CHELPER_DIR%\kin_extruder.c" ^
    "%CHELPER_DIR%\kin_shaper.c" ^
    "%CHELPER_DIR%\kin_idex.c" ^
    "%CHELPER_DIR%\kin_generic.c" ^
    -lws2_32

if %errorlevel% equ 0 (
    echo.
    echo ============================================
    echo SUCCESS: c_helper.dll built at %OUTPUT%
    echo ============================================
) else (
    echo.
    echo ============================================
    echo FAILED: Compilation error
    echo ============================================
)

pause
