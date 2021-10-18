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

import cryptography
import OpenSSL
import socket


def generate_cert_chain(hostname, port):
    context = OpenSSL.SSL.Context(method=OpenSSL.SSL.TLSv1_2_METHOD)
    context.set_verify(OpenSSL.SSL.VERIFY_NONE)
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock = OpenSSL.SSL.Connection(context=context, socket=sock)
    sock.settimeout(5)
    sock.connect((hostname, port))
    sock.setblocking(1)
    sock.do_handshake()
    for cert in sock.get_peer_cert_chain():
        yield cert.to_cryptography()
    sock.shutdown()
    sock.close()


def load_root_certificate(host, port):
    full_cert_chain = list(generate_cert_chain(host, port))
    if full_cert_chain:
        root_cert = full_cert_chain[-1]
        return root_cert.public_bytes(cryptography.hazmat.primitives.serialization.Encoding.PEM)
    else:
        raise RuntimeError("Failed to load certificate chain for '{}:{}'".format(host, port))
