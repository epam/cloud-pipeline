# Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

from dataclasses import dataclass
from typing import Optional

@dataclass
class RouteSpec:
    edge_location_path: str
    pod_id: str
    pod_ip: str
    pod_owner: str
    shared_users_sids: str
    shared_groups_sids: str
    service_name: str
    is_default_endpoint: bool
    is_ssl_backend: bool
    is_same_tab: bool
    edge_num: int
    edge_location: str
    custom_domain: Optional[str]
    edge_target: str
    run_id: str
    additional: str
    sensitive: bool
    create_dns_record: bool
    cloudRegionId: Optional[str]
    external_app: bool
    cookie_location: Optional[str]
    edge_jwt_auth: bool
    edge_pass_bearer: bool
