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


import datetime
import json
from flask import Flask, Response
import election

SERVICE_NAME = election.get_service_name()

app = Flask(__name__)


def master_pod_info_response(pod_name):
    return Response(json.dumps({
        "name": pod_name
    }))


@app.route('/')
def is_master():
    api = election.get_kube_api()
    metadata = election.get_service_metadata(api, SERVICE_NAME)
    leader_name = election.get_leader_name(metadata)
    if leader_name is None:
        my_name = election.get_my_pod_name()
        now = datetime.datetime.now()
        now_epochs = int(now.timestamp())
        election.set_leader_labels(api, my_name, now_epochs)
        return master_pod_info_response(my_name)
    else:
        return master_pod_info_response(leader_name)


if __name__ == '__main__':
    app.run(port="4040")
