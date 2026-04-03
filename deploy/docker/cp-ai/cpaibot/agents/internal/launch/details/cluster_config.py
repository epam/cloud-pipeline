from cpaibot.common.model.chat import Message, MessagePart, MessagePartType
from cpaibot.common.logger import CpLogger as Logger
from cpaibot.common.utils import extract_json_response
from cpaibot.agents.internal.planning import Action
from cpaibot.agents.internal.misc import default_logger

from cpaibot.agents.internal.launch.base import LaunchPayloadIntent
from cpaibot.agents.internal.launch.utils import extract_info, stream_result
from cpaibot.agents.internal.launch.prompts import cluster_info_prompt
from cpaibot.managers.chat import (create_chat_message_part,
                                   update_message_part)

from cpaibot.agents.pipeline.types import ClusterInfo


def get_cluster_config(
        messages: list[Message],
        response_message: Message,
        action: Action | None = None,
        intent: LaunchPayloadIntent | None = None,
        logger: Logger | None = None,
) -> tuple[ClusterInfo | None, bool]:
    if not logger:
        logger = default_logger
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

    def stream_result_wrapper(prompt: str, message_part: MessagePart | None = None):
        return stream_result(response_message,
                             messages,
                             prompt,
                             intent=intent,
                             action=action,
                             part=message_part)

    message_id = response_message.identifier
    message_id = message_id[:8]

    logger.info(f"message #{message_id}: extracting cluster info from context...")
    cluster_info = extract_info_wrapper(cluster_info_prompt)
    logger.info(f"message #{message_id}: extracting cluster from context: {cluster_info}")
    if cluster_info.lower() == "none":
        cluster_info = None

    try:
        cluster_info = extract_json_response(cluster_info)
    except:
        return None, True

    if cluster_info is None:
        return None, True

    try:
        cluster_info = ClusterInfo(**cluster_info)
    except:
        return None, True

    if cluster_info.mode in {"cluster", "auto-scaled"}:
        if not cluster_info.sge and not cluster_info.slurm and not cluster_info.spark and not cluster_info.kubernetes:
            cluster_info.sge = True
    else:
        cluster_info.sge = False
        cluster_info.slurm = False
        cluster_info.spark = False
        cluster_info.kubernetes = False

    if cluster_info.mode == "cluster":
        if not cluster_info.node_count:
            stream_result_wrapper(
                f'Begin your response with: "To create your cluster, I need to know the number of worker nodes."\n\n'
                f'Then continue by:\n'
                f'- Asking how many worker nodes the user would like\n'
                f'- Optionally suggesting they consider their workload requirements\n'
                f'- Keeping the response brief and to the point\n\n'
                f'No introductory phrases like "Okay" or "I can help".'
            )
            return None, False
    elif cluster_info.mode == "auto-scaled":
        if not cluster_info.node_count:
            stream_result_wrapper(
                f'Begin your response with: "To create your cluster, I need to know the number of worker nodes."\n\n'
                f'Then continue by:\n'
                f'- Asking how many worker nodes the user would like\n'
                f'- Optionally suggesting they consider their workload requirements\n'
                f'- Keeping the response brief and to the point\n\n'
                f'No introductory phrases like "Okay" or "I can help".'
            )
            return None, False

    part.text = f"CLUSTER CONFIGURATION: {cluster_info.model_dump()}"
    update_message_part(part)

    return cluster_info, True
