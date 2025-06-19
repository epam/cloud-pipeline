from logging import Logger
from pymongo import MongoClient, AsyncMongoClient, IndexModel,  ASCENDING, DESCENDING
from pymongo.collection import Collection
from pymongo.asynchronous.collection import AsyncCollection
from cp_ai.common.settings import cp_ai_settings
from cp_ai.common.logger import create_logger
from cp_ai.common.types import Chat, Message
from datetime import datetime


chats_collection = 'chats'
messages_collection = 'messages'


class ChatsDatabaseReader:
    client: MongoClient | None = None
    async_client: AsyncMongoClient | None = None

    def __init__(
            self,
            /,
            db_path: str | None = None,
            db_name: str | None = None,
            logger: Logger | None = None
    ):
        if db_path is None:
            db_path = cp_ai_settings.CHATS_DB_PATH
        if db_name is None:
            db_name = cp_ai_settings.CHATS_DB_NAME
        self.db_path = db_path
        self.db_name = db_name
        self.logger = logger or create_logger('chats.db')
        self.initialized = False
        self.async_initialized = False
        self.client = None
        self.async_client = None

    def initialize(self) -> MongoClient:
        if self.initialized:
            if self.client is None:
                raise RuntimeError('mongodb client is not initialized')
            return self.client
        if self.db_path is None:
            raise RuntimeError('error initializing mongodb chats reader (db path is not specified)')
        self.client = MongoClient(self.db_path)
        self.initialized = True
        return self.client

    async def initialize_async(self) -> AsyncMongoClient:
        if self.async_initialized:
            if self.async_client is None:
                raise RuntimeError('mongodb async client is not initialized')
            return self.async_client
        if self.db_path is None:
            raise RuntimeError('error initializing mongodb chats reader (db path is not specified)')
        self.async_client = AsyncMongoClient(self.db_path)
        self.async_initialized = True
        return self.async_client

    def destroy(self):
        if self.client is not None:
            self.client.close()

    async def destroy_async(self):
        if self.async_client is not None:
            await self.async_client.close()

    def _get_chats_collection(self) -> Collection:
        client = self.initialize()
        db = client[self.db_name]
        collection = db[chats_collection]
        collection.create_indexes([IndexModel('chat_id'),
                                   IndexModel('user')])
        return collection

    async def _get_chats_collection_async(self, logger: Logger | None = None) -> AsyncCollection:
        client = await self.initialize_async()
        db = client[self.db_name]
        collection = db[chats_collection]
        await collection.create_indexes([IndexModel('chat_id'),
                                         IndexModel('user')])
        return collection

    def _get_messages_collection(self) -> Collection:
        client = self.initialize()
        db = client[self.db_name]
        collection = db[messages_collection]
        collection.create_indexes([IndexModel('message_id'),
                                   IndexModel('chat_id')])
        return collection

    async def _get_messages_collection_async(self) -> AsyncCollection:
        client = await self.initialize_async()
        db = client[self.db_name]
        collection = db[messages_collection]
        await collection.create_indexes([IndexModel('message_id'),
                                         IndexModel('chat_id')])
        return collection

    def drop_collections(self):
        try:
            client = self.initialize()
            db = client[self.db_name]
            db.drop_collection(chats_collection)
            db.drop_collection(messages_collection)
        except BaseException as e:
            self.logger.error('error dropping collections',
                              exc_info=e)

    # sync methods

    def add_chat(self, chat: Chat) -> Chat:
        chats = self._get_chats_collection()
        chats.insert_one(chat.to_dict(dates_mode='date'))
        return chat

    def get_chat(self, chat_id: str) -> Chat:
        chats = self._get_chats_collection()
        document = chats.find_one({"chat_id": chat_id})
        if document:
            return Chat.from_dict(document)
        raise RuntimeError(f'chat #{chat_id} is not found')

    def save_chat(self, chat: Chat) -> Chat:
        chats = self._get_chats_collection()
        chats.update_one(filter={"chat_id": chat.chat_id},
                         update={"$set": chat.to_dict(dates_mode='date')},
                         upsert=True)
        return chat

    def save_message(self, chat_id: str, message: Message) -> Message:
        messages = self._get_messages_collection()
        if not message.created_date:
            message.created_date = datetime.now()
        message.chat_id = chat_id
        messages.insert_one(message.to_dict(dates_mode='date'))
        return message

    def get_message(self, message_id: str) -> Message:
        messages = self._get_messages_collection()
        document = messages.find_one({"message_id": message_id})
        if document:
            return Message.from_dict(document)
        raise RuntimeError(f'message #{message_id} is not found')

    def get_messages(self, chat_id: str) -> list[Message]:
        messages = self._get_messages_collection()
        cursor = messages.find({"chat_id": chat_id})
        if cursor is None:
            return []
        cursor = cursor.sort("created_date", 1)
        result = cursor.to_list()
        return [Message.from_dict(doc) for doc in result]


    def delete_message(self, message_id: str):
        messages = self._get_messages_collection()
        messages.delete_one({"message_id": message_id})

    # async methods

    async def add_chat_async(self, chat: Chat) -> Chat:
        chats = await self._get_chats_collection_async()
        await chats.insert_one(chat.to_dict(dates_mode='date'))
        return chat

    async def get_chat_async(self, chat_id: str) -> Chat:
        chats = await self._get_chats_collection_async()
        document = await chats.find_one({"chat_id": chat_id})
        if document:
            return Chat.from_dict(document)
        raise RuntimeError(f'chat #{chat_id} is not found')

    async def save_chat_async(self, chat: Chat) -> Chat:
        chats = await self._get_chats_collection_async()
        await chats.update_one(
            filter={"chat_id": chat.chat_id},
            update={"$set": chat.to_dict(dates_mode='date')},
            upsert=True)
        return chat

    async def save_message_async(self, chat_id: str, message: Message) -> Message:
        messages = await self._get_messages_collection_async()
        message.chat_id = chat_id
        await messages.insert_one(message.to_dict(dates_mode='date'))
        return message

    async def get_message_async(self, message_id: str) -> Message:
        messages = await self._get_messages_collection_async()
        document = await messages.find_one({"message_id": message_id})
        if document:
            return Message.from_dict(document)
        raise RuntimeError(f'message #{message_id} is not found')

    async def get_messages_async(self, chat_id: str) -> list[Message]:
        messages = await self._get_messages_collection_async()
        cursor = messages.find({"chat_id": chat_id})
        if cursor is None:
            return []
        cursor = cursor.sort("created_date", 1)
        result = await cursor.to_list()
        return [Message.from_dict(doc) for doc in result]


    async def delete_message_async(self, message_id: str):
        messages = await self._get_messages_collection_async()
        await messages.delete_one({"message_id": message_id})
