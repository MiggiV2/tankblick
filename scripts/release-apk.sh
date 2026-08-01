#!/usr/bin/env bash
#
# Builds a signed release APK, names it after what is actually inside it, and
# offers to publish it to your own F-Droid repo.
#
# See usage() below, or run with --help. Tests for the parsing live next door in
# release-apk.test.sh; everything in this file above main() is written to be
# sourceable without side effects.
#
# None of this is what f-droid.org does. That rebuilds from source and signs
# with its own key. See RELEASING.md.
set -euo pipefail

readonly APK_IN=app/build/outputs/apk/release/app-release.apk
readonly OUT_DIR=build/release
readonly DEFAULT_FDROID_REPO=$HOME/git/private/fdroid-repo

die() {
  printf '%s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Builds a signed release APK and offers to publish it.

  scripts/release-apk.sh [--publish|--no-publish] [gradle args...]

Output: build/release/tankblick-<versionCode>-<abi>-release.apk

The version code and the ABIs are read back out of the finished APK, so the
name can never disagree with what is in the file.

Options:
  --publish      Publish without asking. Fails if the repo script is missing.
  --no-publish   Build only, ask nothing.
  -h, --help     This text.

Anything else goes to Gradle unchanged, in order.

To bake an API key into the build, export it rather than passing -P: the
Gradle command line is echoed, the environment is not.

  export TANKBLICK_API_KEY=<uuid>

Environment:
  TANKBLICK_API_KEY       Key compiled into BuildConfig.API_KEY.
  TANKBLICK_FDROID_REPO   Checkout holding publish-fdroid.sh.
                          Default: ~/git/private/fdroid-repo
EOF
}

# Sets publish_mode and gradle_args. Unrecognised arguments are passed through
# in the order they arrived - that is how callers hand -P options to Gradle.
parse_args() {
  publish_mode=ask
  gradle_args=()
  while (($#)); do
    case $1 in
      --publish) publish_mode=yes ;;
      --no-publish) publish_mode=no ;;
      -h | --help)
        usage
        exit 0
        ;;
      *) gradle_args+=("$1") ;;
    esac
    shift
  done
}

sdk_dir() {
  if [[ -n ${ANDROID_HOME:-} ]]; then
    printf '%s' "$ANDROID_HOME"
  elif [[ -n ${ANDROID_SDK_ROOT:-} ]]; then
    printf '%s' "$ANDROID_SDK_ROOT"
  elif [[ -f local.properties ]]; then
    sed -n 's/^sdk\.dir=//p' local.properties
  else
    printf '%s' "$HOME/Android/Sdk"
  fi
}

# One field out of `aapt2 dump badging`, read from stdin. Anchored to the
# package line: versionCode sits next to platformBuildVersionCode, and an
# unanchored match would happily return the wrong one.
badging_field() {
  sed -n "s/^package:.*[ ']$1='\([^']*\)'.*/\1/p"
}

# The ABI slot for the file name, from a listing of lib/<abi>/<file> paths on
# stdin.
#
# Exactly one ABI means a split APK, and naming it is useful. Several means a
# universal APK - listing them all would read like a split while installing
# anywhere. None means no native code at all.
abi_slot() {
  local -a abis
  mapfile -t abis < <(cut -d/ -f2 | sort -u)
  if ((${#abis[@]} == 1)); then
    printf '%s' "${abis[0]}"
  else
    printf 'universal'
  fi
}

# The ABI slot for an APK on disk.
#
# unzip exits 11 when the pattern matches nothing, which is precisely the
# no-native-code case and not a failure. Left alone, pipefail and errexit turn
# it into an abort with no message, before the APK is even renamed - so it is
# swallowed here rather than at the call site, where it would be easy to lose
# again.
apk_abi_slot() {
  { unzip -Z1 "$1" 'lib/*' 2>/dev/null || true; } | abi_slot
}

main() {
  cd "$(dirname "${BASH_SOURCE[0]}")/.."

  parse_args "$@"

  # Without keystore.properties the release build quietly produces an unsigned
  # APK, which is deliberate so a fresh clone and CI can build. Here it is a
  # hard error: an unsigned APK named "-release" is a trap.
  [[ -f keystore.properties ]] ||
    die "keystore.properties is missing, so the APK would come out unsigned.
See RELEASING.md for the four properties it needs."

  local sdk
  sdk=$(sdk_dir) || die "Could not work out where the Android SDK is."
  [[ -d $sdk/build-tools ]] || die "No Android SDK build-tools under $sdk."

  # Newest build-tools wins. aapt2 and apksigner are stable enough that the
  # exact version does not matter for reading and verifying.
  local newest
  newest=$(command ls -1 "$sdk/build-tools" | sort -V | tail -1) ||
    die "Could not list $sdk/build-tools."
  local aapt2=$sdk/build-tools/$newest/aapt2
  local apksigner=$sdk/build-tools/$newest/apksigner
  local tool
  for tool in "$aapt2" "$apksigner"; do
    [[ -x $tool ]] || die "Not executable: $tool"
  done

  # Clean, because this artifact is the one people install. A stale
  # intermediate is not worth the minutes it saves, and RELEASING.md asks for
  # two clean builds to produce identical APKs.
  echo "==> ./gradlew clean assembleRelease ${gradle_args[*]-}"
  ./gradlew clean assembleRelease ${gradle_args[@]+"${gradle_args[@]}"}

  [[ -f $APK_IN ]] || die "Expected an APK at $APK_IN and found none."

  local badging version_code version_name abi
  badging=$("$aapt2" dump badging "$APK_IN")
  version_code=$(badging_field versionCode <<<"$badging")
  version_name=$(badging_field versionName <<<"$badging")
  [[ -n $version_code ]] || die "Could not read versionCode from $APK_IN."
  abi=$(apk_abi_slot "$APK_IN")

  local apk_out=$OUT_DIR/tankblick-$version_code-$abi-release.apk
  mkdir -p "$OUT_DIR"
  cp "$APK_IN" "$apk_out"

  # An APK that is not signed cannot be installed, and the device is a slower
  # place to find that out.
  echo
  echo "==> apksigner verify"
  "$apksigner" verify --print-certs "$apk_out" |
    grep -E 'Signer #1 certificate (DN|SHA-256 digest)' ||
    die "apksigner could not verify $apk_out."

  echo
  echo "  file     $apk_out"
  echo "  version  $version_name ($version_code)"
  echo "  size     $(du -h "$apk_out" | cut -f1)"
  echo "  sha256   $(sha256sum "$apk_out" | cut -d' ' -f1)"

  publish "$apk_out"
}

# The repo script copies the APK into its repo/, re-signs the index and pushes
# to a live k3s cluster. That is not a side effect of asking for a build, so:
# only on request, and never without a terminal to ask at.
publish() {
  local apk_out=$1
  local repo=${TANKBLICK_FDROID_REPO:-$DEFAULT_FDROID_REPO}
  local script=$repo/publish-fdroid.sh

  case $publish_mode in
    no) return 0 ;;
    ask)
      [[ -t 0 ]] || return 0
      if [[ ! -x $script ]]; then
        echo
        echo "note: no publish script at $script, so nothing to offer."
        return 0
      fi
      echo
      local reply
      read -rp "Publish to your F-Droid repo at $repo? [y/N] " reply || reply=
      [[ $reply == [yY]* ]] || {
        echo "Not published."
        return 0
      }
      ;;
    yes)
      [[ -x $script ]] ||
        die "--publish was given but $script is not executable.
Set TANKBLICK_FDROID_REPO to the checkout that holds it."
      ;;
  esac

  # An absolute path: the publish script chdirs to its own directory before it
  # looks at its arguments.
  echo
  exec "$script" "$PWD/$apk_out"
}

if [[ ${BASH_SOURCE[0]} == "$0" ]]; then
  main "$@"
fi
