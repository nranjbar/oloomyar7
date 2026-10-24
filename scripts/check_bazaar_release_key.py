"""Exercise real Gradle release-key guards, restoring checkout files in all cases."""
import json
import os
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / 'config/bazaar-grade7.properties'
LOCAL = ROOT / 'local.properties'
OUT = ROOT / 'app/build/bazaar-key-verification'


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    original = CONFIG.read_bytes()
    local_original = LOCAL.read_bytes() if LOCAL.exists() else None
    fingerprint = re.search(rb'^GRADE7_BAZAAR_RSA_PUBLIC_KEY_SHA256=(\S+)$', original, re.M).group(1).decode()
    results = []

    def run_case(name, task, expected_message, succeeds, arguments=(), environment=None):
        result = subprocess.run(
            [str(ROOT / 'gradlew'), '--no-daemon', task, *arguments],
            cwd=ROOT, env=environment, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, timeout=300,
        )
        (OUT / (name + '.log')).write_text(result.stdout)
        valid = (result.returncode == 0) == succeeds and expected_message in result.stdout
        results.append({'name': name, 'status': 'PASS' if valid else 'FAIL',
                        'exitCode': result.returncode, 'expectedSuccess': succeeds})
        if not valid:
            raise AssertionError(f'{name}: unexpected Gradle result; see {OUT / (name + ".log")}')

    try:
        CONFIG.write_bytes(re.sub(rb'^GRADE7_BAZAAR_RSA_PUBLIC_KEY=.*$',
            b'GRADE7_BAZAAR_RSA_PUBLIC_KEY=invalid-key-for-release-test', original, flags=re.M))
        run_case('malformed-tracked-key', ':app:preReleaseBuild',
            'Invalid Grade 7 Bazaar RSA key in config/bazaar-grade7.properties.', False)

        CONFIG.write_bytes(re.sub(rb'^GRADE7_BAZAAR_RSA_PUBLIC_KEY_SHA256=.*$',
            b'GRADE7_BAZAAR_RSA_PUBLIC_KEY_SHA256=' + b'0' * 64, original, flags=re.M))
        run_case('mismatched-key-fingerprint', ':app:preReleaseBuild',
            'Grade 7 Bazaar RSA fingerprint mismatch in config/bazaar-grade7.properties.', False)

        CONFIG.write_bytes(original)
        LOCAL.write_bytes((local_original or b'') + b'\nGRADE7_BAZAAR_RSA_PUBLIC_KEY=obsolete-local-key\n')
        environment = dict(os.environ, GRADE7_BAZAAR_RSA_PUBLIC_KEY='obsolete-environment-key')
        run_case('legacy-overrides-cannot-replace-tracked-key', ':app:verifyBazaarReleaseConfiguration',
            fingerprint, True, ('-PGRADE7_BAZAAR_RSA_PUBLIC_KEY=obsolete-gradle-key',), environment)
    finally:
        CONFIG.write_bytes(original)
        if local_original is None:
            LOCAL.unlink(missing_ok=True)
        else:
            LOCAL.write_bytes(local_original)
        report = {'status': 'PASS' if len(results) == 3 and all(r['status'] == 'PASS' for r in results) else 'FAIL',
                  'tests': results, 'publicKeyDerSha256': fingerprint,
                  'trackedConfigurationRestored': CONFIG.read_bytes() == original,
                  'localPropertiesRestored': (LOCAL.read_bytes() if LOCAL.exists() else None) == local_original}
        (OUT / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
