from typing import Protocol, Any
from datetime import datetime
from cpaibot.common.model.chat import (
    Chat,
    ChatBranch,
    Message,
    MessagePart,
)
from cpaibot.common.logger import CpLogger as Logger
from cpaibot.database.chat.options import ChatsRequest, ChatsResponse


class ChatsProtocol(Protocol):
    # ------------------------------------------------------------------
    # lifecycle
    # ------------------------------------------------------------------

    def initialize(self, logger: Logger | None = None) -> Any:
        ...

    def destroy(self, logger: Logger | None = None) -> None:
        ...

    def clear(self, logger: Logger | None = None) -> None:
        ...

    async def clear_async(self, logger: Logger | None = None) -> None:
        ...

    async def initialize_async(self, logger: Logger | None = None) -> Any:
        ...

    async def destroy_async(self, logger: Logger | None = None) -> None:
        ...

    # ------------------------------------------------------------------
    # chats
    # ------------------------------------------------------------------

    def upsert_chat(self, chat: Chat) -> Chat:
        ...

    def get_chat(self, chat_id: str) -> Chat | None:
        ...

    def list_chats(self, options: ChatsRequest | None = None) -> ChatsResponse:
        ...

    def delete_chat(self, chat_id: str) -> None:
        ...

    async def upsert_chat_async(self, chat: Chat) -> Chat:
        ...

    async def get_chat_async(self, chat_id: str) -> Chat | None:
        ...

    async def list_chats_async(self, options: ChatsRequest | None = None) -> ChatsResponse:
        ...

    async def delete_chat_async(self, chat_id: str) -> None:
        ...

    # ------------------------------------------------------------------
    # branches
    # ------------------------------------------------------------------

    def upsert_branch(self, branch: ChatBranch) -> ChatBranch:
        ...

    def get_branch(self, branch_id: str) -> ChatBranch | None:
        ...

    def list_branches(self, chat_id: str) -> list[ChatBranch]:
        ...

    def delete_branch(self, branch_id: str) -> None:
        ...

    async def upsert_branch_async(self, branch: ChatBranch) -> ChatBranch:
        ...

    async def get_branch_async(self, branch_id: str) -> ChatBranch | None:
        ...

    async def list_branches_async(self, chat_id: str) -> list[ChatBranch]:
        ...

    async def delete_branch_async(self, branch_id: str) -> None:
        ...

    # ------------------------------------------------------------------
    # messages
    # ------------------------------------------------------------------

    def upsert_message(self, message: Message) -> Message:
        ...

    def get_message(self, message_id: str) -> Message | None:
        ...

    def list_messages(
            self,
            chat_id: str,
            messages_ids: list[str] | None = None,
    ) -> list[Message]:
        ...

    def delete_message(self, message_id: str) -> None:
        ...

    async def upsert_message_async(self, message: Message) -> Message:
        ...

    async def get_message_async(self, message_id: str) -> Message | None:
        ...

    async def list_messages_async(
            self,
            chat_id: str,
            messages_ids: list[str] | None = None,
    ) -> list[Message]:
        ...

    async def delete_message_async(self, message_id: str) -> None:
        ...

    # ------------------------------------------------------------------
    # message parts
    # ------------------------------------------------------------------

    def upsert_message_part(self, part: MessagePart) -> MessagePart:
        ...

    def get_message_part(self, part_id: str) -> MessagePart | None:
        ...

    def list_message_parts(self, message_id: str | list[str]) -> list[MessagePart]:
        ...

    def delete_message_part(self, part_id: str) -> None:
        ...

    async def upsert_message_part_async(self, part: MessagePart) -> MessagePart:
        ...

    async def get_message_part_async(self, part_id: str) -> MessagePart | None:
        ...

    async def list_message_parts_async(self, message_id: str | list[str]) -> list[MessagePart]:
        ...

    async def delete_message_part_async(self, part_id: str) -> None:
        ...
