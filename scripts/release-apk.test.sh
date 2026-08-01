#!/usr/bin/env bash
#
# Tests for the pure logic in release-apk.sh: argument parsing, reading fields
# out of aapt2 output, and picking the ABI slot for the file name.
#
# Deliberately no Gradle, no Android SDK, no signing key - those parts are
# verified by running the real thing. What is covered here is the text handling
# that would otherwise only ever be checked by eye.
#
#   scripts/release-apk.test.sh
set -uo pipefail

cd "$(dirname "$0")"

# shellcheck source=release-apk.sh
source ./release-apk.sh
# The script under test turns on errexit; a failing check should be reported,
# not abort the run.
set +e

declare -i passed=0 failed=0

check() {
  local what=$1 want=$2 got=$3
  if [[ $want == "$got" ]]; then
    passed+=1
  else
    failed+=1
    printf 'FAIL  %s\n        want: %q\n        got:  %q\n' "$what" "$want" "$got"
  fi
}

# --- argument parsing ------------------------------------------------------
#
# Unrecognised arguments have to reach Gradle untouched and in order, because
# that is how a caller passes -P options through.
parse() {
  publish_mode=
  gradle_args=()
  parse_args "$@"
}

parse
check "no args: mode" ask "$publish_mode"
check "no args: nothing for gradle" 0 "${#gradle_args[@]}"

parse --publish
check "--publish" yes "$publish_mode"

parse --no-publish
check "--no-publish" no "$publish_mode"

parse -Ptankblick.apiKey=abc --publish
check "flag after a gradle arg: mode" yes "$publish_mode"
check "flag after a gradle arg: passthrough" "-Ptankblick.apiKey=abc" "${gradle_args[*]}"

parse --no-publish -Pfoo=1 -Pbar=2
check "several gradle args keep their order" "-Pfoo=1 -Pbar=2" "${gradle_args[*]}"

parse --publish --no-publish
check "last flag wins" no "$publish_mode"

# --- reading aapt2 output --------------------------------------------------
#
# Real first line of `aapt2 dump badging` for this app, kept verbatim so a
# change in aapt2's output shape shows up here rather than in a wrong file name.
readonly BADGING="package: name='de.mymiggi.tankblick' versionCode='2' versionName='0.2.0' platformBuildVersionName='17' platformBuildVersionCode='37' compileSdkVersion='37' compileSdkVersionCodename='17'
minSdkVersion:'24'
targetSdkVersion:'36'"

check "versionCode" 2 "$(badging_field versionCode <<<"$BADGING")"
check "versionName" 0.2.0 "$(badging_field versionName <<<"$BADGING")"
check "missing field is empty" "" "$(badging_field nosuchfield <<<"$BADGING")"

# A four-digit code, so nothing is truncated or matched too eagerly.
check "four digit versionCode" 2002 \
  "$(badging_field versionCode <<<"package: name='x' versionCode='2002' versionName='1.2.3'")"

# What the [ '] anchor is for. The decoy ends with the field being read and sits
# to the right, so the greedy match reaches it first: drop the anchor and this
# returns 99. The real aapt2 line has platformBuildVersionCode, which differs in
# case and so never actually collides - this is the case that would.
check "a field ending in the wanted name is not mistaken for it" 2 \
  "$(badging_field versionCode <<<"package: versionCode='2' platformversionCode='99'")"

# --- ABI slot --------------------------------------------------------------
#
# One ABI means a split APK and the ABI belongs in the name. Several means a
# universal APK, and listing them all would read like a split that installs
# anywhere. None means no native code at all.
check "four ABIs are universal" universal "$(abi_slot <<'EOF'
lib/arm64-v8a/libandroidx.graphics.path.so
lib/arm64-v8a/libdatastore_shared_counter.so
lib/armeabi-v7a/libandroidx.graphics.path.so
lib/x86/libandroidx.graphics.path.so
lib/x86_64/libandroidx.graphics.path.so
EOF
)"

check "a single ABI names itself" arm64-v8a "$(abi_slot <<'EOF'
lib/arm64-v8a/libfoo.so
lib/arm64-v8a/libbar.so
EOF
)"

check "no native code is universal" universal "$(abi_slot </dev/null)"

# --- ABI slot, through the real invocation ---------------------------------
#
# abi_slot on its own cannot catch the trap here. unzip exits 11 when nothing
# matches lib/*, and under pipefail that aborted the whole script with no
# message - before the APK was renamed or its signature checked. It only stayed
# hidden because this build happens to bundle two native libraries that came in
# with Compose and DataStore. A dependency bump dropping them would have broken
# releases silently, so this goes through apk_abi_slot with real zip files.
fixture_dir=$(mktemp -d)
trap 'rm -rf "$fixture_dir"' EXIT

(
  cd "$fixture_dir"
  mkdir -p lib/arm64-v8a lib/x86
  : > lib/arm64-v8a/libfoo.so
  : > lib/x86/libfoo.so
  : > classes.dex
  zip -q -r two-abis.apk classes.dex lib
  zip -q -r one-abi.apk classes.dex lib/arm64-v8a
  zip -q no-lib.apk classes.dex
)

check "apk with two ABIs" universal "$(apk_abi_slot "$fixture_dir/two-abis.apk")"
check "apk with one ABI" arm64-v8a "$(apk_abi_slot "$fixture_dir/one-abi.apk")"
check "apk without native code" universal "$(apk_abi_slot "$fixture_dir/no-lib.apk")"

# The exit status matters as much as the output: this is what aborted the run.
(
  set -euo pipefail
  abi=$(apk_abi_slot "$fixture_dir/no-lib.apk")
  [[ $abi == universal ]]
)
check "apk without native code does not abort under errexit" 0 "$?"

# --- result ----------------------------------------------------------------
printf '\n%d passed, %d failed\n' "$passed" "$failed"
((failed == 0))
