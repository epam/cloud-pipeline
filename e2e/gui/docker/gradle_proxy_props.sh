#!/bin/bash

# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Writes Gradle proxy settings (systemProp.http(s).proxyHost/proxyPort/nonProxyHosts) built from the
# http_proxy/https_proxy/no_proxy environment variables into $GRADLE_USER_HOME/gradle.properties:
# neither Gradle nor its wrapper reads these variables. Does nothing if no proxy variable is set.
# gradle.properties is used instead of GRADLE_OPTS, as gradlew evals GRADLE_OPTS ("|" of nonProxyHosts would break it).
# Usage: bash gradle_proxy_props.sh

_props_file="${GRADLE_USER_HOME:-$HOME/.gradle}/gradle.properties"

function _host_port {
    # http://user:pass@host:port/ -> "host port"
    local _url="${1#*://}"
    _url="${_url##*@}"
    _url="${_url%%/*}"
    local _host="${_url%%:*}"
    local _port="${_url##*:}"
    [ "$_port" == "$_url" ] && _port="$2"
    echo "$_host $_port"
}

_http="${http_proxy:-$HTTP_PROXY}"
_https="${https_proxy:-$HTTPS_PROXY}"
_no="${no_proxy:-$NO_PROXY}"

[ -z "$_http" ] && [ -z "$_https" ] && exit 0

mkdir -p "$(dirname "$_props_file")"
touch "$_props_file"
sed -i '/^systemProp\.http[s]*\.\(proxyHost\|proxyPort\|nonProxyHosts\)=/d' "$_props_file"

if [ "$_http" ]; then
    read -r _h _p <<< "$(_host_port "$_http" 80)"
    printf 'systemProp.http.proxyHost=%s\nsystemProp.http.proxyPort=%s\n' "$_h" "$_p" >> "$_props_file"
fi
if [ "$_https" ]; then
    read -r _h _p <<< "$(_host_port "$_https" 443)"
    printf 'systemProp.https.proxyHost=%s\nsystemProp.https.proxyPort=%s\n' "$_h" "$_p" >> "$_props_file"
fi
if [ "$_no" ]; then
    # "a.com,.b.com" -> "a.com|*.b.com" (http.nonProxyHosts is used for https as well)
    _non_proxy="$(echo "$_no" | tr ',' '\n' | sed -e 's/^ *//' -e 's/ *$//' -e '/^$/d' -e 's/^\./*./' | paste -sd'|')"
    printf 'systemProp.http.nonProxyHosts=%s\nsystemProp.https.nonProxyHosts=%s\n' "$_non_proxy" "$_non_proxy" >> "$_props_file"
fi
echo "Gradle proxy settings are written to $_props_file"
