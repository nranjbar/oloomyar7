#!/usr/bin/env bash
set -uo pipefail
review_status=0
adb shell settings put global hide_error_dialogs 1
./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.oloomyar.app.Grade7WorkbookUiTest,com.oloomyar.app.Grade7DiscoveryUiTest --stacktrace || review_status=$?
adb pull /sdcard/Download/grade7-screenshots app/build/grade7-screenshots || true
if [ "$review_status" -eq 0 ]; then
  python3 scripts/check_install_and_process_restore.py || review_status=$?
  adb pull /sdcard/Download/grade7-screenshots app/build/grade7-screenshots || true
fi
exit "$review_status"
