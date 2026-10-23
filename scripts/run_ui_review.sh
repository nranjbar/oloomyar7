#!/usr/bin/env bash
set -uo pipefail
review_status=0
collect_review_artifacts() {
  mkdir -p app/build/grade7-screenshots
  local attempt export_dir copied=1
  # UTP can briefly reconnect adb after instrumentation finishes. Reconnect and
  # retry the export so a partial pull never becomes the review evidence.
  for attempt in 1 2 3; do
    timeout 10s adb reconnect offline >/dev/null 2>&1 || true
    if timeout 20s adb wait-for-device; then
      export_dir=$(mktemp -d app/build/grade7-device-export.XXXXXX)
      if timeout 30s adb pull /sdcard/Download/grade7-screenshots "$export_dir/"; then
        cp -a "$export_dir/grade7-screenshots/." app/build/grade7-screenshots/
        copied=0
      fi
      rm -rf -- "$export_dir"
      [ "$copied" -eq 0 ] && break
    fi
  done
  timeout 20s adb logcat -d -v threadtime > app/build/grade7-screenshots/device-logcat.txt || true
  return "$copied"
}
trap 'collect_review_artifacts || true' EXIT
adb shell settings put global hide_error_dialogs 1
# Always run the body and action-dock sweeps. A failed preceding build supplies
# no geometry evidence, even if its follow-up changes only test source.
full_body_review=1
./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.oloomyar.app.Grade7WorkbookUiTest,com.oloomyar.app.Grade7DiscoveryUiTest,com.oloomyar.app.Grade7AllChaptersUiTest,com.oloomyar.app.Grade7NativeContentUiTest,com.oloomyar.app.Grade7ZoomDirectionTest,com.oloomyar.app.Grade7PurchaseAccessUiTest,com.oloomyar.app.billing.PermanentReceiptStorageTest,com.oloomyar.app.billing.BazaarInventoryReaderTest -Pandroid.testInstrumentationRunnerArguments.timeout_msec=600000 --stacktrace || review_status=$?
collect_review_artifacts || { [ "$review_status" -ne 0 ] || review_status=1; }
if [ "$review_status" -eq 0 ]; then
  python3 scripts/check_install_and_process_restore.py || review_status=$?
  collect_review_artifacts || { [ "$review_status" -ne 0 ] || review_status=1; }
fi
if [ "$review_status" -eq 0 ] && [ "$full_body_review" -eq 1 ] && [ ! -s app/build/grade7-screenshots/native-layout-audit.json ]; then
  echo 'Missing the completed native geometry audit from the device.' >&2
  review_status=1
fi
exit "$review_status"
