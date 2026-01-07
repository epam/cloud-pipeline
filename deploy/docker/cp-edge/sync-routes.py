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

import os
from sync_routes_lib.config import CP_KUBE_NAMESPACE
from sync_routes_lib.logger import do_log
from sync_routes_lib.cp_api import CloudPipelineAPI
from sync_routes_lib.kube_client import KubeClient
from sync_routes_lib.nginx import NginxManager
from sync_routes_lib.synchronizer import RouteSynchronizer

def main():
    api_url = os.environ.get('API')
    api_token = os.environ.get('API_TOKEN')
    
    api_domain_name = os.environ.get('CP_API_SRV_EXTERNAL_HOST')
    if not api_domain_name:
            api_domain_name = os.environ.get('CP_API_SRV_INTERNAL_HOST')

    if not api_url or not api_token:
            print('API url or API token are not set. Exiting')
            exit(1)
            
    kube_config_path = HTTPClient(KubeConfig.from_service_account())
    
    api_client = CloudPipelineAPI(api_url, api_token)
    kube_client = KubeClient(kube_config_path)
    nginx_manager = NginxManager(api_domain_name)
    
    synchronizer = RouteSynchronizer(kube_client, api_client, nginx_manager)
    
    if api_domain_name:
        do_log('API domain name is determined as {}. It will be used to detect friendly URLs'.format(api_domain_name))
    else:
        do_log('[WARN] Cannot get API domain name from the environment')

    synchronizer.sync()

if __name__ == '__main__':
    main()
