#!/bin/bash
set -e

SDK_DIR="$HOME/Android/Sdk"
CMDLINE_DIR="$SDK_DIR/cmdline-tools"

echo "=========================================================="
echo " Setting up Android SDK Command-Line Tools in $SDK_DIR"
echo "=========================================================="

mkdir -p "$CMDLINE_DIR"
cd "$CMDLINE_DIR"

if [ ! -d "$CMDLINE_DIR/latest" ]; then
    echo "⬇️  Downloading Google Android Command-Line Tools..."
    wget -q --show-progress "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -O cmdline-tools.zip
    echo "📦 Extracting command-line tools..."
    unzip -q cmdline-tools.zip
    rm -rf latest
    mv cmdline-tools latest
    rm -f cmdline-tools.zip
    echo "✅ Command-line tools installed!"
else
    echo "✅ Command-line tools already present in $CMDLINE_DIR/latest"
fi

SDKMANAGER="$CMDLINE_DIR/latest/bin/sdkmanager"
export PATH="$CMDLINE_DIR/latest/bin:$SDK_DIR/platform-tools:$PATH"

echo "📜 Accepting SDK licenses..."
yes | "$SDKMANAGER" --licenses > /dev/null 2>&1 || true

echo "⬇️  Installing platform-tools (adb), platforms;android-31, and build-tools..."
"$SDKMANAGER" "platforms;android-31" "build-tools;30.0.3" "platform-tools"

echo ""
echo "=========================================================="
echo "🎉 Android SDK Setup Complete!"
echo "   SDK Location: $SDK_DIR"
echo "   'adb' is available at: $SDK_DIR/platform-tools/adb"
echo "   Now you can build the app:"
echo "     cd ~/Desktop/rfidrs38gopala/UHFSample_forAS/UHFSample"
echo "     ./gradlew assembleDebug"
echo "=========================================================="

