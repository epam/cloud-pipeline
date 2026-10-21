#!/bin/bash

set -e

CLOUD_DATA_PATH="$1"
if [ -z "$CLOUD_DATA_PATH" ]; then
  echo "[ERROR] Path to cloud data is not defined"
  exit 1
fi

if [ ! -d "$CLOUD_DATA_PATH" ]; then
  echo "[ERROR] cloud data path is not a directory, might be does not exist"
  exit 1
fi

echo "Starting cloud data signing for MacOS"

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

echo "Signing $CLOUD_DATA_PATH"
find "$CLOUD_DATA_PATH" -type f \( -name "*.node" \) -exec \
  codesign --force --options runtime \
  --entitlements entitlements.plist \
  --sign "$MAC_SIGN_IDENTITY" {} \;

codesign --deep --force --verify --verbose \
  --options runtime \
  --entitlements entitlements.plist \
  --sign "$MAC_SIGN_IDENTITY" \
  "$CLOUD_DATA_PATH"

echo "Verifying signature..."
codesign --verify --deep --strict --verbose=2 "$CLOUD_DATA_PATH"

echo "Creating ZIP for notarization..."
ditto -c -k --keepParent "$CLOUD_DATA_PATH" cloud-data.zip

echo "Submitting for notarization..."
echo "$MAC_SIGN_P8" > AuthKey.p8
xcrun notarytool submit cloud-data.zip \
  --key AuthKey.p8 \
  --key-id "$MAC_SIGN_KEY_ID" \
  --issuer "$MAC_SIGN_ISSUER_ID" \
  --team-id "$MAC_SIGN_TEAM_ID" \
  --wait

echo "Cleanup..."
security delete-keychain "$MAC_SIGN_KEYCHAIN"
rm -f entitlements.plist
rm -f cert.p12
rm -f pipe-cli.zip
rm -f AuthKey.p8

echo "Signing for pipe-cli completed"
