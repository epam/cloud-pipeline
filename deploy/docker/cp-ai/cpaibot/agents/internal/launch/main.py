import json

from llama_index.core.base.llms.types import ChatMessage, MessageRole

from cpaibot.agents.pipeline.launch.tools import LaunchException
from cpaibot.common.logger import CpLogger as Logger
from cpaibot.common.model.chat import Message, MessagePartType, MessagePart
from cpaibot.common.utils import extract_json_response
from cpaibot.llm.base import llm_chat
from cpaibot.agents.internal.planning import Action
from cpaibot.agents.internal.launch.base import LaunchMode, LaunchPayloadIntent

from cpaibot.agents.internal.launch.tool import get_tool_launch_result
from cpaibot.agents.internal.launch.pipeline import get_pipeline_launch_result
from cpaibot.agents.internal.launch.utils import stream_result
from cpaibot.managers.chat import (create_chat_message_part,
                                   update_message_part)

from cpaibot.agents.internal.misc import preferences, default_logger
from cpaibot.agents.internal.launch.utils import get_launch_payloads


def extract_launch_intent(
        response_message: Message,
        messages: list[Message],
        action: Action | None = None,
        part: MessagePart | None = None,
) -> LaunchPayloadIntent:
    if len(messages) == 0:
        raise RuntimeError("No messages received")

    user_message = next((m for m in messages[::-1] if m.role == MessageRole.USER), None)
    user_message_text = user_message.to_llama_index().content if user_message is not None else ""

    if action:
        action_info = f"""\nAction: {action.action}\n"""
    else:
        action_info = ""

    prompt = f"""Analyze this user request about launching something on an {preferences.deployment_name} platform:

User request: "{user_message_text}"{action_info}

Determine:
1. MODE: Is this for a "tool" (system image like Ubuntu, CentOS, Rocky Linux, etc. or a general compute instance) or a "pipeline" (workflow like Nextflow, Snakemake, etc.)?
2. IS_UPDATE: Is this modifying/updating an existing configuration (true) or creating a new one (false)?
3. DETAILS: Extract ALL technical specifications as concise text

Guidelines for MODE:
- "tool" = Operating system images, base systems, containers (ubuntu, centos, rocky, debian, etc.)
- "pipeline" = Workflows, analysis pipelines, computational workflows (nextflow, snakemake, cromwell, etc.)
- If request mentions both, choose the primary focus
- Keywords: "launch ubuntu" → tool, "run nextflow pipeline" → pipeline, "start workflow" → pipeline

Guidelines for IS_UPDATE:
- true = "modify", "update", "change", "adjust", "increase", "add to", "set the RAM to" (implying existing config)
- false = "launch", "start", "create", "new", "set up", "prepare" (implying new config)

Guidelines for DETAILS:
- Include: image/pipeline names, versions, resource specs (RAM, CPU, disk), paths, parameters, identifiers
- Format: Concise, technical, comma-separated or brief phrases
- Omit: pleasantries, questions, explanations
- Example: "ubuntu, 32GB RAM, 500GB disk, 8 CPUs"
- Example: "nextflow pipeline, workflow.nf, 16GB RAM, spot instance"

Examples:

Input: "Launch ubuntu with 32GB RAM and 500GB disk"
Output: {{"mode": "tool", "is_update": false, "details": "ubuntu, 32GB RAM, 500GB disk"}}

Input: "Start a nextflow pipeline with my workflow.nf file and fastq /path/to/fastq/r1.fastq"
Output: {{"mode": "pipeline", "is_update": false, "details": "nextflow pipeline, workflow.nf, fastq: /path/to/fastq/r1.fastq"}}

Input: "Increase the RAM to 64GB"
Output: {{"mode": "tool", "is_update": true, "details": "64GB RAM"}}

Input: "Update my pipeline to use 8 CPUs instead of 4"
Output: {{"mode": "pipeline", "is_update": true, "details": "8 CPUs"}}

Input: "Launch centos 7 with spot instance and 16GB memory"
Output: {{"mode": "tool", "is_update": false, "details": "centos 7, spot instance, 16GB RAM"}}

Input: "Can I run a snakemake workflow with 100GB disk?"
Output: {{"mode": "pipeline", "is_update": false, "details": "snakemake, 100GB disk"}}

Input: "Change the disk size to 1TB for my ubuntu instance"
Output: {{"mode": "tool", "is_update": true, "details": "ubuntu, 1TB disk"}}

Respond with ONLY a JSON object, no other text:"""

    response = llm_chat(
        [
            *messages,
            response_message,
            ChatMessage(content=prompt, role=MessageRole.USER)
        ],
        part=part,
    )

    parsed = extract_json_response(response)

    intent = LaunchPayloadIntent(**parsed)
    if intent.is_update:
        previous_payloads = get_launch_payloads(messages)
        prev_payload = previous_payloads[-1] if len(previous_payloads) > 0 else None
        if prev_payload:
            intent.previous_launch_payload = prev_payload
    return intent


def get_launch_result(
        messages: list[Message],
        response_message: Message,
        action: Action | None = None,
        logger: Logger | None = None
):
    if not logger:
        logger = default_logger
    part = create_chat_message_part(response_message,
                                    part_type=MessagePartType.LAUNCH)
    part.pending = True
    update_message_part(part)
    intent: LaunchPayloadIntent | None = None
    message_id = response_message.identifier
    message_id = message_id[:8]
    try:
        logger.info(f"message #{message_id}: classifying launch request...")
        intent = extract_launch_intent(response_message, messages, action=action, part=part)
    except Exception as e:
        if isinstance(e, LaunchException):
            part.warnings.append(e.launch_exception_message)
        part.warnings.append(e.__str__())
        logger.warning(f"message #{message_id}: error classifying launch request",
                       exception=e)
    try:
        if intent and intent.mode == LaunchMode.PIPELINE:
            logger.info(f"message #{message_id}: preparing launch payload for pipeline...")
            payload, continue_exec = get_pipeline_launch_result(
                messages,
                response_message,
                intent=intent,
                action=action,
                part=part,
            )
        else:
            logger.info(f"message #{message_id}: preparing launch payload for tool...")
            payload, continue_exec = get_tool_launch_result(
                messages,
                response_message,
                intent=intent,
                action=action,
                part=part,
                logger=logger,
            )
        if not continue_exec:
            return False
        if not payload:
            raise LaunchException("Unable to generate launch payload. Please provide more details")
        payload_dump = payload.to_json()
        part.set_launch_payload(payload_dump)
        payload_dump_str = json.dumps(payload_dump)
        stream_result(
            response_message,
            messages,
            f'Here\'s the generated payload for user query:\n\n'
            f'```json\n{payload_dump_str}\n```\n\n'
            f'Please provide a short summary for the generated response, '
            f'start your response with: "I\'ve ..."\n\n'
            f'Do not start with "Okay", "Sure", "I understand", or similar phrases. '
            f'Do not use "JSON payload" or similar phrases',
            part=part
        )
    except Exception as e:
        if isinstance(e, LaunchException):
            part.errors.append(e.launch_exception_message)
        else:
            part.errors.append(e.__str__())
        stream_result(
            response_message,
            messages,
            f'Start your response with: "I couldn\'t prepare the launch payload."\n\n'
            f'Then:\n'
            f'- Explain the error in user-friendly terms (technical error: {e.__str__()})\n'
            f'- Suggest possible solutions or next steps if applicable\n'
            f'- Keep it concise and actionable\n\n'
            f'Do not start with "Okay", "Sure", "I understand", or similar phrases.\n'
            f'Do not apologize excessively.'
        )
        if not isinstance(e, LaunchException):
            raise e
    finally:
        part.pending = False
        update_message_part(part)
    return True
