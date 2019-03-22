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

try:
    from pykube.config import KubeConfig
    from pykube.http import HTTPClient
    from pykube.http import HTTPError
    from pykube.objects import Pod
    from pykube.objects import Event
except ImportError:
    raise RuntimeError('pykube is not installed. KubernetesJobTask requires pykube.')


class PodModel:

    def __init__(self, obj, run_id):
        self.run_id = run_id
        self.name = obj['metadata']['name']
        if 'status' in obj:
            if 'phase' in obj['status']:
                self.status = obj['status']['phase']
            if 'podIP' in obj['status']:
                self.ip = obj['status']['podIP']


class Kubernetes:

    def __init__(self):
        self.__kube_api = HTTPClient(KubeConfig.from_service_account())
        self.__kube_api.session.verify = False

    def get_pod(self, run_id):
        pods = Pod.objects(self.__kube_api).filter(selector={'runid': run_id})
        if len(pods.response['items']) == 0:
            return None
        else:
            return PodModel(pods.response['items'][0], run_id)
