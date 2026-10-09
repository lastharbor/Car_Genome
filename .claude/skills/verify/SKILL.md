---
name: verify
description: Run the CarGenome "done" checklist - targeted tests, full unit tests, debug build, and an on-device check for UI changes. Use before declaring a task finished or before committing.
---

# Verify a change

1. Pick the narrowest proof first: the test class for the changed code
   (`python scripts/ai/gradle.py :app:testDebugUnitTest --tests "*XxxTest"` or `:core:vin:test`).
2. Then the full gate in one Gradle call, delegated to the `build-runner` subagent so its output stays out of this context:
   `python scripts/ai/gradle.py :core:vin:test :app:testDebugUnitTest :app:assembleDebug`
   Add `:app:lintDebug` when resources, manifest or strings changed.
3. UI change and a device is attached. Preferred: `mobile` MCP — `mobile_install_app`, `mobile_launch_app`
   (`com.cargenome.app.debug`), then `mobile_list_elements_on_screen` to check texts and tap targets.
   Fallback without MCP:
   ```
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.cargenome.app.debug/com.cargenome.app.MainActivity
   adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml ui.xml
   python tools/ui_nodes.py ui.xml
   ```
   Prefer the text dump over screenshots (a screenshot costs far more tokens). Take a screenshot
   (`adb exec-out screencap -p > screen_check.png`, then Read it) only when layout or visuals are the point.
   If the package name differs, read `applicationId`/`applicationIdSuffix` in `app/build.gradle.kts`.
4. `git status --short`: nothing unexpected (no stray dumps, logs, scratch files).

Report: PASS/FAIL per step in one line each; on failure, the failing file:line and cause.
