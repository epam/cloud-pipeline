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


import win32crypt
import win32cryptcon
import win32serviceutil
from pipeline.utils.pki import load_root_certificate
from pipeline.utils.reg import set_user_dword_value, set_local_machine_dword_value
from pipeline.utils.scheduler import schedule_powershell_script_on_logon

_microsoft_settings = 'SOFTWARE\\Microsoft'
_internet_settings = _microsoft_settings + '\\Windows\\CurrentVersion\\Internet Settings'
_internet_trusted_sources = _internet_settings + '\\ZoneMap\\EscDomains'
_internet_explorer_main_settings = _microsoft_settings + '\\Internet Explorer\\Main'
_windows_webdav_client_parameters = 'SYSTEM\\CurrentControlSet\\Services\\WebClient\\Parameters'


def add_root_cert_to_trusted_root(host, port):
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


def configure_environment(username, edge_host):
    set_user_dword_value(username, _internet_trusted_sources + '\\{}'.format(edge_host), 'https', 2)
    set_user_dword_value(username, _internet_trusted_sources + '\\internet', 'about', 2)
    set_user_dword_value(username, _internet_explorer_main_settings, 'DisableFirstRunCustomize', 1)
    set_user_dword_value(username, _internet_settings, 'WarnonZoneCrossing', 0)
    set_local_machine_dword_value(_windows_webdav_client_parameters, 'FileSizeLimitInBytes', 0xFFFFFFFF)
    win32serviceutil.RestartService('WebClient')


def schedule_mapping(username, edge_host, edge_port, token, script):
    replacement_dict = {
        '<USER_NAME>': username,
        '<USER_TOKEN>': token,
        '<EDGE_HOST>': edge_host,
        '<EDGE_PORT>': edge_port
    }
    schedule_powershell_script_on_logon(username, 'CloudPipelineDriveMapping', script, replacement_dict, True)
