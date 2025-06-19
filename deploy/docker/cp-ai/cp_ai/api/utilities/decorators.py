import os
import functools
import inspect
from logging import Logger
from fastapi.responses import FileResponse, StreamingResponse
from starlette.responses import ContentStream
from typing import Callable, TypeVar, Any
from .logger import api_logger
from cp_ai.common.types import SerializableModel


F = TypeVar("F", bound=Callable[..., Any])


def app_response(func: F | None = None,
                 /,
                 logger: Logger | None = None):
    if logger is None:
        logger = api_logger

    if func is None:
        def decorator(f: F | None = None):
            return app_response(f, logger=logger)
        return decorator

    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        try:
            return _success(func(*args, **kwargs))
        except Exception as ex:
            logger.error(f'error executing {func.__name__}',
                         exc_info=ex)
            return _error(str(ex))

    @functools.wraps(func)
    async def async_wrapper(*args, **kwargs):
        try:
            res = await func(*args, **kwargs)
            return _success(res)
        except Exception as ex:
            logger.error(f'error executing {func.__name__}',
                         exc_info=ex)
            return _error(str(ex))

    return async_wrapper if inspect.iscoroutinefunction(func) else wrapper


class FileResponseConfig:
    file_path: str
    file_name: str | None = None
    disposition: str | None = None
    media_type: str | None = None

    def __init__(self,
                 file_path: str,
                 file_name: str | None = None,
                 media_type: str | None = None,
                 disposition: str | None = None):
        self.file_path = file_path
        self.file_name = file_name
        self.media_type = media_type
        self.disposition = disposition


def app_response_file(func: F | None = None,
                      /,
                      logger: Logger | None = None):
    if logger is None:
        logger = api_logger

    if func is None:
        def decorator(f: F | None = None):
            return app_response_file(f, logger=logger)
        return decorator

    def generate_response(res: Any):
        if isinstance(res, str):
            file_path = res
            file_name = os.path.basename(file_path)
            disposition_type = 'attachment'
            media_type = None
        elif isinstance(res, FileResponseConfig):
            file_path = res.file_path
            file_name = res.file_name or os.path.basename(file_path)
            disposition_type = res.disposition or 'attachment'
            media_type = res.media_type
        else:
            raise RuntimeError('unexpected return type (expected FileResponseConfig or file path (str))')
        return FileResponse(file_path,
                            content_disposition_type=disposition_type,
                            media_type=media_type,
                            filename=file_name)

    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        try:
            res = func(*args, **kwargs)
            return generate_response(res)
        except Exception as ex:
            logger.error(f'error executing {func.__name__}',
                         exc_info=ex)
            return _error(str(ex))

    @functools.wraps(func)
    async def async_wrapper(*args, **kwargs):
        try:
            res = await func(*args, **kwargs)
            return generate_response(res)
        except Exception as ex:
            logger.error(f'error executing {func.__name__}',
                         exc_info=ex)
            return _error(str(ex))

    return async_wrapper if inspect.iscoroutinefunction(func) else wrapper


class StreamingResponseConfig:
    content: ContentStream
    file_name: str | None = None
    media_type: str | None = None
    disposition: str | None = None

    def __init__(self,
                 content: ContentStream,
                 file_name: str | None = None,
                 media_type: str | None = None,
                 disposition: str | None = None):
        self.content = content
        self.file_name = file_name
        self.media_type = media_type
        self.disposition = disposition


def app_streaming_response(func: F | None = None,
                           /,
                           logger: Logger | None = None):
    if logger is None:
        logger = api_logger

    if func is None:
        def decorator(f: F | None = None):
            return app_streaming_response(f, logger=logger)
        return decorator

    def generate_response(res: Any):
        if isinstance(res, StreamingResponseConfig):
            content = res.content
            file_name = res.file_name
            disposition_type = res.disposition
            media_type = res.media_type
        else:
            content = res
            file_name = os.path.basename(content)
            disposition_type = 'attachment'
            media_type = None
        response = StreamingResponse(
            content,
            media_type=media_type
        )
        if file_name is not None:
            response.headers["Content-Disposition"] = f"{disposition_type}; filename={file_name}"
        return response

    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        try:
            res = func(*args, **kwargs)
            return generate_response(res)
        except Exception as ex:
            logger.error(f'error executing {func.__name__}',
                         exc_info=ex)
            return _error(str(ex))

    @functools.wraps(func)
    async def async_wrapper(*args, **kwargs):
        try:
            res = await func(*args, **kwargs)
            return generate_response(res)
        except Exception as ex:
            logger.error(f'error executing {func.__name__}',
                         exc_info=ex)
            return _error(str(ex))

    return async_wrapper if inspect.iscoroutinefunction(func) else wrapper


def _success(payload):
    return {
        'payload': payload.to_dict(dates_mode='str') if isinstance(payload, SerializableModel) else payload,
        'status': 'OK'
    }


def _error(message):
    return {
        'message': message,
        'status': 'ERROR'
    }
