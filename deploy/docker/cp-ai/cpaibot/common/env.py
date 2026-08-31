import os
import logging
from dotenv import load_dotenv
from typing import Callable

from pydantic import Field
from enum import Enum

dotenv_loaded = load_dotenv()

default_logger = logging.getLogger("env")
default_logger.setLevel(logging.INFO)


def env(env_var: str,
        default_value: str | None = None,
        secure: bool | None = False) -> str | None:
    val = os.getenv(env_var)
    is_default = val is None and default_value is not None
    value_descr = val if val is not None else default_value
    if secure and value_descr is not None:
        value_descr = '*****'
    value_descr = value_descr or ''
    value_descr = '%s (default)' % value_descr if is_default else value_descr
    default_logger.debug('%s: %s' % (env_var, value_descr))
    return os.getenv(env_var, default_value)


def env_bool(env_var: str,
             default_value: bool = False,
             secure: bool | None = False) -> bool:
    return env(env_var,
               default_value=str(default_value),
               secure=secure).lower() == 'true'


def env_int(env_var: str,
            default_value: int = 0,
            secure: bool | None = False) -> int:
    return int(env(env_var,
                   default_value=str(default_value),
                   secure=secure))


def env_int_or_none(env_var: str,
                    default_value: int | None = None,
                    secure: bool | None = False) -> int | None:
    val = env(env_var,
              default_value=str(default_value) if default_value is not None else None,
              secure=secure)
    return int(val) if val is not None else None


def env_float(env_var: str,
              default_value: float = 0,
              secure: bool | None = False) -> float:
    return float(env(env_var,
                     default_value=str(default_value),
                     secure=secure))


def env_float_or_none(env_var: str,
                      default_value: float | None = None,
                      secure: bool | None = False) -> float | None:
    val = env(env_var,
              default_value=str(default_value) if default_value is not None else None,
              secure=secure)
    return float(val) if val is not None else None


class EnvField:
    @staticmethod
    def str_env(env_var: str,
                default_value: str | None = None,
                secure: bool | None = False,
                internal: bool | None = False) -> Field:
        return Field(default=env(env_var, default_value=default_value, secure=secure),
                     json_schema_extra={'env': env_var, 'internal': internal})

    @staticmethod
    def str_enum(env_var: str,
                 enum: Callable[[str], Enum],
                 default_value: str | None = None,
                 secure: bool | None = False,
                 internal: bool | None = False) -> Field:
        return Field(default=enum(env(env_var, default_value=default_value, secure=secure)),
                     json_schema_extra={'env': env_var, 'internal': internal})

    @staticmethod
    def bool_env(env_var: str,
                 default_value: bool = False,
                 secure: bool | None = False,
                 internal: bool | None = False) -> Field:
        return Field(default=env_bool(env_var, default_value=default_value, secure=secure),
                     json_schema_extra={'env': env_var, 'internal': internal})

    @staticmethod
    def int_env(env_var: str,
                default_value: int = 0,
                secure: bool | None = False,
                internal: bool | None = False) -> Field:
        return Field(default=env_int(env_var, default_value=default_value, secure=secure),
                     json_schema_extra={'env': env_var, 'internal': internal})

    @staticmethod
    def float_env(env_var: str,
                  default_value: float = 0,
                  secure: bool | None = False,
                  internal: bool | None = False) -> Field:
        return Field(default=env_float(env_var, default_value=default_value, secure=secure),
                     json_schema_extra={'env': env_var, 'internal': internal})

    @staticmethod
    def optional_int(env_var: str,
                     default_value: int | None = None,
                     secure: bool | None = False,
                     internal: bool | None = False) -> Field:
        return Field(default=env_int_or_none(env_var, default_value=default_value, secure=secure),
                     json_schema_extra={'env': env_var, 'internal': internal})

    @staticmethod
    def optional_float(env_var: str,
                       default_value: float | None = None,
                       secure: bool | None = False,
                       internal: bool | None = False) -> Field:
        return Field(default=env_float_or_none(env_var, default_value=default_value, secure=secure),
                     json_schema_extra={'env': env_var, 'internal': internal})
