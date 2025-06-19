import json
from pydantic import BaseModel
from cp_ai.pipeline.tools import (get_docker_images,
                                  get_docker_image_versions,
                                  get_docker_image_settings,
                                  find_docker_image)
from cp_ai.pipeline.types import DockerImage
from cp_ai.common.utilities import extract_json_response
from cp_ai.llm import llm_simple_query
from ..launch.tools import LaunchException, generate_launch_payload
from ..logger import agents_logger
from ..utilities import (batched_execution,
                         pick_best_elements)


class _ScoredDockerImage(BaseModel):
    docker_image: DockerImage
    score: float


_docker_image_score_prompt = (f'Calculate score based on the following rules:\n'
                              f'- docker image identifier (number) is mentioned by user => exact match, score = 2.\n'
                              f'- docker image name, group and registry are mentioned by user and fully match '
                              f'(in "registry/group/image" format) => score = 1.9.\n'
                              f'- docker image name and group are mentioned by user and fully match '
                              f'(in "group/image" format) => score = 1.8.\n'
                              f'- otherwise: summ score parts if\n'
                              f'  - docker image name matches as is: +1.\n'
                              f'  - docker image name is similar (partial matches): +0.5.\n'
                              f'  - tool is_common == true: +0.3.\n'
                              f'  - tool is_personal == true: +0.5.\n')


def pick_docker_image_by_query(
        query: str,
        /,
        bearer: str | None = None
) -> list[DockerImage]:
    """Search docker images based on the user query. Returns list of matched docker images, if any"""
    all_images = get_docker_images(bearer=bearer)
    if len(all_images) == 0:
        return []

    exact = find_docker_image(query, docker_images=all_images)
    if exact is not None:
        agents_logger.info(f'docker image {query} found (exact match)')
        return [exact]

    agents_logger.debug(f'picking docker images by query -> docker images count: {len(all_images)}')

    specific_tool_prompt = (f'Based on the user query, answer:\n'
                            f'- "YES" if user asks to launch or submit specific tool or docker image;\n'
                            f'- "NO" if user asks to launch or submit any compute instance and does not specifies image at all.\n\n'
                            f'User query:\n'
                            f'-------------\n'
                            f'{query}\n'
                            f'-------------\n\n'
                            f'Your answer as a plain string, only "YES" / "NO":')

    specific_tool_resp = llm_simple_query(specific_tool_prompt)
    agents_logger.debug(f'pick_docker_image_by_query -> specific tool llm response: {specific_tool_resp}')
    specific_tool_resp = extract_json_response(specific_tool_resp) or specific_tool_resp
    if isinstance(specific_tool_resp, dict):
        specific_tool_resp = specific_tool_resp.get('result', 'yes')
    is_specific_tool = specific_tool_resp.lower().strip() in {'yes', 'true'}

    if not is_specific_tool:
        query = 'library/rockylinux:latest'
    return pick_best_elements(
        query,
        all_images,
        name_fn=lambda x: x.full_image,
        description_fn=lambda x: {'image': x.full_image},
        score_description_fn=lambda x: {
            'image': x.full_image,
            'is_common': x.group.name.lower() == 'library',
            'is_personal': x.group.privateGroup
        },
        element_name='docker image',
        scoring_rules_prompt=_docker_image_score_prompt,
        logger=agents_logger
    )


def _get_docker_image_version_from_query(
        query: str,
        docker_image: DockerImage
) -> str | None:
    versions = get_docker_image_versions(docker_image.id)
    if len(versions) == 1:
        return versions[0]

    docker_image_version: str | None = None
    if len(versions) > 1:
        version_prompt = (f'Here is the user query for launching a tool:\n'
                          f'{query}\n\n'
                          f'Please provide specified tool version, if any.\n'
                          f'Output format: JSON, example:\n'
                          f'```json'
                          '{"version": "..."}\n'
                          '```\n\n'
                          'If user asks to launch latest version, '
                          'or does not specify pipeline version at all, '
                          'return empty object `{}`.')
        version_raw = extract_json_response(llm_simple_query(version_prompt))
        if isinstance(version_raw, dict) and 'version' in version_raw:
            version_str = version_raw.get('version')
        else:
            version_str = None
        if version_str:
            # user specified pipeline version - we need to find it
            def find_version_batch(items: list[str]) -> list[str]:
                versions_str = json.dumps(items)
                prompt = (f'Based on the user query and the available tool versions, '
                          f'find best version that matches user query.\n'
                          f'Available versions:\n\n'
                          f'```json\n'
                          f'{versions_str}'
                          f'```\n\n'
                          f'User query:\n'
                          f'{query}\n\n'
                          'Output format: JSON object {"version": "..."} or {}.\n'
                          'Provide a JSON object `{"version": "..."}` of the matched tool version or '
                          'empty object `{}` if none of the available versions matches user query:')
                r = llm_simple_query(prompt)
                agents_logger.debug(f'find_version_batch -> llm response: {r}')
                r = extract_json_response(r)
                if isinstance(r, dict) and 'version' in r:
                    return [v for v in items if v.lower() == str(r.get('version')).lower()]
                return []

            found = batched_execution(versions,
                                      find_version_batch,
                                      batch_size=20,
                                      title='find tool version')
            if len(found) > 0:
                # if we have a match - we'll use it
                version_str = found[0]
        else:
            # user did not specify pipeline version - we need to use the latest one (draft), or any
            latest = next((v for v in versions if v.lower() == 'latest'), None) or versions[0]
            version_str = latest
        docker_image_version = version_str

    if docker_image_version is None:
        # tool does not have versions - something wierd
        return None

    return docker_image_version


def launch_tool_by_user_query(
        query: str,
        bearer: str | None = None,
        **kwargs
) -> str:
    """Searches docker images based on the user query and generates launch payload.
    If several docker images match a user query, returns a "Please specify a docker image" message"""
    try:
        docker_images = pick_docker_image_by_query(query, bearer=bearer)
        if len(docker_images) == 0:
            raise LaunchException('Docker image not found; specify docker image')
        if len(docker_images) > 1:

            def docker_image_to_md(tool: DockerImage) -> str:
                url = tool.url
                title = f'**[{tool.image}]({url})**' if url is not None else f'**{tool.image}**'
                return f'{title}: {tool.shortDescription}' if tool.shortDescription is not None else title

            s = '\n\n'.join([docker_image_to_md(p) for p in docker_images])
            raise LaunchException(
                f'There are {len(docker_images)} docker images that match user query:\n\n'
                f'{s}'
                f'\n\n'
                f'Please, specify which docker image to launch'
            )
        docker_image = docker_images[0]
        agents_logger.info(f'launch_tool_by_user_query -> docker image found: {docker_image.image} ({docker_image.id})')
        version = _get_docker_image_version_from_query(query, docker_image)
        if version is None:
            raise LaunchException(f'Docker image {docker_image.full_image} version not found, please specify version')
        agents_logger.info(f'launch_tool_by_user_query -> docker image version found: {version}')
        settings = get_docker_image_settings(docker_image.id)
        version_settings = next((s for s in settings if s.version.lower() == version.lower()), None)
        if version_settings is None:
            version_settings = next((s for s in settings if s.version.lower() == 'latest'), None)
        if version_settings is None:
            raise LaunchException(f'Docker image {docker_image.full_image} settings not found for version "{version}"')
        configuration = next((s for s in version_settings.settings if s.default), None)
        if configuration is None and len(version_settings.settings) > 0:
            configuration = version_settings.settings[0]
        if configuration is None:
            raise LaunchException(f'Docker image {docker_image.full_image} configuration not found for version "{version}"')
        cfg = configuration.configuration
        if cfg.docker_image is None:
            cfg.docker_image = f'{docker_image.full_image}:{version}'
        if cfg.cmd_template is None:
            cfg.cmd_template = 'sleep infinity'
        payload = generate_launch_payload(
            cfg,
            user_query=query,
            bearer=bearer
        )
        payload_str = json.dumps(payload)
        return f'<<<LAUNCH:{payload_str}>>>'
    except LaunchException as le:
        agents_logger.error(le.launch_exception_message)
        return str(le)
    except BaseException as e:
        agents_logger.error('error generataing tool\'s launch payload',
                            exc_info=e)
        raise
