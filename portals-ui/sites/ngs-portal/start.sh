#!/bin/bash

export CP_API_HOST=$(echo $API | awk -F[/] '{print $3}')

NGINX_CONF="$(envsubst '${CP_API_HOST} ${API_TOKEN}' < /etc/nginx/nginx.conf)"
echo "$NGINX_CONF" > /etc/nginx/nginx.conf

nginx
