from pydantic import BaseModel
from typing import Optional
from llama_index.core import Document


class GithubComment(BaseModel):
    body: str
    author: str | None
    created_at: str
    url: str

    @classmethod
    def from_graphql_object(cls, obj: dict) -> 'GithubComment | None':
        if not obj:
            return None
        return cls(
            body=obj.get("body", ""),
            author=obj.get("author", {}).get("login") if obj.get("author") else None,
            created_at=obj.get("createdAt", ""),
            url=obj.get("url", "")
        )


class GithubIssue(BaseModel):
    identifier: str
    title: str
    body: str
    url: str
    author: str
    closed: bool
    comments: list[GithubComment] | None = []

    @classmethod
    def from_graphql_object(cls, data: dict) -> Optional["GithubIssue"]:
        identifier = data.get("number")
        title = data.get("title")
        body = data.get("body")
        url = data.get("url")
        author = data.get("author", {}).get("login")

        comments_data = data.get("comments", {}).get("nodes", [])
        comments = [GithubComment.from_graphql_object(c) for c in comments_data]
        comments = [c for c in comments if c is not None]

        if identifier is not None and title and body and url and author:
            return GithubIssue(
                identifier=str(identifier),
                title=title,
                body=body,
                url=url,
                author=author,
                comments=comments,
                closed=data.get("closed", False),
            )
        return None

    def to_llama_index_document(self) -> Document:
        text_parts = [
            f"Issue #{self.identifier}: {self.title}",
            f"Author: {self.author}",
            f"Status: {'Closed' if self.closed else 'Open'}",
            f"\n{self.body}\n"
        ]

        if self.comments:
            text_parts.append(f"\n--- Comments ({len(self.comments)}) ---\n")
            for i, comment in enumerate(self.comments, 1):
                author = comment.author or "Unknown"
                text_parts.append(f"\nComment {i} by {author}:")
                text_parts.append(comment.body)

        return Document(
            text="\n".join(text_parts),
            metadata={
                "title": f"Issue #{self.identifier}: {self.title}",
                "url": self.url,
                "document_type": "github issue",
                "issue_number": self.identifier,
                "author": self.author,
                "closed": self.closed,
                "comment_count": len(self.comments)
            }
        )