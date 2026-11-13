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

# Prints a TestNG suite (testng.xml) with a subset of the GUI tests, in the order given.
#   make-suite.sh <Class|Class#method>[,<Class|Class#method>...]
# Methods of the same class are grouped into one <test> with an include list. A class given without methods is
# taken with the method selection of the main testng.xml (its include/exclude lists), if it is listed there.

_SPEC="$1"
[ "$_SPEC" ] || { echo "Usage: $0 Class[#method][,Class[#method]...]" >&2; exit 1; }
_MAIN_SUITE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/testng.xml"
_PACKAGE="com.epam.pipeline.autotests"

declare -A _METHODS
_ORDER=()
IFS=',' read -r -a _ITEMS <<< "$_SPEC"
for _item in "${_ITEMS[@]}"; do
    _item="$(echo "$_item" | tr -d ' ')"
    [ "$_item" ] || continue
    _class="${_item%%#*}"
    _class="${_class#$_PACKAGE.}"
    [[ "$_class" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || { echo "Invalid class name: $_class" >&2; exit 1; }
    [[ " ${_ORDER[*]} " == *" $_class "* ]] || _ORDER+=("$_class")
    if [[ "$_item" == *"#"* ]]; then
        _METHODS[$_class]="${_METHODS[$_class]} ${_item#*#}"
    fi
done

echo '<!DOCTYPE suite SYSTEM "http://testng.org/testng-1.0.dtd" >'
echo ''
echo '<suite name="UITests" verbose="1" parallel="classes" thread-count="1">'
for _class in "${_ORDER[@]}"; do
    echo ""
    echo "    <test name=\"$_class\">"
    echo "        <classes>"
    if [ "${_METHODS[$_class]}" ]; then
        echo "            <class name=\"$_PACKAGE.$_class\">"
        echo "                <methods>"
        for _m in ${_METHODS[$_class]}; do
            echo "                    <include name=\"$_m\"/>"
        done
        echo "                </methods>"
        echo "            </class>"
    else
        # Reuse the method selection of the main suite, if any
        _block="$(awk -v c="$_PACKAGE.$_class" '
            !found && index($0, "<class name=\"" c "\"") { found = 1; print; if ($0 ~ /\/>[[:space:]]*$/) exit; next }
            found { print }
            found && /<\/class>/ { exit }' "$_MAIN_SUITE")"
        if [ "$_block" ]; then
            echo "$_block" | sed 's/^[[:space:]]*/            /' | \
                sed -e 's/^            <methods>/                <methods>/' -e 's/^            <\(include\|exclude\)/                    <\1/' \
                    -e 's/^            <\/methods>/                <\/methods>/'
        else
            echo "            <class name=\"$_PACKAGE.$_class\"/>"
        fi
    fi
    echo "        </classes>"
    echo "    </test>"
done
echo '</suite>'
