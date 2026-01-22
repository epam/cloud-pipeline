from pydantic import BaseModel
from typing import Optional

class Chat(BaseModel):
    chat_id: Optional[str] = None
    title: Optional[str] = 'Untitled'

