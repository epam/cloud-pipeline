from logging import Logger
from cpaibot.pipeline.logger import cp_api_logger
from cpaibot.pipeline.types import (DockerImage,
                                    DockerImageSettings,
                                    DockerRegistry)
from cpaibot.pipeline.utilities import perform_cp_api_request

def get_docker_registries(
        *,
        logger: Logger | None = None,
        bearer: str | None = None
) -> list[DockerRegistry]:
    if logger is None:
        logger = cp_api_logger
    try:
        data = perform_cp_api_request('dockerRegistry/loadTree',
                                      bearer=bearer)
        if isinstance(data, dict):
            registries = [DockerRegistry.from_payload(d) for d in data.get('registries', [])]
            return [p for p in registries if p is not None]
        return []
    except BaseException as e:
        logger.error('error fetching docker registries',
                     exc_info=e)
        return []


def get_docker_images(
        *,
        bearer: str | None = None
) -> list[DockerImage]:
    registries = get_docker_registries(bearer=bearer)
    result: list[DockerImage] = []
    for registry in registries:
        for group in registry.groups:
            result.extend([DockerImage(**t.model_dump(),
                                       registry=registry,
                                       group=group) for t in group.tools])
    return result


def find_docker_image(image: str, /, docker_images: list[DockerImage] | None = None) -> DockerImage | None:
    """Searches docker image by <registry/group/image[:version]> string"""
    if docker_images is None or not isinstance(docker_images, list) or len(docker_images) == 0:
        docker_images = get_docker_images()
    parts = image.split('/')
    if len(parts) != 3:
        return None
    registry_path, group_name, image_and_version = parts
    i_parts = image_and_version.split(':')
    if len(i_parts) == 2:
        docker_image, version = i_parts
    else:
        docker_image = image
        version = 'latest'
    registry_path = registry_path.lower()
    group_name = group_name.lower()
    image_name = docker_image.lower()
    full_image = f'{registry_path}/{group_name}/{image_name}'
    return next((di for di in docker_images if di.full_image.lower() == full_image), None)


def get_docker_image_versions(
        tool_id: int,
        logger: Logger | None = None,
        bearer: str | None = None,
) -> list[str]:
    if logger is None:
        logger = cp_api_logger
    try:
        data = perform_cp_api_request(f'tool/{tool_id}/tags',
                                      bearer=bearer)
        return data if isinstance(data, list) else []
    except BaseException as e:
        logger.error(f'error fetching docker image {tool_id} versions',
                     exc_info=e)
        return []


def get_docker_image_settings(
        tool_id: int,
        logger: Logger | None = None,
        bearer: str | None = None,
) -> list[DockerImageSettings]:
    if logger is None:
        logger = cp_api_logger
    try:
        data = perform_cp_api_request(f'tool/{tool_id}/settings',
                                      bearer=bearer)
        if isinstance(data, list):
            di_settings = [DockerImageSettings.from_payload(d) for d in data]
            return [p for p in di_settings if p is not None]
        return []
    except BaseException as e:
        logger.error(f'error fetching docker image {tool_id} settings',
                     exc_info=e)
        return []
