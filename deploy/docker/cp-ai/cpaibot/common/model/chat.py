import json
from datetime import datetime, UTC
from enum import Enum
from pydantic import BaseModel, ConfigDict, PrivateAttr
from typing import Self
from uuid import uuid4
from llama_index.core.base.llms.types import ChatMessage, TextBlock, MessageRole


class ChatElementBaseModel(BaseModel):
    model_config = ConfigDict(use_enum_values=True, )
    identifier: str
    timestamp: datetime
    created: datetime

    _dirty: bool = PrivateAttr(False)

    @staticmethod
    def base_fields(
            *,
            identifier: str | None = None,
    ) -> dict:
        ts = ChatElementBaseModel.generate_timestamp()
        return {
            "identifier": identifier or ChatElementBaseModel.generate_id(),
            "created": ts,
            "timestamp": ts,
        }

    @staticmethod
    def generate_id() -> str:
        return uuid4().hex

    @staticmethod
    def generate_timestamp() -> datetime:
        return datetime.now(UTC)

    @classmethod
    def deserialize(cls: Self, data: dict) -> Self:
        return cls(**data)

    def serialize(self) -> dict:
        return self.model_dump(
            mode="json",
        )

    @property
    def dirty(self) -> bool:
        return self._dirty

    @dirty.setter
    def dirty(self, value: bool) -> None:
        self._dirty = value

    def mark_updated(self):
        self.timestamp = ChatElementBaseModel.generate_timestamp()

    def to_json(self) -> dict:
        return self.serialize()


class Chat(ChatElementBaseModel):
    title: str
    user: str
    active_branch_id: str | None = None

    @classmethod
    def create(
            cls,
            /,
            identifier: str | None = None,
            title: str | None = None,
            user: str | None = None,
            active_branch_id: str | None = None,
    ) -> "Chat":
        res = cls(
            title=title or "",
            user=user or "",
            active_branch_id=active_branch_id,
            **cls.base_fields(identifier=identifier),
        )
        res.dirty = True
        return res


class ChatBranch(ChatElementBaseModel):
    chat_id: str
    messages: list[str] | None = []
    pending: bool | None = False

    _chat: Chat | None = PrivateAttr(None)

    @classmethod
    def create(
            cls,
            chat_id: str,
            /,
            identifier: str | None = None,
            messages: list[str] | None = None,
    ) -> "ChatBranch":
        res = cls(
            chat_id=chat_id,
            messages=messages or [],
            **cls.base_fields(identifier=identifier),
        )
        res.dirty = True
        return res

    @property
    def chat(self) -> Chat | None:
        return self._chat

    @chat.setter
    def chat(self, chat: Chat | None) -> None:
        self._chat = chat


class Message(ChatElementBaseModel):
    chat_id: str
    role: MessageRole
    previous_id: str | None = None
    pending: bool = False
    status: str | None = None

    _chat: Chat | None = PrivateAttr(None)
    _branches: list["ChatBranch"] = PrivateAttr([])
    _parts: list["MessagePart"] = PrivateAttr([])

    @classmethod
    def create(
            cls,
            chat_id: str,
            /,
            identifier: str | None = None,
            role: MessageRole | None = None,
            previous_id: str | None = None,
    ) -> "Message":
        res = cls(
            chat_id=chat_id,
            role=role or MessageRole.USER,
            previous_id=previous_id,
            **cls.base_fields(identifier=identifier),
        )
        res.dirty = True
        return res

    def _get_parts(self, filter_out_context_parts = False) -> list["MessagePart"]:
        if filter_out_context_parts:
            return [p for p in (self._parts or []) if p.type != MessagePartType.CONTEXT]
        return self._parts or []

    def to_llama_index(self, filter_out_context_parts = False) -> ChatMessage:
        return ChatMessage(
            content=[p.to_llama_index() for p in self._get_parts(filter_out_context_parts)],
            role=self.role,
        )

    def to_string(self, filter_out_context_parts = False) -> str:
        content = self.to_llama_index(filter_out_context_parts).content
        return (f"[{self.role}]\n\n"
                f"{content}\n\n"
                f"")

    def to_json(self, filter_out_context_parts=False) -> dict:
        md = self.model_dump(mode="json")
        parts = self._get_parts(filter_out_context_parts=filter_out_context_parts)
        parts = [p.to_json() for p in parts]
        md.update({"parts": parts})
        return md

    @property
    def chat(self) -> Chat | None:
        return self._chat

    @chat.setter
    def chat(self, chat: Chat | None) -> None:
        self._chat = chat

    @property
    def branches(self) -> list[ChatBranch]:
        return self._branches

    @branches.setter
    def branches(self, branches: list[ChatBranch]) -> None:
        self._branches = branches

    @property
    def parts(self) -> list["MessagePart"]:
        return self._parts

    @parts.setter
    def parts(self, parts: list["MessagePart"]) -> None:
        self._parts = parts

    @property
    def is_empty(self) -> bool:
        return len(self.parts) == 0

    def message_is_empty(self, filter_out_context_parts = False) -> bool:
        parts = self._get_parts(filter_out_context_parts=filter_out_context_parts)
        parts = [p for p in parts if not p.is_empty]
        return len(parts) == 0

    def get_launch_payloads(self) -> list[dict]:
        payloads = []
        for part in (self.parts or []):
            payload = part.get_launch_payload()
            if payload:
                payloads.append(payload)
        return payloads


class MessagePartType(str, Enum):
    TEXT = "text"
    LAUNCH = "launch"
    DECISION = "decision"
    ERROR = "error"
    CONTEXT = "context"
    RUN = "run"


class MessagePart(ChatElementBaseModel):
    message_id: str
    chat_id: str
    pending: bool = False
    type: MessagePartType | None = MessagePartType.TEXT
    metadata: dict | None = {}
    data: dict | None = {}
    context: dict | None = {}
    text: str | None = ""
    warnings: list[str] | None = []
    errors: list[str] | None = []
    logs: list[str] | None = []
    status: str | None = None

    def set_launch_payload(self, payload: dict) -> None:
        if not self.data:
            self.data = {}
        self.data.update({"launch_payload": payload})

    def set_run_payload(self, payload: dict) -> dict | None:
        if not self.data:
            self.data = {}
        self.data.update({"run_payload": payload})

    def set_sources(self, sources: list[dict]):
        if not self.data:
            self.data = {}
        self.data.update({"sources": sources})

    def get_launch_payload(self) -> dict | None:
        if self.data and "launch_payload" in self.data:
            return self.data["launch_payload"]
        return None

    def get_run_payload(self) -> dict | None:
        if self.data and "run_payload" in self.data:
            return self.data["run_payload"]
        return None

    @property
    def is_empty(self) -> bool:
        return not self.to_string()

    @property
    def is_contentful(self) -> bool:
        return self.type in {MessagePartType.TEXT}

    @classmethod
    def create(
            cls,
            chat_id: str,
            message_id: str,
            /,
            identifier: str | None = None,
            part_type: MessagePartType | None = None,
    ) -> "MessagePart":
        res = cls(
            chat_id=chat_id,
            message_id=message_id,
            type=part_type or MessagePartType.TEXT,
            **cls.base_fields(identifier=identifier),
        )
        res.dirty = True
        return res

    def to_llama_index(self) -> TextBlock:
        text = self.to_string()
        return TextBlock(text=text)

    def to_string(self) -> str:
        text = ""
        if self.type == MessagePartType.LAUNCH:
            payload = self.get_launch_payload()
            if payload:
                text += (f"Launch payload:\n"
                         f"```json\n"
                         f"{json.dumps(payload)}\n"
                         f"```\n\n")
        if self.type == MessagePartType.RUN:
            payload = self.get_run_payload()
            if payload:
                text += (f"Launched run payload:\n"
                         f"```json\n"
                         f"{json.dumps(payload)}\n"
                         f"```\n\n")
        if self.text:
            text += self.text
        return text

    def to_json(self) -> dict:
        return self.model_dump(mode="json")
