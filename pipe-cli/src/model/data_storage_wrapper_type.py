# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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


class WrapperType(object):
    LOCAL = 'LOCAL'
    S3 = 'S3'
    AZURE = 'AZ'
    GS = 'GS'
    FTP = 'FTP'
    HTTP = 'HTTP'

    __cloud_types = [S3, AZURE, GS]
    __dynamic_cloud_scheme = 'cp'
    __s3_cloud_scheme = 's3'
    __azure_cloud_scheme = 'az'
    __gs_cloud_scheme = 'gs'
    __cloud_schemes = [__dynamic_cloud_scheme, __s3_cloud_scheme, __azure_cloud_scheme, __gs_cloud_scheme]
    __cloud_schemes_map = {S3: __s3_cloud_scheme, AZURE: __azure_cloud_scheme, GS: __gs_cloud_scheme}

    @classmethod
    def cloud_types(cls):
        return WrapperType.__cloud_types

    @classmethod
    def cloud_schemes(cls):
        return WrapperType.__cloud_schemes

    @classmethod
    def is_dynamic_cloud_scheme(cls, scheme):
        return scheme == WrapperType.__dynamic_cloud_scheme

    @classmethod
    def cloud_scheme(cls, type):
        if type in WrapperType.__cloud_schemes_map:
            return WrapperType.__cloud_schemes_map[type]
        else:
            raise RuntimeError('Storage provider %s is not in the list of supported cloud providers %s'
                               % (type, WrapperType.cloud_types()))
