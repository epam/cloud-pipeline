import json
from cp_ai.pipeline.pipelines import (get_all_pipelines,
                                      get_pipeline_versions,
                                      get_pipeline_configurations)
from cp_ai.pipeline.types import (Pipeline,
                                  ConfigurationEntry)
from cp_ai.common.utilities import extract_json_response
from cp_ai.llm import llm_simple_query
from ..launch.tools import generate_launch_payload, LaunchException
from ..logger import agents_logger
from ..utilities import pick_best_elements


_pipeline_score_prompt = (f'Calculate score based on the following rules:\n'
                          f'- pipeline identifier (number) is mentioned by user => exact match, score = 1.\n'
                          f'- pipeline name is mentioned by user as-is => score = 0.95.\n'
                          f'- other: summ score parts if\n'
                          f'  - pipeline description matches: +0.5.\n'
                          f'  - pipeline name matches partially: +0.3.')


def pick_pipelines_by_query(query: str,
                            /,
                            skip_pipelines_without_description = False,
                            bearer: str | None = None) -> list[Pipeline]:
    """Search pipelines based on the user query. Returns list of matched pipelines, if any"""
    all_pipelines = get_all_pipelines(bearer=bearer)
    if len(all_pipelines) == 0:
        return []

    def match_id(o) -> int | None:
        if isinstance(o, int):
            return o
        if isinstance(o, str):
            try:
                return int(o[1:]) if o.startswith('#') else int(o)
            except:
                return None
        if isinstance(o, dict) and 'id' in o:
            return match_id(o.get('id'))
        return None

    by_ids_prompt = (f'Please help me analyze user query.\n'
                     f'If user asks to launch a pipeline or several pipelines '
                     f'**by specifying ONLY their identifiers (numbers)**, provide these pipeline identifiers '
                     f'as JSON array of numbers.\n'
                     f'Otherwise, return empty array "[]".\n\n'
                     f'Examples:\n'
                     f'"Please submit pipeline 12345" -> `[12345]`\n'
                     f'"Please submit pipeline 12345, 67890" -> `[12345, 67890]`\n'
                     f'"Please submit pipelines 12345, my-pipeline-1" -> `[]`\n'
                     f'"Please submit pipeline my-pipeline-1" -> `[]`\n\n'
                     f'Here is the user query:\n'
                     f'{query}\n\n'
                     f'---------\n'
                     f'Provide JSON array of pipeline identifiers (numbers) or empty array:')

    ids_raw = llm_simple_query(by_ids_prompt)
    agents_logger.debug(f'pick_pipelines_by_query -> exact identifiers llm response: {ids_raw}')
    ids = extract_json_response(ids_raw)
    if not isinstance(ids, list):
        ids = []
    ids = [match_id(i) for i in ids]
    ids = [i for i in ids if i is not None]

    if len(ids) > 0:
        # user specified exact pipeline identifiers
        agents_logger.info(f'pick_pipelines_by_query -> user specified exact pipeline identifiers {ids}')
        result = [p for p in all_pipelines if p.id in ids]
        agents_logger.info('picked pipelines:')
        for pipeline in result:
            agents_logger.info(f'- #{pipeline.id} {pipeline.name}')
        return result

    if skip_pipelines_without_description is None:
        skip_pipelines_without_description = False

    if skip_pipelines_without_description:
        all_pipelines = [p for p in all_pipelines if p.description is not None and len(p.description) > 0]

    agents_logger.debug(f'picking pipelines by query -> skip pipelines without description: {skip_pipelines_without_description}')
    agents_logger.debug(f'picking pipelines by query -> pipelines count: {len(all_pipelines)}')

    return pick_best_elements(
        query,
        all_pipelines,
        element_name='pipeline',
        scoring_rules_prompt=_pipeline_score_prompt,
        logger=agents_logger
    )


def _get_pipeline_version_from_query(
        query: str,
        pipeline: Pipeline
) -> str | None:
    versions = get_pipeline_versions(pipeline.id)
    if len(versions) == 1:
        return versions[0].name

    pipeline_version: str | None = None
    if len(versions) > 1:
        version_prompt = (f'Here is the user query for launching pipeline:\n'
                          f'{query}\n\n'
                          f'Please provide specified pipeline version, if any.\n'
                          f'Output format: JSON, example:\n'
                          f'```json'
                          '{"version": "..."}\n'
                          '```\n\n'
                          'If user asks to launch latest version, or draft version, '
                          'or does not specify pipeline version at all, '
                          'return empty object `{}`.')
        version_raw = extract_json_response(llm_simple_query(version_prompt))
        if isinstance(version_raw, dict) and 'version' in version_raw:
            version_str = version_raw.get('version')
        else:
            version_str = None
        if version_str:
            # user specified pipeline version - we need to find it

            found = pick_best_elements(
                query,
                versions,
                description_fn=lambda x: {'version': x.name},
                element_name='pipeline version',
                logger=agents_logger,
            )
            if len(found) > 0:
                # if we have a match - we'll use it
                version_str = found[0].name
        else:
            # user did not specify pipeline version - we need to use the latest one (draft), or any
            latest = next((v for v in versions if v.draft), None) or versions[0]
            version_str = latest.name
        pipeline_version = version_str

    if pipeline_version is None:
        # pipeline does not have versions - something wierd
        return None

    return pipeline_version


def _get_pipeline_configuration_from_query(
        query: str,
        pipeline: Pipeline,
        version: str
) -> ConfigurationEntry | None:
    configurations = get_pipeline_configurations(pipeline.id, version)
    if len(configurations) == 1:
        return configurations[0]

    pipeline_configuration: str | None = None
    if len(configurations) > 1:
        cfg_prompt = (f'Here is the user query for launching pipeline:\n'
                      f'{query}\n\n'
                      f'Please provide specified pipeline configuration, if any (i.e., if user '
                      f'asks to launch specific configuration, like '
                      f'"launch pipeline XXX, version YYY, **configuration ZZZ**").\n'
                      f'Output format: JSON, example:\n'
                      f'```json'
                      '{"configuration": "..."}\n'
                      '```\n\n'
                      'If user does not specify pipeline configuration explicitly, '
                      'return empty object `{}`.')
        cfg_name_raw = extract_json_response(llm_simple_query(cfg_prompt))
        if isinstance(cfg_name_raw, dict) and 'configuration' in cfg_name_raw:
            cfg_name = cfg_name_raw.get('configuration')
        else:
            cfg_name = None
        if cfg_name:
            # user specified pipeline configuration - we need to find it
            found = pick_best_elements(
                query,
                configurations,
                description_fn=lambda x: {'name': x.name},
                element_name='pipeline configuration',
                logger=agents_logger,
            )
            if len(found) > 0:
                # if we have a match - we'll use it
                cfg_name = found[0].name
        else:
            # user did not specify pipeline configuration - we need to use the default one, or any
            dflt = next((v for v in configurations if v.default), None) or configurations[0]
            cfg_name = dflt.name
        pipeline_configuration = cfg_name

    if pipeline_configuration is None:
        # pipeline does not have versions - something wierd
        return None

    return (next((c for c in configurations if c.name == pipeline_configuration), None) or
            next((c for c in configurations if c.default), None))


def generate_pipeline_launch_payload(
        query: str,
        pipeline: Pipeline,
        /,
        bearer: str | None = None
) -> dict:
    version = _get_pipeline_version_from_query(query, pipeline)
    if version is None:
        raise LaunchException(f'Pipeline {pipeline.md_title} does not have versions')
    configuration = _get_pipeline_configuration_from_query(query, pipeline, version)
    if configuration is None:
        raise LaunchException(f'Configuration not found for {pipeline.md_title}')
    return generate_launch_payload(
        configuration.configuration,
        user_query=query,
        pipeline=pipeline,
        pipeline_version=version,
        bearer=bearer
    )


def launch_pipeline_by_user_query(
        query: str,
        bearer: str | None = None,
        **kwargs
) -> str:
    """Searches pipelines based on the user query and generates launch payload.
    If several pipelines match a user query, returns a "Please specify a pipeline" message"""
    try:
        agents_logger.info(f'launch_pipeline_by_query -> user query: {query}')
        matched_pipelines = pick_pipelines_by_query(query, bearer=bearer)
        agents_logger.info(f'launch_pipeline_by_query -> {len(matched_pipelines)} pipelines found')
        if len(matched_pipelines) == 0:
            raise LaunchException('Pipelines that matches user query are not found')

        def pipeline_to_md(pipeline: Pipeline) -> str:
            url = pipeline.url
            title = f'**[{pipeline.name}]({url})**' if url is not None else f'**{pipeline.name}**'
            return f'{title}: {pipeline.description}' if pipeline.description is not None else title

        if len(matched_pipelines) > 1:
            s = '\n\n'.join([pipeline_to_md(p) for p in matched_pipelines[:10]])
            raise LaunchException(
                f'There are {len(matched_pipelines)} pipelines that match user query:\n\n'
                f'{s}'
                f'\n\n'
                f'Please, specify which pipeline to launch'
            )
        payload = generate_pipeline_launch_payload(
            query,
            matched_pipelines[0],
            bearer=bearer
        )
        payload_str = json.dumps(payload)
        return f'<<<LAUNCH:{payload_str}>>>'
    except LaunchException as le:
        agents_logger.error(le.launch_exception_message)
        return str(le)
    except BaseException as e:
        agents_logger.error('error generataing launch pipeline payload',
                            exc_info=e)
        raise
