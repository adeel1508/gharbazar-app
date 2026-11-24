"""Update Firestore app_config/version after a successful APK release.

Reads the service-account JSON from the FIREBASE_SERVICE_ACCOUNT env var,
mints an OAuth access token, and PATCHes the document with the new
latestVersionCode and apkUrl. The installed app reads exactly these two
fields on launch and prompts for an update when latestVersionCode is higher
than its own versionCode and apkUrl is non-empty.

Never prints the service-account JSON or the access token; logs only the
HTTP status and the values written.
"""

import json
import os
import sys
import urllib.request

from google.auth.transport.requests import Request
from google.oauth2 import service_account

PROJECT = "gharbazar-d8f79"
DOC_PATH = f"projects/{PROJECT}/databases/(default)/documents/app_config/version"
SCOPES = ["https://www.googleapis.com/auth/datastore"]


def main() -> int:
    sa_json = os.environ.get("FIREBASE_SERVICE_ACCOUNT", "")
    version_code = os.environ.get("VERSION_CODE", "")
    apk_url = os.environ.get("APK_URL", "")
    if not sa_json or not version_code or not apk_url:
        print(
            "Missing FIREBASE_SERVICE_ACCOUNT, VERSION_CODE or APK_URL",
            file=sys.stderr,
        )
        return 1
    try:
        version_code_int = int(version_code)
    except ValueError:
        print(f"VERSION_CODE is not an integer: {version_code!r}", file=sys.stderr)
        return 1

    creds = service_account.Credentials.from_service_account_info(
        json.loads(sa_json),
        scopes=SCOPES,
    )
    creds.refresh(Request())
    token = creds.token

    body = {
        "fields": {
            "latestVersionCode": {"integerValue": str(version_code_int)},
            "apkUrl": {"stringValue": apk_url},
        }
    }
    url = (
        "https://firestore.googleapis.com/v1/"
        + DOC_PATH
        + "?updateMask.fieldPaths=latestVersionCode&updateMask.fieldPaths=apkUrl"
    )
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        },
        method="PATCH",
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        print("Firestore update HTTP", resp.status)
        print("Wrote latestVersionCode =", version_code_int)
        print("Wrote apkUrl =", apk_url)
        print(resp.read().decode("utf-8")[:500])
    return 0


if __name__ == "__main__":
    sys.exit(main())
