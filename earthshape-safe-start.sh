#!/usr/bin/env bash
# Restarts a NeoForge server after isolating the optional mod named by its most
# recent construction-time crash. Usage: ./earthshape-safe-start.sh ./run.sh nogui
set -uo pipefail

if (( $# == 0 )); then
  echo "Usage: $0 <server command> [arguments...]" >&2
  exit 64
fi

mods_dir="${EARTHSHAPE_MODS_DIR:-mods}"
disabled_dir="${EARTHSHAPE_DISABLED_MODS_DIR:-mods-disabled-by-earthshape}"
max_retries="${EARTHSHAPE_MAX_CRASH_RETRIES:-5}"
mkdir -p -- "$disabled_dir"

latest_diagnostic() {
  local newest=""
  if [[ -d crash-reports ]]; then
    newest="$(find crash-reports -maxdepth 1 -type f -name 'crash-*.txt' -newer "$start_marker" -printf '%T@ %p\n' 2>/dev/null | sort -nr | head -n1 | cut -d' ' -f2-)"
  fi
  if [[ -n "$newest" ]]; then
    printf '%s\n' "$newest"
  elif [[ -f logs/latest.log && logs/latest.log -nt "$start_marker" ]]; then
    printf '%s\n' 'logs/latest.log'
  fi
}

find_failed_mod_id() {
  local diagnostic="$1"
  sed -nE \
    -e 's/.*Failed to create mod instance\. ModID: ([A-Za-z0-9_.-]+).*/\1/p' \
    -e 's/^[[:space:]]*Mod ID:[[:space:]]*([A-Za-z0-9_.-]+).*/\1/p' \
    "$diagnostic" | tail -n1
}

find_failed_jar() {
  local diagnostic="$1" mod_id="$2" direct base jar manifest
  if [[ -n "$mod_id" ]] && command -v unzip >/dev/null 2>&1; then
    while IFS= read -r -d '' jar; do
      manifest="$(unzip -p "$jar" META-INF/neoforge.mods.toml 2>/dev/null || true)"
      if grep -Eiq "modId[[:space:]]*=[[:space:]]*['\"]${mod_id}['\"]" <<<"$manifest"; then
        printf '%s\n' "$jar"
        return 0
      fi
    done < <(find "$mods_dir" -maxdepth 1 -type f -name '*.jar' -print0)
  fi

  direct="$(sed -nE 's/^[[:space:]]*Mod file:[[:space:]]*(.*\.jar)[[:space:]]*$/\1/p' "$diagnostic" | tail -n1)"
  direct="${direct%$'\r'}"
  base="$(basename -- "$direct")"
  if [[ -n "$direct" && -f "$mods_dir/$base" ]]; then
    printf '%s\n' "$mods_dir/$base"
    return 0
  fi
  return 1
}

for ((attempt=0; attempt<=max_retries; attempt++)); do
  start_marker="$(mktemp)"
  trap 'rm -f -- "$start_marker"' EXIT
  "$@"
  status=$?
  (( status == 0 )) && exit 0
  (( attempt == max_retries )) && exit "$status"

  diagnostic="$(latest_diagnostic)"
  if [[ -z "$diagnostic" ]]; then
    echo "[EarthShape safe-start] No crash report or latest.log was found; not disabling an unknown mod." >&2
    exit "$status"
  fi

  mod_id="$(find_failed_mod_id "$diagnostic")"
  case "$mod_id" in
    minecraft|neoforge|earthshape)
      echo "[EarthShape safe-start] Refusing to isolate protected mod '$mod_id'." >&2
      exit "$status"
      ;;
  esac

  failed_jar="$(find_failed_jar "$diagnostic" "$mod_id")"
  if [[ -z "$failed_jar" ]]; then
    echo "[EarthShape safe-start] Could not identify the failed mod JAR from $diagnostic." >&2
    exit "$status"
  fi

  destination="$disabled_dir/$(date +%Y%m%d-%H%M%S)-$(basename -- "$failed_jar")"
  mv -- "$failed_jar" "$destination"
  echo "[EarthShape safe-start] Isolated failed mod '${mod_id:-unknown}': $failed_jar -> $destination" >&2
  echo "[EarthShape safe-start] Restarting server without that JAR." >&2
  rm -f -- "$start_marker"
  trap - EXIT
done
