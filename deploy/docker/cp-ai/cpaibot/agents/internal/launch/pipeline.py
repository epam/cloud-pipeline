from cpaibot.common.model.chat import Message, MessagePart
from cpaibot.agents.internal.planning import Action
from cpaibot.agents.internal.launch.base import LaunchPayloadIntent

from cpaibot.agents.internal.launch.details import get_docker_image
from cpaibot.agents.pipeline.types import LaunchPayload


def get_pipeline_launch_result(
        messages: list[Message],
        response_message: Message,
        intent: LaunchPayloadIntent | None = None,
        action: Action | None = None,
        part: MessagePart | None = None,
) -> tuple[LaunchPayload | None, bool]:
    di, version, continue_exec = get_docker_image(messages,
                                                  response_message,
                                                  intent=intent,
                                                  action=action,
                                                  is_pipeline=True)
    if not continue_exec:
        return None, False
    return None, True