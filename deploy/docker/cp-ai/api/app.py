import argparse
import json
import logging
import os
import sys
from os import mkdir
import socketio
import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from llama_index.core.memory import BaseMemory
from llama_index.llms.google_genai import GoogleGenAI
from llama_index.core.llms import ChatMessage
from llama_index.core.tools import FunctionTool
from llama_index.core.agent import (FunctionCallingAgentWorker, ReActAgent)
from pydantic import Field, BaseModel
from typing import Optional

from api.documents_index import query_documents
import datetime
from datetime import datetime
import sqlite3

ROLE_ASSISTANT = "assistant"

# Configure logging
logging_level = logging.DEBUG
logging_dir = 'logs'
logging_file = 'ai.log'

if not os.path.exists(logging_dir):
    mkdir(logging_dir)

logging_format = '%(asctime)s:%(levelname)s: %(message)s'
logging_formatter = logging.Formatter(logging_format)
logging.getLogger().setLevel(logging_level)
default_logger = logging.getLogger(name="AI")
default_logger.setLevel(logging_level)
console_handler = logging.StreamHandler(sys.stdout)
console_handler.setLevel(logging_level)
console_handler.setFormatter(logging_formatter)
default_logger.addHandler(console_handler)

file_handler = logging.FileHandler(os.path.join(logging_dir, logging_file))
file_handler.setLevel(logging_level)
file_handler.setFormatter(logging_formatter)
default_logger.addHandler(file_handler)

app = FastAPI()
DATABASE = 'chatbot.db'
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

#os.environ["GOOGLE_API_KEY"] = "GOOGLE_API_KEY"  # add your GOOGLE API key here
llm = GoogleGenAI(model=os.environ["GOOGLE_GENAI_MODEL"])

def get_command_to_run_compute_instance(
        docker_image_name: str = Field(
            description="""
        Defines a docker image name to be used for the user's compute task.
        This parameter is optional, if not specified in the user prompt use default value: 'library/rockylinux:latest'.
        """
        ),
        compute_instance_size: str = Field(
            description="""
        Defines AWS EC2 instance type to be created for the user's compute task.
        This parameter is optional, if not specified in the user prompt use default value: 'm5.xlarge'.
        """
        ),
        compute_instance_disk_size: str = Field(
            description="""
        Defines size of the disk provisioned for EC2 instance in gigabytes.
        This parameter is optional, if not specified in the user prompt use default value: '50'.
        """
        ),
        task_command: str = Field(
            description="""
        Defines a shell command used to start the user's task within a docker container.
        This parameter is optional, if not specified in the user prompt use default value: 'sleep infinity'.
        """
        ),
        input_paths: str = Field(
            description="""
        An optional list of AWS S3 paths, which are used by the compute instance.
        This parameter shall be formatted as a JSON array of objects. Each object shall have two fields: 'name' and 'path'.
        Example: 
        '[
            {
                "name": "file_input",
                "path": "s3://bucket_name/file_name.txt"
            },
            {
                "name": "directory_input",
                "path": "s3://bucket_name/directory_name"
            }
        ]'
        This parameter is optional, if not specified in the user prompt use default value: '[]'.
        """
        ),
        output_paths: str = Field(
            description="""
        An optional list of AWS S3 paths, which will be used by the compute instance to upload the results of processing the 'input_paths'.
        This parameter shall be formatted as a JSON array of objects. Each object shall have two fields: 'name' and 'path'. 'path' is always a directory.
        Example: 
        '[
            {
                "name": "directory1_output",
                "path": "s3://bucket_name/directory1_name"
            },
            {
                "name": "directory2_output",
                "path": "s3://bucket_name/directory2_name"
            }
        ]'
        This parameter is optional, if not specified in the user prompt use default value: '[]'.
        """
        )
) -> str:
    """Useful to get a command to start compute instance in AWS.
    Returns json to be used to start a compute task"""

    params = {}

    input_paths_cmd = ""
    if input_paths:
        for path_item in json.loads(input_paths):
            name = path_item['name']
            path = path_item['path']
            input_paths_cmd += f" {name} 'input?{path}' "
            params[name] = {'type': 'input'}

    output_paths_cmd = ""
    if output_paths:
        for path_item in json.loads(output_paths):
            name = path_item['name']
            path = path_item['path']
            output_paths_cmd += f" {name} 'output?{path}' "
            params[name] = {'type': 'output'}

    start_command = {
        "dockerImage": docker_image_name,
        "instanceType": compute_instance_size,
        "disk": compute_instance_disk_size,
        "cmd": task_command,
        "is_spot": False,
        "parameters": params
    }

    json_str = json.dumps(start_command)

    result = f"""Result: <<<LAUNCH:{json_str}>>>. Include this result into response to user."""
    print(result)
    return result

def stop_compute_instance(
        instance_run_id: int = Field(
            description="""
        Run ID of the compute instance.
        This is mandatory field. If it's not specified in the user prompt - reject to stop an instance.
        """
        )
) -> str:
    """Useful to stop an existing compute instance in AWS by it's Run ID."""
    pipe_run_cmd = f"""pipe stop \
                       -y \
                       {instance_run_id}"""
    print(pipe_run_cmd)
    return pipe_run_cmd


def get_compute_instance_state(
        instance_run_id: int = Field(
            description="""
        Run ID of the compute instance to report status.
        If specified as '-1' - all available instances statuses are returned.
        """
        )
) -> str:
    """Useful to get information about compute instances. Either a specific one or all available instances."""
    pipe_run_cmd = "pipe view-runs --parameters-details --tasks-details "
    if instance_run_id != -1:
        pipe_run_cmd += str(instance_run_id)
    print(pipe_run_cmd)
    return pipe_run_cmd

tool_run_compute_instance = FunctionTool.from_defaults(get_command_to_run_compute_instance)
tool_stop_compute_instance = FunctionTool.from_defaults(stop_compute_instance)
tool_get_compute_instance_state = FunctionTool.from_defaults(get_compute_instance_state)
documents_tool = FunctionTool.from_defaults(
    fn=query_documents,
    name="documents_tool",
    description="Searches documents and issues and returns a list of sources."
)

worker = FunctionCallingAgentWorker.from_tools(
    [tool_run_compute_instance, tool_stop_compute_instance, tool_get_compute_instance_state], verbose=True, llm=llm
)

agent = ReActAgent(
    tools=[tool_run_compute_instance, tool_stop_compute_instance, tool_get_compute_instance_state, documents_tool],
    verbose=True,
    llm=llm,
    memory=BaseMemory.from_defaults()
)

class Chat(BaseModel):
    chat_id: Optional[int] = None
    title: Optional[str] = 'Untitled'

class Message(BaseModel):
    message_id: Optional[int] = None
    chat_id: Optional[int] = None
    created_date: Optional[datetime] = None
    role: str
    content: str
    attributes : Optional[dict] = None

def _get_db_connection():
    conn = sqlite3.connect(DATABASE)
    conn.row_factory = sqlite3.Row
    return conn

def _add_chat(chat: Chat):
    conn = _get_db_connection()
    cursor = conn.cursor()
    cursor.execute('INSERT INTO chats (title) VALUES (?)', (chat.title,))
    chat_id = cursor.lastrowid
    conn.commit()
    conn.close()
    return chat_id

def _get_chat(chat_id: int):
    conn = _get_db_connection()
    result = conn.execute('SELECT * FROM chats WHERE chat_id = ?', (chat_id,)).fetchall()
    conn.close()
    return result

def _save_message(chat_id: int, message: Message):
    attributes_json = None
    if message.attributes:
        attributes_json = json.dumps(message.attributes)
    conn = _get_db_connection()
    cursor = conn.cursor()
    cursor.execute('INSERT INTO messages (chat_id, created_date, role, content, attributes) VALUES (?,?,?,?,?)',
                   (chat_id, datetime.now(), message.role, message.content, attributes_json))
    message_id = cursor.lastrowid
    conn.commit()
    conn.close()
    return message_id

def _get_message(message_id: int):
    conn = _get_db_connection()
    result = conn.execute('SELECT * FROM messages WHERE message_id = ?', (message_id,)).fetchall()
    conn.close()
    return result

def _get_messages(chat_id: int):
    conn = _get_db_connection()
    result = conn.execute('SELECT * FROM messages WHERE chat_id = ? ORDER BY created_date ASC', (chat_id,)).fetchall()
    conn.close()
    return result

def _delete_message(message_id: int):
    conn = _get_db_connection()
    conn.execute('DELETE FROM messages WHERE message_id = ?', (message_id,))
    conn.commit()
    conn.close()
    return message_id

@app.post('/chat')
def add_chat(chat: Chat):
    return _add_chat(chat)

@app.get('/chat/{chat_id}')
def get_chat(chat_id: int):
    return _get_chat(chat_id)

@app.post('/chat/{chat_id}/message')
def add_message(chat_id: int, message: Message):
    return _save_message(chat_id, message)

@app.delete('/chat/message/{message_id}')
def delete_message(message_id: int):
    return _delete_message(message_id)

@app.get('/chat/message/{message_id}')
def get_message(message_id: int):
    return _get_message(message_id)

@app.get('/chat/{chat_id}/messages')
def get_messages(chat_id: int):
    return _get_messages(chat_id)

@sio_server.event
def connect(sid: str, env, auth):
    print('connect ', sid)

@sio_server.event
def disconnect(sid: str):
    print('disconnect ', sid)

@sio_server.on('ping')
async def handle_message(sid, data):
    await sio_server.emit('pong', data, to=sid)

@sio_server.on('assistant')
async def handle_message(sid, request_data: dict):
    try:
        chat_id = request_data.get('chat_id')
        chat_messages = _get_messages(chat_id)
        messages = [ ChatMessage.from_str(dict(m)['content'], dict(m)['role']) for m in chat_messages ]
        message = messages.pop().content
        message = f'''
        This is user query, provide an answer for this query using provided agents or general LLM knowledge. 
        Do not use tools if it is not requested directly: {message}. If answer from tools starts with '<<<' sequence, 
        include this answer to response as is.
        '''
        resp = await agent.astream_chat(message=message, chat_history=messages)
        async for chunk in resp.async_response_gen():
            await sio_server.emit('chunk', chunk, to=sid)

        message = Message(
            role=ROLE_ASSISTANT,
            content=resp.response
        )
        _save_message(chat_id, message)

    except Exception as e:
        default_logger.error('error handling user message', exc_info=e)
        await sio_server.emit('error', e.__str__(), to=sid)

    await sio_server.emit('done', 'done', to=sid)

@sio_server.on('assistant_test')
async def handle_message(sid, request_data: dict):
    try:
        messages = [ ChatMessage.from_str(m['content'], m['role']) for m in request_data.get('messages', []) ]
        message=messages.pop().content
        message = f'''
        This is user query, provide an answer for this query using provided agents or general LLM knowledge. 
        Do not use tools if it is not requested directly: {message}. If answer from tools starts with '<<<' sequence, 
        include this answer to response as is.
        '''
        resp = await agent.astream_chat(message=message, chat_history=messages)
        async for chunk in resp.async_response_gen():
            await sio_server.emit('chunk', chunk, to=sid)
    except Exception as e:
        default_logger.error('error handling user message', exc_info=e)
        await sio_server.emit('error', e.__str__(), to=sid)
    await sio_server.emit('done', 'done', to=sid)

app.mount('/', sio_app)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", default=7861)
    args = parser.parse_args()
    default_logger.info(f'launching app at {args.host}:{args.port}')
    uvicorn.run(sio_app,
                host=args.host,
                port=int(args.port),
                loop='asyncio')


if __name__ == '__main__':
    main()