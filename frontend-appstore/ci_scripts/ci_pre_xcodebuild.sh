#!/bin/sh
# Xcode Cloud release gate. This does not submit or change an App Review build.
set -eu

repository_path="${CI_PRIMARY_REPOSITORY_PATH:?Xcode Cloud repository path is required}"
package_path="$repository_path/frontend-appstore"

echo "Validating routing, billing, retry and session policies before Xcode builds."
swift test --package-path "$package_path"
for business_zone in UTC Europe/Istanbul America/Los_Angeles; do
    TZ="$business_zone" swift test --package-path "$package_path" \
        --filter 'BusinessDateParserTests|TicketListTests'
done
