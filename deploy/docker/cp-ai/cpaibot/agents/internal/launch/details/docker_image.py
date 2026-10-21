from cpaibot.common.model.chat import Message, MessagePart, MessagePartType
from cpaibot.agents.internal.planning import Action
from cpaibot.common.logger import CpLogger as Logger
from cpaibot.managers.chat import create_chat_message_part, update_message_part

from cpaibot.agents.pipeline.docker_image.tools import pick_docker_image_by_query, get_docker_image_version_from_query
from cpaibot.agents.internal.launch.base import LaunchPayloadIntent
from cpaibot.agents.internal.launch.utils import extract_info, stream_result
from cpaibot.agents.internal.launch.prompts import docker_image_prompt
from cpaibot.agents.internal.misc import default_logger
from cpaibot.pipeline.types import DockerImage


def get_docker_image(
        messages: list[Message],
        response_message: Message,
        action: Action | None = None,
        intent: LaunchPayloadIntent | None = None,
        is_pipeline=False,
        logger: Logger | None = None,
) -> tuple[DockerImage | None, str | None, bool]:
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

    logger.info(f"message #{message_id}: extracting docker image info from context...")
    docker_image = extract_info_wrapper(docker_image_prompt)
    logger.info(f"message #{message_id}: image info from context: {docker_image}")
    if docker_image.lower() == "none":
        docker_image = None

    if not docker_image and intent and intent.previous_launch_payload:
        docker_image = intent.previous_launch_payload.docker_image
        logger.info(f"message #{message_id}: using docker image from previous launch payload: {docker_image}")

    if not docker_image:
        docker_image = 'library/rockylinux:latest'
        logger.info(f"message #{message_id}: using default docker image: {docker_image}")


    logger.info(f"message #{message_id}: fetching available docker images and matching the requested docker image...")
    docker_images_list = pick_docker_image_by_query(docker_image, is_image_name=True, logger=logger)
    logger.info(f"message #{message_id}: {len(docker_images_list)} matching docker images found")

    if len(docker_images_list) != 1:
        if len(docker_images_list) == 0:
            stream_result_wrapper(
                f'Begin your response with: "I couldn\'t find any tools or Docker images matching \'{docker_image}\'"\n\n'
                f'Then continue by:\n'
                f'- Asking the user to verify the spelling or provide more context\n'
                f'- Offering to help search using different keywords or a description\n\n'
                f'No introductory phrases like "Okay" or "I can help".'
            )
            return None, None, False
        logger.info(f"message #{message_id}: user interaction required (select docker image)")
        docker_images_list_descr = '\n'.join([f"- {di.to_markdown()}" for di in docker_images_list])
        decision_part = create_chat_message_part(response_message, part_type=MessagePartType.DECISION)
        if not decision_part.metadata:
            decision_part.metadata = {}
        decision_part.metadata.update({
            "select_type": "tool",
            "options": [di.to_json() for di in docker_images_list],
        })
        stream_result_wrapper(
            f'Begin with: "I found {len(docker_images_list)} docker images matching \'{docker_image}\'"\n\n'
            f'Images:\n{docker_images_list_descr}\n\n'
            f'Then:\n'
            f'- Present the list clearly\n'
            f'- Ask which one the user wants to use\n'
            f'- If relevant, note key differences between them\n\n'
            f'No "Okay" or "Sure" at the start.',
            message_part=decision_part
        )
        return None, None, False
    logger.info(f"message #{message_id}: extracting docker image {docker_image} version from context...")
    docker_image_version = get_docker_image_version_from_query(
        docker_image,
        docker_images_list[0],
        docker_image_full=docker_image
    )
    logger.info(f"message #{message_id}: docker image {docker_image} version from context: {docker_image_version}")
    if docker_image_version is None:
        logger.info(f"message #{message_id}: using latest version for docker image {docker_image}")
        docker_image_version = "latest"
    part.text = f"SELECTED DOCKER IMAGE: {docker_images_list[0].full_image}:{docker_image_version}"
    update_message_part(part)
    return docker_images_list[0], docker_image_version, True
