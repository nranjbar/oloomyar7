#!/usr/bin/env bash
set -uo pipefail
review_status=0
collect_review_artifacts() {
  mkdir -p app/build/grade7-screenshots
  timeout 20s adb pull /sdcard/Download/grade7-screenshots app/build/grade7-screenshots || true
  timeout 20s adb logcat -d -v threadtime > app/build/grade7-screenshots/device-logcat.txt || true
}
trap collect_review_artifacts EXIT
adb shell settings put global hide_error_dialogs 1
./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.oloomyar.app.Grade7WorkbookUiTest,com.oloomyar.app.Grade7DiscoveryUiTest,com.oloomyar.app.Grade7AllChaptersUiTest -Pandroid.testInstrumentationRunnerArguments.timeout_msec=600000 --stacktrace || review_status=$?
adb pull /sdcard/Download/grade7-screenshots app/build/grade7-screenshots || true
if [ "$review_status" -eq 0 ]; then
  python3 scripts/check_install_and_process_restore.py || review_status=$?
  adb pull /sdcard/Download/grade7-screenshots app/build/grade7-screenshots || true
fi
exit "$review_status"
