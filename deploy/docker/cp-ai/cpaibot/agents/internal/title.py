from llama_index.core.base.llms.types import ChatMessage, MessageRole
from cpaibot.llm.base import llm_chat
from cpaibot.common.utils import extract_quoted_response
from cpaibot.common.model.chat import Message


def generate_chat_title(
        messages: list[Message],
) -> str:

    if len(messages) == 0:
        raise RuntimeError("No messages received")

    messages = [m.to_llama_index() for m in messages]

    title_prompt = f"""You are generating a short title for a chat conversation.

## Input
You are given a list of chat messages (system, user, assistant).
Focus primarily on the user's intent and task.

## Task
Generate a concise, descriptive title that summarizes the main purpose of the conversation.

## Rules
1. Use 3–7 words.
2. Be specific and technical when appropriate.
3. Prefer verbs or concrete tasks (e.g., "Extract Job Parameters", "Debug Launch Payload").
4. Ignore greetings, filler, and meta discussion.
5. Do NOT mention the assistant, AI, or chat itself.
6. Do NOT include punctuation like quotes or trailing periods.
7. Do NOT use emojis.
8. If multiple topics exist, pick the dominant or most recent task.
9. Return ONLY the title text.

Title:
"""

    response = llm_chat([*messages, ChatMessage(content=title_prompt, role=MessageRole.USER)])
    try:
        return extract_quoted_response(response).strip()
    except:
        raise RuntimeError("Unable to generate chat title")
