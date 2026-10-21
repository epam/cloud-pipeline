from cpaibot.database.chat.base import ChatsProtocolBase
from cpaibot.database.chat.options import ChatsRequest, ChatsResponse
from cpaibot.common.model.chat import (
    Chat,
    ChatBranch,
    Message,
    MessagePart,
)
from cpaibot.common.settings import settings
from cpaibot.common.logger import CpLogger as Logger
from pymongo import MongoClient, AsyncMongoClient, IndexModel, DESCENDING, ASCENDING
from pymongo.collection import Collection
from pymongo.asynchronous.collection import AsyncCollection


default_logger = Logger("chats")

database_name = settings.MONGODB_DATABASE_NAME

chats_table = "chats"
branches_table = "chat_branches"
messages_table = "chat_messages"
message_parts_table = "chat_message_parts"


_index = {
    chats_table: [
        IndexModel("identifier"),
        IndexModel("created"),
        IndexModel("timestamp")
    ],
    branches_table: [
        IndexModel("identifier"),
        IndexModel("created"),
        IndexModel("timestamp"),
        IndexModel("chat_id"),
    ],
    messages_table: [
        IndexModel("identifier"),
        IndexModel("created"),
        IndexModel("timestamp"),
        IndexModel("chat_id"),
        IndexModel("branch_id"),
    ],
    message_parts_table: [
        IndexModel("identifier"),
        IndexModel("created"),
        IndexModel("timestamp"),
        IndexModel("chat_id"),
        IndexModel("message_id"),
    ],
}


class MongoDbChats(ChatsProtocolBase):
    client: MongoClient | None = None
    async_client: AsyncMongoClient | None = None

    def __init__(self, /, logger: Logger | None = None):
        self.db_path = settings.mongodb_url
        self.logger = logger or default_logger
        self.initialized = False
        self.async_initialized = False
        self.client = None
        self.async_client = None

    @property
    def available(self) -> bool:
        return self.db_path is not None

    # ------------------------------------------------------------------
    # helpers
    # ------------------------------------------------------------------

    def _db(self):
        return self.initialize()[database_name]

    async def _adb(self):
        client = await self.initialize_async()
        return client[database_name]

    # ------------------------------------------------------------------
    # lifecycle
    # ------------------------------------------------------------------

    def initialize(self, logger: Logger | None = None) -> MongoClient:
        if self.initialized:
            if self.client is None:
                raise RuntimeError("mongodb client is not initialized")
            return self.client
        if self.db_path is None:
            raise RuntimeError("mongodb url is not specified")
        self.client = MongoClient(self.db_path)
        self.initialized = True
        return self.client

    async def initialize_async(self, logger: Logger | None = None) -> AsyncMongoClient:
        if self.async_initialized:
            if self.async_client is None:
                raise RuntimeError("mongodb async client is not initialized")
            return self.async_client
        if self.db_path is None:
            raise RuntimeError("mongodb url is not specified")
        self.async_client = AsyncMongoClient(self.db_path)
        self.async_initialized = True
        return self.async_client

    def destroy(self, logger: Logger | None = None) -> None:
        if self.client is not None:
            self.client.close()

    def clear(self, logger: Logger | None = None) -> None:
        logger = logger or self.logger

        collections = [
            self._get_chats_collection(logger=logger),
            self._get_chat_branches_collection(logger=logger),
            self._get_messages_collection(logger=logger),
            self._get_message_parts_collection(logger=logger),
        ]

        for collection in collections:
            result = collection.delete_many({})
            logger.info(
                f"Cleared collection {collection.name}, deleted {result.deleted_count} documents"
            )


    async def clear_async(self, logger: Logger | None = None) -> None:
        logger = logger or self.logger

        collections = [
            await self._get_chats_collection_async(logger=logger),
            await self._get_chat_branches_collection_async(logger=logger),
            await self._get_messages_collection_async(logger=logger),
            await self._get_message_parts_collection_async(logger=logger),
        ]

        for collection in collections:
            result = await collection.delete_many({})
            logger.info(
                f"Cleared collection {collection.name}, deleted {result.deleted_count} documents"
            )

    async def destroy_async(self, logger: Logger | None = None) -> None:
        if self.async_client is not None:
            await self.async_client.close()

    def _get_collection(self, collection: str, logger: Logger | None = None) -> Collection:
        client = self.initialize(logger=logger)
        db = client[database_name]
        db_collection = db[collection]
        if collection in _index:
            idx = _index[collection]
            db_collection.create_indexes(idx)
        return db_collection

    async def _get_collection_async(self, collection: str, logger: Logger | None = None) -> AsyncCollection:
        client = await self.initialize_async(logger=logger)
        db = client[database_name]
        db_collection = db[collection]
        if collection in _index:
            idx = _index[collection]
            await db_collection.create_indexes(idx)
        return db_collection

    def _get_chats_collection(self, logger: Logger | None = None) -> Collection:
        return self._get_collection(chats_table, logger=logger)

    async def _get_chats_collection_async(self, logger: Logger | None = None) -> AsyncCollection:
        return await self._get_collection_async(chats_table, logger=logger)

    def _get_chat_branches_collection(self, logger: Logger | None = None) -> Collection:
        return self._get_collection(branches_table, logger=logger)

    async def _get_chat_branches_collection_async(self, logger: Logger | None = None) -> AsyncCollection:
        return await self._get_collection_async(branches_table, logger=logger)

    def _get_messages_collection(self, logger: Logger | None = None) -> Collection:
        return self._get_collection(messages_table, logger=logger)

    async def _get_messages_collection_async(self, logger: Logger | None = None) -> AsyncCollection:
        return await self._get_collection_async(messages_table, logger=logger)

    def _get_message_parts_collection(self, logger: Logger | None = None) -> Collection:
        return self._get_collection(message_parts_table, logger=logger)

    async def _get_message_parts_collection_async(self, logger: Logger | None = None) -> AsyncCollection:
        return await self._get_collection_async(message_parts_table, logger=logger)

    # ------------------------------------------------------------------
    # chats
    # ------------------------------------------------------------------

    def upsert_chat(self, chat: Chat) -> Chat:
        collection = self._get_chats_collection()
        collection.update_one(filter={"identifier": chat.identifier},
                              update={"$set": chat.serialize()},
                              upsert=True)
        return chat

    def get_chat(self, chat_id: str) -> Chat | None:
        collection = self._get_chats_collection()
        doc = collection.find_one({"identifier": chat_id})
        return Chat.deserialize(doc) if doc else None

    def list_chats(self, options: ChatsRequest | None = None) -> ChatsResponse:
        collection = self._get_chats_collection()
        page = None
        page_size = None
        user = None
        if options:
            page = options.page
            page_size = options.page_size
            user = options.user

        query = {"user": user} if user else {}
        if page is None or page < 0:
            page = 0
        if page_size is None or page_size <= 0:
            page_size = 20
        skip = page * page_size
        cur = collection.find(query)
        total = collection.count_documents(query)
        if cur is None:
            return ChatsResponse.build(page=page, page_size=page_size, total=total)
        cur = cur.sort('created', DESCENDING)
        if skip > 0:
            cur = cur.skip(skip)
        cur = cur.limit(page_size)
        res = cur.to_list()
        chats = [Chat.deserialize(c) for c in res]
        return ChatsResponse.build(page=page, page_size=page_size, total=total, elements=chats)

    def delete_chat(self, chat_id: str) -> None:
        chats_collection = self._get_chats_collection()
        chat_branches_collection = self._get_chat_branches_collection()
        messages_collection = self._get_messages_collection()
        message_parts_collection = self._get_message_parts_collection()
        chats_collection.delete_one({"identifier": chat_id})
        chat_branches_collection.delete_many({"chat_id": chat_id})
        messages_collection.delete_many({"chat_id": chat_id})
        message_parts_collection.delete_many({"chat_id": chat_id})

    async def upsert_chat_async(self, chat: Chat) -> Chat:
        collection = await self._get_chats_collection_async()
        await collection.update_one(
            filter={"identifier": chat.identifier},
            update={"$set": chat.serialize()},
            upsert=True)
        return chat

    async def get_chat_async(self, chat_id: str) -> Chat | None:
        collection = await self._get_chats_collection_async()
        doc = await collection.find_one({"identifier": chat_id})
        return Chat.deserialize(doc) if doc else None

    async def list_chats_async(self, options: ChatsRequest | None = None) -> ChatsResponse:
        collection = await self._get_chats_collection_async()
        page = None
        page_size = None
        user = None
        if options:
            page = options.page
            page_size = options.page_size
            user = options.user

        query = {"user": user} if user else {}
        if page is None or page < 0:
            page = 0
        if page_size is None or page_size <= 0:
            page_size = 20
        skip = page * page_size
        cur = collection.find(query)
        total = await collection.count_documents(query)
        if cur is None:
            return ChatsResponse.build(page=page, page_size=page_size, total=total)
        cur = cur.sort('created', DESCENDING)
        if skip > 0:
            cur = cur.skip(skip)
        cur = cur.limit(page_size)
        res = await cur.to_list()
        chats = [Chat.deserialize(c) for c in res]
        return ChatsResponse.build(page=page, page_size=page_size, total=total, elements=chats)

    async def delete_chat_async(self, chat_id: str) -> None:
        chats_collection = self._get_chats_collection()
        chat_branches_collection = self._get_chat_branches_collection()
        messages_collection = self._get_messages_collection()
        message_parts_collection = self._get_message_parts_collection()
        chats_collection.delete_one({"identifier": chat_id})
        chat_branches_collection.delete_many({"chat_id": chat_id})
        messages_collection.delete_many({"chat_id": chat_id})
        message_parts_collection.delete_many({"chat_id": chat_id})

    # ------------------------------------------------------------------
    # branches
    # ------------------------------------------------------------------

    def upsert_branch(self, branch: ChatBranch) -> ChatBranch:
        collection = self._get_chat_branches_collection()
        collection.update_one(filter={"identifier": branch.identifier},
                              update={"$set": branch.serialize()},
                              upsert=True)
        return branch

    def get_branch(self, branch_id: str) -> ChatBranch | None:
        collection = self._get_chat_branches_collection()
        doc = collection.find_one({"identifier": branch_id})
        return ChatBranch.deserialize(doc) if doc else None

    def list_branches(self, chat_id: str) -> list[ChatBranch]:
        collection = self._get_chat_branches_collection()
        query = {"chat_id": chat_id}
        cur = collection.find(query)
        if cur is None:
            return []
        cur = cur.sort('created', DESCENDING)
        res = cur.to_list()
        return [ChatBranch.deserialize(c) for c in res]

    def delete_branch(self, branch_id: str) -> None:
        chat_branches_collection = self._get_chat_branches_collection()
        messages_collection = self._get_messages_collection()
        message_parts_collection = self._get_message_parts_collection()
        chat_branches_collection.delete_one({"identifier": branch_id})
        messages_collection.delete_many({"branch_id": branch_id})
        message_parts_collection.delete_many({"branch_id": branch_id})

    async def upsert_branch_async(self, branch: ChatBranch) -> ChatBranch:
        collection = await self._get_chat_branches_collection_async()
        await collection.update_one(
            filter={"identifier": branch.identifier},
            update={"$set": branch.serialize()},
            upsert=True
        )
        return branch

    async def get_branch_async(self, branch_id: str) -> ChatBranch | None:
        collection = await self._get_chat_branches_collection_async()
        doc = await collection.find_one({"identifier": branch_id})
        return ChatBranch.deserialize(doc) if doc else None

    async def list_branches_async(self, chat_id: str) -> list[ChatBranch]:
        collection = await self._get_chat_branches_collection_async()
        query = {"chat_id": chat_id}
        cur = collection.find(query)
        if cur is None:
            return []
        cur = cur.sort('created', DESCENDING)
        res = await cur.to_list()
        return [ChatBranch.deserialize(c) for c in res]

    async def delete_branch_async(self, branch_id: str) -> None:
        chat_branches_collection = await self._get_chat_branches_collection_async()
        messages_collection = await self._get_messages_collection_async()
        message_parts_collection = await self._get_message_parts_collection_async()
        await chat_branches_collection.delete_one({"identifier": branch_id})
        await messages_collection.delete_many({"branch_id": branch_id})
        await message_parts_collection.delete_many({"branch_id": branch_id})

    # ------------------------------------------------------------------
    # messages
    # ------------------------------------------------------------------

    def upsert_message(self, message: Message) -> Message:
        collection = self._get_messages_collection()
        collection.update_one(
            filter={"identifier": message.identifier},
            update={"$set": message.serialize()},
            upsert=True
        )
        return message

    def get_message(self, message_id: str) -> Message | None:
        collection = self._get_messages_collection()
        doc = collection.find_one({"identifier": message_id})
        return Message.deserialize(doc) if doc else None

    def list_messages(
            self,
            chat_id: str,
            messages_ids: list[str] | None = None,
    ) -> list[Message]:
        query = {"chat_id": chat_id}
        if messages_ids:
            query.update({"identifier": {"$in": messages_ids}})
        collection = self._get_messages_collection()
        cur = collection.find(query)
        if cur is None:
            return []
        cur = cur.sort('created', ASCENDING)
        res = cur.to_list()
        return [Message.deserialize(o) for o in res]

    def delete_message(self, message_id: str) -> None:
        messages_collection = self._get_messages_collection()
        message_parts_collection = self._get_message_parts_collection()
        messages_collection.delete_one({"identifier": message_id})
        message_parts_collection.delete_many({"message_id": message_id})

    async def upsert_message_async(self, message: Message) -> Message:
        collection = await self._get_messages_collection_async()
        await collection.update_one(
            filter={"identifier": message.identifier},
            update={"$set": message.serialize()},
            upsert=True
        )
        return message

    async def get_message_async(self, message_id: str) -> Message | None:
        collection = await self._get_messages_collection_async()
        doc = await collection.find_one({"identifier": message_id})
        return Message.deserialize(doc) if doc else None

    async def list_messages_async(
            self,
            chat_id: str,
            messages_ids: list[str] | None = None,
    ) -> list[Message]:
        query = {"chat_id": chat_id}
        if messages_ids:
            query.update({"identifier": {"$in": messages_ids}})
        collection = await self._get_messages_collection_async()
        cur = collection.find(query)
        if cur is None:
            return []
        cur = cur.sort('created', ASCENDING)
        res = await cur.to_list()
        return [Message.deserialize(o) for o in res]

    async def delete_message_async(self, message_id: str) -> None:
        messages_collection = await self._get_messages_collection_async()
        message_parts_collection = await self._get_message_parts_collection_async()
        await messages_collection.delete_one({"identifier": message_id})
        await message_parts_collection.delete_many({"message_id": message_id})

    # ------------------------------------------------------------------
    # message parts
    # ------------------------------------------------------------------

    def upsert_message_part(self, part: MessagePart) -> MessagePart:
        collection = self._get_message_parts_collection()
        collection.update_one(
            filter={"identifier": part.identifier},
            update={"$set": part.serialize()},
            upsert=True
        )
        return part

    def get_message_part(self, part_id: str) -> MessagePart | None:
        collection = self._get_message_parts_collection()
        doc = collection.find_one({"identifier": part_id})
        return MessagePart.deserialize(doc) if doc else None

    def list_message_parts(self, message_id: str | list[str]) -> list[MessagePart]:
        collection = self._get_message_parts_collection()
        if isinstance(message_id, str):
            ids = [message_id]
        else:
            ids = message_id
        query = {"message_id": {"$in": ids}}
        cur = collection.find(query)
        if cur is None:
            return []
        cur = cur.sort('created', ASCENDING)
        res = cur.to_list()
        return [MessagePart.deserialize(o) for o in res]

    def delete_message_part(self, part_id: str) -> None:
        message_parts_collection = self._get_message_parts_collection()
        message_parts_collection.delete_many({"identifier": part_id})

    async def upsert_message_part_async(self, part: MessagePart) -> MessagePart:
        collection = await self._get_message_parts_collection_async()
        await collection.update_one(
            filter={"identifier": part.identifier},
            update={"$set": part.serialize()},
            upsert=True
        )
        return part

    async def get_message_part_async(self, part_id: str) -> MessagePart | None:
        collection = await self._get_message_parts_collection_async()
        doc = await collection.find_one({"identifier": part_id})
        return MessagePart.deserialize(doc) if doc else None

    async def list_message_parts_async(self, message_id: str | list[str]) -> list[MessagePart]:
        collection = await self._get_message_parts_collection_async()
        if isinstance(message_id, str):
            ids = [message_id]
        else:
            ids = message_id
        query = {"message_id": {"$in": ids}}
        cur = collection.find(query)
        if cur is None:
            return []
        cur = cur.sort('created', ASCENDING)
        res = await cur.to_list()
        return [MessagePart.deserialize(o) for o in res]

    async def delete_message_part_async(self, part_id: str) -> None:
        message_parts_collection = await self._get_message_parts_collection_async()
        await message_parts_collection.delete_many({"identifier": part_id})

