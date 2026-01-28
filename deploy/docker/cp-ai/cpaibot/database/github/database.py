import chromadb
import time

from cpaibot.common.logger import CpLogger as Logger
from cpaibot.common.settings import settings
from cpaibot.database.github.misc import github_logger
from cpaibot.llm.base import embed_model

from llama_index.core import VectorStoreIndex, StorageContext
from llama_index.vector_stores.chroma import ChromaVectorStore

from .documents import get_documents
from .issues import get_issues

from google.genai.errors import ClientError

default_logger = github_logger

GITHUB_DOCUMENTS_COLLECTION_NAME = "github"


def create_github_documents_db(
        logger: Logger | None = None,
        /,
):
    if not logger:
        logger = default_logger
    logger.info(f'github documents database: initializing...')
    if settings.CHROMA_DB_PATH is None:
        raise RuntimeError('CHROMA_DB_PATH is not set')
    logger.info(f'github documents database: db path {settings.CHROMA_DB_PATH}.')
    chroma_client = chromadb.PersistentClient(path=settings.CHROMA_DB_PATH)
    collections = chroma_client.list_collections()
    if any(c.name == GITHUB_DOCUMENTS_COLLECTION_NAME for c in collections):
        logger.info(f'github documents database: already initialized.')
        return

    if settings.GITHUB_REPO is None:
        raise RuntimeError('GITHUB_REPO is not set')
    if settings.GITHUB_REPO_OWNER is None:
        raise RuntimeError('GITHUB_REPO_OWNER is not set')
    repository = settings.GITHUB_REPO
    owner = settings.GITHUB_REPO_OWNER
    branch = settings.GITHUB_REPO_BRANCH
    docs_path = settings.GITHUB_REPO_DOCS_PATH
    logger.info(f'github documents database: repository "{repository}".')
    logger.info(f'github documents database: repository owner "{owner}".')
    logger.info(f'github documents database: repository branch "{branch}".')
    logger.info(f'github documents database: docs path "{docs_path}".')
    logger.info(f'github documents database: fetching issues...')
    issues = get_issues(repository=repository,
                        repository_owner=owner,
                        logger=logger)
    logger.info(f'github documents database: fetching documents...')
    documents = get_documents(settings.GITHUB_CLONE_TMP_PATH,
                              repository=repository,
                              repository_owner=owner,
                              repository_branch=branch,
                              repository_docs_path=docs_path,
                              logger=logger)

    all_documents = [
        *issues,
        *documents,
    ]

    logger.info(f'github documents database: {len(all_documents)} documents extracted.')
    logger.info(f'github documents database: creating "{GITHUB_DOCUMENTS_COLLECTION_NAME}" collection...')
    chroma_collection = chroma_client.create_collection(GITHUB_DOCUMENTS_COLLECTION_NAME)
    logger.info(f'github documents database: initializing vector store...')
    vector_store = ChromaVectorStore(chroma_collection=chroma_collection)
    logger.info(f'github documents database: initializing storage context...')
    storage_context = StorageContext.from_defaults(vector_store=vector_store)
    logger.info(f'github documents database: initializing vector store index...')
    index = VectorStoreIndex.from_documents(
        [],
        storage_context=storage_context,
        embed_model=embed_model
    )
    logger.info(f'github documents database: storing {len(all_documents)} documents...')
    batch_size = 100
    batches_count = (len(all_documents) + batch_size - 1) // batch_size
    max_retries = 10
    retry_delay_seconds = 3
    for batch_idx in range(batches_count):
        start = batch_idx * batch_size
        end = min(start + batch_size, len(all_documents))
        batch = all_documents[start:end]
        nodes = [doc.to_node() if hasattr(doc, 'to_node') else doc for doc in batch]

        valid_nodes = []
        skipped_count = 0

        for i, node in enumerate(nodes):
            # Check if node has text content
            text = getattr(node, 'text', None) or getattr(node, 'get_content', lambda: '')()
            if text and text.strip():
                valid_nodes.append(node)
            else:
                skipped_count += 1
                doc_idx = start + i
                file_name = node.metadata.get('file_name', None)
                doc_name = file_name or f"#{doc_idx}"
                logger.warning(f'github documents database: skipping document {doc_name} - empty text content')

        if not valid_nodes:
            logger.info(f'github documents database: batch {batch_idx + 1}/{batches_count} - all {len(nodes)} documents had empty content, skipping batch')
            continue

        logger.info(f'github documents database: storing batch {batch_idx + 1}/{batches_count} ({start}...{end}) - {len(valid_nodes)} valid nodes, {skipped_count} skipped')
        for attempt in range(max_retries):
            try:
                index.insert_nodes(valid_nodes)
            except ClientError as e:
                if e.code == 429 or e.status == "RESOURCE_EXHAUSTED":
                    if attempt < max_retries - 1:
                        delay = retry_delay_seconds * (2 ** attempt)  # Exponential backoff
                        logger.warning(f'github documents database: storing batch {batch_idx + 1}/{batches_count} error,'
                                       f' rate limit exceeded after {max_retries} attempts')
                        time.sleep(delay)
                    else:
                        logger.error(f'github documents database: storing batch {batch_idx + 1}/{batches_count} error,'
                                     f' rate limit exceeded after {max_retries} attempts')
                        raise  # Re-raise after all retries exhausted
                else:
                    # If it's a different error, raise immediately
                    logger.error(f'github documents database: storing batch {batch_idx + 1}/{batches_count} error',
                                 exception=e)
                    raise
            except Exception as e:
                logger.error(f'github documents database: storing batch {batch_idx + 1}/{batches_count} error',
                             exception=e)

    logger.info(f'github documents database: initialized.')
