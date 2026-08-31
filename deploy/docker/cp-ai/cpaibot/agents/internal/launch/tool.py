from cpaibot.agents.pipeline.launch.tools import LaunchException, generate_launch_payload
from cpaibot.common.logger import CpLogger as Logger
from cpaibot.common.model.chat import Message, MessagePart
from cpaibot.agents.internal.planning import Action
from cpaibot.agents.internal.launch.base import LaunchPayloadIntent
from cpaibot.agents.pipeline.types import LaunchPayload
from cpaibot.agents.internal.misc import default_logger

from cpaibot.agents.internal.launch.details import (get_docker_image,
                                                    get_instance_type,
                                                    get_cluster_config,
                                                    get_disk_size,
                                                    get_parameters)
from cpaibot.managers.chat import update_message_part
from cpaibot.pipeline.tools import get_docker_image_settings


def get_tool_launch_result(
        messages: list[Message],
        response_message: Message,
        intent: LaunchPayloadIntent | None = None,
        action: Action | None = None,
        part: MessagePart | None = None,
        logger: Logger | None = None,
) -> tuple[LaunchPayload | None, bool]:
    if logger is None:
        logger = default_logger
    message_id = response_message.identifier
    message_id = message_id[:8]
    def log_status(status: str | None):
        if part:
            part.status = status
            update_message_part(part)
        if status:
            logger.info(f"messsage #{message_id}: {status}")

    try:
        log_status("Determining docker image...")
        docker_image, version, continue_exec = get_docker_image(messages,
                                                                response_message,
                                                                action=action,
                                                                intent=intent,
                                                                is_pipeline=False,
                                                                logger=logger)
        if not continue_exec or not docker_image:
            return None, False
        log_status(f"Docker image: {docker_image.full_image} (version: {version})")
        log_status("Determining instance type...")
        instance_type, continue_exec = get_instance_type(messages,
                                                         response_message,
                                                         action=action,
                                                         intent=intent,
                                                         docker_image_id=docker_image.id)
        if not continue_exec:
            return None, False
        if not instance_type and intent and intent.previous_launch_payload:
            instance_type = intent.previous_launch_payload.instance_type
        if instance_type:
            log_status(f"instance type: {instance_type}")
        log_status("Determining disk size...")
        disk_size, continue_exec = get_disk_size(messages,
                                                 response_message,
                                                 action=action,
                                                 intent=intent)
        if not continue_exec:
            return None, False
        if not disk_size and intent and intent.previous_launch_payload:
            disk_size = intent.previous_launch_payload.disk

        cluster_config, continue_exec = get_cluster_config(messages,
                                                           response_message,
                                                           action=action,
                                                           intent=intent)
        if not continue_exec:
            return None, False

        log_status("Fetching docker image settings...")
        settings = get_docker_image_settings(docker_image.id)
        version_settings = next((s for s in settings if s.version.lower() == version.lower()), None)
        if version_settings is None:
            version_settings = next((s for s in settings if s.version.lower() == 'latest'), None)
        if version_settings is None:
            raise LaunchException(f'Docker image {docker_image.full_image} settings not found for version "{version}". Please contact administrator or specify another version.')
        configuration = next((s for s in version_settings.settings if s.default), None)
        if configuration is None and len(version_settings.settings) > 0:
            configuration = version_settings.settings[0]
        if configuration is None:
            raise LaunchException(f'Docker image {docker_image.full_image} configuration not found for version "{version}". Please contact administrator or specify another version.')
        cfg = configuration.configuration
        if cfg.docker_image is None:
            cfg.docker_image = f'{docker_image.full_image}:{version}'
        if cfg.cmd_template is None:
            cfg.cmd_template = 'sleep infinity'
        log_status("Determining parameters...")
        parameters, continue_exec = get_parameters(messages,
                                                   response_message,
                                                   cfg,
                                                   intent=intent,
                                                   action=action,)
        if not continue_exec:
            return None, False
        if not parameters and intent and intent.previous_launch_payload:
            parameters = intent.previous_launch_payload.parameters
        log_status("Generating payload...")
        payload = generate_launch_payload(
            cfg,
            instance_type=instance_type,
            instance_disk=disk_size,
            parameters=parameters,
            cluster_config=cluster_config,
        )
        return payload, True
    finally:
        log_status(None)
