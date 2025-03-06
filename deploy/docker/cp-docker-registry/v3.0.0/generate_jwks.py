# Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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

import sys
import base64
from hashlib import sha256
import json
from cryptography.hazmat.primitives.hashes import SHA1
from jose import constants, jwk
from cryptography import x509
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives import serialization


class JWKSGenerator:
    def __init__(
        self,
        file_path: str
    ) -> None:
        with open(file_path, 'rb') as f:
            self._cert_pem_raw_data: bytes = f.read().strip()

        self._cert = x509.load_pem_x509_certificate(self._cert_pem_raw_data, default_backend())

    def get_kid(self):
        pub_key_der = self._cert.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
        pub_key_der_sha256 = sha256(pub_key_der).digest()
        kid_raw = base64.b32encode(pub_key_der_sha256[:30]).decode('utf-8')
        kid = ':'.join(kid_raw[i:i+4] for i in range(0, len(kid_raw), 4))
        return kid

    def process(self):
        jwks = jwk.RSAKey(algorithm=constants.Algorithms.RS512, key=self._cert_pem_raw_data).to_dict()

        jwks.update({
            "kid": self.get_kid()
        })
        return { "keys": [ jwks ] }


cert_path = sys.argv[1]
jwks = JWKSGenerator(
            file_path=cert_path
        ).process()

print(json.dumps(jwks, indent=4))
