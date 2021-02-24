# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

from .base import API
import json
import datetime
from ..model.pipeline_model import PipelineModel
from ..model.version_model import VersionModel
from ..model.pipeline_run_parameters_model import PipelineRunParametersModel
from ..model.pipeline_run_model import PipelineRunModel, PriceType
from ..model.datastorage_rule_model import DataStorageRuleModel
from ..model.instance_price import InstancePrice
from ..api.pipeline_run import PipelineRun

TYPE_VALUE_DELIMITER = '?'
TYPE_DEFAULT = 'string'


class Pipeline(API):
    def __init__(self):
        super(Pipeline, self).__init__()

    @classmethod
    def list(cls):
        api = cls.instance()
        result = []
        response_data = api.call('pipeline/loadAll?loadVersion=true', None)
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        if 'payload' in response_data:
            for pipeline_json in response_data['payload']:
                pipeline = PipelineModel.load(pipeline_json)
                if 'currentVersion' in pipeline_json:
                    pipeline.set_current_version(VersionModel.load(pipeline_json['currentVersion']))
                result.append(pipeline)
        return result

    @classmethod
    def get(cls, identifier, load_storage_rules=True, load_versions=True, load_run_parameters=True, config_name=None):
        api = cls.instance()
        response_data = api.call('pipeline/find?id={}'.format(identifier), None)
        pipeline = PipelineModel.load(response_data['payload'])
        if pipeline is None:
            raise RuntimeError('Pipeline \'{}\' was not found'.format(identifier))
        if load_storage_rules:
            response_data = api.call('datastorage/rule/load?pipelineId={}'.format(pipeline.identifier), None)
            if 'payload' in response_data:
                pipeline.storage_rules = []
                for datastorage_rule_json in response_data['payload']:
                    pipeline.storage_rules.append(DataStorageRuleModel.load(datastorage_rule_json))
        if load_versions:
            pipeline.set_versions(list(cls.load_versions(pipeline.identifier)))
        if pipeline.current_version is not None and load_run_parameters:
            pipeline.current_version.set_run_parameters(cls.load_run_parameters(pipeline.identifier,
                                                                                pipeline.current_version_name,
                                                                                config_name=config_name))
        return pipeline

    @classmethod
    def load_versions(cls, identifier):
        api = cls.instance()
        response_data = api.call('pipeline/{}/versions'.format(identifier), None)
        for version_json in response_data['payload']:
            yield VersionModel.load(version_json)

    def get_by_id(self, identifier):
        response_data = self.call('pipeline/{}/load'.format(identifier), None)
        return PipelineModel.load(response_data['payload'])

    @classmethod
    def load_run_parameters(cls, identifier, version, config_name=None):
        api = cls.instance()
        api_method = 'pipeline/{}/parameters?version={}'.format(identifier, version)
        if config_name:
            api_method += '&name={}'.format(config_name)
        response_data = api.call(api_method, None)
        return PipelineRunParametersModel.load(response_data['payload'], version)

    @classmethod
    def parse_parameter_value(cls, value):
        parameter_type = TYPE_DEFAULT
        parameter_value = value
        is_type_defined = False
        if TYPE_VALUE_DELIMITER in value:
            user_parameter_value_parts = parameter_value.split(TYPE_VALUE_DELIMITER)
            parameter_type = user_parameter_value_parts[0]
            parameter_value = user_parameter_value_parts[1]
            is_type_defined = True
        return parameter_value, parameter_type, is_type_defined

    @classmethod
    def launch_pipeline(cls, pipeline_id, version, parameters,
                        instance_disk=None, instance_type=None,
                        docker_image=None, cmd_template=None,
                        timeout=None, config_name=None, instance_count=None,
                        price_type=None, region_id=None, parent_node=None, non_pause=None, friendly_url=None):
        api = cls.instance()
        params = {}
        for parameter in parameters:
            if parameter.value:
                parameter_value = cls.parse_parameter_value(parameter.value)
                params[parameter.name] = {'value': parameter_value[0], 'type': parameter_value[1] if parameter_value[2] else parameter.parameter_type}
        payload = {'pipelineId': pipeline_id, 'version': version, 'params': params}
        if instance_disk is not None:
            payload['hddSize'] = instance_disk
        if instance_type is not None:
            payload['instanceType'] = instance_type
        if docker_image is not None:
            payload['dockerImage'] = docker_image
        if cmd_template is not None:
            payload['cmdTemplate'] = cmd_template
        if timeout is not None:
            payload['timeout'] = timeout
        if config_name is not None:
            payload['configurationName'] = config_name
        if instance_count is not None and instance_count > 0:
            payload['nodeCount'] = instance_count
        if price_type:
            payload['isSpot'] = price_type == PriceType.SPOT
        if region_id is not None:
            payload['cloudRegionId'] = region_id
        if parent_node is not None:
            cls.__add_parent_node_params(payload, parent_node)
        if non_pause is not None:
            payload['nonPause'] = non_pause
        if friendly_url:
            payload['prettyUrl'] = friendly_url
        data = json.dumps(payload)
        response_data = api.call('run', data)
        return PipelineRunModel.load(response_data['payload'])

    @classmethod
    def launch_command(cls, instance_disk, instance_type,
                       docker_image, cmd_template, parameters,
                       timeout=None, instance_count=None, price_type=None,
                       region_id=None, parent_node=None, non_pause=None, friendly_url=None):
        api = cls.instance()
        payload = {}
        if instance_disk is not None:
            payload['hddSize'] = instance_disk
        if instance_type is not None:
            payload['instanceType'] = instance_type
        if docker_image is not None:
            payload['dockerImage'] = docker_image
        if cmd_template is not None:
            payload['cmdTemplate'] = cmd_template
        if timeout is not None:
            payload['timeout'] = timeout
        if instance_count is not None and instance_count > 0:
            payload['nodeCount'] = instance_count
        if price_type:
            payload['isSpot'] = price_type == PriceType.SPOT
        if region_id is not None:
            payload['cloudRegionId'] = region_id
        if parent_node is not None:
            cls.__add_parent_node_params(payload, parent_node)
        if non_pause is not None:
            payload['nonPause'] = non_pause
        if friendly_url:
            payload['prettyUrl'] = friendly_url
        if parameters is not None:
            params = {}
            for key in parameters.keys():
                parameter_value = cls.parse_parameter_value(parameters.get(key))
                params[key] = {'value': parameter_value[0], 'type': parameter_value[1]}
            payload['params'] = params
        data = json.dumps(payload)
        response_data = api.call('run', data)
        return PipelineRunModel.load(response_data['payload'])

    @classmethod
    def stop_pipeline(cls, run_id):
        api = cls.instance()
        data = json.dumps({'status': 'STOPPED', 'endDate': datetime.datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S.%f')})
        response_data = api.call('run/{}/status'.format(run_id), data)
        return PipelineRunModel.load(response_data['payload'])

    @classmethod
    def resume_pipeline(cls, run_id):
        api = cls.instance()
        response_data = api.call('/run/{}/resume'.format(run_id), None, http_method='post')
        return PipelineRunModel.load(response_data['payload'])

    @classmethod
    def pause_pipeline(cls, run_id, check_size):
        api = cls.instance()
        response_data = api.call('/run/{}/pause?checkSize={}'.format(run_id, check_size), None, http_method='post')
        return PipelineRunModel.load(response_data['payload'])

    @classmethod
    def get_estimated_price(cls, pipeline_id, version, instance_type, instance_disk, config_name=None,
                            price_type=None, region_id=None):
        api = cls.instance()
        data = {}
        if instance_type is not None:
            data['instanceType'] = instance_type
        if instance_disk is not None:
            data['instanceDisk'] = instance_disk
        if price_type:
            data['spot'] = price_type == PriceType.SPOT
        if region_id is not None:
            data['regionId'] = region_id
        api_url = 'pipeline/{}/price?version={}'.format(pipeline_id, version)
        if config_name:
            api_url += '&config={}'.format(config_name)
        response_data = api.call(api_url, json.dumps(data))
        return InstancePrice.load(response_data['payload'])

    @classmethod
    def __add_parent_node_params(cls, params, parent_node):
        run_model = PipelineRun.get(parent_node)
        for ins_param in run_model.instance:
            if ins_param[0] == 'nodeDisk':
                params['hddSize'] = ins_param[1]
            elif ins_param[0] == 'nodeType':
                params['instanceType'] = ins_param[1]
        params['parentRunId'] = parent_node
        params['parentNodeId'] = parent_node
