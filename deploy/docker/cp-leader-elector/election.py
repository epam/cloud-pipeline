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
import os
import pykube
import time
import urllib3

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

def get_service_name():
    return os.environ['HA_ELECTION_SERVICE_NAME']


HA_ELECTION_PERIOD_SEC = int(os.environ['HA_ELECTION_PERIOD_SEC'])
HA_VOTE_EXPIRATION_PERIOD_SEC = int(os.environ['HA_VOTE_EXPIRATION_PERIOD_SEC'])
SERVICE_NAME = get_service_name()
ELECTION_TIME_LABEL = SERVICE_NAME + "/service-leader-election-time"
LEADER_NAME_LABEL = SERVICE_NAME + "/service-leader"


def get_my_pod_name():
    return os.environ['HOSTNAME']


def is_pod_alive(api, pod_id):
    pods = pykube.Pod.objects(api).filter(field_selector={'metadata.name': pod_id, 'status.phase': 'Running'})
    return len(pods) == 1


def get_kube_api():
    try:
        api = pykube.HTTPClient(pykube.KubeConfig.from_service_account())
    except Exception as e:
        api = pykube.HTTPClient(pykube.KubeConfig.from_file("~/.kube/config"))
    api.session.verify = False
    return api


def set_leader_labels(api, name, election_time):
    deploy = pykube.Deployment.objects(api).get_by_name(SERVICE_NAME)
    deploy.labels[ELECTION_TIME_LABEL] = str(election_time)
    deploy.labels[LEADER_NAME_LABEL] = name
    deploy.update()
    print("[{}] selected as leader".format(name))


def get_leader_name(metadata):
    name = None
    if 'labels' in metadata:
        labels = metadata['labels']
        if LEADER_NAME_LABEL in labels:
            name = labels[LEADER_NAME_LABEL]
    return name


def get_election_time(metadata):
    time = None
    if 'labels' in metadata:
        labels = metadata['labels']
        if ELECTION_TIME_LABEL in labels:
            time = labels[ELECTION_TIME_LABEL]
    return int(time)


def check_leader_info_labels(sync_start_epochs):
    my_name = get_my_pod_name()
    api = get_kube_api()
    metadata = get_service_metadata(api, SERVICE_NAME)
    leader_name = get_leader_name(metadata)
    if leader_name is not None:
        prev_leader_selection_time = get_election_time(metadata)
        if prev_leader_selection_time is not None:
            vote_diff = sync_start_epochs - prev_leader_selection_time
            if my_name != leader_name and is_pod_alive(api, leader_name) and vote_diff < HA_VOTE_EXPIRATION_PERIOD_SEC:
                print("Keep [{}] as active leader.".format(leader_name))
                return
        else:
            print("Leader [{}] without election timestamp was found, change the leader to current one."
                  .format(leader_name))
    else:
        print("No leader found, set current one.")
    set_leader_labels(api, my_name, sync_start_epochs)


def get_service_metadata(api, service_name):
    service = pykube.Deployment.objects(api).get_by_name(service_name)
    metadata = service.metadata
    return metadata


def main():
    while True:
        sync_start = datetime.datetime.now()
        sync_start_epochs = int(sync_start.timestamp())
        print("Electing leader at {}".format(sync_start))
        try:
            check_leader_info_labels(sync_start_epochs)
        except Exception as e:
            print('Exception occurred during election: {}!'.format(str(e)))
        print("Electing round is finished")
        time.sleep(float(HA_ELECTION_PERIOD_SEC))


if __name__ == '__main__':
    main()
