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

import json
import os
from src.config import Config


def mocked_url(url_prefix_to_mock):
    api = Config.instance().api.strip('/')
    return os.path.join(api, url_prefix_to_mock)


def mock_pipe_load_by_id(pipeline_id, pipeline_name, repository, server_date):
    get_by_id_response = {
        "payload": {
            "id": pipeline_id,
            "name": pipeline_name,
            "description": "",
            "repository": repository,
            "createdDate": server_date,
            "currentVersion": "awesome_version"
        },
        "status": "OK"
    }
    return json.dumps(get_by_id_response)


def mock_run(pipeline_id, docker_image="", identifier=None):
    run_response = {
        "payload": {
            "id": identifier,
            "pipelineId": pipeline_id,
            "dockerImage": docker_image,
            "status": "SCHEDULED",
            "pipelineRunParameters": [
                {
                    "name": "param_name",
                    "value": "param",
                }
            ]
        },
        "status": "OK"
    }
    return json.dumps(run_response)


def mock_run_parameters(instance_disk, instance_type, param_name="param_name", param_value="param"):
    run_parameters_response = {
        "payload": {
            "main_file": "file",
            "instance_disk": instance_disk,
            "instance_size": instance_type,
            "main_class": "test_class",
            "parameters": {
                param_name: {
                    "required": False,
                    "type": "input",
                    "value": param_value
                }
            }
        },
        "status": "OK"
    }
    return json.dumps(run_parameters_response)


def mock_pipe(pipeline_id, pipeline_name, raw_date, v_1):
    list_response = {
        "payload": {
            "id": pipeline_id,
            "name": pipeline_name,
            "currentVersion": mock_version(raw_date, v_1)
        },
        "status": "OK"
    }
    return json.dumps(list_response)


def mock_load_pipes(id_1, id_2, name_1, name_2, raw_date, v_1, repo):
    pipe_1 = {
        "id": id_1,
        "name": name_1,
        "currentVersion": mock_version(raw_date, v_1),
        "createdDate": raw_date,
        "repository": repo
    }
    pipe_2 = {
        "id": id_2,
        "name": name_2,
        "currentVersion": mock_version(raw_date, v_1),
        "createdDate": raw_date,
        "repository": repo
    }
    response = {
        "payload": [pipe_1, pipe_2],
        "status": "OK"
    }
    return json.dumps(response)


def mock_versions(raw_date, v_1, v_2):
    version_1 = mock_version(raw_date, v_1)
    version_2 = mock_version(raw_date, v_2)
    version_response = {
        "payload": [version_1, version_2],
        "status": "OK"
    }
    return json.dumps(version_response)


def mock_version(raw_date, v_1):
    version_1 = {
        "name": v_1,
        "draft": "draft",
        "createdDate": raw_date,
        "commitId": "commit"
    }
    return version_1


def mock_pipeline_datastorage(pipeline_id, raw_date):
    datastorage_1 = {
        "fileMask": "bucket/1",
        "pipelineId": pipeline_id,
        "createdDate": raw_date
    }
    datastorage_2 = {
        "fileMask": "bucket/2",
        "pipelineId": pipeline_id,
        "createdDate": raw_date
    }
    datastorage_response = {
        "payload": [datastorage_1, datastorage_2],
        "status": "OK"
    }
    return json.dumps(datastorage_response)


def mock_datastorage(id, name, path, type, storage_policy={}):
    datastorage = {
        "id": id,
        "name": name,
        "path": path,
        "type": type,
        "storagePolicy": storage_policy
    }
    datastorage_response = {
        "payload": datastorage,
        "status": "OK"
    }
    return json.dumps(datastorage_response)


def mock_storage_policy(backup_duration, lts_duration, sts_duration, versioning_enabled):
    storage_policy = {
        "backupDuration": backup_duration,
        "longTermStorageDuration": lts_duration,
        "shortTermStorageDuration": sts_duration,
        "versioningEnabled": versioning_enabled
    }
    return storage_policy


def mock_price(instance_disk, instance_type):
    price_response = {
        "payload": {
            "instanceType": instance_type,
            "instanceDisk": instance_disk,
            "pricePerHour": 0.12,
            "minimumTimePrice": 0.12,
            "maximumTimePrice": 0.12,
            "averageTimePrice": 0.12
        },
        "status": "OK"
    }
    return json.dumps(price_response)


def mock_run_filter(total_count="42", elements=[]):
    response = {
        "payload": {
            "totalCount": total_count,
            "elements": elements
        },
        "status": "OK"
    }
    return json.dumps(response)


def mock_element(identifier=None, pipeline_id=None, pipeline_name=None, version=None, status=None, start_date=None,
                 end_date=None, parameters=[], instance={}):
    response = {
        "id": identifier,
        "pipelineId": pipeline_id,
        "pipelineName": pipeline_name,
        "status": status,
        "version": version,
        "startDate": start_date,
        "endDate": end_date,
        "pipelineRunParameters": parameters,
        "instance": instance
    }
    return response


def mock_instance(node_ip):
    response = {
        "nodeIP": node_ip
    }
    return response


def mock_parameter(name=None, value=None):
    response = {
        "name": name,
        "value": value
    }
    return response


def mock_cluster_load_all(nodes=[]):
    response = {
        "payload": nodes,
        "status": "OK"
    }
    return json.dumps(response)


def mock_node(node, addresses):
    response = {
        "uid": node.uid,
        "name": node.name,
        "creationTimestamp": node.created,
        "pipelineRun": node.pipeline_run,
        "systemInfo": node.system_info,
        "labels": node.labels,
        "allocatable": node.allocatable,
        "capacity": node.capacity,
        "addresses": mock_node_addresses(addresses),
        "pods": node.pods
    }
    return response


def mock_node_addresses(addresses):
    response = []
    for address in addresses:
        address_response = {
            "address": address.address,
            "type": address.address_type
        }
        response.append(address_response)
    return response


def mock_node_pods(pod):
    response = {
        "uid": pod.uid,
        "name": pod.name,
        "namespace": pod.namespace,
        "nodeName": pod.node_name,
        "phase": pod.phase
    }
    return response


def mock_node_load(node, addresses):
    response = {
        "payload": mock_node(node, addresses),
        "status": "OK"
    }
    return json.dumps(response)


def mock_pipeline_run(model):
    """
    :type model: PipelineRunModel
    """
    response = {
        "payload": {
            "id": model.identifier,
            "pipelineId": model.pipeline_id,
            "pipelineName": model.pipeline,
            "version": model.version,
            "startDate": model.scheduled_date,
            "endDate": model.end_date,
            "owner": model.owner,
            "status": model.status
        },
        "status": "OK"
    }
    return json.dumps(response)
