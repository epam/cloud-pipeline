from .chats import ChatsDatabaseConnection
from .documents import (query_documents,
                        search_platform_documents_and_issues,
                        create_index as create_documents_index)


__all__ = [
    "query_documents",
    "search_platform_documents_and_issues",
    "create_documents_index",
    "ChatsDatabaseConnection"
]
