import asyncio
import time
from typing import Any

from cp_ai.agents import answer_using_default_agent_sync
import cp_ai.database.chats.methods as chats
from cp_ai.common.types import Message
from celery import Celery, current_task
from celery.result import AsyncResult
from cp_ai.common.settings import cp_ai_settings

import redis
import redis.asyncio as aioredis

cp_ai_service_broker = cp_ai_settings.CP_AI_SERVICE_BROKER
cp_ai_service_backend = cp_ai_settings.CP_AI_SERVICE_BACKEND
celery_app = Celery(
    "worker",
    broker=cp_ai_service_broker,
    backend=cp_ai_service_backend
)
celery_app.conf.update(
    task_track_started=True
)
r = redis.Redis()
aior = aioredis.Redis()

@celery_app.task
def assist(messages, chat_id, bearer):
    task_id = current_task.request.id
    resp = answer_using_default_agent_sync(
        messages=messages,
        bearer=bearer
    )
    for chunk in resp.response_gen:
        r.rpush(task_id, chunk)
        print(chunk)
    r.rpush(task_id, "done")
    message = Message.create_message(
        resp.response,
        chat_id=chat_id,
        is_assistant=True
    )
    chats.save_message(chat_id, message)

async def stream_celery_task_response(task_id):
    start = time.time()
    while True:
        if time.time() - start > cp_ai_settings.CP_AI_RESPONSE_TIMEOUT:
            raise asyncio.TimeoutError("llm getting result timed out")
        chunk = await aior.lpop(task_id)
        if chunk is None:
            await asyncio.sleep(0.5)
            continue
        if "done" == chunk.decode():
            break
        yield chunk

def get_celery_task_status(task_id: str) -> dict[str, Any]:
    result = AsyncResult(task_id)
    return {"status": result.status}
