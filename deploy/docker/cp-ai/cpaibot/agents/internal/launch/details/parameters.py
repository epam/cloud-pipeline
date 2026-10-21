import json

from cpaibot.common.model.chat import Message, MessagePartType
from cpaibot.agents.internal.planning import Action

from cpaibot.agents.internal.launch.base import LaunchPayloadIntent
from cpaibot.agents.internal.launch.utils import extract_info
from cpaibot.agents.internal.launch.prompts import parameters_prompt
from cpaibot.pipeline.types import Configuration
from cpaibot.common.utils import extract_json_response
from cpaibot.managers.chat import create_chat_message_part, update_message_part
from typing import Any
from cpaibot.agents.pipeline.logger import agents_logger


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


def get_parameters(
        messages: list[Message],
        response_message: Message,
        configuration: Configuration,
        /,
        action: Action | None = None,
        intent: LaunchPayloadIntent | None = None,
        exclude_parameters: list[str] | None = None,
) -> tuple[dict | None, bool]:
    part = create_chat_message_part(response_message, part_type=MessagePartType.CONTEXT)
    def extract_info_wrapper(prompt: str) -> str:
        return extract_info(
            response_message,
            messages,
            prompt,
            intent=intent,
            action=action,
            remove_quotes=False,
            part=part
        )

    params = extract_info_wrapper(parameters_prompt)
    if params.lower() == "none":
        return None, True

    try:
        params = extract_json_response(params)
    except:
        return None, True

    if params is None:
        return None, True

    if "parameters" not in params or not isinstance(params["parameters"], dict):
        return None, True

    if len(params["parameters"]) == 0:
        return None, True

    user_specified_parameters = params["parameters"]
    user_specified_parameters_dump = json.dumps(user_specified_parameters, indent=' ')

    cfg = _configuration_to_json_str(
        configuration,
        exclude_parameters=exclude_parameters,
        only_parameters=True
    )
    prompt = (
        f'You are helping to generate a JSON payload for a cloud job submission API.\n\n'
        f'## Goal\n'
        f'The goal is to extract ALL parameters explicitly requested by the user.\n'
        f'The provided schema is used for:\n'
        f'- validating parameter names\n'
        f'- correcting user typos\n'
        f'- mapping user-facing terms to actual parameter names\n\n'
        f'Infrastructure-related parameters must be ignored.\n\n'
        f'## Parameter Schema (for validation & lookup)\n'
        f'```json\n'
        f'{cfg}\n'
        f'```\n\n'
        f'## User Requested Parameters\n'
        f'```json\n'
        f'{user_specified_parameters_dump}\n'
        f'```\n\n'
        f'## Task\n'
        f'Extract parameter values from the user request and produce a flat JSON object:\n'
        f'{{"parameter_name": parameter_value}}\n\n'
        f'## Rules\n'
        f'1. Extract ALL parameters explicitly requested by the user.\n'
        f'2. EXCLUDE infrastructure parameters such as:\n'
        f'   - instance_size\n'
        f'   - instance_type\n'
        f'   - instance_disk\n'
        f'   - disk\n'
        f'   - docker_image\n'
        f'   - cmd_template\n'
        f'3. INCLUDE parameters starting with CP_ or any ALL_UPPERCASE names exactly as provided by the user.\n'
        f'4. If the user uses informal names or typos (e.g. "fastq"), use the schema to infer the correct parameter name.\n'
        f'5. Include parameters even if they are NOT present in the schema (except infrastructure parameters).\n'
        f'6. Preserve correct data types (string, number, boolean, list).\n'
        f'7. Use "$RUN_ID" for job-dependent paths if requested by the user.\n'
        f'8. Do NOT invent parameters or default values.\n'
        f'9. Return ONLY valid JSON. No explanations. No markdown.\n\n'
        f'## Examples\n\n'
        f'### Example 1\n'
        f'Schema:\n'
        f'{{"DATASET": {{"type": "string", ...}}, "BATCH_SIZE": {{"type": "string", ...}}}}\n\n'
        f'User request:\n'
        f'{{"dataset": "mnist", "batch": 32, "instance_size": "large"}}\n\n'
        f'Output:\n'
        f'{{"DATASET": "mnist", "BATCH_SIZE": 32}}\n\n'
        f'### Example 2\n'
        f'Schema:\n'
        f'{{"DATASET": {{"type": "string", ...}}, "SAMPLESHEET": {{"type": "input", ...}}, "BATCH_SIZE": {{"type": "string", ...}}}}\n\n'
        f'User request:\n'
        f'{{"dataset": "mnist", "batch": 32, "input": "/mybucket/inputs/samplesheet.csv", "output": "/mybucket/outputs/RUN_ID"}}\n\n'
        f'Output:\n'
        f'{{"DATASET": "mnist", "BATCH_SIZE": 32, "SAMPLESHEET": "/mybucket/inputs/samplesheet.csv", "output": "/mybucket/outputs/$RUN_ID"}}\n\n'
        f'JSON payload:'
    )


    params = extract_info_wrapper(prompt)

    parsed = extract_json_response(params)
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
    result = {p: v for p, v in result.items() if p not in {'instance_size', 'instance_disk', 'disk', 'instance_type'}}
    agents_logger.info(f'fulfilled parameters: {repr(result)}')
    return result, True