#!/bin/sh

set -eu

if [ -z "${CI_BUILD_NUMBER:-}" ]; then
    echo "CI_BUILD_NUMBER is not set; leaving the local build number unchanged."
    exit 0
fi

case "$CI_BUILD_NUMBER" in
    *[!0-9]*|'')
        echo "CI_BUILD_NUMBER must be a positive integer." >&2
        exit 1
        ;;
esac

# Build 2 was uploaded before Xcode Cloud was enabled. Offset Cloud's
# monotonically increasing number so every generated build is newer.
build_number=$((CI_BUILD_NUMBER + 2))
repository_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
project_file="$repository_root/frontend-appstore/PusulaService.xcodeproj/project.pbxproj"

if [ ! -f "$project_file" ]; then
    echo "Xcode project file was not found at the expected path." >&2
    exit 1
fi

sed -i '' -E \
    "s/CURRENT_PROJECT_VERSION = [^;]+;/CURRENT_PROJECT_VERSION = $build_number;/g" \
    "$project_file"

if ! grep -q "CURRENT_PROJECT_VERSION = $build_number;" "$project_file"; then
    echo "Failed to set the Xcode build number." >&2
    exit 1
fi

echo "Configured CFBundleVersion as $build_number for Xcode Cloud build $CI_BUILD_NUMBER."
