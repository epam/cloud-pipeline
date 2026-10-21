from contextlib import AbstractContextManager, AbstractAsyncContextManager
from cpaibot.common.logger import CpLogger as Logger
from typing import ContextManager, AsyncContextManager
from .implementations import MongoDbChats
from .protocol import ChatsProtocol


def get_chats_protocol(logger: Logger | None = None) -> ChatsProtocol:
    m = MongoDbChats(logger=logger)
    if not m.available:
        raise RuntimeError("chats client is not available")
    return m


class ChatsManager(
    AbstractContextManager,
    AbstractAsyncContextManager,
    ContextManager[ChatsProtocol],
    AsyncContextManager[ChatsProtocol]
):
    def __enter__(self, *args, **kwargs) -> ChatsProtocol:
        self.reader = get_chats_protocol()
        self.reader.initialize()
        return self.reader

    def __exit__(self, *args, **kwargs) -> bool | None:
        self.reader.destroy()
        return False

    async def __aenter__(self, *args, **kwargs) -> ChatsProtocol:
        self.reader = get_chats_protocol()
        await self.reader.initialize_async()
        return self.reader

    async def __aexit__(self, *args, **kwargs) -> bool | None:
        await self.reader.destroy_async()
        return False
