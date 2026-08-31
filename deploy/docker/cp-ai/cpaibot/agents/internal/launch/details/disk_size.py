from cpaibot.common.model.chat import Message, MessagePartType
from cpaibot.common.utils import extract_json_response
from cpaibot.agents.internal.planning import Action

from cpaibot.agents.internal.launch.base import LaunchPayloadIntent
from cpaibot.agents.internal.launch.utils import extract_info
from cpaibot.agents.internal.launch.prompts import disk_size_prompt

from cpaibot.managers.chat import (create_chat_message_part,
                                   update_message_part)


def get_disk_size(
        messages: list[Message],
        response_message: Message,
        action: Action | None = None,
        intent: LaunchPayloadIntent | None = None,
) -> tuple[str | None, bool]:

    part = create_chat_message_part(response_message, part_type=MessagePartType.CONTEXT)

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

    disk_info = extract_info_wrapper(disk_size_prompt)
    if disk_info.lower() == "none":
        return None, True

    try:
        disk_info = extract_json_response(disk_info)
    except:
        return None, True

    if disk_info is None:
        return None, True

    if "disk_gb" not in disk_info:
        return None, True

    gb = disk_info["disk_gb"]

    part.text = f"SELECTED DISK SIZE: {gb}GB"
    update_message_part(part)

    return gb, True