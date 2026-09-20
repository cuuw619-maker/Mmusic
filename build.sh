#!/usr/bin/env bash
set -e

# Change to script directory (project root)
cd "$(dirname "$0")"

echo "==> Building Android Music Player APK..."

# Ensure executable permissions on gradlew
chmod +x ./gradlew 2>/dev/null || true

# Check if gradlew exists, otherwise run gradle wrapper
if [ ! -f "./gradlew" ]; then
    echo "gradlew not found, generating wrapper with Gradle..."
    gradle wrapper || {
        echo "Error: Gradle is required to generate the wrapper."
        exit 1
    }
    chmod +x ./gradlew
fi

# Run Gradle assembleDebug
echo "==> Running assembleDebug..."
./gradlew assembleDebug --stacktrace "$@"

echo "==> Build finished successfully!"
echo "APK location: app/build/outputs/apk/debug/app-debug.apk"
