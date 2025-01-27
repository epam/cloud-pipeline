#!/bin/bash

export CP_API_HOST=$(echo $API | awk -F[/] '{print $3}')
export NGS_PORTAL_ROOT="${NGS_PORTAL_ROOT:-/opt/ngs-portal}"

NGINX_CONF="$(envsubst '${CP_API_HOST} ${API_TOKEN} ${NGS_PORTAL_ROOT}' < /etc/nginx/nginx.conf)"
echo "$NGINX_CONF" > /etc/nginx/nginx.conf

nginx
