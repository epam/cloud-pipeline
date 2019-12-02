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


from scripts.autoscale_sge import CloudProvider, CloudPipelineInstanceHelper

AZURE_DSV = "Dsv3"
AZURE_BMS = "Bms"

GCP_STANDARD = "standard"
GCP_HIGHCPU = "highcpu"

AWS_C5 = "c5"
AWS_P2 = "p2"


def test_aws_familes():
    family = CloudPipelineInstanceHelper.get_family_from_type(CloudProvider.AWS, "c5.xlarge")
    assert family == AWS_C5
    family = CloudPipelineInstanceHelper.get_family_from_type(CloudProvider.AWS, "p2.xlarge")
    assert family == AWS_P2


def test_gcp_familes():
    family = CloudPipelineInstanceHelper.get_family_from_type(CloudProvider.GCP, "n2-standard-2")
    assert family == GCP_STANDARD
    family = CloudPipelineInstanceHelper.get_family_from_type(CloudProvider.GCP, "n2-highcpu-2")
    assert family == GCP_HIGHCPU
    family = CloudPipelineInstanceHelper.get_family_from_type(CloudProvider.GCP, "custom-12-16")
    assert family is None


def test_azure_familes():
    family = CloudPipelineInstanceHelper.get_family_from_type(CloudProvider.AZURE, "Standard_B1ms")
    assert family == AZURE_BMS
    family = CloudPipelineInstanceHelper.get_family_from_type(CloudProvider.AZURE, "Standard_D2s_v3")
    assert family == AZURE_DSV
    family = CloudPipelineInstanceHelper.get_family_from_type(CloudProvider.AZURE, "Standard_D16s_v3")
    assert family == AZURE_DSV

