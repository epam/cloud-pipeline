#!/bin/bash

# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

TRANSFER_DIR="$1"
GIT_REPO="$2"
USER_NAME="$3"
USER_EMAIL="$4"

cd "$TRANSFER_DIR"
if [[ -d "$TRANSFER_DIR/.git" ]]; then
    rm -rf "$TRANSFER_DIR/.git"
fi
git init
git config user.name "$USER_NAME"
git config user.email "$USER_EMAIL"
git add "$TRANSFER_DIR/*"
git commit -m "Initial commit"
git push "$GIT_REPO" master
exit_code="$?"
rm -rf "$TRANSFER_DIR/.git"
exit "$exit_code"
