#!/bin/bash

set -e

PIPE_CLI_PATH="$1"
if [ -z "$PIPE_CLI_PATH" ]; then
  echo "[ERROR] Path to pipe cli is not defined"
  exit 1
fi

if [ ! -d "$PIPE_CLI_PATH" ] && [ ! -f "$PIPE_CLI_PATH" ]; then
  echo "[ERROR] CLI path is not a directory nor a file, might be does not exist"
  exit 1
fi

echo "Starting pipe-cli signing for MacOS"

echo "Creating keychain..."
security create-keychain -p "$MAC_SIGN_KEYCHAIN_PASSWORD" "$MAC_SIGN_KEYCHAIN"
security default-keychain -s "$MAC_SIGN_KEYCHAIN"
security unlock-keychain -p "$MAC_SIGN_KEYCHAIN_PASSWORD" "$MAC_SIGN_KEYCHAIN"
security set-keychain-settings -t 3600 -u "$MAC_SIGN_KEYCHAIN"

echo "Importing certificate..."
echo "$MAC_SIGN_P12" | base64 --decode > cert.p12
security import cert.p12 -k "$MAC_SIGN_KEYCHAIN" -P "$MAC_SIGN_P12_PASSWORD" -T /usr/bin/codesign
security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$MAC_SIGN_KEYCHAIN_PASSWORD" "$MAC_SIGN_KEYCHAIN"

echo "Writing entitlements..."
cat > entitlements.plist <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
 "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.security.cs.allow-jit</key>
    <true/>
    <key>com.apple.security.cs.allow-unsigned-executable-memory</key>
    <true/>
    <key>com.apple.security.cs.disable-library-validation</key>
    <true/>
</dict>
</plist>
EOF

if [ -d "$PIPE_CLI_PATH" ]; then
  echo "Signing all binaries in directory $PIPE_CLI_PATH"
  find "$PIPE_CLI_PATH" -type f \( -perm +111 -o -name "*.dylib" \) -exec \
    codesign --force --options runtime \
    --entitlements entitlements.plist \
    --sign "$MAC_SIGN_IDENTITY" {} \;
else
  echo "Signing single binary $PIPE_CLI_PATH"
  codesign --force --options runtime \
    --entitlements entitlements.plist \
    --sign "$MAC_SIGN_IDENTITY" "$PIPE_CLI_PATH"
fi

echo "Verifying signature..."
codesign --verify --deep --strict --verbose=2 "$PIPE_CLI_PATH/pipe"

echo "Creating ZIP for notarization..."
ditto -c -k --keepParent "$PIPE_CLI_PATH" pipe-cli.zip

echo "Submitting for notarization..."
xcrun notarytool submit pipe-cli.zip \
  --apple-id "$MAC_SIGN_APPLE_ID" \
  --team-id "$MAC_SIGN_APPLE_TEAM_ID" \
  --password "$MAC_SIGN_APPLE_APP_PASSWORD" \
  --wait

echo "Cleanup..."
security delete-keychain "$MAC_SIGN_KEYCHAIN"
rm -f entitlements.plist
rm -f cert.p12
rm -f pipe-cli.zip

echo "Signing for pipe-cli completed"
