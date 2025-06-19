from logging import Logger
from urllib.parse import quote
from .types import InstanceType
from .utilities import perform_cp_api_request
from .logger import cp_api_logger


def get_allowed_instance_types(
        *,
        tool_id: int | None = None,
        spot: bool | None = None,
        region_id: int | None = None,
        bearer: str | None = None,
        is_docker: bool | None = None,
        logger: Logger | None = None
) -> list[InstanceType]:
    if logger is None:
        logger = cp_api_logger
    uri = 'cluster/instance/allowed'
    query = {}
    if tool_id is not None:
        query['toolId'] = tool_id
    if spot is not None:
        query['spot'] = 'true' if spot else 'false'
    if region_id is not None:
        query['regionId'] = region_id
    if len(query) > 0:
        def map_key_value(key, value):
            return quote(key, safe='') + '=' + quote(str(value), safe='')
        uri += '?' + '&'.join([map_key_value(key, value) for key, value in query.items()])
    try:
        data = perform_cp_api_request(uri, bearer=bearer)
        if data is None or not isinstance(data, dict):
            data = {}
        key = 'cluster.allowed.instance.types.docker' if is_docker else 'cluster.allowed.instance.types'
        data = data.get(key, [])
        if isinstance(data, list):
            return [InstanceType(**d) for d in data]
        return []
    except BaseException as e:
        logger.error('error fetching allowed instance types', exc_info=e)
        return []
