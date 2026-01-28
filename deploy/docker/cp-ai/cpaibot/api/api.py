import argparse
import socketio
import uvicorn
from multiprocessing import Process
from threading import Thread
from http.cookies import SimpleCookie
from fastapi import FastAPI, Depends, Cookie
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from cpaibot.common.model.chat import Chat, MessagePartType
from cpaibot.common.settings import settings
from cpaibot.database.chat import ChatsManager, ChatsRequest
from cpaibot.managers.chat import (create_chat_message_async,
                                   create_chat_async,
                                   generate_empty_response_message_async,
                                   subscribe_to_chat_message_update_async,
                                   load_message_details_async,
                                   load_conversation_async,
                                   create_chat_message_part_async,
                                   update_message_part_async)
from cpaibot.agents.agents import process_assistant_message
from cpaibot.api.utilities.logger import api_logger
from cpaibot.api.utilities.decorators import app_response
from cpaibot.common.utils import get_username_from_bearer


app = FastAPI()
sio_server = socketio.AsyncServer(
    async_mode='asgi',
    cors_allowed_origins='*'
)
sio_app = socketio.ASGIApp(
    socketio_server=sio_server,
    other_asgi_app=app
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=['*'],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

def _get_bearer_cookie(
        bearer: str | None = Cookie(None),
        Bearer: str | None = Cookie(None)
) -> str | None:
    return bearer or Bearer

@app.post('/chats')
@app_response(logger=api_logger)
async def list_chats(body: ChatsRequest, bearer = Depends(_get_bearer_cookie)):
    async with ChatsManager() as chats:
        return await chats.list_chats_async(body)


@app.post('/chats/my')
@app_response(logger=api_logger)
async def list_my_chats(body: ChatsRequest, bearer = Depends(_get_bearer_cookie)):
    async with ChatsManager() as chats:
        if not body.user:
            body.user = get_username_from_bearer(bearer)
        return await chats.list_chats_async(body)


@app.post('/chat')
@app_response(logger=api_logger)
async def add_chat(bearer = Depends(_get_bearer_cookie)):
    return await create_chat_async(user=get_username_from_bearer(bearer))

@app.get('/chat/{chat_id}')
@app_response(logger=api_logger)
async def get_chat(chat_id: str):
    async with ChatsManager() as chats:
        return await chats.get_chat_async(chat_id)


class MessagePayload(BaseModel):
    text: str | None = None
    type: str | None = None
    data: dict | None = None
    metadata: dict | None = None


@app.post('/chat/{chat_id}/message')
@app_response(logger=api_logger)
async def add_message(chat_id: str, message: MessagePayload, bearer = Depends(_get_bearer_cookie)):
    async with ChatsManager() as chats:
        chat = await chats.get_chat_async(chat_id)
        if chat is None:
            raise RuntimeError(f"Chat {chat_id} not found")
        if chat.active_branch_id:
            active_conversation = await chats.get_branch_async(chat.active_branch_id)
            if active_conversation is not None and active_conversation.pending:
                raise RuntimeError(f"Unable to submit message: current conversation is pending")
    new_message = await create_chat_message_async(
        chat=chat_id,
        user=get_username_from_bearer(bearer),
    )

    if not message.type:
        message.type = MessagePartType.TEXT

    message_type = MessagePartType(message.type)

    if not message.text and message_type == MessagePartType.TEXT:
        raise RuntimeError(f"Message should not be empty")

    new_part = await create_chat_message_part_async(
        new_message,
        text=message.text,
        part_type=message_type
    )

    if message.metadata:
        if not new_part.metadata:
            new_part.metadata = {}
        new_part.metadata.update(message.metadata)

    if message.data:
        if not new_part.data:
            new_part.data = {}
        new_part.data.update(message.data)

    await update_message_part_async(new_part)

    assistant_message = await generate_empty_response_message_async(
        new_message,
    )

    if assistant_message:
        if settings.CP_SUBMIT_ASSISTANT_AS_PROCESS:
            p = Process(
                target=process_assistant_message,
                args=(assistant_message.identifier,),
                daemon=True  # optional
            )
            p.start()
        else:
            t = Thread(
                target=process_assistant_message,
                args=(assistant_message.identifier,),
                daemon=True  # optional
            )
            t.start()
        assistant_message = await load_message_details_async(assistant_message, force=True)

    new_message = await load_message_details_async(new_message, force=True)

    return {
        "user": new_message.to_json(),
        "assistant": assistant_message.to_json() if assistant_message else None,
    }

@app.delete('/chat/message/{message_id}')
@app_response(logger=api_logger)
async def delete_message(message_id: str):
    async with ChatsManager() as chats:
        return await chats.delete_message_async(message_id)

@app.get('/chat/message/{message_id}')
@app_response(logger=api_logger)
async def get_message(message_id: str):
    async with ChatsManager() as chats:
        return await chats.get_message_async(message_id)

@app.get('/chat/{chat_id}/messages')
@app_response(logger=api_logger)
async def get_messages(chat_id: str):
    return await load_conversation_async(chat=chat_id)

@sio_server.event
async def connect(sid: str, env: dict, auth):
    headers = env.get('asgi.scope', {}).get('headers', [])
    bearer_token: str | None = None
    for header, value in headers:
        if isinstance(header, bytes):
            header = header.decode()
        if isinstance(value, bytes):
            value = value.decode()
        if str(header).lower() == 'cookie':
            cookie = SimpleCookie(str(value))
            bearer = cookie.get('bearer')
            bearer_token = bearer.value if bearer else None
    api_logger.info(f'client {sid} connected')
    api_logger.info(f'client {sid} bearer token: {repr(bearer_token)}')
    await sio_server.save_session(sid, {'bearer': bearer_token})

@sio_server.event
async def disconnect(sid: str):
    api_logger.info(f'client {sid} disconnected')

@sio_server.on('ping')
async def handle_message(sid, data):
    api_logger.info(f'client {sid}: ping')
    await sio_server.emit('pong', data, to=sid)

async def _get_bearer_from_socket_session(sid: str) -> str | None:
    session = await sio_server.get_session(sid)
    if not isinstance(session, dict):
        session = {}
    return session.get('bearer', None)

@sio_server.on('assistant')
async def handle_message(sid, request_data: dict):
    try:
        bearer = await _get_bearer_from_socket_session(sid)
        api_logger.info(f'socket "assistant" event:\nsid: {sid}\nbearer: {bearer}\n\nPayload:\n{repr(request_data)}')
        chat_id = request_data.get('chat_id')
        if chat_id is None:
            raise RuntimeError('chat identifier is not specified')
        message_id = request_data.get('message_id')
        if message_id is None:
            raise RuntimeError('message identifier is not specified')
        async def sio_callback(ev, data):
            api_logger.info(f'socket "assistant" event (sid {sid}): sending "{ev}" event')
            await sio_server.emit(ev, data, to=sid)
        await subscribe_to_chat_message_update_async(
            message_id,
            sio_callback
        )

    except Exception as e:
        api_logger.error('error handling user message', exc_info=e)
        await sio_server.emit('error', e.__str__(), to=sid)

    await sio_server.emit('done', 'done', to=sid)
    api_logger.info(f'socket "assistant" event (sid {sid}) done')


class AssistantRequest(BaseModel):
    message: str
    history: list[str] | None = None


app.mount('/', sio_app)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", default=7861)
    args = parser.parse_args()
    api_logger.info(f'launching app at {args.host}:{args.port}')
    uvicorn.run(sio_app,
                host=args.host,
                port=int(args.port),
                loop='asyncio')


if __name__ == '__main__':
    main()