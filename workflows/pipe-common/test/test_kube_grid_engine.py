#  Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import pykube
from datetime import datetime
from mock import MagicMock, Mock

from pipeline.hpc.engine.gridengine import GridEngineJobState
from pipeline.hpc.engine.kube import KubeGridEngine, KubeResourceParser

pykube.Pod = Mock()

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')

QUEUE = 'main.q'
HOSTLIST = '@allhosts'
QUEUE_DEFAULT = True
OWNER = 'owner'

kube = Mock()
resource_parser = KubeResourceParser()
grid_engine = KubeGridEngine(kube=kube, resource_parser=resource_parser, owner=OWNER)


def setup_function():
    pass


def test_get_jobs():
    pykube.Pod.objects = MagicMock(return_value=[
        _get_running_pod(name='pod-1', cpu='1', mem='1Gi'),
        _get_pending_pod(name='pod-2', cpu='2', mem='2Gi'),
        _get_pending_pod(name='pod-3', cpu=None, mem=None)])

    jobs = grid_engine.get_jobs()

    assert len(jobs) == 3
    job_1, job_2, job_3 = jobs

    assert job_1.root_id == '8ff147ce-1724-4d8e-a76e-6a02852ec6f7'
    assert job_1.id == '8ff147ce-1724-4d8e-a76e-6a02852ec6f7'
    assert job_1.name == 'pod-1'
    assert job_1.user == OWNER
    assert job_1.state == GridEngineJobState.RUNNING
    assert job_1.cpu == 1
    assert job_1.gpu == 0
    assert job_1.mem == 1
    assert job_1.datetime == datetime(2023, 12, 27,
                                      9, 52, 37)
    assert job_1.hosts == ['pipeline-53659']

    assert job_2.root_id == '7d1480ae-22a2-4614-bb40-eaf8e70f499d'
    assert job_2.id == '7d1480ae-22a2-4614-bb40-eaf8e70f499d'
    assert job_2.name == 'pod-2'
    assert job_2.user == OWNER
    assert job_2.state == GridEngineJobState.PENDING
    assert job_2.cpu == 2
    assert job_2.gpu == 0
    assert job_2.mem == 2
    assert job_2.datetime == datetime(2023, 12, 27,
                                      9, 52, 47)
    assert job_2.hosts == []

    assert job_3.root_id == '7d1480ae-22a2-4614-bb40-eaf8e70f499d'
    assert job_3.id == '7d1480ae-22a2-4614-bb40-eaf8e70f499d'
    assert job_3.name == 'pod-3'
    assert job_3.user == OWNER
    assert job_3.state == GridEngineJobState.PENDING
    assert job_3.cpu == 1
    assert job_3.gpu == 0
    assert job_3.mem == 0
    assert job_3.datetime == datetime(2023, 12, 27,
                                      9, 52, 47)
    assert job_3.hosts == []


def _get_pending_pod(name, cpu, mem):
    pod = Mock()
    pod.obj = _get_pending_pod_obj(name=name, cpu=cpu, mem=mem)
    pod.name = pod.obj.get('metadata', {}).get('name')
    return pod


def _get_running_pod(name, cpu, mem):
    pod = Mock()
    pod.obj = _get_running_pod_obj(name=name, cpu=cpu, mem=mem)
    pod.name = pod.obj.get('metadata', {}).get('name')
    return pod


def _get_running_pod_obj(name, cpu, mem):
    return {
        "status": {
            "hostIP": "10.244.49.68",
            "qosClass": "Guaranteed",
            "containerStatuses": [
                {
                    "restartCount": 0,
                    "name": "nginx",
                    "started": True,
                    "image": "docker.io/library/nginx:1.14.2",
                    "imageID": "docker.io/library/nginx@sha256:f7988fb6c02e0ce69257d9bd9cf37ae20a60f1df7563c3a2a6abe24160306b8d",
                    "state": {
                        "running": {
                            "startedAt": "2023-12-27T09:52:42Z"
                        }
                    },
                    "ready": True,
                    "lastState": {},
                    "containerID": "containerd://c3ca0ac0ae07e9e031bc365c2821c774be7996ac5b5ca4467ccbf662641ea331"
                }
            ],
            "podIP": "172.16.1.2",
            "startTime": "2023-12-27T09:52:37Z",
            "podIPs": [
                {
                    "ip": "172.16.1.2"
                }
            ],
            "phase": "Running",
            "conditions": [
                {
                    "status": "True",
                    "lastProbeTime": None,
                    "type": "Initialized",
                    "lastTransitionTime": "2023-12-27T09:52:37Z"
                },
                {
                    "status": "True",
                    "lastProbeTime": None,
                    "type": "Ready",
                    "lastTransitionTime": "2023-12-27T09:52:42Z"
                },
                {
                    "status": "True",
                    "lastProbeTime": None,
                    "type": "ContainersReady",
                    "lastTransitionTime": "2023-12-27T09:52:42Z"
                },
                {
                    "status": "True",
                    "lastProbeTime": None,
                    "type": "PodScheduled",
                    "lastTransitionTime": "2023-12-27T09:52:37Z"
                }
            ]
        },
        "spec": {
            "dnsPolicy": "ClusterFirst",
            "securityContext": {},
            "serviceAccountName": "default",
            "schedulerName": "default-scheduler",
            "enableServiceLinks": True,
            "serviceAccount": "default",
            "priority": 0,
            "terminationGracePeriodSeconds": 30,
            "restartPolicy": "Always",
            "tolerations": [
                {
                    "operator": "Exists",
                    "tolerationSeconds": 300,
                    "effect": "NoExecute",
                    "key": "node.kubernetes.io/not-ready"
                },
                {
                    "operator": "Exists",
                    "tolerationSeconds": 300,
                    "effect": "NoExecute",
                    "key": "node.kubernetes.io/unreachable"
                }
            ],
            "preemptionPolicy": "PreemptLowerPriority",
            "containers": [
                {
                    "name": "nginx",
                    "image": "nginx:1.14.2",
                    "resources": {
                        "requests": {
                            "cpu": cpu,
                            "memory": mem
                        }
                    }
                }
            ],
            "nodeName": "pipeline-53659"
        },
        "metadata": {
            "name": name,
            "namespace": "default",
            "creationTimestamp": "2023-12-27T09:52:37Z",
            "uid": "8ff147ce-1724-4d8e-a76e-6a02852ec6f7"
        }
    }


def _get_pending_pod_obj(name, cpu, mem):
    return {
        "status": {
            "phase": "Pending",
            "conditions": [
                {
                    "status": "False",
                    "lastTransitionTime": "2023-12-27T09:52:47Z",
                    "reason": "Unschedulable",
                    "lastProbeTime": None,
                    "message": "0/2 nodes are available: 1 Insufficient cpu, 1 node(s) had untolerated taint {node-role.kubernetes.io/master: }. preemption: 0/2 nodes are available: 1 No preemption victims found for incoming pod, 1 Preemption is not helpful for scheduling..",
                    "type": "PodScheduled"
                }
            ],
            "qosClass": "Guaranteed"
        },
        "spec": {
            "dnsPolicy": "ClusterFirst",
            "securityContext": {},
            "serviceAccountName": "default",
            "schedulerName": "default-scheduler",
            "enableServiceLinks": True,
            "serviceAccount": "default",
            "priority": 0,
            "terminationGracePeriodSeconds": 30,
            "restartPolicy": "Always",
            "tolerations": [
                {
                    "operator": "Exists",
                    "tolerationSeconds": 300,
                    "effect": "NoExecute",
                    "key": "node.kubernetes.io/not-ready"
                },
                {
                    "operator": "Exists",
                    "tolerationSeconds": 300,
                    "effect": "NoExecute",
                    "key": "node.kubernetes.io/unreachable"
                }
            ],
            "containers": [
                {
                    "name": "nginx",
                    "image": "nginx:1.14.2",
                    "resources": {
                        "requests": {
                            "cpu": cpu,
                            "memory": mem
                        }
                    }
                }
            ],
            "preemptionPolicy": "PreemptLowerPriority"
        },
        "metadata": {
            "name": name,
            "namespace": "default",
            "creationTimestamp": "2023-12-27T09:52:47Z",
            "uid": "7d1480ae-22a2-4614-bb40-eaf8e70f499d"
        }
    }
