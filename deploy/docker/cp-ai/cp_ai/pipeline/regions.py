from logging import Logger
from .types import CloudRegion
from .logger import cp_api_logger
from .utilities import (perform_cp_api_request)


def get_cloud_regions(
        logger: Logger | None = None
) -> list[CloudRegion]:
    if logger is None:
        logger = cp_api_logger
    try:
        data = perform_cp_api_request('cloud/region')
        if isinstance(data, dict):
            regions = [CloudRegion.from_payload(d) for d in data.get('registries', [])]
            return [r for r in regions if r is not None]
        return []
    except BaseException as e:
        logger.error('error fetching cloud regions',
                     exc_info=e)
        return []
