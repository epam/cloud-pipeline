# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import logging

from pipeline.hpc.cloud import CloudProvider
from pipeline.hpc.utils import ScaleCommonUtils

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')

utils = ScaleCommonUtils()


def test_aws_familes():
    assert utils.extract_family_from_instance_type(CloudProvider.aws(), "c5.xlarge") == "c5"
    assert utils.extract_family_from_instance_type(CloudProvider.aws(), "p2.xlarge") == "p2"
    assert utils.extract_family_from_instance_type(CloudProvider.aws(), "g4dn.2xlarge") == "g4dn"


def test_gcp_familes():
    assert utils.extract_family_from_instance_type(CloudProvider.gcp(), "n2-standard-2") == "n2-standard"
    assert utils.extract_family_from_instance_type(CloudProvider.gcp(), "n2-highcpu-2") == "n2-highcpu"
    assert utils.extract_family_from_instance_type(CloudProvider.gcp(), "n2d-highcpu-128") == "n2d-highcpu"
    assert utils.extract_family_from_instance_type(CloudProvider.gcp(), "e2-small") == "e2-small"
    assert utils.extract_family_from_instance_type(CloudProvider.gcp(), "c3-standard-4") == "c3-standard"
    assert utils.extract_family_from_instance_type(CloudProvider.gcp(), "c3-standard-4-lssd") == "c3-standard-lssd"
    assert utils.extract_family_from_instance_type(CloudProvider.gcp(), "a2-highgpu-1g") == "a2-highgpu"
    assert utils.extract_family_from_instance_type(CloudProvider.gcp(), "a2-ultragpu-4g") == "a2-ultragpu"
    assert utils.extract_family_from_instance_type(CloudProvider.gcp(), "custom-12-16384") == "custom"
    assert utils.extract_family_from_instance_type(CloudProvider.gcp(), "n2-custom-12-16384") == "n2-custom"
    assert utils.extract_family_from_instance_type(CloudProvider.gcp(), "gpu-custom-4-16384-k80-1") == "gpu-custom-k80"
    assert utils.extract_family_from_instance_type(CloudProvider.gcp(), "gpu-n2-custom-4-16384-k80-1") == "gpu-n2-custom-k80"


def test_azure_familes():
    assert utils.extract_family_from_instance_type(CloudProvider.azure(), "Standard_B1ms") == "Bms"
    assert utils.extract_family_from_instance_type(CloudProvider.azure(), "Standard_D2s_v3") == "Dsv3"
    assert utils.extract_family_from_instance_type(CloudProvider.azure(), "Standard_D16s_v3") == "Dsv3"
