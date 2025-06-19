from .context import ChatsDatabaseConnection
from cp_ai.common.types import Chat, Message


def add_chat(chat: Chat) -> Chat:
    with ChatsDatabaseConnection() as reader:
        return reader.add_chat(chat)

async def add_chat_async(chat: Chat) -> Chat:
    async with ChatsDatabaseConnection() as reader:
        return await reader.add_chat_async(chat)

def get_chat(chat_id: str) -> Chat:
    with ChatsDatabaseConnection() as reader:
        return reader.get_chat(chat_id)

async def get_chat_async(chat_id: str) -> Chat:
    async with ChatsDatabaseConnection() as conn:
        return await conn.get_chat_async(chat_id)

def save_message(chat_id: str, message: Message) -> Message:
    with ChatsDatabaseConnection() as reader:
        chat = reader.get_chat(chat_id)
        if chat is None:
            raise RuntimeError(f'chat #{chat_id} not found')
        chat.update_timestamp()
        reader.save_chat(chat)
        return reader.save_message(chat_id, message)

async def save_message_async(chat_id: str, message: Message) -> Message:
    async with ChatsDatabaseConnection() as reader:
        chat = await reader.get_chat_async(chat_id)
        if chat is None:
            raise RuntimeError(f'chat #{chat_id} not found')
        chat.update_timestamp()
        await reader.save_chat_async(chat)
        return await reader.save_message_async(chat_id, message)

def get_message(message_id: str) -> Message | None:
    with ChatsDatabaseConnection() as reader:
        return reader.get_message(message_id)

async def get_message_async(message_id: str) -> Message | None:
    async with ChatsDatabaseConnection() as reader:
        return await reader.get_message_async(message_id)

def get_messages(chat_id: str) -> list[Message]:
    with ChatsDatabaseConnection() as reader:
        return reader.get_messages(chat_id)

async def get_messages_async(chat_id: str) -> list[Message]:
    async with ChatsDatabaseConnection() as reader:
        return await reader.get_messages_async(chat_id)

def delete_message(message_id: str):
    with ChatsDatabaseConnection() as reader:
        reader.delete_message(message_id)

async def delete_message_async(message_id: str):
    async with ChatsDatabaseConnection() as reader:
        await reader.delete_message_async(message_id)