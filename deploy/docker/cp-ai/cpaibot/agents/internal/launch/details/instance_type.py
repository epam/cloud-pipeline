from cpaibot.agents.pipeline.utilities import pick_best_elements
from cpaibot.common.model.chat import Message, MessagePart, MessagePartType
from cpaibot.common.logger import CpLogger as Logger
from cpaibot.common.utils import extract_json_response
from cpaibot.agents.internal.planning import Action
from cpaibot.agents.internal.misc import default_logger
from cpaibot.managers.chat import create_chat_message_part, update_message_part

from cpaibot.agents.internal.launch.base import LaunchPayloadIntent
from cpaibot.agents.internal.launch.utils import extract_info, stream_result
from cpaibot.agents.internal.launch.prompts import instance_type_prompt
from cpaibot.pipeline.instances import get_allowed_instance_types
from cpaibot.pipeline.types import InstanceType


def get_instance_type(
        messages: list[Message],
        response_message: Message,
        action: Action | None = None,
        intent: LaunchPayloadIntent | None = None,
        docker_image_id = None,
        is_spot = None,
        logger: Logger | None = None,
) -> tuple[str | None, bool]:
    if not logger:
        logger = default_logger

    part = create_chat_message_part(
        response_message,
        part_type=MessagePartType.CONTEXT
    )

    def extract_info_wrapper(prompt: str) -> str:
        return extract_info(
            response_message,
            messages,
            prompt,
            intent=intent,
            action=action,
            remove_quotes=False,
            part=part,
        )

    def stream_result_wrapper(prompt: str, message_part: MessagePart | None = None):
        return stream_result(response_message,
                             messages,
                             prompt,
                             intent=intent,
                             action=action,
                             part=message_part)

    message_id = response_message.identifier
    message_id = message_id[:8]

    logger.info(f"message #{message_id}: extracting instance type info from context...")
    instance_type = extract_info_wrapper(instance_type_prompt)
    logger.info(f"message #{message_id}: image info from context: {instance_type}")
    if instance_type.lower() == "none":
        instance_type = None

    try:
        instance_type = extract_json_response(instance_type)
    except:
        return None, True

    if instance_type is None:
        return None, True

    instance_type_val = instance_type.get("instance_type", None)
    cpu_val = instance_type.get("cpu", None)
    ram_val = instance_type.get("ram", None)
    gpu_val = instance_type.get("gpu", None)

    if instance_type_val:
        logger.info(f"message #{message_id}: using instance type: {instance_type_val}")
        part.text = f"SELECTED INSTANCE TYPE: {instance_type_val}"
        update_message_part(part)
        return instance_type_val, True

    if not cpu_val and not ram_val and not gpu_val:
        return None, True

    _parts = []
    def add_part(a_part, title):
        if a_part:
            _parts.append(f'{title}: {a_part}')

    add_part(cpu_val, "CPU")
    add_part(ram_val, "RAM")
    add_part(gpu_val, "GPU")

    instance_type_request = ", ".join(_parts)

    logger.info(f"message #{message_id}: picking instance type for {instance_type_request}...")
    picked_instance_types = _pick_instance_type(
        instance_type_request,
        docker_image_id=docker_image_id,
        is_spot=is_spot,
        logger=logger
    )
    logger.info(f"message #{message_id}: picking instance type for {instance_type_request}: {len(picked_instance_types)} instance types found")

    if len(picked_instance_types) == 0 and intent and intent.previous_launch_payload:
        logger.info(f"message #{message_id}: using instance type from previous launch payload: {intent.previous_launch_payload.instance_type}")
        return intent.previous_launch_payload.instance_type, True

    if len(picked_instance_types) != 1:
        if len(picked_instance_types) == 0:
            stream_result_wrapper(
                f'Begin your response with: "I couldn\'t find any instance types matching <request description>"\n'
                f'Request technical description: "{instance_type_request}"\n\n'
                f'Then continue by:\n'
                f'- Asking the user to verify the spelling or provide more context\n'
                f'- Offering to help search using different keywords or a description\n\n'
                f'No introductory phrases like "Okay" or "I can help".'
            )
            return None, False
        instance_types_descr = '\n'.join([f"- {di.to_markdown()}" for di in picked_instance_types])
        decision_part = create_chat_message_part(response_message, part_type=MessagePartType.DECISION)
        if not decision_part.metadata:
            decision_part.metadata = {}
        decision_part.metadata.update({
            "select_type": "instance_type",
            "options": [di.to_json() for di in picked_instance_types],
        })
        logger.info(f"message #{message_id}: user interaction required (select instance type)")
        stream_result_wrapper(
            f'Begin with: "I found {len(picked_instance_types)} instance types matching <request>"\n'
            f'Request technical description: {instance_type_request}\n\n'
            f'Instance types:\n{instance_types_descr}\n\n'
            f'Then:\n'
            f'- Present the list clearly\n'
            f'- Ask which one the user wants to use\n'
            f'- If relevant, note key differences between them\n\n'
            f'No "Okay" or "Sure" at the start.',
            message_part=decision_part
        )
        return None, False

    logger.info(f"message #{message_id}: using {picked_instance_types[0].name} instance type")
    part.text = f"SELECTED INSTANCE TYPE: {picked_instance_types[0].name}"
    update_message_part(part)

    return picked_instance_types[0].name, True


def _pick_instance_type(
        instance_type_request: str,
        docker_image_id = None,
        is_spot = None,
        logger: Logger | None = None,
):
    if not logger:
        logger = default_logger
    logger.info("fetching allowed instance types...")
    allowed_instance_types = get_allowed_instance_types(
        tool_id=docker_image_id,
        spot=is_spot,
    )
    logger.info(f"{len(allowed_instance_types)} instance types fetched")

    class _InstanceTypeIdentified(InstanceType):
        id: int

    _instance_type_score_prompt = (f'Calculate score based on the following rules:\n'
                                   f'- instance type name fully matched case-insensitive, e.g. "m5.2xlarge" (for AWS), "n2-standard-8" (for GCP), "Standard_D8s_v3" (for Azure) => score = 2.\n'
                                   f'- instance type name partially matched, and in terms of cpu / memory / gpu is close to the requested by user => score = 1.5.\n'
                                   f'- instance type has the same or larger cpu / memory / gpu parameters, than requested by user => score = 1.2.\n'
                                   f'- instance type has the lower cpu / memory / gpu parameters, than requested by user => score = 0.8.\n'
                                   f'- otherwise (does not match user query) => score = 0.\n')

    instance_types = [_InstanceTypeIdentified(**i.model_dump(), id=idx) for idx, i in enumerate(allowed_instance_types)]
    return pick_best_elements(
        instance_type_request,
        instance_types,
        scoring_rules_prompt=_instance_type_score_prompt,
        logger=logger,
    )