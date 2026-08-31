from enum import Enum
from pydantic import BaseModel
from typing import Optional
from llama_index.core.base.llms.types import ChatMessage, MessageRole, TextBlock
from cpaibot.llm.base import llm_stream_chat, llm_chat
from cpaibot.common.utils import extract_json_response, extract_quoted_response
from cpaibot.agents.internal.intent import Intent
from cpaibot.common.model.chat import Message, MessagePartType
from cpaibot.managers.chat import create_chat_message_part
from cpaibot.agents.internal.misc import preferences


class ActionType(str, Enum):
    DOCUMENTATION = "DOCUMENTATION"
    LAUNCH = "LAUNCH"
    CHAT = "CHAT"


class Action(BaseModel):
    type: ActionType
    action: str

    @classmethod
    def deserialize(cls, data: dict) -> Optional["Action"]:
        a_type = ActionType.CHAT
        if "type" in data:
            try:
                a_type = ActionType(data["type"])
            except:
                pass
        if "action" in data:
            return Action(type=a_type, action=extract_quoted_response((data["action"] or "").strip()))
        return None


def generate_plan(response_message: Message, messages: list[Message], intent: Intent) -> list[Action]:
    """Generate a brief intro explaining what will be done"""

    if intent.intent.value == "CHAT":
        return []

    if len(messages) == 0:
        raise RuntimeError("No messages received")

    user_message = next((m for m in messages[::-1] if m.role == MessageRole.USER), None)
    user_message_text = user_message.to_llama_index().content if user_message is not None else ""

    introduction_prompt = f"""You are a helpful {preferences.deployment_name} platform assistant. A user has made the following request:

User request: "{user_message_text}"

Intent: {intent.intent.value}
Extracted entities: {intent.entities}

Generate a BRIEF introduction explaining what you will do to help them.

Guidelines:
- Be concise and specific
- Mention what you'll search for (if QUESTION or MIXED)
- Mention what you'll prepare (if LAUNCH, or MODIFY_PAYLOAD, or MIXED)
- Use friendly, professional tone
- Start with acknowledgment like "I will ...", "I'll help you with ...", "Okay, ...", "Let me...", if appropriate

Examples:

User: "How can I launch a XXXX pipeline?"
Response: I'll search our documentation for information about launching XXXX pipelines and guide you through the process.

User: "Launch ubuntu with 32GB RAM and 500GB disk"
Response: I'll prepare a launch configuration for an Ubuntu instance with 32GB RAM and 500GB disk storage.

User: "Can I use spot instances? If yes, set one up with 16GB RAM"
Response: I'll check our documentation about spot instance support and prepare a launch configuration with 16GB RAM if they're available.

Now generate the intro for this request:"""

    msg = create_chat_message_part(response_message)
    msg.type = MessagePartType.TEXT
    msg.text = ""

    llm_stream_chat(
        [*messages, introduction_prompt],
        part=msg
    )

    introduction = msg.text

    plan_prompt_1 = f"""You are a helpful {preferences.deployment_name} platform assistant. A user has made the following request:

User request: "{user_message_text}"

Intent: {intent.intent.value}
Extracted entities: {intent.entities}"""
    plan_prompt_2 = f"""Here's the brief introduction / execution plan description explaining what YOU will do to help them:

Plan description: {introduction}
"""

    plan_prompt_3 = f"""Based on the introduction, generate a MINIMAL plan (list of steps) that will help YOU to select appropriate tools.

Guidelines:
- Keep the plan as SHORT as possible - prefer 1-2 actions over many
- Only create separate steps for actions that use DIFFERENT tool types
- Do NOT create separate steps for: reasoning, deciding, or providing final answers (these are implicit)
- Each step should represent a concrete tool usage, not a thinking/reasoning step
- For "type" category, specify one of the following:
  - "DOCUMENTATION" for searching documentation
  - "LAUNCH" for generating or updating launch configuration
  - "CHAT" for general conversation (use sparingly - only when no tools needed)
- Provide a JSON array of action items
- General rule: MIXED intent may require more than 1 step, other intent types most often result in single step

Examples:

User: "How can I launch a XXXX pipeline?"
Plan description: "I'll search our documentation for information about launching XXXX pipelines and guide you through the process."
Plan: `[
  {{"action": "Search documentation for XXXX pipeline launch instructions", "type": "DOCUMENTATION"}}
]`

User: "How can I configure dark theme?"
Plan description: "I'll search our documentation for information about configuring dark theme settings."
Plan: `[
  {{"action": "Search documentation for dark theme configuration", "type": "DOCUMENTATION"}}
]`

User: "Launch ubuntu with 32GB RAM and 500GB disk"
Plan description: "I'll prepare a launch configuration for an Ubuntu instance with 32GB RAM and 500GB disk storage."
Plan: `[
  {{"action": "Generate launch configuration for Ubuntu with 32GB RAM and 500GB disk", "type": "LAUNCH"}}
]`

User: "Can I use spot instances? If yes, set one up with 16GB RAM"
Plan description: "I'll check our documentation about spot instance support and prepare a launch configuration with 16GB RAM if they're available."
Plan: `[
  {{"action": "Search documentation for spot instance availability and configuration", "type": "DOCUMENTATION"}},
  {{"action": "Generate launch configuration with 16GB RAM if spot instances are supported", "type": "LAUNCH"}}
]`

Now generate the MINIMAL plan (aim for 1-2 steps):"""

    plan_response = llm_chat(
        [*messages, ChatMessage(content=[
            TextBlock(text=plan_prompt_1),
            TextBlock(text=plan_prompt_2),
            TextBlock(text=plan_prompt_3),
        ], role=MessageRole.USER)],
        part=msg
    )

    plan_response_parsed = extract_json_response(plan_response)

    actions = [Action.deserialize(a) for a in plan_response_parsed]
    actions = [a for a in actions if a is not None]

    if len(actions) > 1:
        actions_str = '\n'.join([f"{a_idx+1}) {a.action}" for a_idx, a in enumerate(actions)])
        actions_str = "Here's what I will do step-by-step:\n" + actions_str
    else:
        actions_str = "Here's what I will do: " + actions[0].action

    create_chat_message_part(response_message,
                             text=actions_str,
                             part_type=MessagePartType.CONTEXT)

    return actions
