# Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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
import zlib
import struct
import hashlib
from abc import ABCMeta, abstractmethod
from botocore.model import StringShape
import xml.etree.ElementTree as ET


class ChecksumProcessor:
    """
    This class provides methods to force old versions of botocore library to support additional checksum calculation.
    """

    def __init__(self):
        self.algorithm = None
        self.enabled = False
        self.calculator = None
        self.checksum_header = None
        self.boto_field = None

    def init(self, algorithm):
        self.algorithm = algorithm.lower()
        self.enabled = True
        self.checksum_header = 'x-amz-checksum-%s' % self.algorithm
        self.boto_field = 'Checksum%s' % algorithm.upper()
        if self.algorithm == 'crc32':
            self.calculator = CRC32ChecksumCalculator()
        elif self.algorithm == 'sha256':
            self.calculator = SHA256ChecksumCalculator()
        else:
            raise NotImplementedError("Checksum algorithm '%s' is not supported yet" % algorithm)

    def prepare_boto_client(self, client):
        def _add_checksum_algorithm_header(request, *args, **kwargs):
            self.remove_checksum_algorithm_header(request)
            self.add_checksum_algorithm_header(request)

        def _add_put_object_header(request, *args, **kwargs):
            self.remove_checksum_header(request)
            checksum_value = self.calculate_checksum(request.body)
            request.body.seek(0)
            self.add_checksum_header(request, checksum_value)

        def _add_checksum_of_checksums_header(request, *args, **kwargs):
            checksum_value = self.calculate_checksum_of_checksums(request.body)
            self.remove_checksum_header(request)
            self.add_checksum_header(request, checksum_value)

        client.meta.events.register_first('before-sign.s3.CreateMultipartUpload', _add_checksum_algorithm_header)
        client.meta.events.register_first('before-sign.s3.UploadPart', _add_put_object_header)
        client.meta.events.register_first('before-sign.s3.PutObject', _add_put_object_header)
        client.meta.events.register_first('before-sign.s3.CompleteMultipartUpload', _add_checksum_of_checksums_header)

        self.add_checksum_shape_to_model(client)

    def add_checksum_algorithm_header(self, request):
        request.headers.add_header('x-amz-checksum-algorithm', self.algorithm)

    def remove_checksum_algorithm_header(self, request):
        self.remove_header(request, 'x-amz-checksum-algorithm')

    def add_checksum_header(self, request, checksum_value):
        request.headers.add_header(self.checksum_header, checksum_value)

    def remove_checksum_header(self, request):
        self.remove_header(request, self.checksum_header)

    def calculate_checksum(self, chunk):
        return self.calculator.calculate_checksum(chunk.read())

    def calculate_checksum_of_checksums(self, mpu_parts):
        decoded_parts = self.decode_mpu_parts(mpu_parts, self.boto_field)
        return self.calculator.calculate_checksum(decoded_parts)

    def add_checksum_shape_to_model(self, client):
        checksum_field = self.boto_field
        client.meta.service_model.operation_model('CompleteMultipartUpload') \
            .input_shape.members['MultipartUpload'].members['Parts'].member.members[checksum_field] =\
            StringShape(checksum_field, {
                'type': 'string',
                'documentation': '',
                'location': 'uri',
                'locationName': checksum_field
            })

    @staticmethod
    def decode_mpu_parts(mpu_parts_xml_string, field):
        decoded_parts = None
        root = ET.fromstring(mpu_parts_xml_string)
        for part in root.findall('{http://s3.amazonaws.com/doc/2006-03-01/}Part'):
            checksum_value = part.find('{http://s3.amazonaws.com/doc/2006-03-01/}%s' % field).text
            decoded_checksum = base64.b64decode(checksum_value)
            if decoded_parts:
                decoded_parts = decoded_parts + decoded_checksum
            else:
                decoded_parts = decoded_checksum
        return decoded_parts

    @staticmethod
    def remove_header(request, header):
        all_headers = request.headers._headers
        new_headers = []
        for header_name, header_value in all_headers:
            if not header_name.lower() == header:
                new_headers.append((header_name, header_value))
        request.headers._headers = new_headers


class ChecksumCalculator:
    __metaclass__ = ABCMeta

    @abstractmethod
    def calculate_checksum(self, chunk):
        pass


class CRC32ChecksumCalculator(ChecksumCalculator):

    def __init__(self):
        pass

    def calculate_checksum(self, chunk):
        crc_raw = zlib.crc32(chunk)
        crc_bytes = struct.pack('>i', crc_raw)
        return base64.b64encode(crc_bytes).decode('utf-8')


class SHA256ChecksumCalculator(ChecksumCalculator):

    def __init__(self):
        pass

    def calculate_checksum(self, data):
        sha256_hex = hashlib.sha256(data).hexdigest()
        sha256_int = int(sha256_hex, 16)

        sha256_bytes = bytearray()
        while sha256_int:
            sha256_bytes.append(sha256_int & 0xFF)
            sha256_int >>= 8
        sha256_bytes.reverse()

        return base64.b64encode(sha256_bytes).decode('utf-8')
