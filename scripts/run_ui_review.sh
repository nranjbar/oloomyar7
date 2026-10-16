#!/usr/bin/env bash
set -uo pipefail
review_status=0
adb shell settings put global hide_error_dialogs 1
./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.oloomyar.app.Grade7WorkbookUiTest --stacktrace || review_status=$?
adb pull /sdcard/Download/grade7-screenshots app/build/grade7-screenshots || true
exit "$review_status"
