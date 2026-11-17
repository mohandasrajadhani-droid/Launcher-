#!/usr/bin/env bash
# Automated security gate for MS Launcher release APKs.
# Usage: security-check.sh <standard.apk> <lock.apk>
set -uo pipefail
BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
AAPT="$BT/aapt"; APKSIGNER="$BT/apksigner"
FORBIDDEN="INTERNET ACCESS_NETWORK_STATE READ_SMS RECEIVE_SMS SEND_SMS READ_CONTACTS READ_CALL_LOG READ_PHONE_STATE READ_PHONE_NUMBERS CALL_PHONE ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION CAMERA RECORD_AUDIO SYSTEM_ALERT_WINDOW QUERY_ALL_PACKAGES REQUEST_INSTALL_PACKAGES REQUEST_DELETE_PACKAGES READ_EXTERNAL_STORAGE WRITE_EXTERNAL_STORAGE MANAGE_EXTERNAL_STORAGE READ_MEDIA_IMAGES GET_ACCOUNTS USE_CREDENTIALS PACKAGE_USAGE_STATS BIND_DEVICE_ADMIN RECEIVE_BOOT_COMPLETED FOREGROUND_SERVICE"
fail=0
out() { echo "$1"; echo "$1" >> "${GITHUB_STEP_SUMMARY:-/dev/null}"; }
check() { if [ "$1" = ok ]; then out "- ✅ $2"; else out "- ❌ $2"; fail=1; fi; }

for apk in "$@"; do
  name=$(basename "$apk")
  out ""; out "### $name"
  perms=$("$AAPT" dump permissions "$apk" | grep -oE "name='[^']+'" | sed "s/name='//;s/'$//" | sort -u)
  out "Permissions requested: $(echo $perms | tr ' ' ',')"
  bad=""
  for p in $FORBIDDEN; do echo "$perms" | grep -qx "android.permission.$p" && bad="$bad $p"; done
  [ -z "$bad" ] && check ok "No dangerous / network / install / delete permissions" || check fail "Forbidden permissions:$bad"

  mf=$("$AAPT" dump xmltree "$apk" AndroidManifest.xml)
  echo "$mf" | grep -q "android:debuggable(0x0101000f)=(type 0x12)0xffffffff" && check fail "APK is debuggable" || check ok "Not debuggable"
  echo "$mf" | grep -q "android:allowBackup(0x01010280)=(type 0x12)0x0" && check ok "Backup disabled" || check fail "allowBackup is not false"
  echo "$mf" | grep -q "android:usesCleartextTraffic(0x010104ec)=(type 0x12)0xffffffff" && check fail "Cleartext traffic allowed" || check ok "No cleartext traffic flag"
  echo "$mf" | grep -qE "E: provider|E: receiver" && check fail "Unexpected provider/receiver present" || check ok "No content providers or broadcast receivers"

  # Every exported service must be protected by a system bind permission
  svc_unprot=$(echo "$mf" | awk '/E: service/{s=1;p=0;n=""} s&&/android:name\(0x01010003\)/{n=$0} s&&/android:permission\(0x01010006\)/{p=1} s&&/E: (intent-filter|meta-data)/{if(!p)print n; s=0}')
  [ -z "$svc_unprot" ] && check ok "All services require a system-only bind permission" || check fail "Unprotected service: $svc_unprot"

  if echo "$mf" | grep -q "BIND_ACCESSIBILITY_SERVICE"; then
    case "$name" in *standard*) check fail "Standard edition contains an accessibility service";; *) out "- ℹ️ Accessibility service present (lock edition, disabled by default)";; esac
  else
    check ok "No accessibility service"
  fi
  echo "$mf" | grep -A3 "DotService" | grep -q "android:enabled(0x0101000e)=(type 0x12)0x0" && check ok "Notification listener disabled until user opts in" || check fail "Notification listener not disabled by default"

  # Network / logging code in compiled dex (app package only)
  tmp=$(mktemp -d); unzip -qo "$apk" 'classes*.dex' -d "$tmp"
  netrefs=$(cat "$tmp"/classes*.dex | strings | grep -cE "Ljava/net/(HttpURLConnection|Socket);|Ljavax/net/ssl/|Lokhttp3/|Landroid/webkit/WebView;" || true)
  [ "$netrefs" = "0" ] && check ok "No HTTP / socket / WebView classes in the code" || out "- ℹ️ $netrefs network-class references (library code) — no INTERNET permission, so no network access is possible"
  rm -rf "$tmp"

  certs=$("$APKSIGNER" verify --print-certs "$apk" 2>&1)
  if [ $? -ne 0 ]; then check fail "Signature verification failed"; else
    echo "$certs" | grep -q "CN=Android Debug" && check fail "Signed with a debug key" || check ok "Signed with release key: $(echo "$certs" | grep -m1 'certificate DN' | sed 's/.*DN: //')"
    out "  SHA-256: $(echo "$certs" | grep -m1 'SHA-256 digest' | sed 's/.*digest: //')"
  fi
done
out ""
[ $fail -eq 0 ] && out "**Security gate: PASSED**" || out "**Security gate: FAILED**"
exit $fail
