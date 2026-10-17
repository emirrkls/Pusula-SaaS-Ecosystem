#!/bin/sh
# Xcode Cloud release gate. This does not submit or change an App Review build.
set -eu

# Simulator workers restore the compiled test artifacts, not the source checkout.
# Policy tests have already passed in the required build-for-testing action.
if [ "${CI_XCODEBUILD_ACTION:-}" = "test-without-building" ]; then
    echo "Policy gate already passed before build-for-testing; running compiled device tests."
    exit 0
fi

repository_path="${CI_PRIMARY_REPOSITORY_PATH:?Xcode Cloud repository path is required}"
package_path="$repository_path/frontend-appstore"

# The offline iOS and Android guides must ship the same reviewed catalog.
cmp "$repository_path/guides/mobile-guide.json" "$package_path/PusulaService/Core/Guide/app-guide.json"
cmp "$repository_path/guides/mobile-guide.json" "$repository_path/frontend-playstore/PusulaService/app/src/main/assets/app-guide.json"

echo "Validating routing, billing, retry and session policies before Xcode builds."
swift test --package-path "$package_path"
for business_zone in UTC Europe/Istanbul America/Los_Angeles; do
    TZ="$business_zone" swift test --package-path "$package_path" \
        --filter 'BusinessDateParserTests|TicketListTests'
done
