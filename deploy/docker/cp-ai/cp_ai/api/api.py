import argparse
import socketio
import uvicorn
from http.cookies import SimpleCookie
from fastapi import FastAPI, Depends, Cookie
from fastapi.middleware.cors import CORSMiddleware
from llama_index.core.llms import ChatMessage
from pydantic import BaseModel
from cp_ai.common.types import Chat, Message
import cp_ai.database.chats.methods as chats
from cp_ai.agents import answer_using_default_agent
from cp_ai.api.utilities.logger import api_logger
from cp_ai.api.utilities.decorators import app_response


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

@app.post('/chat')
@app_response(logger=api_logger)
async def add_chat(bearer = Depends(_get_bearer_cookie)):
    return await chats.add_chat_async(Chat.create_chat(bearer=bearer))

@app.get('/chat/{chat_id}')
@app_response(logger=api_logger)
async def get_chat(chat_id: str):
    return await chats.get_chat_async(chat_id)


class MessagePayload(BaseModel):
    content: str
    attributes: dict | None = None


@app.post('/chat/{chat_id}/message')
@app_response(logger=api_logger)
async def add_message(chat_id: str, message: MessagePayload):
    new_message = Message.create_message(
        message.content,
        chat_id,
        attributes=message.attributes
    )
    return await chats.save_message_async(chat_id, new_message)

@app.delete('/chat/message/{message_id}')
@app_response(logger=api_logger)
async def delete_message(message_id: str):
    return await chats.delete_message_async(message_id)

@app.get('/chat/message/{message_id}')
@app_response(logger=api_logger)
async def get_message(message_id: str):
    return await chats.get_message_async(message_id)

@app.get('/chat/{chat_id}/messages')
@app_response(logger=api_logger)
async def get_messages(chat_id: str):
    return await chats.get_messages_async(chat_id)

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
        chat_messages = chats.get_messages(chat_id)
        messages = [ ChatMessage.from_str(dict(m)['content'], dict(m)['role']) for m in chat_messages ]
        resp = await answer_using_default_agent(
            messages=messages,
            bearer=bearer
        )
        async for chunk in resp.async_response_gen():
            await sio_server.emit('chunk', chunk, to=sid)

        message = Message.create_message(
            resp.response,
            chat_id=chat_id,
            is_assistant=True
        )
        await chats.save_message_async(chat_id, message)

    except Exception as e:
        api_logger.error('error handling user message', exc_info=e)
        await sio_server.emit('error', e.__str__(), to=sid)

    await sio_server.emit('done', 'done', to=sid)

@sio_server.on('assistant_test')
async def handle_message(sid, request_data: dict):
    try:
        bearer = await _get_bearer_from_socket_session(sid)
        messages = [ ChatMessage.from_str(m['content'], m['role']) for m in request_data.get('messages', []) ]
        resp = await answer_using_default_agent(
            messages=messages,
            bearer=bearer
        )
        async for chunk in resp.async_response_gen():
            await sio_server.emit('chunk', chunk, to=sid)
    except Exception as e:
        api_logger.error('error handling user message', exc_info=e)
        await sio_server.emit('error', e.__str__(), to=sid)
    await sio_server.emit('done', 'done', to=sid)


class AssistantRequest(BaseModel):
    message: str
    history: list[str] | None = None


@app.post('/assistant')
@app_response(logger=api_logger)
async def assistant_get_message(req: AssistantRequest,
                                bearer = Depends(_get_bearer_cookie)):
    hist = req.history or []
    resp = await answer_using_default_agent(
        messages=[*hist, req.message],
        bearer=bearer
    )
    result = ''
    api_logger.info(f'answering "{req.message}"...')
    async for chunk in resp.async_response_gen():
        result += chunk
    api_logger.info(result)
    return result

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