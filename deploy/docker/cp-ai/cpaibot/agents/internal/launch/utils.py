from llama_index.core.base.llms.types import ChatMessage, MessageRole

from cpaibot.common.model.chat import Message, MessagePart
from cpaibot.common.utils import extract_quoted_response
from cpaibot.llm.base import llm_chat, llm_stream_chat
from cpaibot.agents.internal.planning import Action
from cpaibot.managers.chat import create_chat_message_part

from cpaibot.agents.internal.launch.base import LaunchPayloadIntent
from cpaibot.agents.internal.misc import preferences
from cpaibot.agents.pipeline.types import LaunchPayload


def extract_info(
        response_message: Message,
        messages: list[Message],
        prompt: str,
        intent: LaunchPayloadIntent | None = None,
        action: Action | None = None,
        remove_quotes: bool = True,
        part: MessagePart | None = None,
):
    if len(messages) == 0:
        raise RuntimeError("No messages received")

    user_message = next((m for m in messages[::-1] if m.role == MessageRole.USER), None)
    user_message_text = user_message.to_llama_index().content if user_message is not None else ""

    if action:
        action_info = f"""\nAction: {action.action}"""
    else:
        action_info = ""

    intent_info = ("\n" + intent.to_markdown()) if intent else ""

    final_prompt = f"""Analyze this user request about launching something on an {preferences.deployment_name} platform.

User request: "{user_message_text}"{action_info}{intent_info}

{prompt}"""

    result = llm_chat(
        [
            *messages,
            response_message,
            ChatMessage(content=final_prompt, role=MessageRole.USER)
        ],
        part=part,
    )
    result = result.strip()
    return extract_quoted_response(result) if remove_quotes else result


def stream_result(
        response_message: Message,
        messages: list[Message],
        prompt: str,
        intent: LaunchPayloadIntent | None = None,
        action: Action | None = None,
        part: MessagePart | None = None,
):
    if len(messages) == 0:
        raise RuntimeError("No messages received")

    user_message = next((m for m in messages[::-1] if m.role == MessageRole.USER), None)
    user_message_text = user_message.to_llama_index().content if user_message is not None else ""

    if action:
        action_info = f"""\nAction: {action.action}"""
    else:
        action_info = ""

    intent_info = intent.to_markdown() if intent else ""

    final_prompt = f"""User request: "{user_message_text}"{action_info}{intent_info}

{prompt}"""

    if not part:
        part = create_chat_message_part(response_message)

    llm_stream_chat(
        [
            *messages,
            response_message,
            ChatMessage(content=final_prompt, role=MessageRole.USER)
        ],
        part=part,
    )


def get_launch_payloads(messages: list[Message]) -> list[LaunchPayload]:
    result = []
    for m in messages:
        payloads = m.get_launch_payloads()
        for p in payloads:
            try:
                payload = LaunchPayload(**p)
                result.append(payload)
            except:
                pass
    return result
