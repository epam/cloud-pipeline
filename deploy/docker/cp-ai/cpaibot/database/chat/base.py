from abc import ABC, abstractmethod
from typing import Any
from datetime import datetime

from cpaibot.common.model.chat import (
    Chat,
    ChatBranch,
    Message,
    MessagePart,
)
from cpaibot.common.logger import CpLogger as Logger
from cpaibot.database.chat.protocol import ChatsProtocol
from cpaibot.database.chat.options import ChatsRequest, ChatsResponse


class ChatsProtocolBase(ABC, ChatsProtocol):
    # ------------------------------------------------------------------
    # lifecycle
    # ------------------------------------------------------------------

    @abstractmethod
    def initialize(self, logger: Logger | None = None) -> Any:
        ...

    @abstractmethod
    def destroy(self, logger: Logger | None = None) -> None:
        ...

    @abstractmethod
    def clear(self, logger: Logger | None = None) -> None:
        ...

    @abstractmethod
    async def clear_async(self, logger: Logger | None = None) -> None:
        ...

    @abstractmethod
    async def initialize_async(self, logger: Logger | None = None) -> Any:
        ...

    @abstractmethod
    async def destroy_async(self, logger: Logger | None = None) -> None:
        ...

    # ------------------------------------------------------------------
    # chats
    # ------------------------------------------------------------------

    @abstractmethod
    def upsert_chat(self, chat: Chat) -> Chat:
        ...

    @abstractmethod
    def get_chat(self, chat_id: str) -> Chat | None:
        ...

    @abstractmethod
    def list_chats(self, options: ChatsRequest | None = None) -> ChatsResponse:
        ...

    @abstractmethod
    def delete_chat(self, chat_id: str) -> None:
        ...

    @abstractmethod
    async def upsert_chat_async(self, chat: Chat) -> Chat:
        ...

    @abstractmethod
    async def get_chat_async(self, chat_id: str) -> Chat | None:
        ...

    @abstractmethod
    async def list_chats_async(self, options: ChatsRequest | None = None) -> ChatsResponse:
        ...

    @abstractmethod
    async def delete_chat_async(self, chat_id: str) -> None:
        ...

    # ------------------------------------------------------------------
    # branches
    # ------------------------------------------------------------------

    @abstractmethod
    def upsert_branch(self, branch: ChatBranch) -> ChatBranch:
        ...

    @abstractmethod
    def get_branch(self, branch_id: str) -> ChatBranch | None:
        ...

    @abstractmethod
    def list_branches(self, chat_id: str) -> list[ChatBranch]:
        ...

    @abstractmethod
    def delete_branch(self, branch_id: str) -> None:
        ...

    @abstractmethod
    async def upsert_branch_async(self, branch: ChatBranch) -> ChatBranch:
        ...

    @abstractmethod
    async def get_branch_async(self, branch_id: str) -> ChatBranch | None:
        ...

    @abstractmethod
    async def list_branches_async(
            self, chat_id: str
    ) -> list[ChatBranch]:
        ...

    @abstractmethod
    async def delete_branch_async(self, branch_id: str) -> None:
        ...

    # ------------------------------------------------------------------
    # messages
    # ------------------------------------------------------------------

    @abstractmethod
    def upsert_message(self, message: Message) -> Message:
        ...

    @abstractmethod
    def get_message(self, message_id: str) -> Message | None:
        ...

    @abstractmethod
    def list_messages(
            self,
            chat_id: str,
            messages_ids: list[str] | None = None,
    ) -> list[Message]:
        ...

    @abstractmethod
    def delete_message(self, message_id: str) -> None:
        ...

    @abstractmethod
    async def upsert_message_async(self, message: Message) -> Message:
        ...

    @abstractmethod
    async def get_message_async(self, message_id: str) -> Message | None:
        ...

    @abstractmethod
    async def list_messages_async(
            self,
            chat_id: str,
            messages_ids: list[str] | None = None,
    ) -> list[Message]:
        ...

    @abstractmethod
    async def delete_message_async(self, message_id: str) -> None:
        ...

    # ------------------------------------------------------------------
    # message parts
    # ------------------------------------------------------------------

    @abstractmethod
    def upsert_message_part(self, part: MessagePart) -> MessagePart:
        ...

    @abstractmethod
    def get_message_part(self, part_id: str) -> MessagePart | None:
        ...

    @abstractmethod
    def list_message_parts(self, message_id: str | list[str]) -> list[MessagePart]:
        ...

    @abstractmethod
    def delete_message_part(self, part_id: str) -> None:
        ...

    @abstractmethod
    async def upsert_message_part_async(self, part: MessagePart) -> MessagePart:
        ...

    @abstractmethod
    async def get_message_part_async(self, part_id: str) -> MessagePart | None:
        ...

    @abstractmethod
    async def list_message_parts_async(self, message_id: str | list[str]) -> list[MessagePart]:
        ...

    @abstractmethod
    async def delete_message_part_async(self, part_id: str) -> None:
        ...

