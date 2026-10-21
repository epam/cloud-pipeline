from llama_index.core.base.llms.types import MessageRole, ChatMessage

from cpaibot.common.logger import CpLogger as Logger
from cpaibot.common.model.chat import Message
from cpaibot.llm.base import llm_stream_chat
from cpaibot.managers.chat import create_chat_message_part
from cpaibot.agents.internal.planning import Action
from cpaibot.agents.internal.misc import default_logger


def get_generic_response(
        messages: list[Message],
        response_message: Message,
        /,
        action: Action | None = None,
        query: str | None = None,
        filter_out_context_parts=False,
        logger: Logger | None = None
) -> bool:
    if not logger:
        logger = default_logger
    part = create_chat_message_part(response_message)
    all_messages = [*messages, response_message]
    chat_messages = [m.to_llama_index(filter_out_context_parts) for m in all_messages if not m.message_is_empty(filter_out_context_parts)]
    if action:
        chat_messages.append(ChatMessage(
            content=action.action,
            role=MessageRole.USER,
        ))
    if query:
        chat_messages.append(ChatMessage(content=query, role=MessageRole.USER))
    llm_stream_chat(chat_messages, part=part, logger=logger)
    return True
