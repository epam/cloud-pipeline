from logging import Logger
from .types import (Pipeline,
                    PipelineVersion,
                    ConfigurationEntry)
from .utilities import perform_cp_api_request
from .logger import cp_api_logger


def get_all_pipelines(
        *,
        bearer: str | None = None,
        logger: Logger | None = None
) -> list[Pipeline]:
    if logger is None:
        logger = cp_api_logger
    try:
        data = perform_cp_api_request('pipeline/loadAll', bearer=bearer)
        if data is None or not isinstance(data, list):
            data = []
        if isinstance(data, list):
            pipelines = [Pipeline.from_payload(d) for d in data]
            return [p for p in pipelines if p is not None]
        return []
    except BaseException as e:
        logger.error('error fetching pipelines',
                     exc_info=e)
        return []

def get_pipeline_versions(
        pipeline_id: int | str,
        logger: Logger | None = None,
        bearer: str | None = None,
) -> list[PipelineVersion]:
    if logger is None:
        logger = cp_api_logger
    try:
        data = perform_cp_api_request(f'pipeline/{pipeline_id}/versions',
                                      bearer=bearer)
        if data is None or not isinstance(data, list):
            data = []
        versions = [PipelineVersion.from_payload(d) for d in data]
        return [p for p in versions if p is not None]
    except BaseException as e:
        logger.error(f'error fetching pipeline #{pipeline_id} versions',
                     exc_info=e)
        return []


def get_pipeline_configurations(
        pipeline_id: int | str,
        version: str,
        logger: Logger | None = None,
        bearer: str | None = None,
) -> list[ConfigurationEntry]:
    if logger is None:
        logger = cp_api_logger
    try:
        data = perform_cp_api_request(f'pipeline/{pipeline_id}/configurations?version={version}',
                                      bearer=bearer)
        if data is None or not isinstance(data, list):
            data = []
        configurations = [ConfigurationEntry.from_payload(d) for d in data]
        return [p for p in configurations if p is not None]
    except BaseException as e:
        logger.error(f'error fetching pipeline #{pipeline_id} {version} configurations',
                     exc_info=e)
        return []
