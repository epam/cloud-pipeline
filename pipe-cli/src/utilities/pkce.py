# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

import base64
import hashlib
import os
import sys


def generate_code_verifier(length=127):
    if not 43 < length < 128:
        raise ValueError('Invalid code verifier length.')
    import secrets
    code_verifier = secrets.token_urlsafe(96)[:length]
    return code_verifier


def generate_code_challenge(code_verifier):
    sha256_hash = hashlib.sha256(code_verifier.encode('ascii')).digest()
    code_challenge = base64.urlsafe_b64encode(sha256_hash).decode('ascii')[:-1]
    return code_challenge


def generate_pkce_pair(length=127):
    code_verifier = generate_code_verifier(length)
    code_challenge = generate_code_challenge(code_verifier)
    return code_verifier, code_challenge
