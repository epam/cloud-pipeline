import json
from pydantic import BaseModel
from cp_ai.pipeline.types import (Configuration,
                                  Pipeline,
                                  InstanceType)
from cp_ai.pipeline.preferences import (get_run_capabilities_parameters,
                                        get_autoscaling_configuration_parameters)
from cp_ai.pipeline.parameters import get_system_parameters, KNOWN_SYSTEM_PARAMETERS
from cp_ai.pipeline.instances import get_allowed_instance_types
from cp_ai.pipeline.tools import find_docker_image
from cp_ai.common.utilities import extract_json_response
from cp_ai.llm import llm_simple_query
from typing import Any, Callable
from ..types import LaunchPayload
from ..utilities import pick_best_elements
from ..logger import agents_logger


class LaunchException(RuntimeError):
    def __init__(self, message: str, details = None):
        super().__init__(message)
        self.launch_exception_message = message
        self.details = details

    def __str__(self):
        return json.dumps({
            'type': 'LaunchException',
            'message': self.launch_exception_message,
            'details': self.details
        })


class MissingProperty(BaseModel):
    missing_key: str
    description: str | None = None

    def __str__(self):
        if self.description:
            return f'{self.missing_key} - {self.description}'
        return self.missing_key


def _configuration_to_json_str(
        config: Configuration,
        /,
        exclude_parameters: list[str] | None = None,
        only_parameters: bool | None = None
):
    exclude = []
    if exclude_parameters is not None:
        exclude = [e.lower() for e in exclude_parameters]
    def get_value(value: Any, default_value = None) -> Any:
        if value is None:
            return default_value
        return value

    params_map = config.parameters or {}
    params = {
        parameter: {
            'type': get_value(value.type, 'string'),
            'default_value': get_value(value.value, None),
            'required': get_value(value.is_required, False),
            'description': value.description
        } for parameter, value in params_map.items() if parameter.lower() not in exclude
    }
    node_config_parameters = {
        'instance_size': {
            'default_value': config.instance_size,
            'description': 'Node instance type',
            'required': True
        },
        'instance_disk': {
            'default_value': config.instance_disk,
            'description': 'Node disk size (GB)',
            'required': True
        },
        'docker_image': {
            'default_value': config.docker_image,
            'description': 'Docker image',
            'required': True
        },
        'cmd_template': {
            'default_value': config.cmd_template,
            'description': 'Command',
            'required': True
        }
    }
    d = node_config_parameters if not only_parameters else {}
    return json.dumps({
        **d,
        'parameters': params
    }, indent=' ')


def _fulfill_parameters_based_on_user_query(
        user_query: str,
        configuration: Configuration,
        /,
        exclude_parameters: list[str] | None = None,
        params: dict[str, Any] | None = None,
        generation_info_callback: Callable[[str], Any] | None = None
):
    cfg = _configuration_to_json_str(
        configuration,
        exclude_parameters=exclude_parameters,
        only_parameters=True
    )
    user_payload = ''
    and_user_payload = ''
    if params is not None and len(params) > 0:
        p = json.dumps(params, indent=' ')
        user_payload = ('Here\'s the user\'s payload:\n'
                        '```json\n'
                        f'{p}\n'
                        '```\n')
        and_user_payload = ' and user\'s payload'
    prompt = (f'Act as a developer that generates a payload (JSON object) to the API endpoint '
              f'that submits a cloud-based job.\n'
              f'Your goal is to fulfill parameters based on the user\'s query{and_user_payload}.\n'
              f'Here\'s the payload schema:\n'
              f'```json\n'
              f'{cfg}\n'
              f'```\n\n'
              f'Here\'s the user\'s query:\n'
              f'----------\n'
              f'{user_query}\n'
              f'----------\n'
              f'{user_payload}\n\n'
              'Your goal is to generate a JSON object of format `{"parameter_name": "parameter_value"}` '
              f'based on the user\'s query{and_user_payload}.\n'
              f'<important>If some parameter is missing in user\'s query{and_user_payload}, and does not have '
              f'default value, SKIP THIS PARAMETER</important>.\n'
              f'User may also request to add custom parameters '
              f'(parameters that are NOT related to the node / cluster / cloud configuration or settings), '
              f'that are not presented in schema; include these parameters as well.\n'
              f'You can use "$RUN_ID" placeholder as a part of the parameter\'s value, if user asks to '
              f'make a parameter value job-dependent (this will be resolved on the runtime, a job / run identifier '
              f'will be substituted everywhere instead of "$RUN_ID", example of such value: "some/storage/path/$RUN_ID/...").\n'
              'Output example: `{"param1": "value1", "param2": true, "param3": 10}`.\n\n'
              f'Resulted payload (as JSON object of <parameter name>: <parameter value> pairs):')
    agents_logger.debug(f'fulfilling parameters -> user query: {user_query}')
    agents_logger.debug(f'fulfilling parameters -> user payload: {repr(params)}')
    r = llm_simple_query(prompt)
    agents_logger.debug(f'fulfilling parameters -> llm response: {r}')
    parsed = extract_json_response(r)
    def reformat(key: str, o: Any) -> dict:
        if not isinstance(o, dict):
            o = {}
        if isinstance(o, dict) and len(o) == 1 and key in o:
            val = o.get(key)
            if isinstance(val, dict):
                o = val
        return o

    # sometimes model responds with {"parameters": {...}} format
    parsed = reformat('parameters', parsed)
    result = {}
    if parsed is not None and isinstance(parsed, dict):
        for p, v in parsed.items():
            if v is not None and isinstance(v, dict):
                v = v.get('value', None)
            if v is not None:
                result.update({p: v})
    if params is not None and isinstance(params, dict):
        for p, v in params.items():
            if v is not None and isinstance(v, dict):
                v = v.get('value', None)
            if v is not None:
                result.update({p: v})
    result = {v: p for v, p in result.items() if p not in {'instance_size', 'instance_disk', 'disk', 'instance_type'}}
    agents_logger.info(f'fulfilled parameters: {repr(result)}')
    return result


def extract_instance_type_from_query(
        query: str,
        /,
        tool_id: int | None = None,
        spot: bool | None = None,
        region_id: int | None = None,
        bearer: str | None = None,
        generation_info_callback: Callable[[str], Any] | None = None
) -> str | None:
    prompt = (f'Here is the user query:\n'
              f'----------------\n'
              f'{query}\n'
              f'----------------\n\n'
              f'Please, answer "YES" if user asks to submit some specific instance type, for example, '
              f'm5.2xlarge (AWS), n2-standard-8 (GCP), Standard_D8s_v3 (Azure), '
              f'or by specifying not instance cpu / memory / gpu requirements, '
              f'or if `instance_type` field is specified.\n\n'
              f'Otherwise, if user does not mention any node instance type requirements, answer "NO".\n\n'
              f'Your answer as a plain text, "YES" or "NO":')
    has_requirements_resp = llm_simple_query(prompt).strip().lower()
    has_requirements = extract_json_response(has_requirements_resp) or has_requirements_resp
    if isinstance(has_requirements, dict):
        has_requirements = has_requirements.get('result', 'yes')
    if str(has_requirements).strip().lower() not in {'no', 'false'}:
        allowed_instance_types = get_allowed_instance_types(
            tool_id=tool_id,
            spot=spot,
            region_id=region_id,
            bearer=bearer
        )

        class _InstanceTypeIdentified(InstanceType):
            id: int

        _instance_type_score_prompt = (f'Calculate score based on the following rules:\n'
                                       f'- instance type name fully matched case-insensitive, e.g. "m5.2xlarge" (for AWS), "n2-standard-8" (for GCP), "Standard_D8s_v3" (for Azure) => score = 2.\n'
                                       f'- instance type name partially matched, and in terms of cpu / memory / gpu is close to the requested by user => score = 1.5.\n'
                                       f'- instance type has the same or larger cpu / memory / gpu parameters, than requested by user => score = 1.2.\n'
                                       f'- instance type has the lower cpu / memory / gpu parameters, than requested by user => score = 0.8.\n'
                                       f'- otherwise (does not match user query) => score = 0.\n')

        instance_types = [_InstanceTypeIdentified(**i.model_dump(), id=idx) for idx, i in enumerate(allowed_instance_types)]
        instance_type_obj = pick_best_elements(
            query,
            instance_types,
            scoring_rules_prompt=_instance_type_score_prompt
        )
        if len(instance_type_obj) > 0:
            result = instance_type_obj[0]
            if generation_info_callback:
                generation_info_callback(f'User requested specific instance type; '
                                         f'{len(instance_type_obj)} instance types were found that match user query. '
                                         f'{repr(result.model_dump())} instance type was selected.')
            return result.name
    return None


def extract_instance_disk_from_query(
        query: str,
        /,
        bearer: str | None = None,
        generation_info_callback: Callable[[str], Any] | None = None
) -> float | None:
    prompt = (f'Here is the user query:\n'
              f'----------------\n'
              f'{query}\n'
              f'----------------\n\n'
              'Please, answer `{"size": ...}` if user specifies a node or disk size '
              '(in gigabytes, terabytes, megabytes, etc.), provide a size in gigabytes.\n'
              'Otherwise, if user does not mention any node size requirements, answer `{}` (empty JSON object).\n\n'
              'Format your answer as a JSON object only, `{"size": ...}` or `{}`:')
    size_requirements_resp = extract_json_response(llm_simple_query(prompt).strip().lower())
    if isinstance(size_requirements_resp, dict):
        size = size_requirements_resp.get('size', None)
        try:
            size = float(repr(size))
        except BaseException as e:
            agents_logger.error(f'error parsing size requirements from "{repr(size)}"',
                                exc_info=e)
            size = None
    else:
        size = None
    return size


def generate_launch_payload(
        configuration: Configuration,
        /,
        user_query: str,
        pipeline: Pipeline | None = None,
        pipeline_version: str | None = None,
        spot: bool | None = None,
        bearer: str | None = None,
        generation_info_callback: Callable[[str], Any] | None = None,
        launch_payload: LaunchPayload | None = None,
) -> LaunchPayload:
    caps = get_run_capabilities_parameters()
    sys_params = get_system_parameters()
    autoscaling_params = get_autoscaling_configuration_parameters()
    system_parameters = list({
        *caps,
        *[p.name for p in sys_params],
        *autoscaling_params,
        *KNOWN_SYSTEM_PARAMETERS
    })
    agents_logger.debug(f'generate_launch_payload -> {len(system_parameters)} system parameters fetched')
    # docker_image
    docker_image = configuration.docker_image
    if docker_image is None:
        raise LaunchException('Provided configuration does not specify docker image')
    # ------------
    docker_image_instance = find_docker_image(docker_image)
    # cloud region id
    cloud_region_id: int | None = None
    if configuration.cloudRegionId is not None:
        cloud_region_id = configuration.cloudRegionId
    # ---------------
    if spot is None:
        spot = configuration.is_spot
    if spot is None:
        spot = False
    # instance type
    instance_type = None
    if instance_type is None:
        instance_type = extract_instance_type_from_query(
            user_query,
            tool_id=docker_image_instance.id if docker_image_instance is not None else None,
            spot=spot,
            region_id=cloud_region_id,
            bearer=bearer,
            generation_info_callback=generation_info_callback
        )
    if instance_type is None and launch_payload is not None:
        instance_type = launch_payload.instance_type
    if instance_type is None:
        instance_type = configuration.instance_size
    missing: list[MissingProperty] = []
    if instance_type is None:
        missing.append(MissingProperty(missing_key='instance type',
                                       description='a node instance type'))
    # -------------
    # instance disk
    instance_disk = None
    if not instance_disk:
        instance_disk = extract_instance_disk_from_query(
            user_query,
            bearer=bearer,
            generation_info_callback=generation_info_callback
        )
    if not instance_disk and launch_payload is not None:
        instance_disk = launch_payload.disk
    if not instance_disk:
        instance_disk = configuration.instance_disk
    if not instance_disk:
        missing.append(MissingProperty(missing_key='node disk size',
                                       description='a node disk size in GB'))
    # -------------
    # cmd template
    cmd_template = None
    if cmd_template is None and launch_payload is not None:
        cmd_template = launch_payload.cmd
    if cmd_template is None:
        cmd_template = configuration.cmd_template
    if cmd_template is None:
        missing.append(MissingProperty(missing_key='command template (cmd_template)',
                                       description='a main command to be executed'))
        raise LaunchException('Please specify cmd template')
    # ------------
    # parameters
    parameters = _fulfill_parameters_based_on_user_query(
        user_query,
        configuration,
        exclude_parameters=system_parameters,
        params=launch_payload.parameters if launch_payload is not None else None,
        generation_info_callback=generation_info_callback
    )
    params = {}
    params_map = configuration.parameters or {}
    for parameter, parameter_config in params_map.items():
        value = parameter_config.value
        if parameters and parameter in parameters:
            value = parameters.get(parameter)
            if isinstance(value, dict):
                value = value.get('value', None)
        elif (
                launch_payload is not None and
                launch_payload.parameters is not None and
                parameter in launch_payload.parameters
        ):
            value = launch_payload.parameters.get(parameter)
            if isinstance(value, dict):
                value = value.get('value', None)
        if value is None and parameter_config.is_required:
            desc = parameter_config.type
            if parameter_config.description:
                desc = f'{parameter_config.type}, {parameter_config.description}'
            missing.append(MissingProperty(missing_key=f'parameter "{parameter}"',
                                           description=desc))
        params.update({parameter: {'value': value}})
    if len(missing) > 0:
        details = '\n'.join(f'- {m.__str__()}' for m in missing)
        raise LaunchException('Please specify required fields:',
                              details)
    if launch_payload is not None:
        for parameter, parameter_value in launch_payload.parameters.items():
            if parameter_value is not None and parameter not in params:
                params.update({parameter: parameter_value})
    if parameters is not None:
        for parameter, parameter_value in parameters.items():
            if parameter_value is not None:
                existing = params.get(parameter, {})
                existing.update({'value': parameter_value})
                params.update({parameter: existing})
    # ------------
    payload = LaunchPayload(
        cloudRegionId=cloud_region_id,
        dockerImage=docker_image,
        instanceType=instance_type,
        disk=instance_disk,
        cmd=cmd_template,
        is_spot=False,
        parameters=params,
        pipelineId=pipeline.id if pipeline is not None else None,
        version=pipeline_version if pipeline is not None and pipeline_version is not None else None
    )
    agents_logger.info(f'generate_launch_payload -> payload: \n{repr(payload.model_dump())}')
    return payload
