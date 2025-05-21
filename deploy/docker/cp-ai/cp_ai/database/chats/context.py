from .mongo import ChatsDatabaseReader
from contextlib import AbstractContextManager, AbstractAsyncContextManager
from typing import ContextManager, AsyncContextManager


class ChatsDatabaseConnection(
    AbstractContextManager,
    AbstractAsyncContextManager,
    ContextManager[ChatsDatabaseReader],
    AsyncContextManager[ChatsDatabaseReader]
):
    def __enter__(self, *args, **kwargs) -> ChatsDatabaseReader:
        self.connection = ChatsDatabaseReader()
        self.connection.initialize()
        return self.connection

    def __exit__(self, *args, **kwargs) -> bool | None:
        try:
            self.connection.destroy()
        except:
            pass
        return False

    async def __aenter__(self, *args, **kwargs) -> ChatsDatabaseReader:
        self.connection = ChatsDatabaseReader()
        await self.connection.initialize_async()
        return self.connection

    async def __aexit__(self, *args, **kwargs) -> bool | None:
        try:
            await self.connection.destroy_async()
        except:
            pass
        return False