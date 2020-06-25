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

import pykube
import os
import time
import datetime

ELECTION_VOTE_TIMEOUT_SECONDS = os.environ['ELECTION_TIMEOUT']
ELECTION_VOTE_EXPIRATION_PERIOD = os.environ['ELECTION_EXP_PERIOD']
SERVICE_NAME = os.environ['SERVICE_NAME']
ELECTION_TIME_LABEL = SERVICE_NAME + "/cp-service-leader-election-time"
LEADER_NAME_LABEL = SERVICE_NAME + "/cp-service-leader"


def get_kube_api():
    try:
        api = pykube.HTTPClient(pykube.KubeConfig.from_service_account())
    except Exception as e:
        api = pykube.HTTPClient(pykube.KubeConfig.from_file("~/.kube/config"))
    api.session.verify = False
    return api


def set_leader_labels(api, name, election_time):
    deploy = pykube.Deployment.objects(api).get_by_name(SERVICE_NAME)
    deploy.labels[ELECTION_TIME_LABEL] = election_time
    deploy.labels[LEADER_NAME_LABEL] = name
    deploy.update()


def set_leader_info_labels(sync_start_epochs):
    api = get_kube_api()
    pykube.Deployment.objects(api)
    service = pykube.Deployment.objects(api).get_by_name(SERVICE_NAME)

    my_name = os.environ['HOSTNAME']

    metadata = service.metadata
    if 'labels' in metadata:
        labels = metadata['labels']
        if LEADER_NAME_LABEL in labels:
            leader_name = labels[LEADER_NAME_LABEL]
            if ELECTION_TIME_LABEL in labels:
                prev_leader_selection_time = leader_name = labels[ELECTION_TIME_LABEL]
                vote_diff = sync_start_epochs - prev_leader_selection_time
                if my_name != leader_name and vote_diff < ELECTION_VOTE_TIMEOUT_SECONDS * 1000:
                    print("Keep [{}] as active leader.".format(leader_name))
                    return
            else:
                print("Leader [{}] without election timestamp was found.".format(leader_name))
        else:
            print("No leader found, set current one.")
    set_leader_labels(api, my_name, sync_start_epochs)


def main():
    while True:
        sync_start = datetime.datetime.now()
        sync_start_epochs = sync_start.timestamp() * 1000
        print("Electing leader at {}".format(sync_start))
        set_leader_info_labels(sync_start_epochs)
        print("Electing round is finished")
        time.sleep(float(ELECTION_VOTE_TIMEOUT_SECONDS))


if __name__ == '__main__':
    main()
