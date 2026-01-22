from pydantic import BaseModel
from typing import Optional
from datetime import datetime

class Message(BaseModel):
    message_id: Optional[str] = None
    chat_id: Optional[str] = None
    created_date: Optional[datetime] = None
    role: str
    content: str
    attributes : Optional[dict] = None

