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

from .kubeprovider import *
from .azureprovider import *
from .gcpprovider import *
from .awsprovider import *
from .utils import *


def create_cloud_provider(cloud, cloud_region):
    if cloud.lower() == "aws":
        return awsprovider.AWSInstanceProvider(cloud_region)
    elif cloud.lower() == "azure":
        return azureprovider.AzureInstanceProvider(cloud_region)
    elif cloud.lower() == "gcp":
        return gcpprovider.GCPInstanceProvider(cloud_region)
    else:
        raise RuntimeError("Cloud: {} is not supported".format(cloud))
