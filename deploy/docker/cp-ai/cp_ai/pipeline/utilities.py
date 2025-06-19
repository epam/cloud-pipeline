import time
import requests
from functools import wraps
from typing import Any
from cp_ai.common.settings import cp_ai_settings

def timed_cache(
        *,
        seconds: float | None = None,
        minutes: float | None = None,
        hours: float | None = None,
        days: float | None = None
):
    if hours is None and days is not None:
        hours = days * 24
    if minutes is None and hours is not None:
        minutes = hours * 60
    if seconds is None and minutes is not None:
        seconds = 60 * minutes
    if seconds is None:
        seconds = 5 * 60 # 5 minutes
    def decorator(func):
        cache = {}
        @wraps(func)
        def wrapper(*args, **kwargs):
            now = time.time()
            if args in cache:
                result, timestamp = cache[args]
                if now - timestamp < seconds:
                    return result
            result = func(*args, **kwargs)
            cache[args] = (result, now)
            return result
        return wrapper
    return decorator

def get_cp_api_endpoint(uri: str) -> str:
    if cp_ai_settings.CP_API is None:
        raise RuntimeError('Cloud Pipeline API url is not specified')
    endpoint = cp_ai_settings.CP_API
    if endpoint.endswith('/'):
        endpoint = endpoint[:-1]
    while uri.startswith('/'):
        uri = uri[1:]
    return f'{endpoint}/{uri}'

def get_cp_request_headers(**kwargs) -> dict:
    d = {**kwargs}
    token = d.pop('bearer', None)
    if token is None:
        token = cp_ai_settings.CP_API_TOKEN
    headers = {**d}
    if token is not None:
        headers.update({"Authorization": f"Bearer {token}"})
    return headers


class CloudPipelineApiError(RuntimeError):
    def __init__(self, message: str):
        super().__init__(message)


def perform_cp_api_request(
        uri: str,
        /,
        bearer: str | None = None
) -> Any:
    cp_api_url = get_cp_api_endpoint(uri)
    response = requests.get(cp_api_url, headers=get_cp_request_headers(bearer=bearer))
    if response.status_code != 200:
        raise CloudPipelineApiError(f'"{uri}": status {response.status_code}')
    o = response.json()
    if isinstance(o, dict):
        status = o.get('status', 'OK')
        payload = o.get('payload', None)
        message = o.get('message', None)
        if status.lower() == 'error':
            raise CloudPipelineApiError(message or f'"{uri}" failed with unknown error')
        return payload
    return o
