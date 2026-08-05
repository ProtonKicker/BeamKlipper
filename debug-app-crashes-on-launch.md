# Debug Session: app-crashes-on-launch

Status: [OPEN]

## Bug Description
- **Symptom**: BeamKlipper app quits by itself immediately; user cannot open it (after the Material 3 Express UI overhaul).
- **Expected**: App launches and shows the redesigned home screen with profile cards / empty state.
- **Environment**: Android 16 SDK 36 (Xiaomi `25053RT47C`), debug build, installed via ADB.
- **Regression window**: Last build that the user verified worked was before the most recent "complete overhaul" pass (new cards, empty state, section header, lavender wash, palette bump).

## Hypotheses (falsifiable)
1. **H1 - Inflation / Resource exception in a new View**: one of the new components (`EmptyStateView`, `SectionHeaderView`, `StatusChipView`, `MainHeaderView`) hits a missing resource / bad attribute at construction time, crashing the Activity in `onCreate`.
2. **H2 - RecyclerView adapter index / view-type bug**: new multi-type adapter logic (`VIEW_TYPE_HEADER / SECTION / EMPTY / INSTANCE`) indexes wrong position (e.g., empty list tries to instantiate or bind wrong view type) → `IndexOutOfBoundsException` / `IllegalStateException` during first layout pass.
3. **H3 - Incorrect import / missing import or attribute**: e.g., `Color.WHITE` / unresolved `iconSize` reassignment / missing drawable (`ic_external_link_outline_24` or `ic_search_outline_28`) causes `Resources.NotFoundException` when layouts/views inflate.
4. **H4 - Start/stop state or web-tile logic**: `bind()` path (or `bindWeb()` if web tile is being drawn at the top) invokes a deprecated / null WifiManager path and throws SecurityException or NPE on SDK 36.
5. **H5 - Coroutines scope / DB access on main thread**: a new code path accidentally calls `KlipperApp.DATABASE.delete/update` or filters without main-thread guards, causing a crash (less likely but possible if new empty/header path accesses data eagerly).

## Evidence Plan
- **Step A**: Run `adb logcat -c` then launch the app via `adb shell monkey -p ru.ytkab0bp.beamklipper -c android.intent.category.LAUNCHER 1` (or explicit `am start`) and collect the FATAL EXCEPTION stack trace.
- **Step B**: If no clear stack, add minimal instrumentation points at the top of `MainActivity.onCreate` (start / adapter created / first bind / view-holders created) to report which step executes last before the crash.
- **Step C**: Isolate via hypothesis: disable new views one-by-one (EmptyState → SectionHeader → StatusChip) until the crash disappears, confirming the culprit.

## Logs & Evidence
*(to be filled in Step 2–6)*
- Pre-fix crash trace: TBD
- Post-fix logcat: TBD

## Root Cause
TBD

## Fix
TBD

## Verification
TBD
