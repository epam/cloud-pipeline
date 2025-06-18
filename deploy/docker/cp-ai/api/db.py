from pymongo import MongoClient
from datetime import datetime
from bson.objectid import ObjectId
from api import config
from api.models.chat import Chat
from api.models.message import Message


client = MongoClient(config.chatbot_db_uri)
chatbot_db = client[config.chatbot_db_name]
chats = chatbot_db["chats"]
messages = chatbot_db["messages"]


def add_chat(_chat: Chat):
    result = chats.insert_one(dict(_chat))
    return str(result.inserted_id)

def get_chat(_chat_id: str):
    object_id = ObjectId(_chat_id)
    document = chats.find_one({"_id": object_id})
    if document:
        return Chat(
            chat_id=_chat_id,
            title=document["title"]
        )
    else:
        return None

def save_message(_chat_id: str, _message: Message):
    if not _message.created_date:
        _message.created_date = datetime.now()
    _message.chat_id = _chat_id

    result = messages.insert_one(dict(_message))
    return str(result.inserted_id)

def get_message(_message_id: str):
    object_id = ObjectId(_message_id)
    document = messages.find_one({"_id": object_id})
    if document:
        return Message(
            message_id=_message_id,
            chat_id=document["chat_id"],
            content=document["content"],
            attributes=document["attributes"],
            created_date=document["created_date"],
            role=document["role"]
        )
    else:
        return None

def get_messages(_chat_id: str):
    cursor = messages.find({"chat_id": _chat_id}).sort("created_date", 1)
    return [Message(
        message_id=str(doc["_id"]),
        chat_id=doc["chat_id"],
        content=doc["content"],
        attributes=doc["attributes"],
        created_date=doc["created_date"],
        role=doc["role"]
    ) for doc in cursor]


def delete_message(_message_id: str):
    object_id = ObjectId(_message_id)
    messages.delete_one({"_id": object_id})


if __name__ == '__main__':
    get_chat("68480f61968990dba14cb7c9")