#  Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import re
from datetime import datetime

from pipeline.hpc.cloud import CloudProvider


class Clock:

    def __init__(self):
        pass

    def now(self):
        return datetime.now()


class ScaleCommonUtils:

    def __init__(self):
        pass

    def get_run_id_from_host(self, host):
        host_elements = host.split('-')
        return host_elements[len(host_elements) - 1]

    def extract_family_from_instance_type(self, cloud_provider, instance_type):
        if cloud_provider == CloudProvider.aws():
            search = re.search('^(\w+)\..*', instance_type)
            return search.group(1) if search else None
        elif cloud_provider == CloudProvider.gcp():
            search = re.search('^(?!custom)(\w+-(?!custom)\w+)-?.*', instance_type)
            return search.group(1) if search else None
        elif cloud_provider == CloudProvider.azure():
            # will return Bms for Standard_B1ms or Dsv3 for Standard_D2s_v3 instance types
            search = re.search('^([a-zA-Z]+)\d+(.*)', instance_type.split('_', 1)[1].replace('_', ''))
            return search.group(1) + search.group(2) if search else None
        else:
            return None