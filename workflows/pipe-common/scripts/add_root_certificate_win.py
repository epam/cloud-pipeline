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

import win32crypt
import win32cryptcon
from pipeline.utils.pki import load_root_certificate


def add_root_cert_to_trusted_root_win(host, port):
    cert_content = load_root_certificate(host, port)
    cert_content = cert_content.decode('utf-8')
    cert_byte = win32crypt.CryptStringToBinary(cert_content, win32cryptcon.CRYPT_STRING_BASE64HEADER)[0]
    cert_mode = win32cryptcon.CERT_SYSTEM_STORE_LOCAL_MACHINE | win32cryptcon.CERT_STORE_OPEN_EXISTING_FLAG
    root_certs_store = win32crypt.CertOpenStore(win32cryptcon.CERT_STORE_PROV_SYSTEM, 0, None, cert_mode, "ROOT")
    try:
        root_certs_store.CertAddEncodedCertificateToStore(win32cryptcon.X509_ASN_ENCODING,
                                                          cert_byte,
                                                          win32cryptcon.CERT_STORE_ADD_REPLACE_EXISTING)
    finally:
        root_certs_store.CertCloseStore(win32cryptcon.CERT_CLOSE_STORE_FORCE_FLAG)
