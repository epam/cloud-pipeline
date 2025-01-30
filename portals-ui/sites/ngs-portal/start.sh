#!/bin/bash

export CP_API_HOST=$(echo $API | awk -F[/] '{print $3}')
export NGS_PORTAL_ROOT="${NGS_PORTAL_ROOT:-/opt/ngs-portal}"

if [ "$NGS_PORTAL_SETTINGS" ]; then
  pipe storage cp "${NGS_PORTAL_SETTINGS}" "${NGS_PORTAL_ROOT}/settings.json"
fi

NGINX_CONF="$(envsubst '${CP_API_HOST} ${API_TOKEN} ${NGS_PORTAL_ROOT}' < /etc/nginx/nginx.conf)"
echo "$NGINX_CONF" > /etc/nginx/nginx.conf

SETTINGS_FILE="$NGS_PORTAL_ROOT/settings.json"

# Check if settings.json exists and read its content
if [ -f "$SETTINGS_FILE" ]; then
  existing_content=$(cat "$SETTINGS_FILE")
else
  existing_content='{}'  # Default to empty JSON object if file does not exist
fi

# Update the "api" property while keeping other properties intact
updated_content=$(echo "$existing_content" | jq 'if has("api") | not then .api = $api else . end' --arg api "$API")

echo "$updated_content" > "$SETTINGS_FILE"

nginx

sleep infinity
