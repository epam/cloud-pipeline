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

from pipeline.hpc.autoscaler import CloudProvider, extract_family_from_instance_type

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')


def test_aws_familes():
    assert extract_family_from_instance_type(CloudProvider.aws(), "c5.xlarge") == "c5"
    assert extract_family_from_instance_type(CloudProvider.aws(), "p2.xlarge") == "p2"
    assert extract_family_from_instance_type(CloudProvider.aws(), "g4dn.2xlarge") == "g4dn"


def test_gcp_familes():
    assert extract_family_from_instance_type(CloudProvider.gcp(), "n2-standard-2") == "n2-standard"
    assert extract_family_from_instance_type(CloudProvider.gcp(), "n2-highcpu-2") == "n2-highcpu"
    assert extract_family_from_instance_type(CloudProvider.gcp(), "n2d-highcpu-128") == "n2d-highcpu"
    assert extract_family_from_instance_type(CloudProvider.gcp(), "e2-small") == "e2-small"
    assert extract_family_from_instance_type(CloudProvider.gcp(), "custom-12-16") is None
    assert extract_family_from_instance_type(CloudProvider.gcp(), "gpu-custom-4-16384-k80-1") is None


def test_azure_familes():
    assert extract_family_from_instance_type(CloudProvider.azure(), "Standard_B1ms") == "Bms"
    assert extract_family_from_instance_type(CloudProvider.azure(), "Standard_D2s_v3") == "Dsv3"
    assert extract_family_from_instance_type(CloudProvider.azure(), "Standard_D16s_v3") == "Dsv3"
