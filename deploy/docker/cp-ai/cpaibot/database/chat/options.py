from typing import Generic, TypeVar, Self
from pydantic import BaseModel
from cpaibot.common.model.chat import (
    ChatElementBaseModel,
    Chat,
)


T = TypeVar("T", bound=ChatElementBaseModel)


class PaginatedRequest(BaseModel):
    page: int | None = None
    page_size: int | None = None

class ChatsRequest(PaginatedRequest):
    user: str | None = None
    # ------------------------------------------------------------------
    # responses
class PaginatedResponse(BaseModel, Generic[T]):
    page: int
    page_size: int
    total: int
    elements: list[T]

    @classmethod
    def build(cls: Self, page: int, page_size: int, total: int, elements: list[T] | None = None) -> Self:
        return cls(page=page, page_size=page_size, total=total, elements=elements or [])

    def serialize(self) -> Self:
        md = self.model_dump(
            mode="json",
            exclude={"elements"}
        )
        md.update({"elements": [o.serialize() for o in self.elements]})
        return md

class ChatsResponse(PaginatedResponse[Chat]):
    ...
