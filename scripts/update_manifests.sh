#!/usr/bin/env bash
set -euo pipefail

# hpm CLI helper script: updates the packageManifest.json entries for apps and drivers
# - Matches entries by filename (not full URL)
# - Adds/removes entries based on files present under Hubitat/Hubspace/{app,drivers}
# - Populates --version from Groovy metadata or helper methods

HPM=/Users/neerpatel/workspace/repos/hubitat-packagemanagertools/HubitatPackageManagerTools/bin/Debug/net8.0/hpm 
PackageManifest=/Users/neerpatel/workspace/repos/Hubitat/Hubitat/Hubspace/packageManifest.json
GITHUBREPO=https://raw.githubusercontent.com/neerpatel/Hubitat/refs/heads/HubSpace/Hubitat/Hubspace
APP_FOLDER=/Users/neerpatel/workspace/repos/Hubitat/Hubitat/Hubspace/

# Diagnostics: check required files and directories
if [ ! -f "$PackageManifest" ]; then
    echo "ERROR: Package manifest not found: $PackageManifest" >&2
    exit 1
fi
if [ ! -d "${APP_FOLDER}drivers" ]; then
    echo "ERROR: Drivers folder not found: ${APP_FOLDER}drivers" >&2
    exit 1
fi
if [ ! -d "${APP_FOLDER}app" ]; then
    echo "ERROR: App folder not found: ${APP_FOLDER}app" >&2
    exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
    echo "ERROR: jq is required but not installed." >&2
    exit 1
fi


# Extract version from a Groovy file. Priority:
# 1) definition(..., version: 'x.y.z')
# 2) driverVer() { return 'x.y.z' }
# 3) appVersion() { return 'x.y.z' }
get_version() {
    local file="$1"
    local v=""
    # Prefer definition(...) version value; works with BSD sed (no \s)
    v=$(grep -Eo "version:[[:space:]]*['\"][^'\"]+['\"]" "$file" | head -n1 | sed -E "s/version:[[:space:]]*['\"]([^'\"]+)['\"]/\1/") || true
    if [ -z "$v" ]; then
        # driverVer() { return 'x' }
        v=$(awk '/driverVer\(\)/, /}/ {print}' "$file" | grep -Eo "['\"][^'\"]+['\"]" | head -n1 | tr -d '"' | tr -d "'") || true
    fi
    if [ -z "$v" ]; then
        # appVersion() { return 'x' }
        v=$(awk '/appVersion\(\)/, /}/ {print}' "$file" | grep -Eo "['\"][^'\"]+['\"]" | head -n1 | tr -d '"' | tr -d "'") || true
    fi
    echo "$v"
}

# Extract a quoted field value from definition(...) e.g., name / namespace.
# Falls back to a provided default if not present.
get_def_field() {
    local file="$1"; shift
    local key="$1"; shift
    local default_val="${1:-}"
    local val
    val=$(grep -Eo "${key}:[[:space:]]*'[^']+'|${key}:[[:space:]]*\"[^\"]+\"" "$file" | head -n1 | sed -E "s/${key}:[[:space:]]*['\"]([^'\"]+)['\"]/\1/") || true
    if [ -z "$val" ]; then
        echo "$default_val"
    else
        echo "$val"
    fi
}

echo "Cleaning up removed drivers from manifest (by filename)..."
while IFS= read -r loc; do
        base=$(basename "$loc")
        echo "Processing driver location: $loc"
        if [ ! -f "${APP_FOLDER}drivers/$base" ]; then
                id=$(jq -r --arg l "$loc" 'first(.drivers[]? | select(.location==$l) | .id) // empty' "$PackageManifest")
                echo "Found driver ID: $id"
                echo "Removing driver not found locally: $base (id=${id:-n/a})"
                if [ -n "$id" ]; then
                        "$HPM" manifest-remove-driver --id="$id" "$PackageManifest" || {
                            echo "ERROR: Failed to remove driver $base (id=$id) from manifest." >&2
                            exit 2
                        }
                else
                        name=$(jq -r --arg l "$loc" 'first(.drivers[]? | select(.location==$l) | .name) // empty' "$PackageManifest")
                        [ -n "$name" ] && "$HPM" manifest-remove-driver --name="$name" "$PackageManifest" || true
                fi
        fi
done < <(jq -r '.drivers[]? .location // empty' "$PackageManifest")

echo "Updating drivers in manifest (match by filename)..."
for driver in "${APP_FOLDER}"drivers/*.groovy; do
        [ -e "$driver" ] || continue
        fname=$(basename "$driver")
        url="$GITHUBREPO/drivers/$fname"
        ver=$(get_version "$driver")
        name=$(get_def_field "$driver" name "$fname")
        ns=$(get_def_field "$driver" namespace "neerpatel/hubspace")
        curLoc=$(jq -r --arg fn "$fname" 'first(.drivers[]? | select((.location|split("/")|last)==$fn) | .location) // empty' "$PackageManifest")
        if [ -n "$curLoc" ]; then
                if [ "$curLoc" != "$url" ]; then
                        echo "Replacing driver entry for $fname (location change)"
                        curId=$(jq -r --arg l "$curLoc" 'first(.drivers[]? | select(.location==$l) | .id) // empty' "$PackageManifest")
                        if [ -n "$curId" ]; then
                                "$HPM" manifest-remove-driver --id="$curId" "$PackageManifest" || {
                                    echo "ERROR: Failed to remove driver $fname (id=$curId) from manifest." >&2
                                    exit 2
                                }
                        else
                                curName=$(jq -r --arg l "$curLoc" 'first(.drivers[]? | select(.location==$l) | .name) // empty' "$PackageManifest")
                                [ -n "$curName" ] && "$HPM" manifest-remove-driver --name="$curName" "$PackageManifest" || true
                        fi
                        if [ -n "$ver" ]; then
                                "$HPM" manifest-add-driver --location="$url" --version="$ver" --name="$name" --namespace="$ns" "$PackageManifest" || {
                                    echo "ERROR: Failed to add driver $fname to manifest." >&2
                                    exit 2
                                }
                        else
                                "$HPM" manifest-add-driver --location="$url" --name="$name" --namespace="$ns" "$PackageManifest" || {
                                    echo "ERROR: Failed to add driver $fname to manifest." >&2
                                    exit 2
                                }
                        fi
                else
                        echo "Modifying driver: $fname version=${ver:-"(none)"}"
                        curId=$(jq -r --arg fn "$fname" 'first(.drivers[]? | select((.location|split("/")|last)==$fn) | .id) // empty' "$PackageManifest")
                        if [ -n "$curId" ]; then
                            if [ -n "$ver" ]; then
                                "$HPM" manifest-modify-driver --id="$curId" --location="$url" --name="$name" --version="$ver" "$PackageManifest" || { echo "ERROR: Failed to modify driver $fname" >&2; exit 2; }
                            else
                                "$HPM" manifest-modify-driver --id="$curId" --location="$url" --name="$name" "$PackageManifest" || { echo "ERROR: Failed to modify driver $fname" >&2; exit 2; }
                            fi
                        else
                            # Fallback by name
                            if [ -n "$ver" ]; then
                                "$HPM" manifest-modify-driver --name="$name" --location="$url" --version="$ver" "$PackageManifest" || true
                            else
                                "$HPM" manifest-modify-driver --name="$name" --location="$url" "$PackageManifest" || true
                            fi
                        fi
                fi
        else
                echo "Adding driver: $fname version=${ver:-"(none)"}"
                if [ -n "$ver" ]; then
                        "$HPM" manifest-add-driver --location="$url" --version="$ver" --name="$name" --namespace="$ns" "$PackageManifest" || {
                            echo "ERROR: Failed to add driver $fname to manifest." >&2
                            exit 2
                        }
                else
                        "$HPM" manifest-add-driver --location="$url" --name="$name" --namespace="$ns" "$PackageManifest" || {
                            echo "ERROR: Failed to add driver $fname to manifest." >&2
                            exit 2
                        }
                fi
        fi
done

echo "Cleaning up removed apps from manifest (by filename)..."
while IFS= read -r loc; do
        base=$(basename "$loc")
        if [ ! -f "${APP_FOLDER}app/$base" ]; then
                id=$(jq -r --arg l "$loc" 'first(.apps[]? | select(.location==$l) | .id) // empty' "$PackageManifest")
                echo "Removing app not found locally: $base (id=${id:-n/a})"
                if [ -n "$id" ]; then
                        "$HPM" manifest-remove-app --id="$id" "$PackageManifest" || {
                            echo "ERROR: Failed to remove app $base (id=$id) from manifest." >&2
                            exit 2
                        }
                else
                        name=$(jq -r --arg l "$loc" 'first(.apps[]? | select(.location==$l) | .name) // empty' "$PackageManifest")
                        [ -n "$name" ] && "$HPM" manifest-remove-app --name="$name" "$PackageManifest" || true
                fi
        fi
done < <(jq -r '.apps[]? .location // empty' "$PackageManifest")

echo "Updating apps in manifest (match by filename)..."
for app in "${APP_FOLDER}"app/*.groovy; do
        [ -e "$app" ] || continue
        fname=$(basename "$app")
        url="$GITHUBREPO/app/$fname"
        ver=$(get_version "$app")
        name=$(get_def_field "$app" name "$fname")
        ns=$(get_def_field "$app" namespace "neerpatel/hubspace")
        curLoc=$(jq -r --arg fn "$fname" 'first(.apps[]? | select((.location|split("/")|last)==$fn) | .location) // empty' "$PackageManifest")
        if [ -n "$curLoc" ]; then
                if [ "$curLoc" != "$url" ]; then
                        echo "Replacing app entry for $fname (location change)"
                        curId=$(jq -r --arg l "$curLoc" 'first(.apps[]? | select(.location==$l) | .id) // empty' "$PackageManifest")
                        if [ -n "$curId" ]; then
                                "$HPM" manifest-remove-app --id="$curId" "$PackageManifest" || {
                                    echo "ERROR: Failed to remove app $fname (id=$curId) from manifest." >&2
                                    exit 2
                                }
                        else
                                curName=$(jq -r --arg l "$curLoc" 'first(.apps[]? | select(.location==$l) | .name) // empty' "$PackageManifest")
                                [ -n "$curName" ] && "$HPM" manifest-remove-app --name="$curName" "$PackageManifest" || true
                        fi
                        if [ -n "$ver" ]; then
                                "$HPM" manifest-add-app --location="$url" --version="$ver" --name="$name" --namespace="$ns" "$PackageManifest" || {
                                    echo "ERROR: Failed to add app $fname to manifest." >&2
                                    exit 2
                                }
                        else
                                "$HPM" manifest-add-app --location="$url" --name="$name" --namespace="$ns" "$PackageManifest" || {
                                    echo "ERROR: Failed to add app $fname to manifest." >&2
                                    exit 2
                                }
                        fi
                else
                        echo "Modifying app: $fname version=${ver:-"(none)"}"
                        curId=$(jq -r --arg fn "$fname" 'first(.apps[]? | select((.location|split("/")|last)==$fn) | .id) // empty' "$PackageManifest")
                        if [ -n "$curId" ]; then
                            if [ -n "$ver" ]; then
                                "$HPM" manifest-modify-app --id="$curId" --location="$url" --name="$name" --namespace="$ns" --version="$ver" "$PackageManifest" || { echo "ERROR: Failed to modify app $fname" >&2; exit 2; }
                            else
                                "$HPM" manifest-modify-app --id="$curId" --location="$url" --name="$name" --namespace="$ns" "$PackageManifest" || { echo "ERROR: Failed to modify app $fname" >&2; exit 2; }
                            fi
                        else
                            # Fallback by name
                            if [ -n "$ver" ]; then
                                "$HPM" manifest-modify-app --name="$name" --location="$url" --namespace="$ns" --version="$ver" "$PackageManifest" || true
                            else
                                "$HPM" manifest-modify-app --name="$name" --location="$url" --namespace="$ns" "$PackageManifest" || true
                            fi
                        fi
                fi
        else
                echo "Adding app: $fname version=${ver:-"(none)"}"
                if [ -n "$ver" ]; then
                        "$HPM" manifest-add-app --location="$url" --version="$ver" --name="$name" --namespace="$ns" "$PackageManifest" || {
                            echo "ERROR: Failed to add app $fname to manifest." >&2
                            exit 2
                        }
                else
                        "$HPM" manifest-add-app --location="$url" --name="$name" --namespace="$ns" "$PackageManifest" || {
                            echo "ERROR: Failed to add app $fname to manifest." >&2
                            exit 2
                        }
                fi
        fi
done
