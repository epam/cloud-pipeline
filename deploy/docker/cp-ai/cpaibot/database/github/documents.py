import requests
import os
from pathlib import Path
import zipfile
import io
from cpaibot.common.logger import CpLogger as Logger
from cpaibot.database.github.misc import github_logger
from llama_index.core import SimpleDirectoryReader, Document

default_logger = github_logger

import re
from urllib.parse import urljoin, urlparse


def normalize_markdown_links(
        text: str,
        base_url: str,
        base_image_url: str | None = None,
) -> str:
    """
    Convert relative links in markdown to absolute URLs.

    Args:
        text: Markdown text content
        base_url: Absolute URL of the document
        base_image_url: Absolute URL of the document (for images)

    Returns:
        Text with normalized links
    """

    if not base_image_url:
        base_image_url = base_url

    # Pattern for markdown links: [text](url)
    link_pattern = r'\[([^\]]+)\]\(([^)]+)\)'

    # Pattern for markdown images: ![alt](url)
    image_pattern = r'!\[([^\]]*)\]\(([^)]+)\)'

    def normalize_url(match, is_image=False):
        if is_image:
            alt_text = match.group(1)
            url = match.group(2)
            prefix = '!'
        else:
            link_text = match.group(1)
            url = match.group(2)
            prefix = ''

        # Skip if already absolute URL or anchor link
        if url.startswith(('http://', 'https://', '#', 'mailto:')):
            return match.group(0)

        # Convert relative URL to absolute
        b_url = base_image_url if is_image else base_url
        absolute_url = urljoin(b_url, url)

        if is_image:
            return f'{prefix}[{alt_text}]({absolute_url})'
        else:
            return f'{prefix}[{link_text}]({absolute_url})'

    # Normalize images first (to avoid conflict with link pattern)
    text = re.sub(image_pattern, lambda m: normalize_url(m, is_image=True), text)

    # Normalize links
    text = re.sub(link_pattern, normalize_url, text)

    return text


def get_documents(
        folder: str,
        /,
        repository_owner: str,
        repository: str,
        repository_branch: str,
        repository_docs_path: str,
        logger: Logger | None = None,
        force: bool | None = None,
        normalize_links: bool | None = None,
) -> list[Document]:
    if not logger:
        logger = default_logger
    if force is None:
        force = False
    if normalize_links is None:
        normalize_links = True

    logger.info(f"extracting documents from github repository {repository} (branch {repository_branch})")

    folder = os.path.abspath(folder)
    docs_folder = str(Path(folder) / f"{repository}-{repository_branch}" / repository_docs_path)

    if not os.path.exists(docs_folder) or force:
        zip_url = (f"https://github.com"
                   f"/{repository_owner}"
                   f"/{repository}"
                   f"/archive/refs/heads"
                   f"/{repository_branch}.zip")
        logger.info(f"cloning {zip_url}...")
        r = requests.get(zip_url)
        z = zipfile.ZipFile(io.BytesIO(r.content))
        z.extractall(folder)

    docs = SimpleDirectoryReader(docs_folder,
                                 recursive=True,
                                 exclude_hidden=False).load_data()
    logger.info(f"extracting documents from github repository {repository} "
                f"(branch {repository_branch}): {len(docs)} extracted")

    def _get_document_urls(document: Document) -> tuple[str, str]:
        file_path = document.metadata.get('file_path')
        if not file_path:
            return "", ""
        path = ((file_path
                 .replace(os.path.abspath(docs_folder), ""))
                .replace(os.path.sep, "/"))
        if not path.startswith('/'):
            path = '/' + path
        _url = (f"https://github.com"
                f"/{repository_owner}"
                f"/{repository}"
                f"/tree"
                f"/{repository_branch}"
                f"/{repository_docs_path}"
                f"{path}")
        _raw_url = (f"https://raw.githubusercontent.com/"
                    f"/{repository_owner}"
                    f"/{repository}"
                    f"/refs/heads"
                    f"/{repository_branch}"
                    f"/{repository_docs_path}"
                    f"{path}")
        return _url, _raw_url

    normalized_docs = []

    for doc in docs:
        file_name = doc.metadata.get('file_name', None)
        is_image = False
        if file_name:
            try:
                fn_ext = os.path.splitext(file_name)[1].lower()
                is_image = fn_ext in {'.png', '.jpg', '.jpeg', '.gif', '.webp', '.tiff'}
            except:
                pass
        doc.metadata["title"] = file_name or "No Title"
        url, raw_url = _get_document_urls(doc)
        doc.metadata["url"] = raw_url if is_image else url
        doc.metadata["document_type"] = "github document"

        if normalize_links and hasattr(doc, 'text'):
            normalized_text = normalize_markdown_links(doc.text, url, raw_url)
            new_doc = Document(
                text=normalized_text,
                metadata={
                    **doc.metadata,
                }
            )
            normalized_docs.append(new_doc)
    return normalized_docs
