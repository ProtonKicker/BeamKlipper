# Agent Session Memory

## Project: BeamKlipper → BeamKalico

### Build Environment
- System Python 3.14/pip 26.x lacks `pip._vendor.six` — use `/tmp/python/bin/python3.10` with pip 23.3.2
- JDK: `/tmp/jdk-17.0.14+7/bin/java`
- Gradle wrapper with Java 17, Chaquopy 15.0.1
- Custom wheels dir: `wheels/` (in-repo, absolute path `file("wheels").absolutePath` in build.gradle)

### Python Dependencies
- Chaquopy cannot compile native C extensions — all wheels must be pure-Python (`py3-none-any`)
- Custom `msgpack-1.1.0-py3-none-any.whl` in `wheels/` (pure-Python replacement)
- Custom `streaming_form_data-1.15.0-py3-none-any.whl` in `wheels/` (contains pure-Python `_parser.py` instead of Cython C extension)
- Source file: `wheels/_parser.py`
- Transitive deps resolved by Chaquopy: `smart_open`, `wrapt` downloaded automatically

### APK Build & Deploy
- Build (debug): `./gradlew assembleDebug -Dorg.gradle.java.home=/tmp/jdk-17.0.14+7` (set PYTHONHOME=/tmp/python first)
- Build (release): `./gradlew assembleRelease -Dorg.gradle.java.home=/tmp/jdk-17.0.14+7`
- Debug APK: `app/build/outputs/apk/debug/BeamKalico_<hash>.apk`
- Release APK: `app/build/outputs/apk/release/BeamKalico_<hash>.apk`
- Deploy: adb install (if device detected) or manual via web/telegram
- Debug APK allows `run-as` for internal file access
- Release APK: `minifyEnabled false`, signed with debug keystore
- APK also copied to `beamklipper/` at repo root for direct access (excluded from git, >100MB)

### Moonraker Debugging
- Moonraker log: `/data/data/ru.ytk0abp.beamklipper/files/instance/<uuid>/public/logs/moonraker.log`
- Moonraker config: `/data/data/ru.ytk0abp.beamklipper/files/instance/<uuid>/public/config/moonraker.conf`
- Run-as commands work after `adb shell run-as ru.ytk0abp.beamklipper`
- Check if listening: `netstat -tnlp 2>/dev/null` or `ss -tnlp`
- Port 7125 default for Moonraker
- Component dirs listed in `app/src/main/moonraker/`

### Hidden-File Filter in bundleKlipper
- `klipper_tmc_autotune` submodule contains `.github/scripts/motor_database_validator.py`
- AAPT silently excludes hidden files (starting with `.`) from APK
- `BundleInstaller` reads `index.json` entries → file not found in APK → crash
- Fix: add `if (sub.startsWith(".") || sub.contains("/.")) return` to all `bundleKlipper` file-processing sections

### APK Build Issues
- `./gradlew tasks` fails: "Could not determine Java version" — needs JDK 17 exactly
- Workaround: rebuild with `./gradlew assembleDebug` using JDK 17
- First build after new deps: always use debug first to check for import errors
- Portable Python at `/tmp/python` compiled with `--prefix=/install` — Chaquopy's `-S` flag bypasses `PYTHONHOME` detection, so `sys.exec_prefix` becomes `/install` and `generateDebugPythonRequirements` fails with `ModuleNotFoundError: No module named 'encodings'`
- Fix: set `PYTHONHOME=/tmp/python` in environment before running Gradle, and kill existing daemon first
