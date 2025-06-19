from abc import ABC, abstractmethod
from typing import Literal
from pydantic import BaseModel
from datetime import datetime, timezone
from llama_index.core.types import MessageRole
from .utilities import generate_identifier, get_username_from_bearer


DateSerializationMode = Literal["float"] | Literal["str"] | Literal["date"]


def _pop_datetime_field(
        data: dict,
        field: str,
) -> datetime | None:
    try:
        dt_str = data.pop(field, None)
        if isinstance(dt_str, datetime):
            dt = dt_str
        elif isinstance(dt_str, float):
            dt = datetime.fromtimestamp(dt_str)
        elif isinstance(dt_str, str):
            dt = datetime.fromisoformat(dt_str)
        else:
            dt = None
        if dt is not None and dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt
    except:
        return None


def _serialize_datetime_field(value: datetime | None, /, mode: DateSerializationMode | None = None) -> str | float | datetime | None:
    if mode is None:
        mode = 'str'
    if value is None:
        return None
    if mode == 'float':
        return value.timestamp()
    if mode == 'str':
        return value.isoformat()
    return value


class SerializableModel(ABC, BaseModel):
    @abstractmethod
    def to_dict(self, dates_mode: DateSerializationMode | None = None) -> dict:
        ...


class Chat(SerializableModel):
    chat_id: str | None = None
    title: str | None = None
    user: str | None = None
    created_date: datetime
    timestamp: datetime

    @classmethod
    def create_chat(
            cls,
            /,
            bearer: str | None = None,
            user: str | None = None
    ) -> "Chat":
        if user is None:
            user = get_username_from_bearer(bearer)
        chat_id = generate_identifier(short=True)
        return cls.from_dict({
            'chat_id': chat_id,
            'user': user,
        })

    @classmethod
    def from_dict(cls, data: dict) -> "Chat":
        payload = {**data}
        created_date = _pop_datetime_field(payload, 'created_date') or datetime.now(tz=timezone.utc)
        timestamp = _pop_datetime_field(payload, 'timestamp') or datetime.now(tz=timezone.utc)
        return Chat(
            **payload,
            created_date=created_date,
            timestamp=timestamp
        )

    def to_dict(self, dates_mode: DateSerializationMode | None = None) -> dict:
        m = self.model_dump(exclude={'created_date', 'timestamp'}, exclude_none=True)
        created_date = _serialize_datetime_field(self.created_date, mode=dates_mode)
        timestamp = _serialize_datetime_field(self.timestamp, mode=dates_mode)
        return {
            **m,
            'created_date': created_date,
            'timestamp': timestamp
        }

    def update_timestamp(self):
        self.timestamp = datetime.now(tz=timezone.utc)


class Message(SerializableModel):
    message_id: str | None = None
    chat_id: str | None = None
    role: str
    content: str
    created_date: datetime
    attributes : dict | None = None

    @classmethod
    def create_message(
            cls,
            message: str,
            chat_id: str,
            role: str | None = None,
            attributes: dict | None = None,
            is_assistant: bool | None = None
    ) -> "Message":
        message_id = generate_identifier(short=True)
        if role is None:
            if is_assistant:
                role = MessageRole.ASSISTANT
            else:
                role = MessageRole.USER
        return Message(
            message_id=message_id,
            chat_id=chat_id,
            content=message,
            role=role,
            created_date=datetime.now(tz=timezone.utc),
            attributes=attributes
        )

    @classmethod
    def from_dict(cls, data: dict) -> "Message":
        payload = {**data}
        created_date = _pop_datetime_field(payload, 'created_date') or datetime.now(tz=timezone.utc)
        return Message(
            **payload,
            created_date=created_date,
        )

    def to_dict(self, dates_mode: DateSerializationMode | None = None) -> dict:
        m = self.model_dump(exclude={'created_date'}, exclude_none=True)
        created_date = _serialize_datetime_field(self.created_date, mode=dates_mode)
        return {
            **m,
            'created_date': created_date,
        }
